package com.example.yamlist.data.file.yaml

import com.example.yamlist.data.local.entity.ProjectEntity
import com.example.yamlist.data.local.entity.TaskCommentEntity
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

        val existing = if (mode == YamlImportMode.UUID_UPSERT && parsed.uuid != null) {
            repo.projects.getByUuid(parsed.uuid)
        } else null

        // A uuid can still be held by a soft-deleted row under the unique index;
        // only reuse the YAML's uuid when it's free (or it's the row being updated).
        val projectUuid = when {
            existing != null -> existing.uuid
            mode == YamlImportMode.UUID_UPSERT && parsed.uuid != null &&
                !repo.projects.uuidExists(parsed.uuid) -> parsed.uuid
            else -> UUID.randomUUID().toString()
        }

        val displayOrder = repo.projects.maxDisplayOrder() + 1
        val projectEntity = ProjectEntity(
            id = existing?.id ?: 0,
            uuid = projectUuid,
            groupId = existing?.groupId,
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
            // Replace the whole task tree. The old rows are removed physically (their
            // comments cascade) so the YAML's task uuids can be inserted again.
            repo.tasks.hardDeleteAllOfProject(existing.id)
            existing.id
        } else {
            repo.projects.insert(projectEntity)
        }

        // Loaded once, after the old tree is gone, instead of a lookup per task.
        val takenTaskUuids: MutableSet<String> =
            if (mode == YamlImportMode.UUID_UPSERT) repo.tasks.allUuids().toHashSet() else HashSet()

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
                uuid = pt.uuid
                    ?.takeIf { mode == YamlImportMode.UUID_UPSERT && it !in takenTaskUuids }
                    ?: UUID.randomUUID().toString(),
                projectId = projectId,
                parentTaskId = parentId,
                title = pt.title,
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
            takenTaskUuids.add(entity.uuid)
            if (pt.comments.isNotEmpty()) {
                repo.comments.insertAll(
                    pt.comments.map { c ->
                        TaskCommentEntity(
                            uuid = UUID.randomUUID().toString(),
                            taskId = newId,
                            body = c.text,
                            createdAt = now,
                            updatedAt = now,
                            struck = c.struck,
                        )
                    }
                )
            }
            pt.children.forEachIndexed { i, child -> insertTask(child, newId, i) }
        }
        parsed.tasks.forEachIndexed { i, task -> insertTask(task, null, i) }
        return projectId
    }
}
