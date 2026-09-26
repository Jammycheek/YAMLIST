package com.example.yamlist.ui.weightsheet

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.yamlist.data.repository.YamlistRepository
import com.example.yamlist.data.repository.TaskWeightChange
import com.example.yamlist.domain.model.TaskNode
import com.example.yamlist.domain.progress.HierarchyNumbering
import com.example.yamlist.domain.progress.TaskTreeBuilder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WeightSheetRow(
    val id: Long,
    val number: String,
    val title: String,
    val depth: Int,
    val originalWeight: Double,
    val isLeaf: Boolean,
    val isProgressTarget: Boolean,
    val leafIds: List<Long>,
)

enum class WeightSheetError { CONFLICT, LOAD_FAILED, SAVE_FAILED }

data class WeightSheetUiState(
    val projectTitle: String = "",
    val rows: List<WeightSheetRow> = emptyList(),
    val drafts: Map<Long, String> = emptyMap(),
    val targets: Map<Long, Boolean> = emptyMap(),
    val loaded: Boolean = false,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val error: WeightSheetError? = null,
) {
    fun parsed(row: WeightSheetRow): Double? =
        WeightSheetRules.parse(drafts[row.id].orEmpty())
    fun isTarget(row: WeightSheetRow): Boolean = targets[row.id] ?: row.isProgressTarget

    val hasInvalid: Boolean get() = rows.any { it.isLeaf && parsed(it) == null }
    val hasChanges: Boolean get() = rows.any {
        it.isLeaf && (parsed(it) != it.originalWeight || isTarget(it) != it.isProgressTarget)
    }
    val total: Double? get() = if (hasInvalid) null else WeightSheetRules.total(rows.mapNotNull {
        if (it.isLeaf && isTarget(it)) parsed(it) else null
    })
    val targetCount: Int get() = rows.count {
        it.isLeaf && isTarget(it) && (parsed(it) ?: 0.0) > 0.0
    }
    val canEdit: Boolean get() = loaded && !saving &&
        error != WeightSheetError.CONFLICT && error != WeightSheetError.LOAD_FAILED
    val canSave: Boolean get() = canEdit && !hasInvalid
    fun share(row: WeightSheetRow): Double? {
        val all = total ?: return null
        if (row.leafIds.none { targets[it] == true }) return null
        val part = row.leafIds.sumOf { id ->
            if (targets[id] == true) WeightSheetRules.parse(drafts[id].orEmpty()) ?: 0.0
            else 0.0
        }
        return WeightSheetRules.share(part, all)
    }
}

@HiltViewModel
class WeightSheetViewModel @Inject constructor(
    private val repo: YamlistRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val projectId: Long = savedStateHandle.get<Long>("projectId") ?: -1L
    private val _state = MutableStateFlow(WeightSheetUiState())
    val state: StateFlow<WeightSheetUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val project = repo.getProjectOnce(projectId)
                if (project == null || project.isDeleted) {
                    _state.update { it.copy(loaded = true, error = WeightSheetError.LOAD_FAILED) }
                    return@launch
                }
                val forest = TaskTreeBuilder.build(repo.observeTasks(projectId).first())
                val numbers = HierarchyNumbering.numbersFor(forest)
                fun leafIds(node: TaskNode): List<Long> =
                    if (node.isLeaf) listOf(node.task.id)
                    else node.children.flatMap(::leafIds)
                val rows = TaskTreeBuilder.flatten(forest).map { (node, depth) ->
                    WeightSheetRow(
                        id = node.task.id,
                        number = numbers[node.task.id].orEmpty(),
                        title = node.task.title,
                        depth = depth,
                        originalWeight = node.task.weight,
                        isLeaf = node.isLeaf,
                        isProgressTarget = node.task.isProgressTarget,
                        leafIds = leafIds(node),
                    )
                }
                _state.value = WeightSheetUiState(
                    projectTitle = project.title,
                    rows = rows,
                    drafts = rows.filter { it.isLeaf }.associate { row ->
                        row.id to WeightSheetRules.display(row.originalWeight)
                    },
                    targets = rows.filter { it.isLeaf }.associate { row ->
                        row.id to row.isProgressTarget
                    },
                    loaded = true,
                )
            } catch (_: Exception) {
                _state.update { it.copy(loaded = true, error = WeightSheetError.LOAD_FAILED) }
            }
        }
    }

    fun changeWeight(id: Long, text: String) {
        _state.update { current ->
            if (!current.canEdit || current.rows.none { it.id == id && it.isLeaf }) current
            else current.copy(drafts = current.drafts + (id to text), error = null)
        }
    }

    fun changeTarget(id: Long, included: Boolean) {
        _state.update { current ->
            if (!current.canEdit || current.rows.none { it.id == id && it.isLeaf }) current
            else current.copy(targets = current.targets + (id to included), error = null)
        }
    }

    fun save() {
        val current = _state.value
        if (!current.canSave) return
        val changes = current.rows.filter { it.isLeaf }.mapNotNull { row ->
            val weight = current.parsed(row) ?: return@mapNotNull null
            val target = current.isTarget(row)
            if (weight == row.originalWeight && target == row.isProgressTarget) null
            else row.id to TaskWeightChange(
                oldWeight = row.originalWeight,
                newWeight = weight,
                oldProgressTarget = row.isProgressTarget,
                newProgressTarget = target,
            )
        }.toMap()
        if (changes.isEmpty()) {
            _state.update { it.copy(saved = true) }
            return
        }
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                val updated = repo.updateTaskWeightsAndTargets(projectId, changes)
                _state.update {
                    it.copy(saving = false, saved = updated,
                        error = if (updated) null else WeightSheetError.CONFLICT)
                }
            } catch (_: Exception) {
                _state.update { it.copy(saving = false, error = WeightSheetError.SAVE_FAILED) }
            }
        }
    }
}
