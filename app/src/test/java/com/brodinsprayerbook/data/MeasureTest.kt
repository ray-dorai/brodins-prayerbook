package com.brodinsprayerbook.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MeasureTest {

    @Test fun timesReadTheWayTheyAreWritten() {
        assertEquals(45, Seconds.parse("45"))
        assertEquals(90, Seconds.parse("1:30"))
        assertEquals(90, Seconds.parse(" 1:30 "))
        assertEquals(3750, Seconds.parse("1:02:30"))
        assertEquals(0, Seconds.parse("0"))
    }

    @Test fun whatIsNotATimeIsRefused() {
        for (bad in listOf("", "abc", "1:75", "1:", ":30", "1:2:3:4", "-5", "1.5")) assertNull(bad, Seconds.parse(bad))
    }

    @Test fun timesAreShownAsMinutesAndSeconds() {
        assertEquals("0:45", Seconds.format(45))
        assertEquals("1:30", Seconds.format(90))
        assertEquals("1:02:30", Seconds.format(3750))
    }

    @Test fun anUnknownMeasureIsWeightAndReps() {
        assertEquals(Measure.TIME, Measure.of("time"))
        assertEquals(Measure.WEIGHT_REPS, Measure.of(""))
        assertEquals(Measure.WEIGHT_REPS, Measure.of("juggling"))
    }
}
