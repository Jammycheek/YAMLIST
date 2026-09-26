package com.example.yamlist.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.yamlist.data.local.entity.AppSettingEntity
import com.example.yamlist.data.local.entity.ProjectEntity
import com.example.yamlist.data.local.entity.ProjectGroupEntity
import com.example.yamlist.data.local.entity.TaskCommentEntity
import com.example.yamlist.data.local.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {

    @Query("SELECT * FROM projects WHERE isDeleted = 0 ORDER BY displayOrder ASC")
    fun observeAll(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id AND isDeleted = 0")
    fun observeById(id: Long): Flow<ProjectEntity?>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getById(id: Long): ProjectEntity?

    @Query("SELECT * FROM projects WHERE uuid = :uuid AND isDeleted = 0 LIMIT 1")
    suspend fun getByUuid(uuid: String): ProjectEntity?

    @Query("SELECT * FROM projects WHERE isDeleted = 0")
    suspend fun getAllOnce(): List<ProjectEntity>

    /** Includes soft-deleted rows: they still hold their uuid under the unique index. */
    @Query("SELECT EXISTS(SELECT 1 FROM projects WHERE uuid = :uuid)")
    suspend fun uuidExists(uuid: String): Boolean

    @Query("SELECT COALESCE(MAX(displayOrder), 0) FROM projects")
    suspend fun maxDisplayOrder(): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(project: ProjectEntity): Long

    @Update
    suspend fun update(project: ProjectEntity)

    @Query("UPDATE projects SET isDeleted = 1, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: Long, now: java.time.LocalDateTime)

    @Query("UPDATE projects SET isArchived = :archived, updatedAt = :now WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean, now: java.time.LocalDateTime)

    /** Live, non-archived projects in one group (null = ungrouped), in list order. */
    @Query(
        "SELECT * FROM projects WHERE groupId IS :groupId AND isDeleted = 0 AND isArchived = 0 " +
            "ORDER BY displayOrder ASC, id ASC"
    )
    suspend fun getGroupSiblings(groupId: Long?): List<ProjectEntity>

    @Query("SELECT COALESCE(MAX(displayOrder), 0) FROM projects WHERE groupId IS :groupId AND isDeleted = 0")
    suspend fun maxDisplayOrderInGroup(groupId: Long?): Long

    @Query("UPDATE projects SET displayOrder = :order, updatedAt = :now WHERE id = :id")
    suspend fun setOrder(id: Long, order: Long, now: java.time.LocalDateTime)

    @Query("UPDATE projects SET groupId = :groupId, displayOrder = :order, updatedAt = :now WHERE id = :id")
    suspend fun setGroupAndOrder(id: Long, groupId: Long?, order: Long, now: java.time.LocalDateTime)

    /** Live projects of a group, archived ones included, in list order. */
    @Query("SELECT * FROM projects WHERE groupId = :groupId AND isDeleted = 0 ORDER BY displayOrder ASC, id ASC")
    suspend fun getAllInGroup(groupId: Long): List<ProjectEntity>

    @Query("UPDATE projects SET groupId = NULL, updatedAt = :now WHERE groupId = :groupId")
    suspend fun clearGroup(groupId: Long, now: java.time.LocalDateTime)

    @Query("DELETE FROM projects")
    suspend fun deleteAllHard()
}

@Dao
interface ProjectGroupDao {

    @Query("SELECT * FROM project_groups ORDER BY displayOrder ASC, id ASC")
    fun observeAll(): Flow<List<ProjectGroupEntity>>

    @Query("SELECT * FROM project_groups ORDER BY displayOrder ASC, id ASC")
    suspend fun getAllOnce(): List<ProjectGroupEntity>

    @Query("SELECT COALESCE(MAX(displayOrder), 0) FROM project_groups")
    suspend fun maxDisplayOrder(): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(group: ProjectGroupEntity): Long

    @Query("UPDATE project_groups SET title = :title, updatedAt = :now WHERE id = :id")
    suspend fun rename(id: Long, title: String, now: java.time.LocalDateTime)

    @Query("UPDATE project_groups SET isCollapsed = :collapsed WHERE id = :id")
    suspend fun setCollapsed(id: Long, collapsed: Boolean)

    @Query("UPDATE project_groups SET displayOrder = :order, updatedAt = :now WHERE id = :id")
    suspend fun setOrder(id: Long, order: Long, now: java.time.LocalDateTime)

    @Query("DELETE FROM project_groups WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM project_groups")
    suspend fun deleteAllHard()
}

@Dao
interface TaskDao {

    @Query("SELECT * FROM tasks WHERE isDeleted = 0")
    fun observeAllLive(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE projectId = :projectId AND isDeleted = 0")
    fun observeByProject(projectId: Long): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE projectId = :projectId AND isDeleted = 0")
    suspend fun getByProject(projectId: Long): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: Long): TaskEntity?

    @Query("SELECT * FROM tasks WHERE id = :id AND isDeleted = 0")
    fun observeById(id: Long): Flow<TaskEntity?>

    @Query(
        "SELECT * FROM tasks WHERE parentTaskId = :parentId AND isDeleted = 0 " +
            "ORDER BY displayOrder ASC, id ASC"
    )
    suspend fun getChildren(parentId: Long): List<TaskEntity>

    /**
     * Same-level siblings, root tasks included via `IS` so a null [parentId]
     * matches correctly (spec §2.2: siblings are scoped per level).
     */
    @Query(
        "SELECT * FROM tasks WHERE projectId = :projectId AND parentTaskId IS :parentId " +
            "AND isDeleted = 0 ORDER BY displayOrder ASC, id ASC"
    )
    suspend fun getSiblings(projectId: Long, parentId: Long?): List<TaskEntity>

    @Query(
        "SELECT COALESCE(MAX(displayOrder), -1) FROM tasks " +
            "WHERE projectId = :projectId AND parentTaskId IS :parentId AND isDeleted = 0"
    )
    suspend fun maxChildOrder(projectId: Long, parentId: Long?): Long

    @Query(
        "SELECT * FROM tasks WHERE status = 'DONE' AND isDeleted = 0 " +
            "AND completedAt IS NOT NULL ORDER BY completedAt DESC"
    )
    fun observeCompletedHistory(): Flow<List<TaskEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(task: TaskEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(tasks: List<TaskEntity>): List<Long>

    @Update
    suspend fun update(task: TaskEntity)

    @Query("UPDATE tasks SET displayOrder = :order, updatedAt = :now WHERE id = :id")
    suspend fun setOrder(id: Long, order: Long, now: java.time.LocalDateTime)

    @Query("UPDATE tasks SET colorCode = :colorCode, updatedAt = :now WHERE id = :id")
    suspend fun setColor(id: Long, colorCode: String?, now: java.time.LocalDateTime)

    @Query("UPDATE tasks SET markType = :markType, updatedAt = :now WHERE id = :id")
    suspend fun setMark(id: Long, markType: String?, now: java.time.LocalDateTime)

    /** Used by move-to-a-different-parent and by promote-children-on-delete. */
    @Query(
        "UPDATE tasks SET parentTaskId = :parentId, displayOrder = :order, updatedAt = :now " +
            "WHERE id = :id"
    )
    suspend fun setParentAndOrder(id: Long, parentId: Long?, order: Long, now: java.time.LocalDateTime)

    /**
     * True when [candidateId] is [rootId] itself or anywhere in its subtree.
     * Used to refuse moving a task into its own descendant, which would create a
     * cycle the app's recursive-CTE deletes could no longer resolve correctly.
     */
    @Query(
        """
        WITH RECURSIVE subtree(id) AS (
            SELECT id FROM tasks WHERE id = :rootId
            UNION ALL
            SELECT t.id FROM tasks t JOIN subtree s ON t.parentTaskId = s.id
        )
        SELECT EXISTS(SELECT 1 FROM subtree WHERE id = :candidateId)
        """
    )
    suspend fun isInSubtree(rootId: Long, candidateId: Long): Boolean

    /** Deletes exactly this one row, leaving children in place (spec: promote-on-delete). */
    @Query("UPDATE tasks SET isDeleted = 1, updatedAt = :now WHERE id = :id")
    suspend fun softDeleteOne(id: Long, now: java.time.LocalDateTime)

    /**
     * Soft-deletes a task and all its descendants in one statement using a recursive CTE
     * (spec §18.2). Returns nothing; the count for the confirm dialog is fetched separately.
     */
    @Query(
        """
        WITH RECURSIVE subtree(id) AS (
            SELECT id FROM tasks WHERE id = :rootId
            UNION ALL
            SELECT t.id FROM tasks t JOIN subtree s ON t.parentTaskId = s.id
        )
        UPDATE tasks SET isDeleted = 1, updatedAt = :now WHERE id IN (SELECT id FROM subtree)
        """
    )
    suspend fun softDeleteSubtree(rootId: Long, now: java.time.LocalDateTime)

    @Query(
        """
        WITH RECURSIVE subtree(id) AS (
            SELECT id FROM tasks WHERE id = :rootId
            UNION ALL
            SELECT t.id FROM tasks t JOIN subtree s ON t.parentTaskId = s.id
        )
        SELECT COUNT(*) - 1 FROM subtree
        """
    )
    suspend fun countDescendants(rootId: Long): Int

    /** Physically removes a project's tasks (comments cascade), freeing their uuids. */
    @Query("DELETE FROM tasks WHERE projectId = :projectId")
    suspend fun hardDeleteAllOfProject(projectId: Long)

    /** Every task uuid, soft-deleted rows included: they still hold theirs under the unique index. */
    @Query("SELECT uuid FROM tasks")
    suspend fun allUuids(): List<String>

    @Query("DELETE FROM tasks")
    suspend fun deleteAllHard()
}

@Dao
interface TaskCommentDao {
    @Query("SELECT * FROM task_comments WHERE taskId = :taskId AND isDeleted = 0 ORDER BY createdAt ASC, id ASC")
    fun observeByTask(taskId: Long): Flow<List<TaskCommentEntity>>

    @Query("SELECT * FROM task_comments WHERE isDeleted = 0")
    suspend fun getAllOnce(): List<TaskCommentEntity>

    @Query(
        "SELECT c.* FROM task_comments c JOIN tasks t ON t.id = c.taskId " +
            "WHERE t.projectId = :projectId AND t.isDeleted = 0 AND c.isDeleted = 0 " +
            "ORDER BY c.createdAt ASC, c.id ASC"
    )
    suspend fun getLiveForProject(projectId: Long): List<TaskCommentEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(comment: TaskCommentEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(comments: List<TaskCommentEntity>)

    @Update
    suspend fun update(comment: TaskCommentEntity)

    @Query("UPDATE task_comments SET struck = :struck, updatedAt = :now WHERE id = :id")
    suspend fun setStruck(id: Long, struck: Boolean, now: java.time.LocalDateTime)

    @Query("UPDATE task_comments SET isDeleted = 1 WHERE id = :id")
    suspend fun softDelete(id: Long)

    /**
     * How many live comments each task has, for the tree's "has comments" mark.
     * Tasks with zero comments simply don't appear in the result.
     */
    @Query("SELECT taskId, COUNT(*) AS count FROM task_comments WHERE isDeleted = 0 GROUP BY taskId")
    fun observeCommentCounts(): Flow<List<TaskCommentCount>>

    @Query("DELETE FROM task_comments")
    suspend fun deleteAllHard()
}

/** Row shape for [TaskCommentDao.observeCommentCounts]. */
data class TaskCommentCount(val taskId: Long, val count: Int)

@Dao
interface AppSettingDao {
    @Query("SELECT * FROM app_settings")
    fun observeAll(): Flow<List<AppSettingEntity>>

    @Query("SELECT value FROM app_settings WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Query("SELECT * FROM app_settings")
    suspend fun getAllOnce(): List<AppSettingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(setting: AppSettingEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putAll(settings: List<AppSettingEntity>)

    @Query("DELETE FROM app_settings")
    suspend fun deleteAllHard()
}
