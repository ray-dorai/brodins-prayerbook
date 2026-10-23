package com.brodinsprayerbook.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.brodinsprayerbook.memoryDb
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Squat 3×5 at 50% of a 137 max, adding 2.5 lb next time when the last set leaves 4+ in reserve. */
@RunWith(AndroidJUnit4::class)
class ProgressionTest {
    private lateinit var db: PrayerBookDb
    private lateinit var dao: PrayerBookDao
    private var squat = 0L
    private var day = 0L
    private var log = 0L

    @Before fun open() {
        db = memoryDb()
        dao = db.dao()
        FormulaEngine.increment = 1.0
        squat = dao.insertExercise(Exercise(name = "Squat", isMainLift = true))
        Maxes.set(dao, squat, 137.0, "2026-10-01", "onboarding")
        day = dao.insertProgramDay(ProgramDay(programId = dao.insertProgram(Program(name = "P")), name = "Day 1"))
        repeat(3) {
            dao.insertPlannedSet(PlannedSet(programDayId = day, exerciseId = squat, setNumber = it + 1, reps = 5,
                weightFormula = "1RM*0.5", progressStep = 2.5))
        }
        log = dao.insertWorkoutLog(WorkoutLog(date = "2026-10-22", programDayId = day))
    }

    @After fun close() {
        db.close()
        FormulaEngine.increment = 5.0
    }

    /** Log the sets as (reps, rir); a null rep count is a set not done. */
    private fun lift(vararg sets: Pair<Int?, Double?>) = sets.forEachIndexed { i, (reps, rir) ->
        dao.insertActualSet(ActualSet(workoutLogId = log, exerciseId = squat, setNumber = i + 1,
            weight = 69.0, reps = reps, rir = rir))
    }

    private fun finish() = Progression.afterSession(dao, log, day, "2026-10-22")
    private fun max() = dao.maxFor(squat)!!.oneRepMax
    private fun nextWeight() = FormulaEngine.resolve("1RM*0.5", null, max())

    @Test fun fourOrMoreInReserveAddsTheStepToNextTimesWeight() {
        lift(5 to null, 5 to null, 5 to 4.0)

        assertEquals(listOf(Progression.Change("Squat", 2.5, 142.0)), finish())

        assertEquals(142.0, max(), 0.0)
        assertEquals(71.0, nextWeight()!!, 0.0)
        assertEquals(MaxHistoryRow("2026-10-22", "Squat", 142.0, "rir"), dao.maxHistoryForExport().last())
    }

    @Test fun twoOrThreeInReserveRepeatsTheWeight() {
        lift(5 to null, 5 to null, 5 to 3.0)

        assertTrue(finish().isEmpty())
        assertEquals(137.0, max(), 0.0)
    }

    @Test fun zeroOrOneInReserveDropsTheMaxFivePercent() {
        lift(5 to null, 5 to null, 5 to 1.0)

        assertEquals(listOf(Progression.Change("Squat", null, 130.15)), finish())
        assertEquals(65.0, nextWeight()!!, 0.0)
    }

    @Test fun aMissedRepDropsTheMaxWhateverTheRir() {
        lift(5 to null, 4 to null, 5 to 3.0)

        assertEquals(130.15, finish().single().oneRepMax, 0.0)
    }

    @Test fun itIsTheLastSetDoneThatCounts() {
        lift(5 to 5.0, 5 to 2.0, null to null)

        assertTrue(finish().isEmpty())
    }

    @Test fun withNoRirLoggedTheMaxIsLeftAlone() {
        lift(5 to null, 5 to null, 5 to null)

        assertTrue(finish().isEmpty())
        assertEquals(137.0, max(), 0.0)
    }

    @Test fun nothingLoggedChangesNothing() {
        assertTrue(finish().isEmpty())
    }

    @Test fun setsWithoutAStepNeverMoveTheMax() {
        val bench = dao.insertExercise(Exercise(name = "Bench Press", isMainLift = true))
        Maxes.set(dao, bench, 91.0, "2026-10-01", "onboarding")
        dao.insertPlannedSet(PlannedSet(programDayId = day, exerciseId = bench, setNumber = 4, reps = 6, weightFormula = "1RM*0.5"))
        dao.insertActualSet(ActualSet(workoutLogId = log, exerciseId = bench, setNumber = 4, weight = 46.0, reps = 6, rir = 5.0))

        assertTrue(finish().isEmpty())
        assertEquals(91.0, dao.maxFor(bench)!!.oneRepMax, 0.0)
    }

    @Test fun aTrainingMaxFormulaMovesTheMaxByTheRightAmount() {
        val press = dao.insertExercise(Exercise(name = "Overhead Press", isMainLift = true))
        Maxes.set(dao, press, 100.0, "2026-10-01", "onboarding") // training max 90
        dao.insertPlannedSet(PlannedSet(programDayId = day, exerciseId = press, setNumber = 4, reps = 5,
            weightFormula = "TM*0.5", progressStep = 1.0))
        dao.insertActualSet(ActualSet(workoutLogId = log, exerciseId = press, setNumber = 4, weight = 45.0, reps = 5, rir = 4.0))

        finish()

        assertEquals(46.0, FormulaEngine.resolve("TM*0.5", dao.maxFor(press)!!.trainingMax)!!, 0.0)
    }
}
