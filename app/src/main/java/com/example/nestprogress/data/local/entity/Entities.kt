package com.example.nestprogress.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalDateTime

@Entity(
    tableName = "projects",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["isDeleted", "isArchived"]),
    ],
)
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    val title: String,
    val description: String? = null,
    val colorCode: String? = null,
    val progressMode: String = "BOTH",
    val defaultSortMode: String = "MANUAL",
    val startDate: LocalDate? = null,
    val dueDate: LocalDate? = null,
    val completedAt: LocalDateTime? = null,
    val displayOrder: Long = 0,
    val isArchived: Boolean = false,
    val isDeleted: Boolean = false,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)

@Entity(
    tableName = "tasks",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentTaskId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["uuid"], unique = true),
        Index(value = ["projectId", "parentTaskId", "isDeleted"]),
        Index(value = ["parentTaskId"]),
    ],
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    val projectId: Long,
    val parentTaskId: Long? = null,
    val title: String,
    val description: String? = null,
    val fixedComment: String? = null,
    val status: String = "TODO",
    val weight: Double = 1.0,
    val markType: String? = null,
    val colorCode: String? = null,
    val plannedYearMonth: String? = null,
    val dueDate: LocalDate? = null,
    val completedAt: LocalDateTime? = null,
    val displayOrder: Long = 0,
    val isProgressTarget: Boolean = true,
    val isDeleted: Boolean = false,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)

@Entity(
    tableName = "task_comments",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["taskId", "isDeleted"]), Index(value = ["uuid"], unique = true)],
)
data class TaskCommentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String,
    val taskId: Long,
    val body: String,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    /** Strikethrough toggle (v0.4): marks a comment as no longer relevant without deleting it. */
    val struck: Boolean = false,
    val isDeleted: Boolean = false,
)

@Entity(tableName = "app_settings")
data class AppSettingEntity(
    @PrimaryKey @ColumnInfo(name = "key") val key: String,
    val value: String,
)
