package com.brodinsprayerbook.data

import android.content.Context
import android.net.Uri
import android.os.Environment
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileWriter

/**
 * Portable program format (.json):
 * {
 *   "name": "My Program",
 *   "description": "...",
 *   "periodizationType": "block",
 *   "cycleWeeks": 4,
 *   "daysPerWeek": 4,
 *   "progressionRule": "recalc_tm_per_cycle",
 *   "days": [
 *     {
 *       "weekIndex": 0,
 *       "dayIndex": 0,
 *       "name": "OHP Day",
 *       "sets": [
 *         { "exercise": "Overhead Press", "set": 1, "reps": 5, "formula": "TM*0.65", "amrap": false },
 *         { "exercise": "Overhead Press", "set": 2, "reps": 5, "formula": "TM*0.75", "amrap": false },
 *         { "exercise": "Overhead Press", "set": 3, "reps": 5, "formula": "TM*0.85", "amrap": true }
 *       ]
 *     }
 *   ]
 * }
 */
object ProgramPorter {

    // --- Export ---

    fun export(context: Context, dao: PrayerBookDao, programId: Long): File? {
        val program = dao.allPrograms().find { it.id == programId } ?: return null
        val days = dao.daysForProgram(programId)
        val exercises = dao.allExercises().associateBy { it.id }

        val json = JSONObject().apply {
            put("format", "brodins-prayerbook-v1")
            put("name", program.name)
            put("description", program.description)
            put("periodizationType", program.periodizationType)
            put("cycleWeeks", program.cycleWeeks)
            put("daysPerWeek", program.daysPerWeek)
            put("progressionRule", program.progressionRule)

            val daysArr = JSONArray()
            for (day in days) {
                val sets = dao.plannedSetsForDay(day.id)
                val dayObj = JSONObject().apply {
                    put("weekIndex", day.weekIndex)
                    put("dayIndex", day.dayIndex)
                    put("name", day.name)
                    val setsArr = JSONArray()
                    for (s in sets) {
                        setsArr.put(JSONObject().apply {
                            put("exercise", s.exerciseName)
                            put("set", s.setNumber)
                            put("reps", s.reps)
                            put("formula", s.weightFormula)
                            put("amrap", s.isAmrap)
                            if (s.repsText.isNotBlank()) put("repsText", s.repsText)
                            if (s.note.isNotBlank()) put("note", s.note)
                        })
                    }
                    put("sets", setsArr)
                }
                daysArr.put(dayObj)
            }
            put("days", daysArr)
        }

        val safeName = program.name.replace(Regex("[^a-zA-Z0-9_-]"), "_").lowercase()
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        val file = File(dir, "${safeName}.json")
        FileWriter(file).use { it.write(json.toString(2)) }
        return file
    }

    // --- Import ---

    fun import(context: Context, dao: PrayerBookDao, uri: Uri): ImportResult {
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
            ?: return ImportResult(false, "Could not read file")

        return try {
            importFromJson(dao, text)
        } catch (e: Exception) {
            ImportResult(false, "Invalid format: ${e.message}")
        }
    }

    fun importFromJson(dao: PrayerBookDao, jsonText: String): ImportResult {
        val json = JSONObject(jsonText)

        val name = json.getString("name")

        // Check for duplicates
        val existing = dao.allPrograms().find { it.name == name }
        if (existing != null) {
            return ImportResult(false, "Program '$name' already exists")
        }

        val programId = dao.insertProgram(Program(
            name = name,
            description = json.optString("description", ""),
            periodizationType = json.optString("periodizationType", "custom"),
            cycleWeeks = json.optInt("cycleWeeks", 1),
            daysPerWeek = json.optInt("daysPerWeek", 1),
            progressionRule = json.optString("progressionRule", ""),
            isBuiltIn = false
        ))

        // Build exercise name → id cache, creating new exercises as needed
        val exerciseCache = dao.allExercises().associateBy({ it.name.lowercase() }, { it.id }).toMutableMap()

        val days = json.getJSONArray("days")
        var setsImported = 0

        for (d in 0 until days.length()) {
            val dayObj = days.getJSONObject(d)
            val dayId = dao.insertProgramDay(ProgramDay(
                programId = programId,
                weekIndex = dayObj.optInt("weekIndex", 0),
                dayIndex = dayObj.optInt("dayIndex", 0),
                name = dayObj.optString("name", "")
            ))

            val sets = dayObj.getJSONArray("sets")
            for (s in 0 until sets.length()) {
                val setObj = sets.getJSONObject(s)
                val exerciseName = setObj.getString("exercise")
                val exerciseId = exerciseCache.getOrPut(exerciseName.lowercase()) {
                    dao.insertExercise(Exercise(name = exerciseName))
                }

                dao.insertPlannedSet(PlannedSet(
                    programDayId = dayId,
                    exerciseId = exerciseId,
                    setNumber = setObj.getInt("set"),
                    reps = setObj.getInt("reps"),
                    weightFormula = setObj.optString("formula", ""),
                    isAmrap = setObj.optBoolean("amrap", false),
                    repsText = setObj.optString("repsText", ""),
                    note = setObj.optString("note", "")
                ))
                setsImported++
            }
        }

        return ImportResult(true, "Imported '$name': ${days.length()} days, $setsImported sets")
    }

    // --- CSV: the shareable format ---

    fun exportCsv(context: Context, dao: PrayerBookDao, programId: Long): File? {
        val program = dao.allPrograms().find { it.id == programId } ?: return null
        val rows = mutableListOf<CsvSet>()
        for (day in dao.daysForProgram(programId)) {
            for (s in dao.plannedSetsForDay(day.id)) {
                val row = CsvSet(day.weekIndex + 1, day.dayIndex + 1, day.name, s.exerciseName, 1,
                    s.reps, s.repsText, s.isAmrap, s.weightFormula, s.note, s.restSeconds)
                val last = rows.lastOrNull()
                // Fold runs of identical sets into one row
                if (last != null && last.copy(sets = 1) == row) rows[rows.size - 1] = last.copy(sets = last.sets + 1)
                else rows += row
            }
        }
        val safeName = program.name.replace(Regex("[^a-zA-Z0-9_-]+"), "_").trim('_').lowercase()
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "$safeName.csv")
        file.writeText(ProgramCsv.format(rows))
        return file
    }

    /** Create a program from parsed CSV rows. Replaces any program of the same name. */
    fun importCsv(dao: PrayerBookDao, name: String, rows: List<CsvSet>): ImportResult {
        if (rows.isEmpty()) return ImportResult(false, "No sets found in the file")
        dao.allPrograms().find { it.name == name }?.let { dao.deleteProgram(it.id) }

        val programId = dao.insertProgram(Program(
            name = name, periodizationType = "custom",
            cycleWeeks = rows.maxOf { it.week }, daysPerWeek = rows.maxOf { it.day }
        ))
        val exercises = dao.allExercises().associateBy({ it.name.lowercase() }, { it }).toMutableMap()
        var sets = 0
        val byDay = rows.groupBy { it.week to it.day }.toSortedMap(compareBy({ it.first }, { it.second }))
        for ((key, dayRows) in byDay) {
            val dayId = dao.insertProgramDay(ProgramDay(
                programId = programId, weekIndex = key.first - 1, dayIndex = key.second - 1,
                name = dayRows.firstOrNull { it.dayName.isNotBlank() }?.dayName ?: "Day ${key.second}"
            ))
            var n = 0
            for (r in dayRows) {
                val usesMax = ProgramCsv.usesMax(r.formula)
                val ex = exercises.getOrPut(r.exercise.lowercase()) {
                    val id = dao.insertExercise(Exercise(name = r.exercise, isMainLift = usesMax))
                    Exercise(id = id, name = r.exercise, isMainLift = usesMax)
                }
                // A lift prescribed as a percentage needs a max, so it must be offered in Update Maxes
                if (usesMax && !ex.isMainLift) {
                    dao.markMainLift(ex.id)
                    exercises[r.exercise.lowercase()] = ex.copy(isMainLift = true)
                }
                repeat(r.sets) {
                    dao.insertPlannedSet(PlannedSet(
                        programDayId = dayId, exerciseId = ex.id, setNumber = ++n, reps = r.reps,
                        weightFormula = r.formula, isAmrap = r.amrap, repsText = r.repsText, note = r.note,
                        restSeconds = r.rest
                    ))
                    sets++
                }
            }
        }
        return ImportResult(true, "Imported '$name': ${byDay.size} days, $sets sets")
    }

    data class ImportResult(val success: Boolean, val message: String)
}
