package com.example.yamlist.ui.project

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.yamlist.data.repository.YamlistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ArchiveViewModel @Inject constructor(
    private val repo: YamlistRepository,
) : ViewModel() {

    /** Archived projects, most recently archived/updated first. */
    val cards: StateFlow<List<ProjectCardUi>> =
        combine(repo.observeProjects(), repo.observeAllTasks()) { projects, tasks ->
            val tasksByProject = tasks.groupBy { it.projectId }
            projects
                .filter { it.isArchived }
                .sortedByDescending { it.updatedAt }
                .map { buildProjectCard(it, tasksByProject[it.id].orEmpty()) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun unarchive(id: Long) = viewModelScope.launch { repo.setProjectArchived(id, false) }
    fun delete(id: Long) = viewModelScope.launch { repo.deleteProject(id) }
}
