package com.example.yamlist.ui.project

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.yamlist.data.repository.YamlistRepository
import com.example.yamlist.domain.model.MarkType
import com.example.yamlist.domain.model.Project
import com.example.yamlist.domain.model.ProjectGroup
import com.example.yamlist.domain.model.Task
import com.example.yamlist.domain.model.TaskStatus
import com.example.yamlist.domain.progress.ProgressCalculator
import com.example.yamlist.domain.progress.ProgressResult
import com.example.yamlist.domain.progress.SiblingReorder
import com.example.yamlist.domain.progress.TaskTreeBuilder
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

data class GroupSectionUi(
    val group: ProjectGroup,
    val cards: List<ProjectCardUi>,
)

data class ProjectListUi(
    /** Projects outside any group, shown first without a header. */
    val ungrouped: List<ProjectCardUi> = emptyList(),
    val groups: List<GroupSectionUi> = emptyList(),
)

fun buildProjectCard(project: Project, tasks: List<Task>): ProjectCardUi {
    val forest = TaskTreeBuilder.build(tasks)
    val progress = ProgressCalculator.forForest(forest)
    val important = tasks.count { it.markType == MarkType.IMPORTANT && it.status != TaskStatus.DONE }
    val confirm = tasks.count { it.markType == MarkType.CONFIRM && it.status != TaskStatus.DONE }
    return ProjectCardUi(project, progress, important, confirm)
}

@HiltViewModel
class ProjectListViewModel @Inject constructor(
    private val repo: YamlistRepository,
) : ViewModel() {

    val ui: StateFlow<ProjectListUi> =
        combine(
            repo.observeProjects(),
            repo.observeAllTasks(),
            repo.observeProjectGroups(),
        ) { projects, tasks, groups ->
            val tasksByProject = tasks.groupBy { it.projectId }
            val cards = projects
                .filterNot { it.isArchived }
                .sortedWith(compareBy({ it.displayOrder }, { it.id }))
                .map { buildProjectCard(it, tasksByProject[it.id].orEmpty()) }
            val groupIds = groups.map { it.id }.toSet()
            val byGroup = cards.groupBy { card -> card.project.groupId?.takeIf { it in groupIds } }
            ProjectListUi(
                ungrouped = byGroup[null].orEmpty(),
                groups = groups.map { GroupSectionUi(it, byGroup[it.id].orEmpty()) },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProjectListUi())

    fun archiveProject(id: Long) = viewModelScope.launch { repo.setProjectArchived(id, true) }
    fun deleteProject(id: Long) = viewModelScope.launch { repo.deleteProject(id) }

    fun moveProjectUp(id: Long) = viewModelScope.launch { repo.moveProject(id, SiblingReorder.UP) }
    fun moveProjectDown(id: Long) = viewModelScope.launch { repo.moveProject(id, SiblingReorder.DOWN) }

    fun moveProjectToGroup(projectId: Long, groupId: Long?) =
        viewModelScope.launch { repo.moveProjectToGroup(projectId, groupId) }

    /** Creates a group and, when [projectId] is given, moves that project into it. */
    fun createGroup(title: String, projectId: Long? = null) = viewModelScope.launch {
        val name = title.trim().takeIf { it.isNotEmpty() } ?: return@launch
        val groupId = repo.createProjectGroup(name)
        if (projectId != null) repo.moveProjectToGroup(projectId, groupId)
    }

    fun renameGroup(id: Long, title: String) = viewModelScope.launch {
        val name = title.trim().takeIf { it.isNotEmpty() } ?: return@launch
        repo.renameProjectGroup(id, name)
    }

    fun toggleGroupCollapsed(group: ProjectGroup) =
        viewModelScope.launch { repo.setProjectGroupCollapsed(group.id, !group.isCollapsed) }

    fun moveGroupUp(id: Long) = viewModelScope.launch { repo.moveProjectGroup(id, SiblingReorder.UP) }
    fun moveGroupDown(id: Long) = viewModelScope.launch { repo.moveProjectGroup(id, SiblingReorder.DOWN) }

    fun deleteGroup(id: Long) = viewModelScope.launch { repo.deleteProjectGroup(id) }
}
