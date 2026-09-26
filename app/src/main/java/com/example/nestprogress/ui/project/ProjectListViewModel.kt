package com.example.nestprogress.ui.project

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nestprogress.data.repository.NestRepository
import com.example.nestprogress.domain.model.MarkType
import com.example.nestprogress.domain.model.Project
import com.example.nestprogress.domain.model.Task
import com.example.nestprogress.domain.progress.ProgressCalculator
import com.example.nestprogress.domain.progress.ProgressResult
import com.example.nestprogress.domain.progress.TaskTreeBuilder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProjectCardUi(
    val project: Project,
    val progress: ProgressResult,
    val importantCount: Int,
    val confirmCount: Int,
)

@HiltViewModel
class ProjectListViewModel @Inject constructor(
    private val repo: NestRepository,
) : ViewModel() {

    val cards: StateFlow<List<ProjectCardUi>> =
        combine(repo.observeProjects(), repo.observeAllTasks()) { projects, tasks ->
            val tasksByProject = tasks.groupBy { it.projectId }
            projects
                .filterNot { it.isArchived }
                .map { project -> buildCard(project, tasksByProject[project.id] ?: emptyList()) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun buildCard(project: Project, tasks: List<Task>): ProjectCardUi {
        val forest = TaskTreeBuilder.build(tasks)
        val progress = ProgressCalculator.forForest(forest)
        val important = tasks.count { it.markType == MarkType.IMPORTANT && it.status.name != "DONE" }
        val confirm = tasks.count { it.markType == MarkType.CONFIRM && it.status.name != "DONE" }
        return ProjectCardUi(project, progress, important, confirm)
    }

    fun archiveProject(id: Long) = viewModelScope.launch { repo.setProjectArchived(id, true) }
    fun deleteProject(id: Long) = viewModelScope.launch { repo.deleteProject(id) }
}
