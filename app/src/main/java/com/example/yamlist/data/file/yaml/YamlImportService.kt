package com.example.yamlist.data.file.yaml

import com.example.yamlist.data.local.entity.ProjectEntity
import com.example.yamlist.data.local.entity.TaskEntity
import com.example.yamlist.data.repository.YamlistRepository
import com.example.yamlist.domain.model.MarkType
import com.example.yamlist.domain.model.TaskStatus
import com.example.yamlist.domain.progress.TaskOrdering
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject

/**
 * Applies a validated [ParsedProject] to the database (spec §15.6, §15.7).
 *
 * The whole project import runs in one transaction; any failure rolls the entire
 * project back so no half-built tree survives (spec §15.7, §26.3). v0.1 defaults to
 * [YamlImportMode.NEW]; UUID_UPSERT and DUPLICATE are provided as documented.
 */
class YamlImportService @Inject constructor(
    private val repo: YamlistRepository,
) {
    suspend fun import(parsed: ParsedProject, mode: YamlImportMode): Long =
        repo.withTransaction { insertProject(parsed, mode) }

    private suspend fun insertProject(parsed: ParsedProject, mode: YamlImportMode): Long {
        val now = LocalDateTime.now()

        val projectUuid = when (mode) {
            YamlImportMode.NEW -> UUID.randomUUID().toString()
            YamlImportMode.DUPLICATE -> UUID.randomUUID().toString()
            YamlImportMode.UUID_UPSERT -> parsed.uuid ?: UUID.randomUUID().toString()
        }

        val existing = if (mode == YamlImportMode.UUID_UPSERT && parsed.uuid != null) {
            repo.projects.getByUuid(parsed.uuid)
        } else null

        val displayOrder = repo.projects.maxDisplayOrder() + 1
        val projectEntity = ProjectEntity(
            id = existing?.id ?: 0,
            uuid = projectUuid,
            title = parsed.title,
            description = parsed.description,
            colorCode = parsed.color,
            progressMode = parsed.progressMode.name,
            defaultSortMode = "MANUAL",
            startDate = parsed.startDate,
            dueDate = parsed.dueDate,
            displayOrder = existing?.displayOrder ?: displayOrder,
            isArchived = false,
            isDeleted = false,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )

        val projectId = if (existing != null) {
            repo.projects.update(projectEntity)
            // Simplest correct upsert for v0.1: replace the whole task tree.
            repo.tasks.softDeleteSubtreeAllOfProject(existing.id, now)
            existing.id
        } else {
            repo.projects.insert(projectEntity)
        }

        // Display order is scoped per level (spec §2.9): the YAML array position
        // drives a 1000-step sequence, and an explicit `order:` key wins when present.
        suspend fun insertTask(pt: ParsedTask, parentId: Long?, indexInLevel: Int) {
            val done = pt.status == TaskStatus.DONE
            val completedAt = when {
                pt.completedAt != null -> pt.completedAt
                done -> now
                else -> null
            }
            val entity = TaskEntity(
                id = 0,
                uuid = when (mode) {
                    YamlImportMode.UUID_UPSERT -> pt.uuid ?: UUID.randomUUID().toString()
                    else -> UUID.randomUUID().toString()
                },
                projectId = projectId,
                parentTaskId = parentId,
                title = pt.title,
                description = pt.description,
                fixedComment = pt.comment,
                status = pt.status.name,
                weight = pt.weight,
                markType = pt.mark.takeIf { it != MarkType.NONE }?.name,
                colorCode = pt.color,
                plannedYearMonth = pt.plannedMonth,
                dueDate = pt.dueDate,
                completedAt = completedAt,
                displayOrder = pt.order ?: ((indexInLevel + 1) * TaskOrdering.STEP),
                isProgressTarget = pt.progressTarget,
                isDeleted = false,
                createdAt = now,
                updatedAt = now,
            )
            val newId = repo.tasks.insert(entity)
            pt.children.forEachIndexed { i, child -> insertTask(child, newId, i) }
        }
        parsed.tasks.forEachIndexed { i, task -> insertTask(task, null, i) }
        return projectId
    }
}
