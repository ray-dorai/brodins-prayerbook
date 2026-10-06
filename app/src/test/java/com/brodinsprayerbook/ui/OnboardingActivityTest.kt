package com.brodinsprayerbook.ui

import android.app.Application
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.brodinsprayerbook.AppStateRule
import com.brodinsprayerbook.all
import com.brodinsprayerbook.context
import com.brodinsprayerbook.data.Exercise
import com.brodinsprayerbook.data.MaxHistoryRow
import com.brodinsprayerbook.data.Maxes
import com.brodinsprayerbook.data.PrayerBookDb
import com.brodinsprayerbook.idle
import com.brodinsprayerbook.withText
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowToast
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class OnboardingActivityTest {
    @get:Rule val appState = AppStateRule()

    private val dao get() = PrayerBookDb.get(context).dao()
    private lateinit var scenario: ActivityScenario<OnboardingActivity>
    private lateinit var activity: OnboardingActivity

    private fun launch() {
        scenario = ActivityScenario.launch(OnboardingActivity::class.java)
        idle()
        if (scenario.state != Lifecycle.State.DESTROYED) scenario.onActivity { activity = it }
    }

    @After fun close() { if (::scenario.isInitialized) scenario.close() }

    private val screen get() = activity.window.decorView
    private fun inputFor(lift: String): EditText {
        val views = screen.all<TextView>()
        return views[views.indexOfFirst { it !is EditText && it.text.toString() == lift } + 1] as EditText
    }
    private fun commence() { screen.withText("Commence Thy Training").performClick(); idle() }
    private fun nextScreen() = shadowOf(context as Application).nextStartedActivity?.component?.className

    @Test fun firstRunSeedsTheProgramsAndAsksForEachMainLift() {
        launch()

        assertEquals(9, dao.allPrograms().size)
        assertTrue(dao.mainLifts().isNotEmpty())
        assertEquals(dao.mainLifts().size, screen.all<EditText>().size)
        assertNotNull(inputFor("Trap Bar Deadlift"))
        assertNull(nextScreen())
    }

    @Test fun maxesEnteredAreSavedAndLiftsLeftBlankAreNot() {
        launch()
        inputFor("Bench Press").setText("185")
        inputFor("Squat").setText("242.5")

        commence()

        val byName = dao.allExercises().associateBy { it.name }
        assertEquals(185.0, dao.maxFor(byName["Bench Press"]!!.id)!!.oneRepMax, 0.0)
        assertEquals(242.5, dao.maxFor(byName["Squat"]!!.id)!!.oneRepMax, 0.0)
        assertEquals(2, dao.allMaxes().size)
        val today = LocalDate.now().toString()
        assertEquals(setOf(MaxHistoryRow(today, "Bench Press", 185.0, "onboarding"),
                           MaxHistoryRow(today, "Squat", 242.5, "onboarding")),
            dao.maxHistoryForExport().toSet())
        assertEquals(MainActivity::class.java.name, nextScreen())
        assertTrue(activity.isFinishing)
    }

    @Test fun atLeastOneMaxIsNeeded() {
        launch()

        commence()

        assertEquals("Enter at least one max.", ShadowToast.getTextOfLatestToast())
        assertTrue(dao.allMaxes().isEmpty())
        assertFalse(activity.isFinishing)
    }

    @Test fun aMaxThatIsNotAPositiveNumberIsPointedOutAndNothingIsSaved() {
        launch()
        inputFor("Bench Press").setText("185")
        inputFor("Squat").setText("0")

        commence()

        assertEquals("Enter a number", inputFor("Squat").error.toString())
        assertTrue(dao.allMaxes().isEmpty())
        assertFalse(activity.isFinishing)
    }

    @Test fun onceMaxesExistTheAppOpensStraightOnTheWorkout() {
        val bench = dao.insertExercise(Exercise(name = "Bench Press", isMainLift = true))
        Maxes.set(dao, bench, 185.0, "2026-10-01", "onboarding")

        launch()

        assertEquals(MainActivity::class.java.name, nextScreen())
        assertEquals(Lifecycle.State.DESTROYED, scenario.state)
    }

    @Test fun anInstallFromBeforeHighFrequencyStrengthGainsItOnNextLaunch() {
        val bench = dao.insertExercise(Exercise(name = "Bench Press", isMainLift = true))
        Maxes.set(dao, bench, 185.0, "2026-10-01", "onboarding")

        launch()

        assertEquals(listOf("High Frequency Strength"), dao.allPrograms().map { it.name })
        assertEquals(bench, dao.allExercises().single { it.name == "Bench Press" }.id)
    }

    @Test fun theWorkoutScreenSendsAFreshInstallBackToSetup() {
        ActivityScenario.launch(MainActivity::class.java).use { main ->
            idle()
            assertEquals(OnboardingActivity::class.java.name, nextScreen())
            assertEquals(Lifecycle.State.DESTROYED, main.state)
        }
    }
}
