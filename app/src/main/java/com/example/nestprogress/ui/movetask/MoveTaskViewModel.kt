package com.example.nestprogress.ui.movetask

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nestprogress.data.repository.NestRepository
import com.example.nestprogress.domain.model.TaskNode
import com.example.nestprogress.domain.progress.HierarchyNumbering
import com.example.nestprogress.domain.progress.TaskTreeBuilder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One selectable destination row: an existing task, indented by depth. */
data class MoveCandidateRow(
    val taskId: Long,
    val title: String,
    val depth: Int,
    val number: String,
    /** True for the task's current parent, so the picker can highlight it. */
    val isCurrentParent: Boolean,
)

data class MoveTaskUiState(
    val taskId: Long = -1L,
    val taskTitle: String = "",
    val candidates: List<MoveCandidateRow> = emptyList(),
    val isCurrentlyRoot: Boolean = false,
    val moved: Boolean = false,
)

/**
 * Picks a new parent for one task (move-across-levels, spec addendum).
 *
 * The candidate list excludes the task itself and everything in its own
 * subtree, since moving a task under its own descendant would create a cycle.
 * "Project root" is offered separately by the screen as a null-parent choice.
 */
@HiltViewModel
class MoveTaskViewModel @Inject constructor(
    private val repo: NestRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val taskId: Long = savedStateHandle.get<Long>("taskId") ?: -1L

    private val movedFlag = MutableStateFlow(false)
    private val _state = MutableStateFlow(MoveTaskUiState(taskId = taskId))
    val state: StateFlow<MoveTaskUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val task = repo.getTask(taskId) ?: return@launch
            combine(repo.observeTasks(task.projectId), movedFlag) { tasks, moved ->
                val forest = TaskTreeBuilder.build(tasks)
                val numbers = HierarchyNumbering.numbersFor(forest)

                // Exclude the task itself and its whole subtree from the candidate
                // list; moving under a descendant would create a cycle.
                val excluded = HashSet<Long>()
                fun markExcluded(node: TaskNode) {
                    excluded.add(node.task.id)
                    node.children.forEach { markExcluded(it) }
                }
                fun find(nodes: List<TaskNode>): TaskNode? {
                    for (n in nodes) {
                        if (n.task.id == taskId) return n
                        find(n.children)?.let { return it }
                    }
                    return null
                }
                find(forest)?.let { markExcluded(it) }

                val currentTask = tasks.firstOrNull { it.id == taskId }
                val rows = ArrayList<MoveCandidateRow>()
                fun walk(node: TaskNode, depth: Int) {
                    if (node.task.id in excluded) return
                    rows.add(
                        MoveCandidateRow(
                            taskId = node.task.id,
                            title = node.task.title,
                            depth = depth,
                            number = numbers[node.task.id].orEmpty(),
                            isCurrentParent = node.task.id == currentTask?.parentTaskId,
                        )
                    )
                    node.children.forEach { walk(it, depth + 1) }
                }
                forest.forEach { walk(it, 0) }

                MoveTaskUiState(
                    taskId = taskId,
                    taskTitle = currentTask?.title ?: task.title,
                    candidates = rows,
                    isCurrentlyRoot = currentTask?.parentTaskId == null,
                    moved = moved,
                )
            }.collect { _state.value = it }
        }
    }

    /** [newParentId] null means "project root". */
    fun moveTo(newParentId: Long?) {
        viewModelScope.launch {
            val ok = repo.moveTaskToParent(taskId, newParentId)
            if (ok) movedFlag.value = true
        }
    }
}
