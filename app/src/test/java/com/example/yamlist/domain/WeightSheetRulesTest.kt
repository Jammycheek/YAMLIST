package com.example.yamlist.domain

import com.example.yamlist.ui.weightsheet.WeightSheetRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeightSheetRulesTest {
    @Test fun acceptsZeroAndDecimalWithinRange() {
        assertEquals(0.0, WeightSheetRules.parse(" 0 ")!!, 0.0)
        assertEquals(2.5, WeightSheetRules.parse("2.5")!!, 0.0)
        assertEquals(9999.0, WeightSheetRules.parse("9999")!!, 0.0)
    }

    @Test fun rejectsBlankNonFiniteAndOutOfRange() {
        listOf("", " ", "NaN", "Infinity", "-1", "10000", "abc").forEach {
            assertNull(it, WeightSheetRules.parse(it))
        }
    }

    @Test fun percentagesUseCurrentTotalAndExcludeZero() {
        val total = WeightSheetRules.total(listOf(2.0, 3.0, 0.0))
        assertEquals(5.0, total, 0.0)
        assertEquals(40.0, WeightSheetRules.share(2.0, total), 0.0001)
        assertEquals(0.0, WeightSheetRules.share(0.0, total), 0.0)
    }
}
