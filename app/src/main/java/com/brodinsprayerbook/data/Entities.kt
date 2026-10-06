package com.brodinsprayerbook.data

import androidx.room.*

@Entity
data class Exercise(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: String = "",
    val isMainLift: Boolean = false,
    @ColumnInfo(defaultValue = "0") val restSeconds: Int = 0 // lifter's own rest for this exercise; 0 = none
)

@Entity(
    foreignKeys = [ForeignKey(entity = Exercise::class, parentColumns = ["id"], childColumns = ["exerciseId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("exerciseId", unique = true)]
)
data class UserMax(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exerciseId: Long,
    val oneRepMax: Double,
    val trainingMaxPct: Double = 0.90,
    val updatedDate: String = ""
) {
    val trainingMax: Double get() = oneRepMax * trainingMaxPct
}

@Entity
data class Program(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val periodizationType: String = "linear",
    val cycleWeeks: Int = 1,
    val daysPerWeek: Int = 1,
    val progressionRule: String = "",
    val isBuiltIn: Boolean = false
)

@Entity(
    foreignKeys = [ForeignKey(entity = Program::class, parentColumns = ["id"], childColumns = ["programId"], onDelete = ForeignKey.CASCADE)]
)
data class ProgramDay(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val programId: Long,
    val weekIndex: Int = 0,
    val dayIndex: Int = 0,
    val name: String = ""
)

@Entity(
    foreignKeys = [
        ForeignKey(entity = ProgramDay::class, parentColumns = ["id"], childColumns = ["programDayId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Exercise::class, parentColumns = ["id"], childColumns = ["exerciseId"], onDelete = ForeignKey.CASCADE)
    ]
)
data class PlannedSet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val programDayId: Long,
    val exerciseId: Long,
    val setNumber: Int,
    val reps: Int,                  // 0 = AMRAP
    val weightFormula: String = "", // "TM*0.65", "1RM*0.775", "135", ""
    val isAmrap: Boolean = false,
    @ColumnInfo(defaultValue = "") val repsText: String = "", // shown instead of reps, e.g. "8–12/side"
    @ColumnInfo(defaultValue = "") val note: String = "",     // short cue shown after the prescription
    @ColumnInfo(defaultValue = "0") val restSeconds: Int = 0  // rest after this set; 0 = the default
)

@Entity
data class WorkoutLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val programDayId: Long? = null,
    @ColumnInfo(defaultValue = "1") val cycle: Int = 1,           // which run through the program
    @ColumnInfo(defaultValue = "0") val completed: Boolean = false, // session finished
    // Snapshot of where this session sat in its program, so history survives program edits
    @ColumnInfo(defaultValue = "") val programName: String = "",
    @ColumnInfo(defaultValue = "0") val week: Int = 0,
    @ColumnInfo(defaultValue = "") val dayName: String = ""
)

@Entity(
    foreignKeys = [
        ForeignKey(entity = WorkoutLog::class, parentColumns = ["id"], childColumns = ["workoutLogId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Exercise::class, parentColumns = ["id"], childColumns = ["exerciseId"], onDelete = ForeignKey.CASCADE)
    ]
)
data class ActualSet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutLogId: Long,
    val exerciseId: Long,
    val setNumber: Int,
    val reps: Int? = null,
    val weight: Double? = null,
    val notes: String = "",
    val plannedWeight: Double? = null,                         // what was prescribed when logged
    @ColumnInfo(defaultValue = "") val plannedReps: String = "",
    val rpe: Double? = null
)

/** Every change to a one-rep max, so progress leaves a trail. */
@Entity
data class MaxHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exerciseId: Long,
    val date: String,
    val oneRepMax: Double,
    val source: String   // "onboarding", "manual", "amrap", "cycle"
)

@Entity(
    foreignKeys = [ForeignKey(entity = Program::class, parentColumns = ["id"], childColumns = ["programId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("programId", unique = true)]
)
data class UserProgramState(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val programId: Long,
    val currentWeek: Int = 0,
    val currentDay: Int = 0,
    val cycleCount: Int = 1
)
