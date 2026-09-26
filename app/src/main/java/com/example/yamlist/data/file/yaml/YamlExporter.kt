package com.example.yamlist.data.file.yaml

import com.example.yamlist.domain.model.MarkType
import com.example.yamlist.domain.model.Project
import com.example.yamlist.domain.model.ProgressMode
import com.example.yamlist.domain.model.TaskColor
import com.example.yamlist.domain.model.TaskNode
import com.example.yamlist.domain.model.TaskStatus
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import java.time.format.DateTimeFormatter

/**
 * Serialises a project + its task forest to YAML (spec §14.6, §26.3).
 *
 * Uses an insertion-ordered map graph dumped by SnakeYAML in block style with
 * unicode allowed, so Japanese text is emitted verbatim and the output re-imports to
 * an equivalent structure. Default-valued fields are omitted to keep files compact.
 */
object YamlExporter {

    private val DATE = DateTimeFormatter.ISO_LOCAL_DATE
    private val DATETIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    @JvmOverloads
    fun export(
        project: Project,
        forest: List<TaskNode>,
        includeOrder: Boolean = false,
    ): String {
        val root = linkedMapOf<String, Any?>(
            "schema_version" to YamlImporter.SUPPORTED_SCHEMA_VERSION,
        )
        val projectMap = linkedMapOf<String, Any?>()
        project.uuid.takeIf { it.isNotBlank() }?.let { projectMap["uuid"] = it }
        projectMap["title"] = project.title
        project.description?.takeIf { it.isNotBlank() }?.let { projectMap["description"] = it }
        projectMap["progress_mode"] = when (project.progressMode) {
            ProgressMode.COUNT -> "count"
            ProgressMode.WEIGHT -> "weight"
            ProgressMode.BOTH -> "both"
        }
        project.colorCode?.let { projectMap["color"] = it }
        project.startDate?.let { projectMap["start_date"] = it.format(DATE) }
        project.dueDate?.let { projectMap["due_date"] = it.format(DATE) }
        if (forest.isNotEmpty()) {
            projectMap["tasks"] = forest.sortedByOrder().map { taskMap(it, includeOrder) }
        }

        root["project"] = projectMap
        return dump(root)
    }

    /** Levels are emitted in displayOrder order so the file mirrors the screen. */
    private fun List<TaskNode>.sortedByOrder(): List<TaskNode> =
        sortedWith(compareBy({ it.task.displayOrder }, { it.task.id }))

    private fun taskMap(node: TaskNode, includeOrder: Boolean): Map<String, Any?> {
        val t = node.task
        val map = linkedMapOf<String, Any?>()
        t.uuid.takeIf { it.isNotBlank() }?.let { map["uuid"] = it }
        map["title"] = t.title
        if (includeOrder) map["order"] = t.displayOrder
        t.description?.takeIf { it.isNotBlank() }?.let { map["description"] = it }
        t.fixedComment?.takeIf { it.isNotBlank() }?.let { map["comment"] = it }
        if (t.status != TaskStatus.TODO) map["status"] = t.status.name.lowercase()
        if (t.weight != 1.0) map["weight"] = t.weight
        if (t.markType != MarkType.NONE) map["mark"] = t.markType.name.lowercase()
        t.colorCode?.let { c ->
            if (TaskColor.fromKeyOrNull(c) != TaskColor.NONE) map["color"] = c
        }
        t.plannedYearMonth?.let { map["planned_month"] = it }
        t.dueDate?.let { map["due_date"] = it.format(DATE) }
        t.completedAt?.let { map["completed_at"] = it.format(DATETIME) }
        if (!t.isProgressTarget) map["progress_target"] = false
        if (node.children.isNotEmpty()) {
            map["tasks"] = node.children.sortedByOrder().map { taskMap(it, includeOrder) }
        }
        return map
    }

    private fun dump(graph: Map<String, Any?>): String {
        val options = DumperOptions().apply {
            defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
            isPrettyFlow = true
            indent = 2
            isAllowUnicode = true
            splitLines = false
        }
        return Yaml(options).dump(graph)
    }
}
