package com.brodinsprayerbook.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.brodinsprayerbook.context
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CsvExportTest {

    @Test fun theLogIsOneRowPerSet() {
        val file = CsvExport.exportLog(context, listOf(
            ExportRow("2026-10-05", "High Frequency Strength", 1, 1, "Mon — Heavy bench, light squat", true,
                "Bench Press", 1, 142.5, "1", 145.0, 1, 7.5, "fast, \"easy\""),
            // No program, nothing prescribed, only a weight typed
            ExportRow("not a date", "", 2, 0, "", false, "KB Curl", 2, null, "", 35.0, null, null, "")
        ), "lb")

        assertEquals("prayerbook_log.csv", file.name)
        assertEquals(
            "date,weekday,program,cycle,week,day,session_finished,exercise,set," +
                "planned_weight,planned_reps,weight,reps,rpe,notes,unit\n" +
            "2026-10-05,Monday,High Frequency Strength,1,1,\"Mon — Heavy bench, light squat\",yes,Bench Press,1," +
                "142.5,1,145,1,7.5,\"fast, \"\"easy\"\"\",lb\n" +
            "not a date,,,2,,,no,KB Curl,2,,,35,,,,lb\n",
            file.readText())
    }

    @Test fun theMaxHistoryIsOneRowPerChange() {
        val file = CsvExport.exportMaxes(context, listOf(
            MaxHistoryRow("2026-10-01", "Bench Press", 185.0, "onboarding"),
            MaxHistoryRow("2026-11-12", "Bench Press", 187.5, "cycle")
        ), "kg")

        assertEquals("prayerbook_maxes.csv", file.name)
        assertEquals(
            "date,exercise,one_rep_max,source,unit\n" +
            "2026-10-01,Bench Press,185,onboarding,kg\n" +
            "2026-11-12,Bench Press,187.5,cycle,kg\n",
            file.readText())
    }

    @Test fun whatIsExportedReadsBackAsCsv() {
        val file = CsvExport.exportLog(context, listOf(
            ExportRow("2026-10-05", "P", 1, 1, "Mon, heavy", true, "Bench Press", 1, null, "", 145.0, 1, null, "a \"note\", with commas")
        ), "lb")

        val cells = ProgramCsv.splitLine(file.readLines()[1])
        assertEquals(16, cells.size)
        assertEquals("Mon, heavy", cells[5])
        assertEquals("a \"note\", with commas", cells[14])
    }
}
