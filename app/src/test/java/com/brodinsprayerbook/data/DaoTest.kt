package com.brodinsprayerbook.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.brodinsprayerbook.memoryDb
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DaoTest {
    private lateinit var db: PrayerBookDb
    private lateinit var dao: PrayerBookDao
    private var bench = 0L
    private var squat = 0L
    private var program = 0L
    private var day = 0L

    @Before fun open() {
        db = memoryDb()
        dao = db.dao()
        bench = dao.insertExercise(Exercise(name = "Bench Press", isMainLift = true))
        squat = dao.insertExercise(Exercise(name = "Squat", isMainLift = true))
        program = dao.insertProgram(Program(name = "Block"))
        day = dao.insertProgramDay(ProgramDay(programId = program, name = "Mon"))
        dao.insertPlannedSet(PlannedSet(programDayId = day, exerciseId = bench, setNumber = 1, reps = 5))
    }

    @After fun close() = db.close()

    /** One session with the given bench sets; a null rep count is a row opened but never filled in. */
    private fun session(date: String, vararg sets: Pair<Double?, Int?>, cycle: Int = 1, dayId: Long? = day): Long {
        val log = dao.insertWorkoutLog(WorkoutLog(
            date = date, programDayId = dayId, cycle = cycle, programName = "Block", week = 1, dayName = "Mon"))
        sets.forEachIndexed { i, (weight, reps) ->
            dao.insertActualSet(ActualSet(workoutLogId = log, exerciseId = bench, setNumber = i + 1,
                weight = weight, reps = reps))
        }
        return log
    }

    @Test fun lastTimeIsTheMostRecentEarlierSessionWithRepsLogged() {
        session("2026-10-01", 130.0 to 5, 135.0 to 5)
        session("2026-10-03", 140.0 to 3)
        session("2026-10-04", null to null)

        assertEquals(listOf(LastSet(140.0, 3, "2026-10-03")), dao.lastTime(bench, "2026-10-05"))
        assertEquals(listOf(130.0, 135.0), dao.lastTime(bench, "2026-10-03").map { it.weight })
        assertTrue(dao.lastTime(bench, "2026-10-01").isEmpty())
        assertTrue(dao.lastTime(squat, "2026-10-05").isEmpty())
    }

    @Test fun liftedInCycleIsLimitedToThatProgramAndCycle() {
        val other = dao.insertProgram(Program(name = "Other"))
        val otherDay = dao.insertProgramDay(ProgramDay(programId = other, name = "Tue"))
        session("2026-10-01", 150.0 to 3, 100.0 to 20, null to 5, cycle = 1)
        session("2026-10-02", 160.0 to 1, cycle = 2)
        session("2026-10-03", 170.0 to 1, cycle = 1, dayId = otherDay)

        assertEquals(listOf(LiftedSet(bench, 150.0, 3)), dao.liftedInCycle(program, 1))
        assertEquals(listOf(LiftedSet(bench, 160.0, 1)), dao.liftedInCycle(program, 2))
    }

    @Test fun sessionsAreNewestFirstAndCountOnlyFilledSets() {
        session("2026-10-01", 130.0 to 5, null to null)
        session("2026-10-03", null to null)

        val sessions = dao.sessions()
        assertEquals(listOf("2026-10-03", "2026-10-01"), sessions.map { it.date })
        assertEquals(listOf(0, 1), sessions.map { it.setsLogged })
        assertEquals("Block", sessions[0].program)
    }

    @Test fun exportLeavesOutSetsNeverTouched() {
        val log = session("2026-10-01", 130.0 to 5, null to null)
        dao.insertActualSet(ActualSet(workoutLogId = log, exerciseId = bench, setNumber = 3, notes = "skipped, shoulder"))

        val rows = dao.allActualsForExport()
        assertEquals(listOf(1, 3), rows.map { it.set })
        assertEquals("Bench Press", rows[0].exercise)
        assertEquals("skipped, shoulder", rows[1].notes)
    }

    @Test fun deletingAProgramKeepsWhatWasLoggedWithIt() {
        session("2026-10-01", 130.0 to 5)
        dao.upsertState(UserProgramState(programId = program))

        dao.deleteProgram(program)

        assertTrue(dao.allPrograms().isEmpty())
        assertTrue(dao.daysForProgram(program).isEmpty())
        assertTrue(dao.plannedSetsForDay(day).isEmpty())
        assertEquals(null, dao.stateFor(program))
        assertEquals("Block", dao.sessions().single().program)
        assertEquals(130.0, dao.allActualsForExport().single().weight!!, 0.0)
    }

    @Test fun plannedSetsCarryTheExercisesOwnRest() {
        dao.setExerciseRest(bench, 45)
        val set = dao.plannedSetsForDay(day).single()
        assertEquals("Bench Press", set.exerciseName)
        assertEquals(45, set.exerciseRest)
        assertEquals(0, set.restSeconds)
    }

    @Test fun eachProgramHasOnePlaceInItsCycle() {
        val state = UserProgramState(programId = program).let { it.copy(id = dao.upsertState(it)) }
        dao.upsertState(state.copy(currentWeek = 2, currentDay = 1))
        assertEquals(2, dao.stateFor(program)!!.currentWeek)
        assertEquals(1, dao.stateFor(program)!!.currentDay)
    }
}
