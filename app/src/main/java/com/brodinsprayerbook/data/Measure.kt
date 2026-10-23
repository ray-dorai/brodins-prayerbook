package com.brodinsprayerbook.data

/**
 * What a set of an exercise records. A set row has two boxes: [first] (none for bodyweight
 * and timed work) and [second], which is the one that marks the set as done.
 */
enum class Measure(val key: String, val label: String, val first: Field?, val second: Field) {
    WEIGHT_REPS("weight_reps", "Weight × reps", Field.WEIGHT, Field.REPS),
    REPS("reps", "Reps", null, Field.REPS),
    TIME("time", "Time", null, Field.TIME),
    WEIGHT_TIME("weight_time", "Weight × time", Field.WEIGHT, Field.TIME),
    DISTANCE_TIME("distance_time", "Distance & time", Field.DISTANCE, Field.TIME),
    WEIGHT_DISTANCE("weight_distance", "Weight × distance", Field.WEIGHT, Field.DISTANCE);

    enum class Field { WEIGHT, REPS, TIME, DISTANCE }

    companion object {
        fun of(key: String) = entries.firstOrNull { it.key == key } ?: WEIGHT_REPS
    }
}

/** Durations as lifters write them: "45", "1:30", "1:02:30". */
object Seconds {
    fun parse(text: String): Int? {
        val parts = text.trim().split(':')
        if (parts.isEmpty() || parts.size > 3 || parts.any { it.isEmpty() }) return null
        val nums = parts.map { it.toIntOrNull()?.takeIf { n -> n >= 0 } ?: return null }
        if (nums.drop(1).any { it >= 60 }) return null
        return nums.fold(0) { total, n -> total * 60 + n }
    }

    fun format(seconds: Int): String =
        if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
        else "%d:%02d".format(seconds / 60, seconds % 60)
}
