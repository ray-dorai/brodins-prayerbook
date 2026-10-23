package com.brodinsprayerbook.data

import kotlin.math.roundToLong

/**
 * Weights that follow the lifter's effort, session to session. When a session is finished, each
 * lift whose sets carry a progress step is judged on the reps in reserve (RIR) logged on its
 * last set:
 *
 *   4+ RIR                          next time's weight goes up by the step
 *   2–3 RIR                         the weights stay
 *   0–1 RIR, or a prescribed rep missed   the max drops 5%
 *
 * The change is made to the lift's max, so every percentage-based weight after it moves too,
 * including later phases of the program. A lift whose last set has no RIR is left alone.
 */
object Progression {

    /** One lift's max moved by a session. [step] is the change in next time's weight; null when it dropped 5%. */
    data class Change(val exercise: String, val step: Double?, val oneRepMax: Double)

    fun afterSession(dao: PrayerBookDao, logId: Long, programDayId: Long, date: String): List<Change> {
        val actuals = dao.actualSetsForLog(logId).filter { it.reps != null }.associateBy { it.exerciseId to it.setNumber }
        val changes = mutableListOf<Change>()
        val byLift = dao.plannedSetsForDay(programDayId).filter { it.progressStep > 0 }.groupBy { it.exerciseId }
        for ((exerciseId, planned) in byLift) {
            val done = planned.filter { (exerciseId to it.setNumber) in actuals }.sortedBy { it.setNumber }
            val last = done.lastOrNull() ?: continue
            val max = dao.maxFor(exerciseId) ?: continue
            val share = FormulaEngine.shareOfMax(last.weightFormula, max.trainingMaxPct)?.takeIf { it > 0 } ?: continue
            val missed = done.any { ps ->
                !ps.isAmrap && ps.repsText.isBlank() && actuals.getValue(exerciseId to ps.setNumber).reps!! < ps.reps
            }
            val rir = actuals.getValue(exerciseId to last.setNumber).rir

            val change = when {
                missed || (rir != null && rir <= 1) -> Change(last.exerciseName, null, round(max.oneRepMax * 0.95))
                rir != null && rir >= 4 -> Change(last.exerciseName, last.progressStep, round(max.oneRepMax + last.progressStep / share))
                else -> continue
            }
            Maxes.set(dao, exerciseId, change.oneRepMax, date, "rir")
            changes += change
        }
        return changes
    }

    private fun round(w: Double) = (w * 100).roundToLong() / 100.0
}
