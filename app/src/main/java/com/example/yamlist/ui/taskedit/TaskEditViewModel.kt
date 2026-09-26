package com.example.yamlist.ui.taskedit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.yamlist.data.repository.YamlistRepository
import com.example.yamlist.domain.model.MarkType
import com.example.yamlist.domain.model.Task
import com.example.yamlist.domain.model.TaskStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject

data class TaskEditState(
    val projectId: Long = -1,
    val taskId: Long = -1,
    val parentId: Long? = null,
    val title: String = "",
    val weightText: String = "1.0",
    val mark: MarkType = MarkType.NONE,
    val colorCode: String? = null,
    val plannedMonth: String = "",
    val dueDate: LocalDate? = null,
    val status: TaskStatus = TaskStatus.TODO,
    val isProgressTarget: Boolean = true,
    val loaded: Boolean = false,
) {
    val isNew: Boolean get() = taskId <= 0L
    val titleError: Boolean get() = title.isBlank() || title.length > 200
    val weightError: Boolean get() {
        val w = weightText.trim().ifBlank { "1.0" }.toDoubleOrNull() ?: return true
        return w < 0.0 || w > 9999.0
    }
    val canSave: Boolean get() = !titleError && !weightError
}

@HiltViewModel
class TaskEditViewModel @Inject constructor(
    private val repo: YamlistRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val projectId: Long = savedStateHandle.get<Long>("projectId") ?: -1L
    private val taskId: Long = (savedStateHandle.get<Long>("taskId") ?: -1L).takeIf { it > 0 } ?: -1L
    private val parentArg: Long = (savedStateHandle.get<Long>("parentId") ?: -1L)

    private val _state = MutableStateFlow(TaskEditState(projectId = projectId, taskId = taskId))
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            if (taskId > 0) {
                repo.getTask(taskId)?.let { t ->
                    _state.value = TaskEditState(
                        projectId = t.projectId, taskId = t.id, parentId = t.parentTaskId,
                        title = t.title,
                        weightText = trimWeight(t.weight), mark = t.markType, colorCode = t.colorCode,
                        plannedMonth = t.plannedYearMonth.orEmpty(), dueDate = t.dueDate,
                        status = t.status, isProgressTarget = t.isProgressTarget, loaded = true,
                    )
                }
            } else {
                _state.update {
                    it.copy(parentId = parentArg.takeIf { p -> p > 0 }, loaded = true)
                }
            }
        }
    }

    fun onTitle(v: String) = _state.update { it.copy(title = v) }
    fun onWeight(v: String) = _state.update { it.copy(weightText = v) }
    fun onMark(v: MarkType) = _state.update { it.copy(mark = v) }
    fun onColor(v: String?) = _state.update { it.copy(colorCode = v) }
    fun onPlannedMonth(v: String) = _state.update { it.copy(plannedMonth = v) }
    fun onDueDate(v: LocalDate?) = _state.update { it.copy(dueDate = v) }
    fun onStatus(v: TaskStatus) = _state.update { it.copy(status = v) }
    fun onProgressTarget(v: Boolean) = _state.update { it.copy(isProgressTarget = v) }

    fun save(onSaved: () -> Unit) {
        val s = _state.value
        if (!s.canSave) return
        // Empty weight -> 1.0 (spec §8.4).
        val weight = s.weightText.trim().ifBlank { "1.0" }.toDoubleOrNull() ?: 1.0
        val plannedMonth = s.plannedMonth.trim().ifBlank { null }
        viewModelScope.launch {
            val now = LocalDateTime.now()
            if (s.isNew) {
                repo.addTask(
                    Task(
                        uuid = UUID.randomUUID().toString(),
                        projectId = s.projectId,
                        parentTaskId = s.parentId,
                        title = s.title.trim(),
                        status = s.status,
                        weight = weight,
                        markType = s.mark,
                        colorCode = s.colorCode,
                        plannedYearMonth = plannedMonth,
                        dueDate = s.dueDate,
                        completedAt = if (s.status == TaskStatus.DONE) now else null,
                        isProgressTarget = s.isProgressTarget,
                        createdAt = now, updatedAt = now,
                    )
                )
            } else {
                val existing = repo.getTask(s.taskId) ?: return@launch
                val completedAt = when {
                    s.status == TaskStatus.DONE && existing.status != TaskStatus.DONE -> now
                    s.status != TaskStatus.DONE -> null
                    else -> existing.completedAt
                }
                repo.updateTask(
                    existing.copy(
                        title = s.title.trim(),
                        status = s.status,
                        weight = weight,
                        markType = s.mark,
                        colorCode = s.colorCode,
                        plannedYearMonth = plannedMonth,
                        dueDate = s.dueDate,
                        completedAt = completedAt,
                        isProgressTarget = s.isProgressTarget,
                    )
                )
            }
            onSaved()
        }
    }

    private fun trimWeight(d: Double): String = if (d % 1.0 == 0.0) d.toLong().toString() else d.toString()
}
