package com.brodinsprayerbook.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.brodinsprayerbook.memoryDb
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProgramSeederTest {
    private lateinit var db: PrayerBookDb
    private lateinit var dao: PrayerBookDao

    @Before fun seed() {
        db = memoryDb()
        dao = db.dao()
        ProgramSeeder.seed(dao)
        ProgramSeeder.seedHighFrequencyStrength(dao)
    }

    @After fun close() = db.close()

    @Test fun everyBuiltInProgramIsSeeded() {
        val names = dao.allPrograms().map { it.name }
        assertEquals(9, names.size)
        assertEquals(names.size, names.toSet().size)
        assertTrue(names.containsAll(listOf("5/3/1", "Starting Strength", "High Frequency Strength")))
        assertTrue(dao.allPrograms().all { it.isBuiltIn })
    }

    @Test fun everyProgramFillsItsCycleWithDaysThatHaveSets() {
        for (program in dao.allPrograms()) {
            val days = dao.daysForProgram(program.id)
            assertTrue("${program.name} has no days", days.isNotEmpty())
            assertEquals("${program.name} weeks", (0 until program.cycleWeeks).toSet(), days.map { it.weekIndex }.toSet())
            assertNotNull("${program.name} has no first session", dao.dayFor(program.id, 0, 0))
            for (day in days) assertTrue("${program.name} / ${day.name} is empty", dao.plannedSetsForDay(day.id).isNotEmpty())
        }
    }

    /** A weight that depends on a max can only be shown if the lifter is asked for that max. */
    @Test fun everyPercentageBasedLiftIsAMainLift() {
        val mainLifts = dao.mainLifts().map { it.id }.toSet()
        for (program in dao.allPrograms()) for (day in dao.daysForProgram(program.id)) {
            for (set in dao.plannedSetsForDay(day.id)) {
                if (ProgramCsv.usesMax(set.weightFormula))
                    assertTrue("${program.name}: ${set.exerciseName} needs a max", set.exerciseId in mainLifts)
                else assertTrue("${program.name}: unreadable weight \"${set.weightFormula}\"",
                    set.weightFormula.isEmpty() || set.weightFormula.toDoubleOrNull() != null)
            }
        }
    }

    @Test fun seedingHighFrequencyStrengthAgainAddsNothing() {
        val exercises = dao.allExercises().size
        ProgramSeeder.seedHighFrequencyStrength(dao)

        assertEquals(1, dao.allPrograms().count { it.name == "High Frequency Strength" })
        assertEquals(exercises, dao.allExercises().size)
    }

    @Test fun highFrequencyStrengthIsSixWeeksOfFiveDays() {
        val program = dao.allPrograms().single { it.name == "High Frequency Strength" }
        val days = dao.daysForProgram(program.id)
        assertEquals(30, days.size)
        val sets = days.flatMap { dao.plannedSetsForDay(it.id) }
        assertEquals(404, sets.size)
        assertEquals(setOf(60, 120, 180), sets.map { it.restSeconds }.toSet())
        assertTrue(sets.filter { it.exerciseName.startsWith("KB ") }.all { it.restSeconds == 60 })
    }
}
