package com.brodinsprayerbook.data

import java.math.BigDecimal
import kotlin.math.floor
import kotlin.math.roundToLong

object FormulaEngine {

    /** Smallest weight step the lifter can load. Set from Settings. */
    var increment: Double = 5.0

    /**
     * Resolve a weight formula to an actual weight.
     * Supported: "TM*0.65", "TM*0.85+5", "TM", "1RM*0.775", "185", ""
     * TM is the training max; 1RM is the true one-rep max.
     * Calculated weights are rounded to the nearest [increment]; fixed weights are left alone.
     */
    fun resolve(formula: String, trainingMax: Double?, oneRepMax: Double? = null): Double? {
        val f = formula.trim()
        if (f.isBlank()) return null
        val (base, expr) = when {
            f.startsWith("TM", ignoreCase = true) -> (trainingMax ?: return null) to f.drop(2).trim()
            f.startsWith("1RM", ignoreCase = true) -> (oneRepMax ?: return null) to f.drop(3).trim()
            else -> return f.toDoubleOrNull()
        }

        // Parse: TM, TM*0.85, TM*0.85+5, TM*0.85-5 (same for 1RM)
        if (expr.isBlank()) return roundToPlate(base)

        var result = base
        // Handle multiplication first
        if (expr.startsWith("*")) {
            val rest = expr.removePrefix("*")
            val mulEnd = rest.indexOfFirst { it == '+' || it == '-' }.let { if (it < 0) rest.length else it }
            val multiplier = rest.substring(0, mulEnd).trim().toDoubleOrNull() ?: return null
            result *= multiplier
            // Then addition/subtraction
            if (mulEnd < rest.length) {
                val addPart = rest.substring(mulEnd).trim()
                val addVal = addPart.toDoubleOrNull() ?: return null
                result += addVal
            }
        }
        return roundToPlate(result)
    }

    private fun step() = if (increment > 0) increment else 5.0

    /** Round to the nearest loadable weight */
    fun roundToPlate(w: Double): Double = (w / step()).roundToLong() * step()

    /** Round down to a loadable weight */
    fun floorToPlate(w: Double): Double = floor(w / step() + 1e-9) * step()

    /**
     * Estimate 1RM from weight × reps using Epley formula.
     * 1RM = weight × (1 + reps/30)
     */
    fun estimate1RM(weight: Double, reps: Int): Double {
        if (reps <= 0) return weight
        if (reps == 1) return weight
        return weight * (1.0 + reps / 30.0)
    }

    /**
     * Best one-rep max each exercise showed across a set of lifts.
     * Singles count at face value; sets of 2–10 are estimated and rounded down.
     */
    fun bestOneRepMaxes(sets: List<LiftedSet>): Map<Long, Double> {
        val best = mutableMapOf<Long, Double>()
        for (s in sets) {
            if (s.reps !in 1..10 || s.weight <= 0) continue
            val est = if (s.reps == 1) s.weight else floorToPlate(estimate1RM(s.weight, s.reps))
            if (est > (best[s.exerciseId] ?: 0.0)) best[s.exerciseId] = est
        }
        return best
    }

    /** Format a weight for display: no trailing zeros */
    fun formatWeight(w: Double?): String {
        if (w == null) return "—"
        return BigDecimal.valueOf(w).setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    }
}
