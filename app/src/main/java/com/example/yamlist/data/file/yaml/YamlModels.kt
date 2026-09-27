package com.example.yamlist.data.file.yaml

import com.example.yamlist.domain.model.MarkType
import com.example.yamlist.domain.model.ProgressMode
import com.example.yamlist.domain.model.TaskStatus
import java.time.LocalDate
import java.time.LocalDateTime

/** Parsed, validated project ready to be persisted (spec §15). */
data class ParsedProject(
    val uuid: String?,
    val title: String,
    val description: String?,
    val progressMode: ProgressMode,
    val color: String?,
    val startDate: LocalDate?,
    val dueDate: LocalDate?,
    val tasks: List<ParsedTask>,
) {
    fun taskCount(): Int = tasks.sumOf { it.subtreeCount() }
    fun maxDepth(): Int = (tasks.maxOfOrNull { it.depth() } ?: 0)
}

data class ParsedTask(
    val uuid: String?,
    val title: String,
    /** Imported as task comments. Legacy `description` / `comment` keys land here too. */
    val comments: List<YamlComment> = emptyList(),
    val status: TaskStatus,
    val weight: Double,
    val mark: MarkType,
    val color: String?,
    val plannedMonth: String?,
    val dueDate: LocalDate?,
    val completedAt: LocalDateTime?,
    val progressTarget: Boolean,
    /** Explicit display order from an `order:` key (spec §2.9); null = derive from array position. */
    val order: Long? = null,
    val children: List<ParsedTask>,
) {
    fun subtreeCount(): Int = 1 + children.sumOf { it.subtreeCount() }
    fun depth(): Int = 1 + (children.maxOfOrNull { it.depth() } ?: 0)
}

/**
 * A task comment in YAML: a plain string, or `{text: ..., struck: true}` for a
 * struck-through one so export -> import keeps it.
 */
data class YamlComment(val text: String, val struck: Boolean = false)

/**
 * A single validation problem, kept language-neutral so the screen can word it in
 * the app's language: [kind] picks the message and [args] fill it in. [locator] is
 * a field or task path ("事前準備 > 図面確認"); [line] is set for syntax errors.
 */
data class YamlError(
    val locator: String,
    val kind: YamlErrorKind,
    val args: List<String> = emptyList(),
    val line: Int? = null,
) {
    override fun toString(): String = "${line?.let { "line $it" } ?: locator}: $kind $args"
}

enum class YamlErrorKind {
    FILE_TOO_LARGE,             // args: max MB
    SYNTAX,                     // args: parser problem
    PARSE_FAILED,               // args: detail
    ROOT_NOT_MAPPING,
    SCHEMA_VERSION_MISSING,
    SCHEMA_VERSION_UNSUPPORTED, // args: found, supported
    PROJECT_MISSING,
    PROJECT_TITLE_MISSING,
    DUPLICATE_UUID,             // args: uuid
    TOO_MANY_TASKS,             // args: max, actual
    TOO_DEEP,                   // args: max, actual
    TASK_NOT_MAPPING,
    TASK_TITLE_MISSING,
    INVALID_PROGRESS_TARGET,    // args: value
    INVALID_COMMENT,
    ORDER_NOT_INTEGER,          // args: value
    ORDER_NEGATIVE,             // args: value
    INVALID_PROGRESS_MODE,      // args: value
    INVALID_STATUS,             // args: value
    WEIGHT_NOT_NUMBER,          // args: value
    WEIGHT_OUT_OF_RANGE,        // args: value
    INVALID_PLANNED_MONTH,      // args: value
    INVALID_DATE,               // args: value
    INVALID_DATETIME,           // args: value
}

sealed interface YamlParseResult {
    data class Success(val project: ParsedProject) : YamlParseResult
    data class Failure(val errors: List<YamlError>) : YamlParseResult
}

/** Import strategy (spec §15.6). */
enum class YamlImportMode { NEW, UUID_UPSERT, DUPLICATE }
