package com.example.yamlist.ui.project

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.yamlist.data.repository.YamlistRepository
import com.example.yamlist.domain.model.Project
import com.example.yamlist.domain.model.ProgressMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject

data class ProjectEditState(
    val id: Long = -1,
    val title: String = "",
    val description: String = "",
    val colorCode: String? = null,
    val progressMode: ProgressMode = ProgressMode.BOTH,
    val startDate: LocalDate? = null,
    val dueDate: LocalDate? = null,
    val loaded: Boolean = false,
) {
    val isNew: Boolean get() = id <= 0L
    val titleError: Boolean get() = title.isBlank()
}

@HiltViewModel
class ProjectEditViewModel @Inject constructor(
    private val repo: YamlistRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val projectId: Long = savedStateHandle.get<Long>("projectId") ?: -1L

    private val _state = MutableStateFlow(ProjectEditState())
    val state = _state.asStateFlow()

    init {
        if (projectId > 0) {
            viewModelScope.launch {
                repo.getProjectOnce(projectId)?.let { p ->
                    _state.value = ProjectEditState(
                        id = p.id, title = p.title, description = p.description.orEmpty(),
                        colorCode = p.colorCode, progressMode = p.progressMode,
                        startDate = p.startDate, dueDate = p.dueDate, loaded = true,
                    )
                }
            }
        } else _state.update { it.copy(loaded = true) }
    }

    fun onTitle(v: String) = _state.update { it.copy(title = v) }
    fun onDescription(v: String) = _state.update { it.copy(description = v) }
    fun onColor(v: String?) = _state.update { it.copy(colorCode = v) }
    fun onProgressMode(v: ProgressMode) = _state.update { it.copy(progressMode = v) }
    fun onStartDate(v: LocalDate?) = _state.update { it.copy(startDate = v) }
    fun onDueDate(v: LocalDate?) = _state.update { it.copy(dueDate = v) }

    /** Returns true when saved; false when validation blocks it. */
    fun save(onSaved: () -> Unit) {
        val s = _state.value
        if (s.titleError) return
        viewModelScope.launch {
            val now = LocalDateTime.now()
            if (s.isNew) {
                repo.createProject(
                    Project(
                        uuid = UUID.randomUUID().toString(),
                        title = s.title.trim(),
                        description = s.description.ifBlank { null },
                        colorCode = s.colorCode,
                        progressMode = s.progressMode,
                        startDate = s.startDate,
                        dueDate = s.dueDate,
                        createdAt = now, updatedAt = now,
                    )
                )
            } else {
                val existing = repo.getProjectOnce(s.id) ?: return@launch
                repo.updateProject(
                    existing.copy(
                        title = s.title.trim(),
                        description = s.description.ifBlank { null },
                        colorCode = s.colorCode,
                        progressMode = s.progressMode,
                        startDate = s.startDate,
                        dueDate = s.dueDate,
                    )
                )
            }
            onSaved()
        }
    }
}
