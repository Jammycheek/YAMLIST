package com.example.nestprogress.data.file.yaml

import com.example.nestprogress.domain.model.MarkType
import com.example.nestprogress.domain.model.ProgressMode
import com.example.nestprogress.domain.model.TaskStatus
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
    val description: String?,
    val comment: String?,
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

/** A single validation problem. [locator] is a line ref for syntax errors or a
 *  task path ("事前準備 > 図面確認") for semantic errors. */
data class YamlError(val locator: String, val message: String) {
    override fun toString(): String = "$locator: $message"
}

sealed interface YamlParseResult {
    data class Success(val project: ParsedProject) : YamlParseResult
    data class Failure(val errors: List<YamlError>) : YamlParseResult
}

/** Import strategy (spec §15.6). */
enum class YamlImportMode { NEW, UUID_UPSERT, DUPLICATE }
