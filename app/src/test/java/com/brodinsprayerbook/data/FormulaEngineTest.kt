package com.brodinsprayerbook.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class FormulaEngineTest {

    @Before fun reset() { FormulaEngine.increment = 5.0 }

    @Test fun resolvesAgainstTheRightMax() {
        assertEquals(145.0, FormulaEngine.resolve("1RM*0.775", 166.5, 185.0)!!, 0.0)
        assertEquals(110.0, FormulaEngine.resolve("TM*0.65", 166.5, 185.0)!!, 0.0)
        assertEquals(145.0, FormulaEngine.resolve("TM*0.85+5", 166.5, 185.0)!!, 0.0)
        assertEquals(35.0, FormulaEngine.resolve("35", null, null)!!, 0.0)
        assertNull(FormulaEngine.resolve("1RM*0.8", 100.0, null))
        assertNull(FormulaEngine.resolve("", 100.0, 100.0))
    }

    @Test fun roundsToTheChosenIncrement() {
        FormulaEngine.increment = 2.5
        assertEquals(142.5, FormulaEngine.resolve("1RM*0.775", null, 185.0)!!, 0.0)
        assertEquals("142.5", FormulaEngine.formatWeight(142.5))
        assertEquals("145", FormulaEngine.formatWeight(145.0))
    }

    @Test fun cycleMaxesTakeTheBestShowing() {
        val best = FormulaEngine.bestOneRepMaxes(listOf(
            LiftedSet(1, 150.0, 3),   // est 165
            LiftedSet(1, 187.5, 1),   // single counts at face value
            LiftedSet(1, 100.0, 20),  // too many reps to trust
            LiftedSet(2, 200.0, 5)    // est 233.3 → 230
        ))
        assertEquals(187.5, best[1]!!, 0.0)
        assertEquals(230.0, best[2]!!, 0.0)
    }
}
