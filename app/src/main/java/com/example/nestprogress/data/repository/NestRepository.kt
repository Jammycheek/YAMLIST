package com.example.nestprogress.data.repository

import com.example.nestprogress.data.local.NestDatabase
import com.example.nestprogress.data.local.withTransactionCompat
import com.example.nestprogress.data.local.entity.ProjectEntity
import com.example.nestprogress.data.local.entity.TaskCommentEntity
import com.example.nestprogress.data.local.entity.TaskEntity
import com.example.nestprogress.domain.model.Project
import com.example.nestprogress.domain.model.Task
import com.example.nestprogress.domain.model.TaskStatus
import com.example.nestprogress.domain.progress.SiblingReorder
import com.example.nestprogress.domain.progress.TaskOrdering
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single point of DB access for the ViewModels. Keeps domain models on its surface
 * and entity mapping internal. Multi-statement operations that must be atomic
 * (subtree delete, YAML import, restore) go through [NestDatabase.runInTransaction]
 * via [withTransaction].
 */
@Singleton
class NestRepository @Inject constructor(
    private val db: NestDatabase,
) {
    private val projectDao = db.projectDao()
    private val taskDao = db.taskDao()
    private val commentDao = db.taskCommentDao()
    val settingDao = db.appSettingDao()

    private fun now() = LocalDateTime.now()
    private fun newUuid() = UUID.randomUUID().toString()

    // ---- Projects ------------------------------------------------------------

    fun observeProjects(): Flow<List<Project>> =
        projectDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeProject(id: Long): Flow<Project?> =
        projectDao.observeById(id).map { it?.toDomain() }

    suspend fun getProjectOnce(id: Long): Project? = projectDao.getById(id)?.toDomain()

    suspend fun createProject(project: Project): Long {
        val order = projectDao.maxDisplayOrder() + 1
        val entity = project.copy(
            uuid = project.uuid.ifBlank { newUuid() },
            displayOrder = order,
            createdAt = now(),
            updatedAt = now(),
        ).toEntity()
        return projectDao.insert(entity)
    }

    suspend fun updateProject(project: Project) {
        projectDao.update(project.copy(updatedAt = now()).toEntity())
    }

    suspend fun deleteProject(id: Long) = projectDao.softDelete(id, now())

    suspend fun setProjectArchived(id: Long, archived: Boolean) =
        projectDao.setArchived(id, archived, now())

    // ---- Tasks ---------------------------------------------------------------

    fun observeAllTasks(): Flow<List<Task>> =
        taskDao.observeAllLive().map { list -> list.map { it.toDomain() } }

    fun observeTasks(projectId: Long): Flow<List<Task>> =
        taskDao.observeByProject(projectId).map { list -> list.map { it.toDomain() } }

    fun observeTask(id: Long): Flow<Task?> =
        taskDao.observeById(id).map { it?.toDomain() }

    suspend fun getTask(id: Long): Task? = taskDao.getById(id)?.toDomain()

    suspend fun addTask(task: Task): Long {
        val order = TaskOrdering.nextOrder(taskDao.maxChildOrder(task.projectId, task.parentTaskId))
        val entity = task.copy(
            uuid = task.uuid.ifBlank { newUuid() },
            displayOrder = order,
            createdAt = now(),
            updatedAt = now(),
        ).toEntity()
        return taskDao.insert(entity)
    }

    /**
     * Creates several children of [parentId] in one shot (spec §1.6, §1.9).
     *
     * Every task takes the same [template] attributes and differs only in title.
     * Orders continue after the existing children of that parent, spaced by
     * [TaskOrdering.STEP]. The whole batch is one transaction: if any row fails,
     * nothing is left behind (spec §1.9).
     */
    suspend fun addChildrenBulk(
        parentId: Long,
        titles: List<String>,
        template: Task,
    ): List<Long> = withTransaction {
        val parent = taskDao.getById(parentId)
            ?: throw IllegalArgumentException("親タスクが見つかりません。")
        if (parent.isDeleted) throw IllegalStateException("親タスクは削除済みです。")
        if (parent.projectId != template.projectId) {
            throw IllegalArgumentException("親タスクが別のプロジェクトに属しています。")
        }

        val timestamp = now()
        val orders = TaskOrdering.appendOrders(
            currentMax = taskDao.maxChildOrder(parent.projectId, parentId),
            count = titles.size,
        )
        val entities = titles.mapIndexed { index, title ->
            template.copy(
                id = 0,
                uuid = newUuid(),
                projectId = parent.projectId,
                parentTaskId = parentId,
                title = title,
                displayOrder = orders[index],
                createdAt = timestamp,
                updatedAt = timestamp,
            ).toEntity()
        }
        taskDao.insertAll(entities)
    }

    /**
     * Applies a manual reorder within one level (spec §2.8): the ids are taken in
     * their new visual order and re-spaced to 1000, 2000, 3000... Parent changes
     * are not performed here; those go through the task edit screen.
     */
    suspend fun reorderSiblings(orderedIds: List<Long>) = withTransaction {
        val timestamp = now()
        TaskOrdering.renumber(orderedIds).forEach { (id, order) ->
            taskDao.setOrder(id, order, timestamp)
        }
    }

    suspend fun updateTask(task: Task) {
        taskDao.update(task.copy(updatedAt = now()).toEntity())
    }

    /**
     * Toggles a leaf task between TODO and DONE (spec §9.2/§9.3),
     * stamping/clearing completedAt accordingly.
     */
    suspend fun toggleDone(taskId: Long) {
        val e = taskDao.getById(taskId) ?: return
        val nowTs = now()
        val updated = if (e.status == "DONE") {
            e.copy(status = "TODO", completedAt = null, updatedAt = nowTs)
        } else {
            e.copy(status = "DONE", completedAt = nowTs, updatedAt = nowTs)
        }
        taskDao.update(updated)
    }

    suspend fun setStatus(taskId: Long, status: TaskStatus) {
        val e = taskDao.getById(taskId) ?: return
        val nowTs = now()
        val completedAt = if (status == TaskStatus.DONE) nowTs else null
        taskDao.update(e.copy(status = status.name, completedAt = completedAt, updatedAt = nowTs))
    }

    /** Bulk status for a parent's whole subtree (spec §7.4 long-press actions). */
    suspend fun setSubtreeStatus(projectId: Long, rootId: Long, status: TaskStatus) {
        db.withTransactionCompat {
            val all = taskDao.getByProject(projectId)
            val byParent = all.groupBy { it.parentTaskId }
            val targets = ArrayList<TaskEntity>()
            fun collect(id: Long) {
                byParent[id]?.forEach { child ->
                    if (child.parentTaskId == id && (byParent[child.id] == null)) {
                        targets.add(child) // leaf
                    }
                    collect(child.id)
                }
            }
            val root = all.firstOrNull { it.id == rootId } ?: return@withTransactionCompat
            if (byParent[root.id] == null) targets.add(root) else collect(root.id)
            val nowTs = now()
            targets.forEach { e ->
                val completedAt = if (status == TaskStatus.DONE) (e.completedAt ?: nowTs) else null
                taskDao.update(
                    e.copy(status = status.name, completedAt = completedAt, updatedAt = nowTs)
                )
            }
        }
    }

    suspend fun countDescendants(taskId: Long): Int = taskDao.countDescendants(taskId)

    suspend fun deleteTaskSubtree(taskId: Long) = taskDao.softDeleteSubtree(taskId, now())

    /**
     * Deletes only [taskId] and re-parents its direct children onto its own
     * parent, so they move up one level instead of disappearing with it.
     * Children keep their relative order and are appended after any tasks
     * already at that level (spec addendum: promote-on-delete).
     */
    suspend fun deleteTaskPromotingChildren(taskId: Long) = withTransaction {
        val task = taskDao.getById(taskId) ?: return@withTransaction
        val children = taskDao.getSiblings(task.projectId, taskId)
        val timestamp = now()
        // Delete first so this task no longer counts toward its own former
        // level's max order — otherwise the promoted children would always
        // land one step further out than necessary.
        taskDao.softDeleteOne(taskId, timestamp)
        if (children.isNotEmpty()) {
            val base = taskDao.maxChildOrder(task.projectId, task.parentTaskId)
            val orders = TaskOrdering.appendOrders(base, children.size)
            children.forEachIndexed { index, child ->
                taskDao.setParentAndOrder(child.id, task.parentTaskId, orders[index], timestamp)
            }
        }
    }

    /**
     * Moves [taskId] under [newParentId] (null = project root), appended at the
     * end of that level. Refuses to move a task into itself or its own subtree,
     * which would otherwise create a cycle.
     */
    suspend fun moveTaskToParent(taskId: Long, newParentId: Long?): Boolean = withTransaction {
        val task = taskDao.getById(taskId) ?: return@withTransaction false
        if (newParentId != null) {
            if (newParentId == taskId) return@withTransaction false
            if (taskDao.isInSubtree(taskId, newParentId)) return@withTransaction false
        }
        val order = TaskOrdering.nextOrder(taskDao.maxChildOrder(task.projectId, newParentId))
        taskDao.setParentAndOrder(taskId, newParentId, order, now())
        true
    }

    /**
     * Moves [taskId] one step up or down among its same-level siblings
     * ([SiblingReorder.UP] / [SiblingReorder.DOWN]) by swapping displayOrder
     * with the neighbour. A no-op at either end of the level.
     */
    suspend fun moveSibling(taskId: Long, direction: Int) = withTransaction {
        val task = taskDao.getById(taskId) ?: return@withTransaction
        val siblings = taskDao.getSiblings(task.projectId, task.parentTaskId)
        val swap = SiblingReorder.swapIndices(siblings.map { it.id }, taskId, direction)
            ?: return@withTransaction
        val (fromIdx, toIdx) = swap
        val a = siblings[fromIdx]
        val b = siblings[toIdx]
        val timestamp = now()
        taskDao.setOrder(a.id, b.displayOrder, timestamp)
        taskDao.setOrder(b.id, a.displayOrder, timestamp)
    }

    suspend fun moveTask(taskId: Long, newOrder: Long) = taskDao.setOrder(taskId, newOrder, now())

    // ---- Comments ------------------------------------------------------------

    fun observeComments(taskId: Long): Flow<List<TaskCommentEntity>> =
        commentDao.observeByTask(taskId)

    suspend fun addComment(taskId: Long, body: String) {
        commentDao.insert(
            TaskCommentEntity(
                uuid = newUuid(), taskId = taskId, body = body,
                createdAt = now(), updatedAt = now(),
            )
        )
    }

    suspend fun setCommentStruck(commentId: Long, struck: Boolean) =
        commentDao.setStruck(commentId, struck, now())

    suspend fun deleteComment(commentId: Long) = commentDao.softDelete(commentId)

    /** Task id -> live comment count, for the tree's "has comments" mark. */
    fun observeCommentCounts(): Flow<Map<Long, Int>> =
        commentDao.observeCommentCounts().map { rows -> rows.associate { it.taskId to it.count } }

    // ---- Completed history (spec §14.8) --------------------------------------

    fun observeCompletedHistory(): Flow<List<Task>> =
        taskDao.observeCompletedHistory().map { list -> list.map { it.toDomain() } }

    // ---- Transaction helper --------------------------------------------------

    suspend fun <R> withTransaction(block: suspend () -> R): R = db.withTransactionCompat(block)

    // Direct entity access for import/backup which manage their own IDs/UUIDs.
    val projects get() = projectDao
    val tasks get() = taskDao
    val comments get() = commentDao
}
