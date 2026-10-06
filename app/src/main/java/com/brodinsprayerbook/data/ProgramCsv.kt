package com.brodinsprayerbook.data

import java.math.BigDecimal

/**
 * Programs as a plain spreadsheet. One row per block of identical sets:
 *
 *   week,day,day_name,exercise,sets,reps,weight,rest,note
 *   1,1,Mon — Heavy bench,Bench Press,3,3,77.5% 1RM,120,
 *   1,1,Mon — Heavy bench,Squat,3,5,60% 1RM,120,to 65%
 *   1,1,Mon — Heavy bench,KB Row,3,10-15/side,35,60,
 *   1,2,OHP Day,Overhead Press,1,5+,85% TM,,
 *
 * reps:   "5", "5+" (as many as possible), or any text such as "8-12/side"
 * weight: "77.5% 1RM", "65% TM", "85% TM +5", a fixed number, or blank
 * rest:   seconds to rest after each set; blank uses the lifter's default
 * Only exercise and reps are required. week and day default to 1, sets to 1.
 */
data class CsvSet(
    val week: Int, val day: Int, val dayName: String, val exercise: String, val sets: Int,
    val reps: Int, val repsText: String, val amrap: Boolean, val formula: String, val note: String,
    val rest: Int = 0
)

object ProgramCsv {
    const val HEADER = "week,day,day_name,exercise,sets,reps,weight,rest,note"

    fun parse(text: String): List<CsvSet> {
        val lines = text.removePrefix("﻿").lines().filter { it.isNotBlank() }
        require(lines.isNotEmpty()) { "The file is empty" }
        val header = splitLine(lines.first()).map { it.trim().lowercase().replace(' ', '_') }
        fun col(vararg names: String) = names.map { header.indexOf(it) }.firstOrNull { it >= 0 } ?: -1
        val cWeek = col("week"); val cDay = col("day"); val cDayName = col("day_name", "session")
        val cEx = col("exercise"); val cSets = col("sets"); val cReps = col("reps")
        val cWeight = col("weight"); val cNote = col("note", "notes")
        val cRest = col("rest", "rest_sec", "rest_(sec)", "rest_seconds")
        require(cEx >= 0 && cReps >= 0) { "The first row must name the columns, including exercise and reps" }

        return lines.drop(1).mapIndexed { i, line ->
            val f = splitLine(line)
            fun get(c: Int) = if (c in f.indices) f[c].trim() else ""
            fun int(c: Int, name: String, default: Int, min: Int = 1): Int {
                val v = get(c)
                if (v.isEmpty()) return default
                return v.toIntOrNull()?.takeIf { it >= min } ?: throw IllegalArgumentException("Row ${i + 2}: $name must be a whole number, got \"$v\"")
            }
            val exercise = get(cEx)
            require(exercise.isNotEmpty()) { "Row ${i + 2}: exercise is missing" }
            val (reps, repsText, amrap) = try { parseReps(get(cReps)) }
                catch (e: IllegalArgumentException) { throw IllegalArgumentException("Row ${i + 2}: ${e.message}") }
            val formula = try { parseWeight(get(cWeight)) }
                catch (e: IllegalArgumentException) { throw IllegalArgumentException("Row ${i + 2}: ${e.message}") }
            CsvSet(int(cWeek, "week", 1), int(cDay, "day", 1), get(cDayName), exercise,
                int(cSets, "sets", 1), reps, repsText, amrap, formula, get(cNote), int(cRest, "rest", 0, min = 0))
        }
    }

    fun format(rows: List<CsvSet>): String = buildString {
        append(HEADER).append('\n')
        for (r in rows) {
            append(listOf(r.week, r.day, esc(r.dayName), esc(r.exercise), r.sets,
                esc(formatReps(r.reps, r.repsText, r.amrap)), esc(formatWeight(r.formula)),
                if (r.rest > 0) r.rest else "", esc(r.note)
            ).joinToString(",")).append('\n')
        }
    }

    /** "5" → 5; "5+" → 5 AMRAP; anything else is kept as text with its first number as the count. */
    fun parseReps(s: String): Triple<Int, String, Boolean> {
        val t = s.trim()
        require(t.isNotEmpty()) { "reps is missing" }
        t.toIntOrNull()?.let { return Triple(it, "", false) }
        if (t.endsWith("+")) t.dropLast(1).trim().toIntOrNull()?.let { return Triple(it, "", true) }
        val first = Regex("\\d+").find(t)?.value?.toIntOrNull() ?: 0
        return Triple(first, t, false)
    }

    fun formatReps(reps: Int, repsText: String, amrap: Boolean): String =
        repsText.ifBlank { if (amrap) "$reps+" else "$reps" }

    private val pctRe = Regex("""^(\d+(?:\.\d+)?)\s*%\s*(?:of\s+)?(1RM|TM)?\s*(?:([+-])\s*(\d+(?:\.\d+)?))?$""", RegexOption.IGNORE_CASE)
    private val formulaRe = Regex("""^(TM|1RM)(?:\*(\d*\.?\d+))?(?:([+-])(\d+(?:\.\d+)?))?$""", RegexOption.IGNORE_CASE)

    /** "77.5% 1RM" → "1RM*0.775"; "85% TM +5" → "TM*0.85+5"; "135" → "135". A bare % means % of 1RM. */
    fun parseWeight(s: String): String {
        val t = s.trim()
        if (t.isEmpty()) return ""
        pctRe.find(t)?.let { m ->
            val mult = BigDecimal(m.groupValues[1]).movePointLeft(2).stripTrailingZeros().toPlainString()
            val base = m.groupValues[2].uppercase().ifEmpty { "1RM" }
            val add = if (m.groupValues[3].isEmpty()) "" else m.groupValues[3] + m.groupValues[4]
            return "$base*$mult$add"
        }
        val compact = t.replace(" ", "")
        if (formulaRe.matches(compact)) return compact.uppercase()
        t.toDoubleOrNull()?.let { return t }
        throw IllegalArgumentException("can't read weight \"$s\" (use e.g. 77.5% 1RM, 65% TM, or 135)")
    }

    fun formatWeight(formula: String): String {
        val m = formulaRe.find(formula.replace(" ", "")) ?: return formula
        val pct = BigDecimal(m.groupValues[2].ifEmpty { "1" }).movePointRight(2).stripTrailingZeros().toPlainString()
        val add = if (m.groupValues[3].isEmpty()) "" else " ${m.groupValues[3]}${m.groupValues[4]}"
        return "$pct% ${m.groupValues[1].uppercase()}$add"
    }

    fun usesMax(formula: String) = formulaRe.matches(formula.replace(" ", ""))

    fun esc(s: String) = if (',' in s || '"' in s || '\n' in s) "\"${s.replace("\"", "\"\"")}\"" else s

    /** Split one CSV line, honouring double-quoted fields. */
    fun splitLine(line: String): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                quoted && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> { cur.append('"'); i++ }
                c == '"' -> quoted = !quoted
                c == ',' && !quoted -> { out += cur.toString(); cur.clear() }
                else -> cur.append(c)
            }
            i++
        }
        out += cur.toString()
        return out
    }
}
