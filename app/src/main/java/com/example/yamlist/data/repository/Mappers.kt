package com.example.yamlist.data.repository

import com.example.yamlist.data.local.entity.ProjectEntity
import com.example.yamlist.data.local.entity.ProjectGroupEntity
import com.example.yamlist.data.local.entity.TaskEntity
import com.example.yamlist.domain.model.MarkType
import com.example.yamlist.domain.model.Project
import com.example.yamlist.domain.model.ProjectGroup
import com.example.yamlist.domain.model.ProgressMode
import com.example.yamlist.domain.model.Task
import com.example.yamlist.domain.model.TaskSortMode
import com.example.yamlist.domain.model.TaskStatus

fun ProjectEntity.toDomain(): Project = Project(
    id = id,
    uuid = uuid,
    groupId = groupId,
    title = title,
    description = description,
    colorCode = colorCode,
    progressMode = enumValueOrDefault(progressMode, ProgressMode.BOTH),
    defaultSortMode = enumValueOrDefault(defaultSortMode, TaskSortMode.MANUAL),
    startDate = startDate,
    dueDate = dueDate,
    completedAt = completedAt,
    displayOrder = displayOrder,
    isArchived = isArchived,
    isDeleted = isDeleted,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun Project.toEntity(): ProjectEntity = ProjectEntity(
    id = id,
    uuid = uuid,
    groupId = groupId,
    title = title,
    description = description,
    colorCode = colorCode,
    progressMode = progressMode.name,
    defaultSortMode = defaultSortMode.name,
    startDate = startDate,
    dueDate = dueDate,
    completedAt = completedAt,
    displayOrder = displayOrder,
    isArchived = isArchived,
    isDeleted = isDeleted,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun TaskEntity.toDomain(): Task = Task(
    id = id,
    uuid = uuid,
    projectId = projectId,
    parentTaskId = parentTaskId,
    title = title,
    status = enumValueOrDefault(status, TaskStatus.TODO),
    weight = weight,
    markType = MarkType.fromKeyOrNone(markType),
    colorCode = colorCode,
    plannedYearMonth = plannedYearMonth,
    dueDate = dueDate,
    completedAt = completedAt,
    displayOrder = displayOrder,
    isProgressTarget = isProgressTarget,
    isDeleted = isDeleted,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun Task.toEntity(): TaskEntity = TaskEntity(
    id = id,
    uuid = uuid,
    projectId = projectId,
    parentTaskId = parentTaskId,
    title = title,
    status = status.name,
    weight = weight,
    markType = markType.takeIf { it != MarkType.NONE }?.name,
    colorCode = colorCode,
    plannedYearMonth = plannedYearMonth,
    dueDate = dueDate,
    completedAt = completedAt,
    displayOrder = displayOrder,
    isProgressTarget = isProgressTarget,
    isDeleted = isDeleted,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun ProjectGroupEntity.toDomain(): ProjectGroup = ProjectGroup(
    id = id,
    uuid = uuid,
    title = title,
    displayOrder = displayOrder,
    isCollapsed = isCollapsed,
)

private inline fun <reified T : Enum<T>> enumValueOrDefault(name: String?, default: T): T =
    enumValues<T>().firstOrNull { it.name.equals(name, ignoreCase = true) } ?: default
