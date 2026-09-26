package com.example.yamlist.domain

import com.example.yamlist.ui.bulkchild.BulkChildUiState
import com.example.yamlist.ui.taskedit.TaskEditState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InputValidationTest {
    @Test
    fun taskEditRejectsNonFiniteWeights() {
        assertFalse(TaskEditState(title = "A", weightText = "NaN").canSave)
        assertFalse(TaskEditState(title = "A", weightText = "Infinity").canSave)
    }

    @Test
    fun bulkCreationRejectsInvalidPlannedMonth() {
        assertFalse(BulkChildUiState(titles = listOf("A"), plannedMonth = "2026-9").canSave)
        assertFalse(BulkChildUiState(titles = listOf("A"), plannedMonth = "2026-13").canSave)
        assertTrue(BulkChildUiState(titles = listOf("A"), plannedMonth = "2026-09").canSave)
    }

    @Test
    fun taskEditRejectsInvalidPlannedMonth() {
        assertFalse(TaskEditState(title = "A", plannedMonth = "2026-9").canSave)
        assertFalse(TaskEditState(title = "A", plannedMonth = "2026-13").canSave)
        assertTrue(TaskEditState(title = "A", plannedMonth = "2026-09 ").canSave)
    }
}
