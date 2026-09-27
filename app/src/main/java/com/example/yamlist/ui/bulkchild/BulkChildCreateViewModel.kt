package com.example.yamlist.ui.bulkchild

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.yamlist.R
import com.example.yamlist.data.repository.YamlistRepository
import com.example.yamlist.domain.bulk.BulkTitleParser
import com.example.yamlist.domain.model.MarkType
import com.example.yamlist.domain.model.Task
import com.example.yamlist.domain.model.TaskStatus
import com.example.yamlist.ui.common.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State for the bulk child creation screen (spec §1.3–§1.8).
 *
 * [titles] and [problems] are recomputed from [inputText] on every edit so the
 * preview and the error list always describe exactly what would be saved.
 */
data class BulkChildUiState(
    val parentId: Long = 0,
    val parentTitle: String = "",
    val projectId: Long = 0,
    val inputText: String = "",
    val stripBullets: Boolean = true,
    // Shared attributes applied to every created child (spec §1.5).
    val weightText: String = "1.0",
    val markType: MarkType = MarkType.NONE,
    val colorKey: String? = null,
    val plannedMonth: String = "",
    val dueDate: String = "",
    val status: TaskStatus = TaskStatus.TODO,
    val isProgressTarget: Boolean = true,
    val titles: List<String> = emptyList(),
    val problems: List<BulkTitleParser.Problem> = emptyList(),
    val weightError: Boolean = false,
    val saving: Boolean = false,
    val message: UiText? = null,
) {
    val count: Int get() = titles.size
    val plannedMonthError: Boolean get() = plannedMonth.isNotBlank() &&
        runCatching { java.time.YearMonth.parse(plannedMonth.trim()) }.isFailure
    val dueDateError: Boolean get() = dueDate.isNotBlank() &&
        runCatching { java.time.LocalDate.parse(dueDate.trim()) }.isFailure
    val canSave: Boolean
        get() = !saving && titles.isNotEmpty() && problems.isEmpty() &&
            !weightError && !plannedMonthError && !dueDateError
}

@HiltViewModel
class BulkChildCreateViewModel @Inject constructor(
    private val repo: YamlistRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val parentId: Long = savedStateHandle.get<Long>("parentId") ?: -1L

    private val _state = MutableStateFlow(BulkChildUiState(parentId = parentId))
    val state: StateFlow<BulkChildUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val parent = repo.getTask(parentId)
            if (parent != null) {
                _state.update {
                    it.copy(parentTitle = parent.title, projectId = parent.projectId)
                }
            }
        }
    }

    fun onInputText(text: String) = _state.update { reparse(it.copy(inputText = text)) }

    fun onStripBullets(value: Boolean) = _state.update { reparse(it.copy(stripBullets = value)) }

    fun onWeightText(text: String) = _state.update {
        val parsed = text.trim().toDoubleOrNull()
        it.copy(weightText = text, weightError = text.isNotBlank() &&
            (parsed == null || !parsed.isFinite() || parsed < 0.0 || parsed > 9999.0))
    }

    fun onMark(mark: MarkType) = _state.update { it.copy(markType = mark) }
    fun onColor(key: String?) = _state.update { it.copy(colorKey = key) }
    fun onPlannedMonth(text: String) = _state.update { it.copy(plannedMonth = text) }
    fun onDueDate(text: String) = _state.update { it.copy(dueDate = text) }
    fun onStatus(status: TaskStatus) = _state.update { it.copy(status = status) }
    fun onProgressTarget(value: Boolean) = _state.update { it.copy(isProgressTarget = value) }
    fun clearMessage() = _state.update { it.copy(message = null) }

    private fun reparse(s: BulkChildUiState): BulkChildUiState {
        val result = BulkTitleParser.parse(s.inputText, s.stripBullets)
        return s.copy(titles = result.titles, problems = result.problems)
    }

    /**
     * Saves all children in one transaction (spec §1.9). On failure nothing is
     * written, and the screen stays open with the input intact so the user can retry.
     */
    fun save(onDone: () -> Unit) {
        val s = _state.value
        if (!s.canSave) return
        _state.update { it.copy(saving = true, message = null) }
        viewModelScope.launch {
            try {
                // uuid / timestamps / displayOrder are assigned per row by the
                // repository; the values here are only placeholders.
                val stamp = java.time.LocalDateTime.now()
                val template = Task(
                    uuid = "",
                    projectId = s.projectId,
                    parentTaskId = s.parentId,
                    title = "",
                    status = s.status,
                    completedAt = if (s.status == TaskStatus.DONE) stamp else null,
                    weight = s.weightText.trim().toDoubleOrNull() ?: 1.0,
                    markType = s.markType,
                    colorCode = s.colorKey,
                    plannedYearMonth = s.plannedMonth.trim().ifBlank { null },
                    dueDate = parseDateOrNull(s.dueDate),
                    isProgressTarget = s.isProgressTarget,
                    createdAt = stamp,
                    updatedAt = stamp,
                )
                repo.addChildrenBulk(s.parentId, s.titles, template)
                onDone()
            } catch (e: Exception) {
                _state.update {
                    it.copy(saving = false, message = UiText(R.string.save_failed))
                }
            }
        }
    }

    private fun parseDateOrNull(text: String): java.time.LocalDate? =
        text.trim().takeIf { it.isNotBlank() }?.let {
            runCatching { java.time.LocalDate.parse(it) }.getOrNull()
        }
}
