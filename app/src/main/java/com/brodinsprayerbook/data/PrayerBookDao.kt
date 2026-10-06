package com.brodinsprayerbook.data

import androidx.room.*

@Dao
interface PrayerBookDao {

    // --- Exercises ---
    @Insert fun insertExercise(e: Exercise): Long
    @Query("SELECT * FROM Exercise ORDER BY name") fun allExercises(): List<Exercise>
    @Query("SELECT * FROM Exercise WHERE isMainLift = 1") fun mainLifts(): List<Exercise>
    @Query("UPDATE Exercise SET isMainLift = 1 WHERE id = :id") fun markMainLift(id: Long)
    @Query("UPDATE Exercise SET restSeconds = :seconds WHERE id = :id") fun setExerciseRest(id: Long, seconds: Int)

    // --- User maxes ---
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun upsertMax(m: UserMax): Long
    @Query("SELECT * FROM UserMax WHERE exerciseId = :exerciseId") fun maxFor(exerciseId: Long): UserMax?
    @Query("SELECT * FROM UserMax") fun allMaxes(): List<UserMax>
    @Insert fun insertMaxHistory(h: MaxHistory): Long
    @Query("""
        SELECT h.date, e.name AS exercise, h.oneRepMax, h.source FROM MaxHistory h
        JOIN Exercise e ON e.id = h.exerciseId ORDER BY h.date, h.id
    """)
    fun maxHistoryForExport(): List<MaxHistoryRow>

    // --- Programs ---
    @Insert fun insertProgram(p: Program): Long
    @Query("DELETE FROM Program WHERE id = :id") fun deleteProgram(id: Long)
    @Query("SELECT * FROM Program ORDER BY name") fun allPrograms(): List<Program>

    // --- Program days ---
    @Insert fun insertProgramDay(d: ProgramDay): Long
    @Query("SELECT * FROM ProgramDay WHERE programId = :pid ORDER BY weekIndex, dayIndex")
    fun daysForProgram(pid: Long): List<ProgramDay>
    @Query("SELECT * FROM ProgramDay WHERE programId = :pid AND weekIndex = :week AND dayIndex = :day LIMIT 1")
    fun dayFor(pid: Long, week: Int, day: Int): ProgramDay?

    // --- Planned sets ---
    @Insert fun insertPlannedSet(s: PlannedSet): Long
    @Query("""
        SELECT ps.*, e.name AS exerciseName, e.restSeconds AS exerciseRest FROM PlannedSet ps
        JOIN Exercise e ON e.id = ps.exerciseId
        WHERE ps.programDayId = :dayId ORDER BY ps.setNumber, ps.id
    """)
    fun plannedSetsForDay(dayId: Long): List<PlannedSetWithName>

    // --- Workout logs ---
    @Insert fun insertWorkoutLog(log: WorkoutLog): Long
    @Update fun updateWorkoutLog(log: WorkoutLog)
    @Query("SELECT * FROM WorkoutLog WHERE date = :date AND programDayId = :dayId LIMIT 1")
    fun logFor(date: String, dayId: Long): WorkoutLog?
    @Query("SELECT * FROM WorkoutLog WHERE date = :date ORDER BY completed DESC, id") fun logsOn(date: String): List<WorkoutLog>

    // --- Calendar: one row per session, newest first ---
    @Query("""
        SELECT w.id AS logId, w.date, w.cycle, w.completed, w.programDayId,
               w.programName AS program, w.week, w.dayName,
               (SELECT COUNT(*) FROM ActualSet a WHERE a.workoutLogId = w.id
                    AND (a.reps IS NOT NULL OR a.weight IS NOT NULL)) AS setsLogged
        FROM WorkoutLog w ORDER BY w.date DESC, w.id DESC
    """)
    fun sessions(): List<SessionRow>

    // --- What this exercise looked like the last time it was done before a date ---
    @Query("""
        SELECT a.weight, a.reps, w.date FROM ActualSet a JOIN WorkoutLog w ON w.id = a.workoutLogId
        WHERE a.exerciseId = :exerciseId AND a.reps IS NOT NULL AND w.date = (
            SELECT MAX(w2.date) FROM ActualSet a2 JOIN WorkoutLog w2 ON w2.id = a2.workoutLogId
            WHERE a2.exerciseId = :exerciseId AND a2.reps IS NOT NULL AND w2.date < :date)
        ORDER BY w.id, a.setNumber
    """)
    fun lastTime(exerciseId: Long, date: String): List<LastSet>

    // --- Sets lifted in one cycle of a program, for end-of-cycle max updates ---
    @Query("""
        SELECT a.exerciseId, a.weight, a.reps FROM ActualSet a
        JOIN WorkoutLog w ON w.id = a.workoutLogId JOIN ProgramDay d ON d.id = w.programDayId
        WHERE d.programId = :pid AND w.cycle = :cycle AND a.weight IS NOT NULL AND a.reps BETWEEN 1 AND 10
    """)
    fun liftedInCycle(pid: Long, cycle: Int): List<LiftedSet>

    // --- Actual sets ---
    @Insert fun insertActualSet(s: ActualSet): Long
    @Update fun updateActualSet(s: ActualSet)
    @Query("""
        SELECT a.*, e.name AS exerciseName FROM ActualSet a
        JOIN Exercise e ON e.id = a.exerciseId
        WHERE a.workoutLogId = :logId ORDER BY a.setNumber, a.id
    """)
    fun actualSetsForLog(logId: Long): List<ActualSetWithName>

    // --- Program state ---
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun upsertState(s: UserProgramState): Long
    @Query("SELECT * FROM UserProgramState WHERE programId = :pid") fun stateFor(pid: Long): UserProgramState?

    // --- Export ---
    @Query("""
        SELECT w.date, w.programName AS program, w.cycle, w.week, w.dayName AS day, w.completed,
               e.name AS exercise, a.setNumber AS 'set', a.plannedWeight, a.plannedReps,
               a.weight, a.reps, a.rpe, a.notes
        FROM ActualSet a JOIN WorkoutLog w ON w.id = a.workoutLogId JOIN Exercise e ON e.id = a.exerciseId
        WHERE a.reps IS NOT NULL OR a.weight IS NOT NULL OR a.notes != ''
        ORDER BY w.date, w.id, a.setNumber
    """)
    fun allActualsForExport(): List<ExportRow>
}

data class PlannedSetWithName(
    val id: Long, val programDayId: Long, val exerciseId: Long,
    val setNumber: Int, val reps: Int, val weightFormula: String,
    val isAmrap: Boolean, val repsText: String, val note: String, val restSeconds: Int,
    val exerciseName: String, val exerciseRest: Int
)

data class ActualSetWithName(
    val id: Long, val workoutLogId: Long, val exerciseId: Long,
    val setNumber: Int, val reps: Int?, val weight: Double?,
    val notes: String, val plannedWeight: Double?, val plannedReps: String, val rpe: Double?,
    val exerciseName: String
)

data class ExportRow(
    val date: String, val program: String, val cycle: Int, val week: Int, val day: String,
    val completed: Boolean, val exercise: String, val set: Int,
    val plannedWeight: Double?, val plannedReps: String,
    val weight: Double?, val reps: Int?, val rpe: Double?, val notes: String
)

data class SessionRow(
    val logId: Long, val date: String, val cycle: Int, val completed: Boolean, val programDayId: Long?,
    val program: String, val week: Int, val dayName: String, val setsLogged: Int
)

data class LiftedSet(val exerciseId: Long, val weight: Double, val reps: Int)

data class LastSet(val weight: Double?, val reps: Int, val date: String)

data class MaxHistoryRow(val date: String, val exercise: String, val oneRepMax: Double, val source: String)
