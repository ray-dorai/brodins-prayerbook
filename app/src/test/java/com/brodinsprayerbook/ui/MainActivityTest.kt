package com.brodinsprayerbook.ui

import android.app.AlarmManager
import android.app.Application
import android.content.Context
import android.content.DialogInterface.BUTTON_POSITIVE
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.Looper
import android.os.SystemClock
import android.os.Vibrator
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.core.view.children
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.brodinsprayerbook.AppStateRule
import com.brodinsprayerbook.R
import com.brodinsprayerbook.all
import com.brodinsprayerbook.context
import com.brodinsprayerbook.data.ActualSetWithName
import com.brodinsprayerbook.data.MaxHistoryRow
import com.brodinsprayerbook.data.Maxes
import com.brodinsprayerbook.data.PrayerBookDb
import com.brodinsprayerbook.data.ProgramCsv
import com.brodinsprayerbook.data.ProgramDay
import com.brodinsprayerbook.data.ProgramPorter
import com.brodinsprayerbook.idle
import com.brodinsprayerbook.items
import com.brodinsprayerbook.latestDialog
import com.brodinsprayerbook.message
import com.brodinsprayerbook.pick
import com.brodinsprayerbook.tap
import com.brodinsprayerbook.title
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowToast
import java.io.File
import java.time.Duration
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * The workout screen, driven the way a lifter drives it. Every test starts with one small
 * program, two days long, and a 100 lb overhead press max:
 *
 *   Press Day: Overhead Press 2×5 @ 50%, 1×5+ @ 80% (rest 1:30), KB Row 1×8-12/side @ 35
 *   Squat Day: Squat 1×5 @ 100
 */
@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule val appState = AppStateRule()

    private val dao get() = PrayerBookDb.get(context).dao()
    private val prefs get() = context.getSharedPreferences("prayerbook", Context.MODE_PRIVATE)
    private val today = LocalDate.now().toString()
    private var program = 0L
    private var press = 0L
    private lateinit var days: List<ProgramDay>
    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var activity: MainActivity

    @Before fun aSmallProgram() {
        ProgramPorter.importCsv(dao, "Test Block", ProgramCsv.parse(ProgramCsv.HEADER + "\n" +
            "1,1,Press Day,Overhead Press,2,5,50% 1RM,90,\n" +
            "1,1,Press Day,Overhead Press,1,5+,80% 1RM,90,\n" +
            "1,1,Press Day,KB Row,1,8-12/side,35,,\n" +
            "1,2,Squat Day,Squat,1,5,100,,\n"))
        program = dao.allPrograms().single().id
        days = dao.daysForProgram(program)
        press = dao.allExercises().single { it.name == "Overhead Press" }.id
        Maxes.set(dao, press, 100.0, "2026-01-01", "onboarding")
    }

    @After fun close() { if (::scenario.isInitialized) scenario.close() }

    private fun launch(intent: Intent = Intent(context, MainActivity::class.java)) {
        scenario = ActivityScenario.launch(intent)
        idle()
        scenario.onActivity { activity = it }
    }

    // --- What is on screen ---

    private fun <T : View> view(id: Int): T = activity.findViewById(id)
    private fun text(id: Int) = view<TextView>(id).text.toString()
    private fun exercises() = view<LinearLayout>(R.id.exerciseContainer).children.toList()
    private fun exercise(name: String) = exercises().first { it.findViewById<TextView>(R.id.exerciseName).text.toString() == name }
    /** The nth set row of an exercise, counting from 1. */
    private fun row(name: String, n: Int): View = exercise(name).findViewById<LinearLayout>(R.id.setsContainer).getChildAt(n - 1)
    private fun View.planned() = findViewById<TextView>(R.id.plannedLabel).text.toString()
    private fun View.weight() = findViewById<EditText>(R.id.inputWeight)
    private fun View.reps() = findViewById<EditText>(R.id.inputReps)
    private fun View.noose() = findViewById<View>(R.id.btnSaveSet)
    private fun tap(id: Int) { view<View>(id).performClick(); idle() }
    private fun View.tap() { performClick(); idle() }
    private fun selectedDay() = view<Spinner>(R.id.daySpinner).selectedItem as String
    private fun selectDay(index: Int) { view<Spinner>(R.id.daySpinner).setSelection(index); idle() }
    private fun toast() = ShadowToast.getTextOfLatestToast()
    private fun pass(seconds: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(seconds))

    // --- What is in the database ---

    private fun log(day: Int) = dao.logFor(today, days[day].id)!!
    private fun logged(day: Int, exercise: String, set: Int): ActualSetWithName =
        dao.actualSetsForLog(log(day).id).single { it.exerciseName == exercise && it.setNumber == set }
    private fun state() = dao.stateFor(program)!!
    private fun pressMax() = dao.maxFor(press)!!.oneRepMax

    // --- The day's prescription ---

    @Test fun itOpensOnTheSessionDueWithWeightsWorkedOutFromTheMax() {
        launch()

        assertTrue(text(R.id.dateHeader), text(R.id.dateHeader).endsWith("$today\nCYCLE 1  ·  WEEK 1 OF 1"))
        assertEquals("Press Day", selectedDay())
        assertEquals(listOf("Overhead Press", "KB Row"),
            exercises().map { it.findViewById<TextView>(R.id.exerciseName).text.toString() })
        assertEquals("50 × 5", row("Overhead Press", 1).planned())
        assertEquals("80 × 5+", row("Overhead Press", 3).planned())
        assertEquals("35 × 8-12/side", row("KB Row", 1).planned())
        assertEquals("Finish Session", text(R.id.btnFinish))
    }

    @Test fun theProgramLastUsedIsTheOneItReopensOn() {
        ProgramPorter.importCsv(dao, "Another", ProgramCsv.parse("exercise,reps\nPull-Up,8\n"))
        prefs.edit().putLong("activeProgramId", program).commit()

        launch()

        assertEquals("Test Block", view<Spinner>(R.id.programSpinner).selectedItem)
        assertEquals("Overhead Press", exercises().first().findViewById<TextView>(R.id.exerciseName).text.toString())
    }

    @Test fun lastTimesNumbersAreShownBesideTheExercise() {
        launch()
        row("Overhead Press", 1).noose().tap()
        row("Overhead Press", 2).noose().tap()
        // The same session, looked at again on a later visit
        dao.updateWorkoutLog(log(0).copy(date = "2026-01-05"))
        scenario.recreate()
        idle()
        scenario.onActivity { activity = it }

        assertEquals("Last (2026-01-05):  50×5 ×2", exercise("Overhead Press").findViewById<TextView>(R.id.lastTime).text.toString())
        assertEquals(View.GONE, exercise("KB Row").findViewById<View>(R.id.lastTime).visibility)
    }

    // --- Logging sets ---

    @Test fun theNooseLogsASetAsPrescribed() {
        launch()
        val row = row("Overhead Press", 1)
        assertEquals("50", row.weight().hint.toString())
        assertFalse(row.noose().isSelected)

        row.noose().tap()

        val set = logged(0, "Overhead Press", 1)
        assertEquals(listOf(50.0, 5, 50.0, "5"), listOf(set.weight, set.reps, set.plannedWeight, set.plannedReps))
        assertEquals("50", row.weight().text.toString())
        assertTrue(row.noose().isSelected)
        assertNull("the other sets stay open", logged(0, "Overhead Press", 2).reps)
    }

    @Test fun whatIsTypedIsWhatTheNooseLogs() {
        launch()
        val row = row("Overhead Press", 1)
        row.weight().setText("47.5")
        row.reps().setText("4")
        row.findViewById<EditText>(R.id.inputRpe).setText("8.5")

        row.noose().tap()

        val set = logged(0, "Overhead Press", 1)
        assertEquals(listOf(47.5, 4, 8.5), listOf(set.weight, set.reps, set.rpe))
        assertEquals("the prescription is kept beside what was done", 50.0, set.plannedWeight!!, 0.0)
    }

    @Test fun typingAloneSavesWithoutTheNoose() {
        launch()
        row("Overhead Press", 2).weight().setText("52.5")
        row("Overhead Press", 2).reps().setText("5")

        val set = logged(0, "Overhead Press", 2)
        assertEquals(listOf(52.5, 5), listOf(set.weight, set.reps))
        assertTrue(text(R.id.restTimer), text(R.id.restTimer).startsWith("Rest  "))
    }

    @Test fun theNooseWillNotGuessRepsForAnAmrapOrARange() {
        launch()

        row("Overhead Press", 3).noose().tap()
        assertEquals("Enter the reps you got, then tap again.", toast())
        assertNull(logged(0, "Overhead Press", 3).reps)

        row("KB Row", 1).noose().tap()
        assertNull("sets are numbered through the day, so this is the fourth", logged(0, "KB Row", 4).reps)
        assertTrue("no rest for a set that was not logged", text(R.id.restTimer).startsWith("Rest  "))
    }

    @Test fun aLoggedSetIsStillThereWhenTheScreenIsRebuilt() {
        launch()
        row("Overhead Press", 1).noose().tap()

        scenario.recreate()
        idle()
        scenario.onActivity { activity = it }

        assertEquals("50", row("Overhead Press", 1).weight().text.toString())
        assertEquals("5", row("Overhead Press", 1).reps().text.toString())
        assertTrue(row("Overhead Press", 1).noose().isSelected)
        assertEquals(1, dao.sessions().size)
    }

    @Test fun aNoteIsSavedWithItsSet() {
        launch()
        val row = row("Overhead Press", 1)

        row.findViewById<View>(R.id.btnNote).tap()
        latestDialog().window!!.decorView.all<EditText>().single().setText("  left shoulder tight ")
        latestDialog().tap(BUTTON_POSITIVE)

        assertEquals("left shoulder tight", logged(0, "Overhead Press", 1).notes)
        assertTrue(row.findViewById<View>(R.id.btnNote).isSelected)
    }

    // --- Maxes ---

    @Test fun anAmrapThatBeatsTheMaxOffersToRaiseIt() {
        launch()
        row("Overhead Press", 3).reps().setText("10")

        row("Overhead Press", 3).noose().tap()

        // 80 × 10 estimates to 106.7, rounded to the nearest 5
        assertEquals("New PR Detected!", latestDialog().title())
        assertTrue(latestDialog().message(), latestDialog().message().startsWith("Overhead Press: estimated 1RM = 105 lb\n(80 × 10)"))
        assertEquals(100.0, pressMax(), 0.0)

        latestDialog().tap(BUTTON_POSITIVE)

        assertEquals(105.0, pressMax(), 0.0)
        assertEquals(MaxHistoryRow(today, "Overhead Press", 105.0, "amrap"), dao.maxHistoryForExport().last())
        assertEquals("Press Day", selectedDay())
        assertEquals("85 × 5+", row("Overhead Press", 3).planned())
        assertEquals("the set already done keeps what was prescribed then", 80.0, logged(0, "Overhead Press", 3).plannedWeight!!, 0.0)
    }

    @Test fun anAmrapWithinTheMaxAsksNothing() {
        launch()
        row("Overhead Press", 3).reps().setText("5")

        row("Overhead Press", 3).noose().tap()

        assertNull(ShadowDialog.getLatestDialog())
        assertEquals(100.0, pressMax(), 0.0)
    }

    @Test fun maxesCanBeUpdatedByHand() {
        launch()
        tap(R.id.btnManage)
        latestDialog().pick("Update Maxes")

        latestDialog().window!!.decorView.all<EditText>().single().setText("110")
        latestDialog().tap(BUTTON_POSITIVE)

        assertEquals(110.0, pressMax(), 0.0)
        assertEquals("manual", dao.maxHistoryForExport().last().source)
        assertEquals("55 × 5", row("Overhead Press", 1).planned())
    }

    // --- Finishing a session moves the program on ---

    @Test fun finishingASessionMovesToTheNextDay() {
        launch()
        row("Overhead Press", 1).noose().tap()

        tap(R.id.btnFinish)

        assertTrue(log(0).completed)
        assertEquals(listOf(0, 1, 1), listOf(state().currentWeek, state().currentDay, state().cycleCount))
        assertEquals("Next: Squat Day", toast())
        assertEquals("Squat Day", selectedDay())
        assertEquals("100 × 5", row("Squat", 1).planned())
        assertEquals("Finish Session", text(R.id.btnFinish))
    }

    @Test fun finishingTheLastDayStartsTheNextCycleAndRaisesMaxesToWhatWasLifted() {
        launch()
        // 100 × 3 estimates to 110, above the 100 max
        row("Overhead Press", 1).weight().setText("100")
        row("Overhead Press", 1).reps().setText("3")
        tap(R.id.btnFinish)
        row("Squat", 1).noose().tap()

        tap(R.id.btnFinish)

        assertEquals("Cycle 1 complete", latestDialog().title())
        assertEquals("New maxes from what you lifted:\n\nOverhead Press: 100 → 110\n\nCycle 2 begins at week 1.", latestDialog().message())
        assertEquals(110.0, pressMax(), 0.0)
        assertEquals(MaxHistoryRow(today, "Overhead Press", 110.0, "cycle"), dao.maxHistoryForExport().last())
        assertEquals(listOf(0, 0, 2), listOf(state().currentWeek, state().currentDay, state().cycleCount))
        assertTrue(text(R.id.dateHeader).contains("CYCLE 2"))
        assertEquals("Press Day", selectedDay())
        assertEquals("55 × 5", row("Overhead Press", 1).planned())
    }

    @Test fun aCycleThatBeatNoMaxLeavesThemAlone() {
        launch()
        row("Overhead Press", 1).noose().tap()
        tap(R.id.btnFinish)

        tap(R.id.btnFinish)

        assertTrue(latestDialog().message(), latestDialog().message().startsWith("No lift beat its current max"))
        assertEquals(100.0, pressMax(), 0.0)
        assertEquals(2, state().cycleCount)
    }

    @Test fun aFinishedSessionCanBeReopened() {
        launch()
        tap(R.id.btnFinish)
        selectDay(0)
        assertEquals("Finished ✓", text(R.id.btnFinish))

        tap(R.id.btnFinish)
        assertEquals("Reopen this session?", latestDialog().title())
        latestDialog().tap(BUTTON_POSITIVE)

        assertFalse(log(0).completed)
        assertEquals(0, state().currentDay)
        assertEquals("Press Day", selectedDay())
        assertEquals("Finish Session", text(R.id.btnFinish))
    }

    @Test fun redoingAnEarlierSessionDoesNotMoveTheProgramBack() {
        dao.upsertState(com.brodinsprayerbook.data.UserProgramState(programId = program, currentDay = 1))
        launch()
        assertEquals("Squat Day", selectedDay())
        selectDay(0)

        tap(R.id.btnFinish)

        assertEquals("Session recorded.", toast())
        assertTrue(log(0).completed)
        assertEquals(1, state().currentDay)
        assertEquals("Press Day", selectedDay())
    }

    @Test fun skippingAWeekPastTheEndStartsTheNextCycle() {
        launch()
        tap(R.id.btnManage)

        latestDialog().pick("Skip to Next Week")

        assertEquals(listOf(0, 0, 2), listOf(state().currentWeek, state().currentDay, state().cycleCount))
    }

    // --- Rest timer ---

    @Test fun loggingASetStartsTheProgramsRestForIt() {
        launch()
        assertEquals("rest 1:30", exercise("Overhead Press").findViewById<TextView>(R.id.restLabel).text.toString())
        assertEquals("rest 2:00", exercise("KB Row").findViewById<TextView>(R.id.restLabel).text.toString())
        assertEquals("Rest  2:00  ·  tap to start", text(R.id.restTimer))

        row("Overhead Press", 1).noose().tap()

        assertEquals("REST  1:30  ·  tap to stop", text(R.id.restTimer))
    }

    @Test fun restCountsDownThenBuzzes() {
        launch()
        row("Overhead Press", 1).noose().tap()

        pass(30)
        assertEquals("REST  1:00  ·  tap to stop", text(R.id.restTimer))
        assertFalse(shadowOf(context.getSystemService(Vibrator::class.java)).isVibrating)

        pass(60)
        assertEquals("REST OVER  ·  tap to restart", text(R.id.restTimer))
        assertTrue(shadowOf(context.getSystemService(Vibrator::class.java)).isVibrating)
    }

    @Test fun theStripStopsAndRestartsTheLastRestUsed() {
        launch()
        row("Overhead Press", 1).noose().tap()

        tap(R.id.restTimer)
        assertEquals("Rest  1:30  ·  tap to start", text(R.id.restTimer))

        tap(R.id.restTimer)
        assertEquals("REST  1:30  ·  tap to stop", text(R.id.restTimer))
    }

    @Test fun theLiftersOwnRestForAnExerciseBeatsThePrograms() {
        launch()
        val label = { exercise("Overhead Press").findViewById<TextView>(R.id.restLabel) }
        assertFalse(label().isSelected)

        label().tap()
        assertEquals("Rest for Overhead Press", latestDialog().title())
        latestDialog().window!!.decorView.all<EditText>().single().setText("45")
        latestDialog().tap(BUTTON_POSITIVE)

        assertEquals(45, dao.allExercises().single { it.id == press }.restSeconds)
        assertEquals("rest 0:45", label().text.toString())
        assertTrue(label().isSelected)
        row("Overhead Press", 1).noose().tap()
        assertEquals("REST  0:45  ·  tap to stop", text(R.id.restTimer))
    }

    @Test fun restDoesNotStartByItselfWhenThatIsSwitchedOff() {
        prefs.edit().putBoolean("restAutoStart", false).commit()
        launch()

        row("Overhead Press", 1).noose().tap()

        assertEquals("Rest  2:00  ·  tap to start", text(R.id.restTimer))
    }

    @Test fun leavingTheAppMidRestHandsTheAlertToTheSystemAndComingBackTakesItOver() {
        val alarms = shadowOf(context.getSystemService(AlarmManager::class.java))
        launch()
        val started = SystemClock.elapsedRealtime()
        row("Overhead Press", 1).noose().tap()
        assertTrue(alarms.scheduledAlarms.isEmpty())

        scenario.moveToState(Lifecycle.State.STARTED)

        assertEquals((started + 90_000).toDouble(), alarms.scheduledAlarms.single().triggerAtMs.toDouble(), 1_000.0)

        // Rest ends while away: the system alarm does the alerting, not the screen
        pass(120)
        assertFalse(shadowOf(context.getSystemService(Vibrator::class.java)).isVibrating)

        scenario.moveToState(Lifecycle.State.RESUMED)
        assertTrue(alarms.scheduledAlarms.isEmpty())
        assertEquals("REST OVER  ·  tap to restart", text(R.id.restTimer))
    }

    @Test fun leavingTheAppWithNoRestRunningSetsNoAlarm() {
        launch()

        scenario.moveToState(Lifecycle.State.STARTED)

        assertTrue(shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms.isEmpty())
    }

    // --- Settings ---

    @Test fun settingsChangeTheDefaultRestAndHowWeightsAreRounded() {
        Maxes.set(dao, press, 105.0, "2026-02-01", "manual")
        launch()
        assertEquals("55 × 5", row("Overhead Press", 1).planned())

        tap(R.id.btnSettings)
        val (seconds, increment) = latestDialog().window!!.decorView.all<EditText>()
        seconds.setText("75")
        increment.setText("2.5")
        latestDialog().tap(BUTTON_POSITIVE)

        assertEquals(75, prefs.getInt("restSeconds", 0))
        assertEquals(2.5f, prefs.getFloat("increment", 0f), 0f)
        assertEquals("Rest  1:15  ·  tap to start", text(R.id.restTimer))
        assertEquals("52.5 × 5", row("Overhead Press", 1).planned())
        assertEquals("rest 1:15", exercise("KB Row").findViewById<TextView>(R.id.restLabel).text.toString())
    }

    // --- Calendar and export ---

    @Test fun theCalendarListsSessionsWithWhatWasLifted() {
        launch()
        row("Overhead Press", 1).noose().tap()
        tap(R.id.btnFinish)
        tap(R.id.btnManage)

        latestDialog().pick("Calendar")

        val weekday = LocalDate.now().dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
        assertEquals(listOf("$today $weekday  ✓\nTest Block · C1 W1 · Press Day"), latestDialog().items())

        latestDialog().pick(today)
        assertEquals("Overhead Press\n   50 × 5", latestDialog().message())
    }

    @Test fun exportSharesTheLogAndTheMaxHistory() {
        launch()
        row("Overhead Press", 1).noose().tap()

        tap(R.id.btnExport)

        val chooser = shadowOf(context as Application).nextStartedActivity
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        @Suppress("DEPRECATION") val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_SEND_MULTIPLE, send.action)
        assertEquals("text/csv", send.type)
        @Suppress("DEPRECATION")
        assertEquals(listOf("prayerbook_log.csv", "prayerbook_maxes.csv"),
            send.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)!!.map { it.lastPathSegment })

        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        val weekday = LocalDate.now().dayOfWeek.getDisplayName(TextStyle.FULL, Locale.US)
        assertEquals("$today,$weekday,Test Block,1,1,Press Day,no,Overhead Press,1,50,5,50,5,,,lb",
            File(dir, "prayerbook_log.csv").readLines().drop(1).single())
        assertEquals("2026-01-01,Overhead Press,100,onboarding,lb", File(dir, "prayerbook_maxes.csv").readLines().drop(1).single())
    }

    // --- Programs in and out ---

    private fun openCsv(fileName: String, csv: String) {
        val uri = Uri.parse("content://some.other.app/$fileName")
        shadowOf(context.contentResolver).registerInputStream(uri, csv.byteInputStream())
        launch(Intent(Intent.ACTION_VIEW, uri, context, MainActivity::class.java))
    }

    @Test fun aProgramCsvOpenedFromAnotherAppIsImportedUnderItsFileName() {
        openCsv("pull_day.csv", "exercise,sets,reps,weight\nPull-Up,3,8,\n")

        assertEquals("Name this program", latestDialog().title())
        assertEquals("pull day", latestDialog().window!!.decorView.all<EditText>().single().text.toString())
        latestDialog().tap(BUTTON_POSITIVE)

        assertEquals("Imported 'pull day': 1 days, 3 sets", toast())
        val imported = dao.allPrograms().single { it.name == "pull day" }
        assertEquals(3, dao.plannedSetsForDay(dao.daysForProgram(imported.id).single().id).size)
    }

    @Test fun importingOverAnExistingProgramAsksFirst() {
        openCsv("anything.csv", "exercise,reps\nPull-Up,8\n")
        latestDialog().window!!.decorView.all<EditText>().single().setText("Test Block")
        latestDialog().tap(BUTTON_POSITIVE)

        assertEquals("Replace Test Block?", latestDialog().title())
        assertEquals(2, dao.daysForProgram(program).size)

        latestDialog().tap(BUTTON_POSITIVE)

        val replaced = dao.allPrograms().single()
        assertEquals("Pull-Up", dao.plannedSetsForDay(dao.daysForProgram(replaced.id).single().id).single().exerciseName)
    }

    @Test fun aCsvThatCannotBeReadSaysWhichRowIsWrong() {
        openCsv("broken.csv", "exercise,reps,weight\nSquat,5,heavy\n")

        assertTrue(toast(), toast().startsWith("Can't import: Row 2: can't read weight \"heavy\""))
        assertNull(ShadowDialog.getLatestDialog())
        assertEquals(1, dao.allPrograms().size)
    }

    @Test fun deletingAProgramEmptiesTheScreenButKeepsItsHistory() {
        launch()
        row("Overhead Press", 1).noose().tap()
        tap(R.id.btnManage)
        latestDialog().pick("Delete Program")
        latestDialog().pick("Test Block")
        assertEquals("Delete Test Block?", latestDialog().title())

        latestDialog().tap(BUTTON_POSITIVE)

        assertTrue(dao.allPrograms().isEmpty())
        assertTrue(exercises().isEmpty())
        assertEquals("Test Block", dao.allActualsForExport().single().program)
    }

    @Test fun backingUpSharesACopyOfTheWholeDatabase() {
        launch()
        row("Overhead Press", 1).noose().tap()
        tap(R.id.btnManage)

        latestDialog().pick("Back Up Everything")

        val chooser = shadowOf(context as Application).nextStartedActivity
        @Suppress("DEPRECATION") val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals("application/octet-stream", send.type)
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "prayerbook_backup_$today.db")
        assertEquals("SQLite format 3", String(file.readBytes(), 0, 15, Charsets.US_ASCII))
        assertTrue(PrayerBookDb.restoreFrom(context, file.inputStream()))
        assertEquals(50.0, dao.allActualsForExport().single().weight!!, 0.0)
    }
}
