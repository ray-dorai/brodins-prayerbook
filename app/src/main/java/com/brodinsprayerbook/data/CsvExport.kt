package com.brodinsprayerbook.data

import android.content.Context
import android.os.Environment
import java.io.File
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

object CsvExport {
    private fun dir(context: Context) = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
    private fun num(d: Double?) = d?.let { FormulaEngine.formatWeight(it) } ?: ""
    private fun line(vararg cells: Any?) = cells.joinToString(",") { ProgramCsv.esc(it?.toString() ?: "") } + "\n"

    /** Every logged set, with where it sat in the calendar and the program. */
    fun exportLog(context: Context, rows: List<ExportRow>, unit: String): File {
        val file = File(dir(context), "prayerbook_log.csv")
        file.bufferedWriter().use { w ->
            w.write("date,weekday,program,cycle,week,day,session_finished,exercise,set," +
                "planned_weight,planned_reps,weight,reps,rpe,notes,unit\n")
            rows.forEach { r ->
                val weekday = try {
                    LocalDate.parse(r.date).dayOfWeek.getDisplayName(TextStyle.FULL, Locale.US)
                } catch (_: Exception) { "" }
                w.write(line(r.date, weekday, r.program, r.cycle, if (r.week > 0) r.week else "", r.day,
                    if (r.completed) "yes" else "no", r.exercise, r.set,
                    num(r.plannedWeight), r.plannedReps, num(r.weight), r.reps, num(r.rpe), r.notes, unit))
            }
        }
        return file
    }

    /** Every change to a one-rep max. */
    fun exportMaxes(context: Context, rows: List<MaxHistoryRow>, unit: String): File {
        val file = File(dir(context), "prayerbook_maxes.csv")
        file.bufferedWriter().use { w ->
            w.write("date,exercise,one_rep_max,source,unit\n")
            rows.forEach { r -> w.write(line(r.date, r.exercise, num(r.oneRepMax), r.source, unit)) }
        }
        return file
    }
}
