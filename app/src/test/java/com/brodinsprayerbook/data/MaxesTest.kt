package com.brodinsprayerbook.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.brodinsprayerbook.memoryDb
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MaxesTest {
    private lateinit var db: PrayerBookDb
    private lateinit var dao: PrayerBookDao
    private var bench = 0L

    @Before fun open() {
        db = memoryDb()
        dao = db.dao()
        bench = dao.insertExercise(Exercise(name = "Bench Press", isMainLift = true))
    }

    @After fun close() = db.close()

    @Test fun everyChangeLandsInTheHistory() {
        Maxes.set(dao, bench, 185.0, "2026-10-01", "onboarding")
        Maxes.set(dao, bench, 190.0, "2026-11-12", "cycle")

        val max = dao.maxFor(bench)!!
        assertEquals(190.0, max.oneRepMax, 0.0)
        assertEquals("2026-11-12", max.updatedDate)
        assertEquals(171.0, max.trainingMax, 1e-9)
        assertEquals(1, dao.allMaxes().size)
        assertEquals(
            listOf(MaxHistoryRow("2026-10-01", "Bench Press", 185.0, "onboarding"),
                   MaxHistoryRow("2026-11-12", "Bench Press", 190.0, "cycle")),
            dao.maxHistoryForExport())
    }

    @Test fun settingTheSameMaxAgainChangesNothing() {
        Maxes.set(dao, bench, 185.0, "2026-10-01", "onboarding")
        Maxes.set(dao, bench, 185.0, "2026-10-09", "manual")

        assertEquals("2026-10-01", dao.maxFor(bench)!!.updatedDate)
        assertEquals(1, dao.maxHistoryForExport().size)
    }

    @Test fun aChangedMaxKeepsItsTrainingMaxPercentage() {
        dao.upsertMax(UserMax(exerciseId = bench, oneRepMax = 185.0, trainingMaxPct = 0.85))
        Maxes.set(dao, bench, 200.0, "2026-10-09", "manual")
        assertEquals(170.0, dao.maxFor(bench)!!.trainingMax, 1e-9)
    }
}
