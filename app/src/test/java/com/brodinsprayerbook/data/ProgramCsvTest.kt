package com.brodinsprayerbook.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ProgramCsvTest {

    @Test fun weightsParseToFormulas() {
        assertEquals("1RM*0.775", ProgramCsv.parseWeight("77.5% 1RM"))
        assertEquals("TM*0.65", ProgramCsv.parseWeight("65% TM"))
        assertEquals("TM*0.85+5", ProgramCsv.parseWeight("85% tm + 5"))
        assertEquals("1RM*0.6", ProgramCsv.parseWeight("60%"))
        assertEquals("1RM*0.7", ProgramCsv.parseWeight("70% of 1RM"))
        assertEquals("135", ProgramCsv.parseWeight("135"))
        assertEquals("TM*0.65", ProgramCsv.parseWeight("TM*0.65"))
        assertEquals("", ProgramCsv.parseWeight("  "))
    }

    @Test fun weightsFormatBack() {
        for (w in listOf("77.5% 1RM", "65% TM", "85% TM +5", "135", ""))
            assertEquals(w, ProgramCsv.formatWeight(ProgramCsv.parseWeight(w)))
        assertEquals("100% TM", ProgramCsv.formatWeight("TM"))
    }

    @Test fun repsParse() {
        assertEquals(Triple(5, "", false), ProgramCsv.parseReps("5"))
        assertEquals(Triple(5, "", true), ProgramCsv.parseReps("5+"))
        assertEquals(Triple(8, "8-12/side", false), ProgramCsv.parseReps("8-12/side"))
    }

    @Test fun fileRoundTrips() {
        val csv = ProgramCsv.HEADER + "\n" +
            "1,1,\"Mon, heavy\",Bench Press,3,3,77.5% 1RM,120,\n" +
            "1,1,\"Mon, heavy\",KB Row,3,10-15/side,35,60,optional\n" +
            "2,1,OHP Day,Overhead Press,1,5+,85% TM,,\n"
        val rows = ProgramCsv.parse(csv)
        assertEquals(3, rows.size)
        assertEquals("Mon, heavy", rows[0].dayName)
        assertEquals("1RM*0.775", rows[0].formula)
        assertEquals("10-15/side", rows[1].repsText)
        assertEquals(listOf(120, 60, 0), rows.map { it.rest })
        assertTrue(rows[2].amrap)
        assertEquals(csv, ProgramCsv.format(rows))
    }

    @Test fun onlyExerciseAndRepsAreRequired() {
        val rows = ProgramCsv.parse("Exercise,Reps\nPull-Up,8\n")
        assertEquals(CsvSet(1, 1, "", "Pull-Up", 1, 8, "", false, "", ""), rows.single())
    }

    @Test fun badRowsNameTheirLine() {
        try {
            ProgramCsv.parse(ProgramCsv.HEADER + "\n1,1,,Squat,3,5,heavy,,\n")
            fail("expected an error")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.startsWith("Row 2"))
        }
    }
}
