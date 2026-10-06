package com.brodinsprayerbook.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.brodinsprayerbook.AppStateRule
import com.brodinsprayerbook.context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class BackupTest {
    @get:Rule val appState = AppStateRule()

    private fun dao() = PrayerBookDb.get(context).dao()
    private fun names() = dao().allExercises().map { it.name }

    @Test fun restoringABackupBringsBackExactlyWhatWasSaved() {
        dao().insertExercise(Exercise(name = "Bench Press"))
        val backup = File(context.cacheDir, "backup.db")
        PrayerBookDb.backupTo(context, backup)
        dao().insertExercise(Exercise(name = "Added After The Backup"))

        assertTrue(PrayerBookDb.restoreFrom(context, backup.inputStream()))

        assertEquals(listOf("Bench Press"), names())
    }

    @Test fun aBackupCarriesTheLogAndTheMaxes() {
        val bench = dao().insertExercise(Exercise(name = "Bench Press", isMainLift = true))
        Maxes.set(dao(), bench, 185.0, "2026-10-01", "onboarding")
        val log = dao().insertWorkoutLog(WorkoutLog(date = "2026-10-05", programName = "Block"))
        dao().insertActualSet(ActualSet(workoutLogId = log, exerciseId = bench, setNumber = 1, weight = 145.0, reps = 3))
        val backup = File(context.cacheDir, "backup.db")
        PrayerBookDb.backupTo(context, backup)

        // As on a new phone: nothing there yet
        PrayerBookDb.close()
        context.deleteDatabase("prayerbook.db")
        assertTrue(names().isEmpty())
        assertTrue(PrayerBookDb.restoreFrom(context, backup.inputStream()))

        assertEquals(185.0, dao().maxFor(bench)!!.oneRepMax, 0.0)
        assertEquals(145.0, dao().allActualsForExport().single().weight!!, 0.0)
        assertEquals(1, dao().maxHistoryForExport().size)
    }

    @Test fun aFileThatIsNotABackupIsRefusedAndNothingIsLost() {
        dao().insertExercise(Exercise(name = "Bench Press"))

        assertFalse(PrayerBookDb.restoreFrom(context, "week,day,exercise\n1,1,Squat\n".byteInputStream()))
        assertFalse(PrayerBookDb.restoreFrom(context, ByteArray(0).inputStream()))

        assertEquals(listOf("Bench Press"), names())
    }
}
