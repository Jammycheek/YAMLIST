package com.example.yamlist.ui.taskdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.yamlist.R
import com.example.yamlist.data.local.entity.TaskCommentEntity
import com.example.yamlist.data.repository.YamlistRepository
import com.example.yamlist.domain.model.Task
import com.example.yamlist.domain.model.TaskStatus
import com.example.yamlist.ui.common.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TaskDetailUi(
    val task: Task? = null,
    val children: List<Task> = emptyList(),
    val comments: List<TaskCommentEntity> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TaskDetailViewModel @Inject constructor(
    private val repo: YamlistRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val taskId: Long = savedStateHandle.get<Long>("taskId") ?: -1L

    private val taskFlow = repo.observeTask(taskId)
    val deleteConfirmationCount = MutableStateFlow<Int?>(null)
    val deleteError = MutableStateFlow<UiText?>(null)
    private var deleting = false

    val ui: StateFlow<TaskDetailUi> =
        combine(
            taskFlow,
            taskFlow.flatMapLatest { t ->
                if (t == null) kotlinx.coroutines.flow.flowOf(emptyList())
                else repo.observeTasks(t.projectId)
            },
            repo.observeComments(taskId),
        ) { task, projectTasks, comments ->
            TaskDetailUi(
                task = task,
                children = projectTasks.filter { it.parentTaskId == taskId }
                    .sortedBy { it.displayOrder },
                comments = comments,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TaskDetailUi())

    fun toggleDone() = viewModelScope.launch { repo.toggleDone(taskId) }
    fun setHold() = viewModelScope.launch { repo.setStatus(taskId, TaskStatus.HOLD) }
    fun addComment(body: String) = viewModelScope.launch {
        if (body.isNotBlank()) repo.addComment(taskId, body.trim())
    }
    fun toggleCommentStruck(commentId: Long, currentlyStruck: Boolean) =
        viewModelScope.launch { repo.setCommentStruck(commentId, !currentlyStruck) }
    fun deleteComment(commentId: Long) = viewModelScope.launch { repo.deleteComment(commentId) }
    fun requestDeleteConfirmation() = viewModelScope.launch {
        if (deleting) return@launch
        deleteError.value = null
        deleteConfirmationCount.value = repo.countDescendants(taskId)
    }
    fun cancelDelete() { deleteConfirmationCount.value = null }
    fun delete(onDeleted: () -> Unit) {
        if (deleting || deleteConfirmationCount.value == null) return
        deleting = true
        deleteConfirmationCount.value = null
        viewModelScope.launch {
            try {
                repo.deleteTaskSubtree(taskId)
            } catch (e: Exception) {
                deleting = false
                deleteError.value = UiText(R.string.delete_failed)
                return@launch
            }
            onDeleted()
        }
    }
}
