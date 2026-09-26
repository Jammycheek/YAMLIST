package com.example.yamlist.ui.tasktree

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.yamlist.data.repository.YamlistRepository
import com.example.yamlist.domain.model.MarkType
import com.example.yamlist.domain.model.ParentDisplayState
import com.example.yamlist.domain.model.Project
import com.example.yamlist.domain.model.TaskNode
import com.example.yamlist.domain.model.TaskStatus
import com.example.yamlist.domain.progress.HierarchyNumbering
import com.example.yamlist.domain.progress.ProgressCalculator
import com.example.yamlist.domain.progress.ProgressResult
import com.example.yamlist.domain.progress.SiblingReorder
import com.example.yamlist.domain.progress.TaskTreeBuilder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A single visible row in the flattened, expansion-aware tree. */
data class TreeRow(
    val node: TaskNode,
    /** Full hierarchy number such as "2.3.1", generated on display (spec §2.7). */
    val number: String,
    val depth: Int,
    val isLeaf: Boolean,
    val expanded: Boolean,
    val progress: ProgressResult,           // subtree progress (for parents)
    val parentState: ParentDisplayState,    // for parents
    val hasComments: Boolean,
) {
    /**
     * Just this task's position inside its own level ("1" rather than "2.3.1").
     * Indentation already communicates the ancestry, so the full path only makes
     * rows harder to scan once the tree gets deep.
     */
    val localNumber: String get() = number.substringAfterLast('.')
}

data class TaskTreeUiState(
    val project: Project? = null,
    val rows: List<TreeRow> = emptyList(),
    val overall: ProgressResult = ProgressResult.EMPTY,
    val depthWarning: Boolean = false,       // spec §7.2: 6+ levels
)

@HiltViewModel
class TaskTreeViewModel @Inject constructor(
    private val repo: YamlistRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val projectId: Long = savedStateHandle.get<Long>("projectId") ?: -1L

    // Collapsed node ids (default = everything expanded).
    private val collapsed = MutableStateFlow<Set<Long>>(emptySet())

    /**
     * Latest built forest, kept so the expand/collapse actions can reason about
     * branches that are currently hidden. It is refreshed whenever [uiState] is
     * collected, which is exactly while the screen (and its buttons) is on show.
     */
    private var currentForest: List<TaskNode> = emptyList()

    val uiState: StateFlow<TaskTreeUiState> =
        combine(
            repo.observeProject(projectId),
            repo.observeTasks(projectId),
            collapsed,
            repo.observeCommentCounts(),
        ) { project, tasks, collapsedIds, commentCounts ->
            val forest = TaskTreeBuilder.build(tasks)
            currentForest = forest
            val overall = ProgressCalculator.forForest(forest)
            val rows = flatten(forest, collapsedIds, commentCounts)
            TaskTreeUiState(
                project = project,
                rows = rows,
                overall = overall,
                depthWarning = TaskTreeBuilder.maxDepth(forest) >= 6,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TaskTreeUiState())

    private fun flatten(
        forest: List<TaskNode>,
        collapsedIds: Set<Long>,
        commentCounts: Map<Long, Int>,
    ): List<TreeRow> {
        val out = ArrayList<TreeRow>()
        // Numbers follow the full tree, not just the expanded rows, so collapsing
        // a branch never renumbers what stays on screen.
        val numbers = HierarchyNumbering.numbersFor(forest)
        fun walk(node: TaskNode, depth: Int) {
            val expanded = node.task.id !in collapsedIds
            out.add(
                TreeRow(
                    node = node,
                    number = numbers[node.task.id].orEmpty(),
                    depth = depth,
                    isLeaf = node.isLeaf,
                    expanded = expanded,
                    progress = ProgressCalculator.forSubtree(node),
                    parentState = if (node.isLeaf) ParentDisplayState.NO_TARGET
                    else ProgressCalculator.parentDisplayState(node),
                    hasComments = (commentCounts[node.task.id] ?: 0) > 0,
                )
            )
            if (expanded) node.children.forEach { walk(it, depth + 1) }
        }
        forest.forEach { walk(it, 0) }
        return out
    }

    fun toggleExpand(taskId: Long) {
        collapsed.value = collapsed.value.toMutableSet().apply {
            if (contains(taskId)) remove(taskId) else add(taskId)
        }
    }

    /** Opens every branch of the tree. */
    fun expandAll() {
        collapsed.value = emptySet()
    }

    /** Closes every branch, leaving only the root tasks visible. */
    fun collapseAll() {
        val parents = HashSet<Long>()
        fun rec(node: TaskNode) {
            if (!node.isLeaf) {
                parents.add(node.task.id)
                node.children.forEach { rec(it) }
            }
        }
        currentForest.forEach { rec(it) }
        collapsed.value = parents
    }

    /**
     * Closes the innermost layer that is currently open, so the tree gets one
     * level shallower each time instead of collapsing everything at once.
     */
    fun collapseOneLevel() {
        val open = visibleParents(expanded = true)
        val deepest = open.maxOfOrNull { it.second } ?: return
        val targets = open.filter { it.second == deepest }.map { it.first }
        collapsed.value = collapsed.value + targets
    }

    /** Opens the shallowest layer that is still closed: the mirror of [collapseOneLevel]. */
    fun expandOneLevel() {
        val shut = visibleParents(expanded = false)
        val shallowest = shut.minOfOrNull { it.second } ?: return
        val targets = shut.filter { it.second == shallowest }.map { it.first }
        collapsed.value = collapsed.value - targets.toSet()
    }

    /**
     * Parent nodes reachable without opening anything first, paired with their
     * depth. [expanded] selects whether to report the open ones or the shut ones.
     */
    private fun visibleParents(expanded: Boolean): List<Pair<Long, Int>> {
        val collapsedIds = collapsed.value
        val out = ArrayList<Pair<Long, Int>>()
        fun rec(node: TaskNode, depth: Int) {
            val isOpen = node.task.id !in collapsedIds
            if (!node.isLeaf && isOpen == expanded) out.add(node.task.id to depth)
            if (isOpen) node.children.forEach { rec(it, depth + 1) }
        }
        currentForest.forEach { rec(it, 0) }
        return out
    }

    fun toggleDone(taskId: Long) = viewModelScope.launch { repo.toggleDone(taskId) }

    fun setColor(taskId: Long, colorKey: String?) =
        viewModelScope.launch { repo.setTaskColor(taskId, colorKey) }

    fun clearMark(taskId: Long) = viewModelScope.launch { repo.setTaskMark(taskId, MarkType.NONE) }

    /**
     * Checkbox action for a parent row: ticking it completes the whole subtree,
     * unticking it returns the subtree to TODO. Progress still counts leaves only,
     * so this is a convenience for the leaves underneath rather than a status the
     * parent carries on its own.
     */
    fun toggleSubtreeDone(rootId: Long, currentlyAllDone: Boolean) =
        setSubtreeStatus(rootId, if (currentlyAllDone) TaskStatus.TODO else TaskStatus.DONE)

    fun moveSiblingUp(taskId: Long) =
        viewModelScope.launch { repo.moveSibling(taskId, SiblingReorder.UP) }

    fun moveSiblingDown(taskId: Long) =
        viewModelScope.launch { repo.moveSibling(taskId, SiblingReorder.DOWN) }

    /** Deletes just this task, promoting its direct children up one level (spec addendum). */
    fun deleteTaskPromotingChildren(taskId: Long) =
        viewModelScope.launch { repo.deleteTaskPromotingChildren(taskId) }

    fun setSubtreeStatus(rootId: Long, status: TaskStatus) =
        viewModelScope.launch { repo.setSubtreeStatus(projectId, rootId, status) }

    fun collapseSubtree(rootId: Long, collapse: Boolean) {
        collapsed.value = collapsed.value.toMutableSet().apply {
            if (collapse) add(rootId) else remove(rootId)
        }
    }

    suspend fun descendantCount(taskId: Long): Int = repo.countDescendants(taskId)

    fun deleteTask(taskId: Long) = viewModelScope.launch { repo.deleteTaskSubtree(taskId) }
}
