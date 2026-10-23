package com.brodinsprayerbook.data

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.brodinsprayerbook.AppStateRule
import com.brodinsprayerbook.context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opens old databases, with the tables as they really were on the phone at that version,
 * and lets Room bring them up to date. Room refuses to open a database whose migrated tables
 * don't match the entities, so opening at all proves the migrations add up.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {
    @get:Rule val appState = AppStateRule()

    private fun createDatabase(version: Int, fill: (SQLiteDatabase) -> Unit) {
        val path = context.getDatabasePath("prayerbook.db")
        path.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
            javaClass.getResource("/schema_v$version.sql")!!.readText().split(";\n")
                .filter { it.isNotBlank() }.forEach { db.execSQL(it) }
            fill(db)
            db.version = version
        }
    }

    private fun createVersion3() = createDatabase(3) { db ->
            db.execSQL("INSERT INTO Exercise VALUES (1,'Bench Press','Chest',1), (2,'KB Row (1-arm)','Back',0), (3,'Plank','Abs',0)")
            db.execSQL("INSERT INTO Program VALUES (1,'High Frequency Strength','','block',6,5,'',1), (2,'5/3/1','','block',4,4,'',1)")
            db.execSQL("INSERT INTO ProgramDay VALUES (1,1,0,0,'Mon — Heavy bench'), (2,1,5,0,'Mon — Bench realization'), (3,2,0,0,'Bench Day')")
            db.execSQL("""INSERT INTO PlannedSet (id, programDayId, exerciseId, setNumber, reps, weightFormula, isAmrap, note) VALUES
                (1,1,1,1,3,'1RM*0.775',0,''), (2,1,2,2,10,'35',0,''),
                (3,2,1,1,1,'',0,'ramp'), (4,2,1,2,5,'1RM*0.6',0,''),
                (5,3,1,1,5,'TM*0.65',0,'')""")
            db.execSQL("INSERT INTO UserMax VALUES (1,1,185.0,0.9,'2026-10-04')")
            db.execSQL("INSERT INTO WorkoutLog VALUES (1,'2026-10-04',1,1,1), (2,'2026-10-03',NULL,1,0)")
            db.execSQL("INSERT INTO ActualSet VALUES (1,1,1,1,3,145.0,'felt fast')")
    }

    @Test fun aVersion3DatabaseOpensWithItsDataCarriedForward() {
        createVersion3()

        val dao = PrayerBookDb.get(context).dao()

        // 3 → 4: sessions remember where they sat in their program, and maxes gain a history
        val (logged, orphan) = dao.sessions() // newest first
        assertEquals(listOf("High Frequency Strength", 1, "Mon — Heavy bench", true),
            listOf(logged.program, logged.week, logged.dayName, logged.completed))
        assertEquals(listOf("", 0, ""), listOf(orphan.program, orphan.week, orphan.dayName))
        assertEquals(listOf(MaxHistoryRow("2026-10-04", "Bench Press", 185.0, "onboarding")), dao.maxHistoryForExport())
        val set = dao.actualSetsForLog(1).single()
        assertEquals(listOf(145.0, 3, "felt fast", ""), listOf(set.weight, set.reps, set.notes, set.plannedReps))
        assertNull(set.plannedWeight)
        assertNull(set.rir)

        // 4 → 5: the built-in High Frequency Strength gets its rests; nothing else does
        assertEquals(listOf(120, 60), dao.plannedSetsForDay(1).map { it.restSeconds })
        assertEquals(listOf(180, 120), dao.plannedSetsForDay(2).map { it.restSeconds })
        assertEquals(listOf(0), dao.plannedSetsForDay(3).map { it.restSeconds })
        assertEquals(listOf(0, 0, 0), dao.allExercises().map { it.restSeconds })

        // 7 → 8: exercises learn what their sets record
        assertEquals(mapOf("Bench Press" to "weight_reps", "KB Row (1-arm)" to "weight_reps", "Plank" to "time"),
            dao.allExercises().associate { it.name to it.measure })
        assertNull(set.seconds)
        assertNull(set.distance)
    }

    @Test fun effortLoggedAsRpeBecomesRepsInReserve() {
        createDatabase(4) { db ->
            db.execSQL("INSERT INTO Exercise VALUES (1,'Bench Press','Chest',1)")
            db.execSQL("INSERT INTO Program VALUES (1,'High Frequency Strength','','block',6,5,'',1), (2,'Mine','','custom',1,1,'',0)")
            db.execSQL("INSERT INTO ProgramDay VALUES (1,1,0,0,'Mon'), (2,1,5,0,'Mon — Bench realization'), (3,2,0,0,'Day 1')")
            db.execSQL("""INSERT INTO PlannedSet (id, programDayId, exerciseId, setNumber, reps, weightFormula, isAmrap, note) VALUES
                (1,1,1,1,1,'',0,'top, RPE 7–8'), (2,1,1,2,1,'',0,'top, RPE 7'), (3,2,1,1,1,'',0,'RPE 8'),
                (4,1,1,3,3,'1RM*0.775',0,''), (5,3,1,1,1,'',0,'RPE 8')""")
            db.execSQL("INSERT INTO WorkoutLog (id, date, programDayId) VALUES (1,'2026-10-04',1)")
            db.execSQL("""INSERT INTO ActualSet (id, workoutLogId, exerciseId, setNumber, reps, weight, notes, rpe) VALUES
                (1,1,1,1,1,155.0,'',8.0), (2,1,1,2,1,160.0,'',9.5), (3,1,1,3,3,145.0,'',NULL)""")
        }

        val dao = PrayerBookDb.get(context).dao()

        assertEquals(listOf(2.0, 0.5, null), dao.actualSetsForLog(1).map { it.rir })
        assertEquals(listOf("top, RIR 2–3", "top, RIR 3", ""), dao.plannedSetsForDay(1).map { it.note })
        assertEquals(listOf("RIR 2"), dao.plannedSetsForDay(2).map { it.note })
        assertEquals("a lifter's own program keeps its wording", listOf("RPE 8"), dao.plannedSetsForDay(3).map { it.note })
    }
}
