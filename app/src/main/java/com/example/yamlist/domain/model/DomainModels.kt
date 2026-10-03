package com.example.yamlist.domain.model

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Task lifecycle status (spec §9).
 * Stored as the enum name in the DB / YAML.
 */
enum class TaskStatus { TODO, DONE }

/**
 * How a project's progress is presented (spec §6.1 progressMode).
 * Calculation always produces both count and weight; this only drives display.
 */
enum class ProgressMode { COUNT, WEIGHT, BOTH }

/**
 * Free-meaning marks (spec §10). The app never enforces semantics.
 * [glyph] is a locale-independent symbol; labels live in string resources.
 */
enum class MarkType(val glyph: String) {
    IMPORTANT("★"),
    WARNING("!"),
    CONFIRM("?"),
    PRIORITY("↑"),
    HOLD("⏸"),
    WORK("🔧"),
    DOCUMENT("📄"),
    NONE("");

    companion object {
        fun fromKeyOrNone(key: String?): MarkType =
            entries.firstOrNull { it.name.equals(key, ignoreCase = true) } ?: NONE
    }
}

/**
 * Standard palette (spec §11.1). Stored as the key string in colorCode.
 */
enum class TaskColor(val key: String) {
    NONE("none"),
    RED("red"),
    ORANGE("orange"),
    YELLOW("yellow"),
    GREEN("green"),
    BLUE("blue"),
    PURPLE("purple"),
    GRAY("gray");

    companion object {
        fun fromKeyOrNull(key: String?): TaskColor? =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) }
    }
}

/** Project ordering options (spec §13.1). */
enum class ProjectSortMode {
    MANUAL, UPDATED_DESC, CREATED_DESC, DUE_ASC, PROGRESS_ASC, PROGRESS_DESC, NAME
}

/** In-level task ordering options (spec §13.2). */
enum class TaskSortMode {
    MANUAL, DUE_ASC, PLANNED_MONTH_ASC, COMPLETED_DESC, COMPLETED_ASC,
    TODO_FIRST, DONE_FIRST, WEIGHT_DESC, MARK_FIRST, NAME
}

/**
 * Domain project. Mirrors the persistence entity but carries no Android/Room types
 * so it can be unit-tested on the plain JVM.
 */
data class ProjectGroup(
    val id: Long = 0,
    val uuid: String,
    val title: String,
    val displayOrder: Long = 0,
    val isCollapsed: Boolean = false,
)

data class Project(
    val id: Long = 0,
    val uuid: String,
    val groupId: Long? = null,
    val title: String,
    val description: String? = null,
    val colorCode: String? = null,
    val progressMode: ProgressMode = ProgressMode.BOTH,
    val defaultSortMode: TaskSortMode = TaskSortMode.MANUAL,
    val startDate: LocalDate? = null,
    val dueDate: LocalDate? = null,
    val completedAt: LocalDateTime? = null,
    val displayOrder: Long = 0,
    val isArchived: Boolean = false,
    val isDeleted: Boolean = false,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)

/**
 * Domain task (spec §6.2). Flat; parent/child links are resolved into a
 * [TaskNode] forest by [com.example.yamlist.domain.progress.TaskTreeBuilder].
 */
data class Task(
    val id: Long = 0,
    val uuid: String,
    val projectId: Long,
    val parentTaskId: Long? = null,
    val title: String,
    val status: TaskStatus = TaskStatus.TODO,
    val weight: Double = 1.0,
    val markType: MarkType = MarkType.NONE,
    val colorCode: String? = null,
    val plannedYearMonth: String? = null,   // "YYYY-MM"
    val dueDate: LocalDate? = null,
    val completedAt: LocalDateTime? = null,
    val displayOrder: Long = 0,
    val isProgressTarget: Boolean = true,
    val isDeleted: Boolean = false,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
) {
    val isDone: Boolean get() = status == TaskStatus.DONE
}

/**
 * A task plus its resolved children. The unit of progress computation.
 * Leaf = [children] is empty.
 */
data class TaskNode(
    val task: Task,
    val children: List<TaskNode> = emptyList(),
) {
    val isLeaf: Boolean get() = children.isEmpty()
}

/** Derived display state for a parent task (spec §7.4). */
enum class ParentDisplayState { NOT_STARTED, IN_PROGRESS, DONE, NO_TARGET }
