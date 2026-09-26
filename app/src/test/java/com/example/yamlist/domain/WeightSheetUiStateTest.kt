package com.example.yamlist.domain

import com.example.yamlist.ui.weightsheet.WeightSheetRow
import com.example.yamlist.ui.weightsheet.WeightSheetError
import com.example.yamlist.ui.weightsheet.WeightSheetUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeightSheetUiStateTest {
    private val first = WeightSheetRow(1, "1.1", "A", 1, 2.0, true, true, listOf(1))
    private val second = WeightSheetRow(2, "1.2", "B", 1, 3.0, true, false, listOf(2))
    private val parent = WeightSheetRow(3, "1", "Parent", 0, 1.0, false, true, listOf(1, 2))

    @Test fun togglingProgressTargetUpdatesTotalAndSharesBeforeSaving() {
        val state = WeightSheetUiState(
            rows = listOf(parent, first, second),
            drafts = mapOf(1L to "2", 2L to "3"),
            targets = mapOf(1L to true, 2L to true),
            loaded = true,
        )
        assertTrue(state.hasChanges)
        assertEquals(5.0, state.total!!, 0.0)
        assertEquals(40.0, state.share(first)!!, 0.0)
        assertEquals(100.0, state.share(parent)!!, 0.0)

        val withoutSecond = state.copy(targets = state.targets + (2L to false))
        assertEquals(2.0, withoutSecond.total!!, 0.0)
        assertNull(withoutSecond.share(second))
        assertEquals(100.0, withoutSecond.share(first)!!, 0.0)
        assertFalse(withoutSecond.hasChanges)
    }

    @Test fun invalidWeightBlocksTotalEvenForExcludedLeaf() {
        val state = WeightSheetUiState(
            rows = listOf(first, second),
            drafts = mapOf(1L to "2", 2L to "NaN"),
            targets = mapOf(1L to true, 2L to false),
        )
        assertTrue(state.hasInvalid)
        assertNull(state.total)
    }

    @Test fun saveFailureAllowsRetryButLoadFailureAndConflictDoNot() {
        val base = WeightSheetUiState(
            rows = listOf(first),
            drafts = mapOf(1L to "4"),
            targets = mapOf(1L to true),
            loaded = true,
        )
        assertTrue(base.copy(error = WeightSheetError.SAVE_FAILED).canEdit)
        assertTrue(base.copy(error = WeightSheetError.SAVE_FAILED).canSave)
        assertFalse(base.copy(error = WeightSheetError.CONFLICT).canEdit)
        assertFalse(base.copy(error = WeightSheetError.CONFLICT).canSave)
        assertFalse(base.copy(error = WeightSheetError.LOAD_FAILED).canSave)
    }
}
