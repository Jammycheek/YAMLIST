package com.example.nestprogress.data.file.yaml

/**
 * Normalizes a "loose" structural YAML document into the strict import schema
 * that [YamlImporter.mapAndValidate] expects.
 *
 * Motivation: writing a site's structure by hand is far more natural as a plain
 * nested outline than as the strict `tasks: [{title: ...}]` form. For example:
 *
 * ```yaml
 * 案件名: 昭和田中生命ビル新築
 * 種別: 工事
 * 階層:
 *   6階:
 *     東:
 *       自動制御盤:
 *         - CP-06-01
 *       空調機:
 *         AHU-06-01:
 *           VAV:
 *             - VAV131
 *         AHU-06-02:
 * ```
 *
 * Conversion rules (mirrors the reference Python converter):
 *  - A map key becomes a task title; its value is recursed into as the children.
 *  - A list element that is a scalar becomes a leaf task titled with that scalar.
 *  - A list element that is a map is expanded key-by-key at the same level.
 *  - A null value means "no children", i.e. the key alone is a leaf task.
 *
 * Only the structure (hierarchy + titles) is produced. Weight, status, marks,
 * dates and so on are intentionally left at their defaults, to be filled in
 * later through the app's own editing screens.
 *
 * Documents that already use the strict schema pass through untouched, so both
 * styles work with the same import flow.
 */
object StructureYamlConverter {

    /** Keys accepted for the project title in a loose document. */
    private val TITLE_KEYS = listOf("案件名", "現場名", "物件名", "プロジェクト名", "title", "project_name")

    /** Keys accepted for a free-text kind/category, folded into the description. */
    private val KIND_KEYS = listOf("種別", "区分", "kind", "type")

    /** Keys accepted for a description in a loose document. */
    private val DESCRIPTION_KEYS = listOf("説明", "備考", "description", "note")

    /** Keys whose value holds the structure tree. */
    private val TREE_KEYS = listOf("階層", "構成", "構造", "tasks", "hierarchy", "structure", "items")

    /** Guard against pathological nesting while recursing. */
    private const val MAX_CONVERT_DEPTH = 100

    /**
     * Returns the strict-schema object graph. If [root] already looks strict it is
     * returned unchanged; otherwise it is converted.
     */
    fun normalize(root: Any?): Any? {
        if (root !is Map<*, *>) return root
        if (looksStrict(root)) return root
        return convert(root)
    }

    /**
     * A document is treated as already-strict when it declares `schema_version`,
     * or when it has a `project` mapping carrying a `title`. Both are things a
     * loose structural outline would not normally contain.
     */
    fun looksStrict(root: Map<*, *>): Boolean {
        if (root.containsKey("schema_version")) return true
        val project = root["project"] as? Map<*, *> ?: return false
        return project.containsKey("title") || project.containsKey("tasks")
    }

    private fun convert(root: Map<*, *>): Map<String, Any?> {
        val title = firstStringOf(root, TITLE_KEYS) ?: "無題プロジェクト"
        val kind = firstStringOf(root, KIND_KEYS)
        val explicitDescription = firstStringOf(root, DESCRIPTION_KEYS)

        val description = when {
            explicitDescription != null && kind != null -> "$explicitDescription（種別: $kind）"
            explicitDescription != null -> explicitDescription
            kind != null -> "種別: $kind"
            else -> null
        }

        // Prefer an explicit tree key; otherwise treat every remaining key as structure.
        val treeNode: Any? = TREE_KEYS.firstNotNullOfOrNull { key ->
            if (root.containsKey(key)) root[key] else null
        } ?: root.filterKeys { k ->
            val name = k?.toString()
            name != null &&
                name !in TITLE_KEYS && name !in KIND_KEYS &&
                name !in DESCRIPTION_KEYS && name !in TREE_KEYS
        }

        val project = LinkedHashMap<String, Any?>()
        project["title"] = title
        if (description != null) project["description"] = description
        project["progress_mode"] = "count"
        project["tasks"] = convertNode(treeNode, depth = 0)

        return linkedMapOf(
            "schema_version" to YamlImporter.SUPPORTED_SCHEMA_VERSION,
            "project" to project,
        )
    }

    /** Recursively turns a loose node into a list of strict task maps. */
    private fun convertNode(node: Any?, depth: Int): List<Map<String, Any?>> {
        if (node == null || depth > MAX_CONVERT_DEPTH) return emptyList()

        return when (node) {
            is Map<*, *> -> node.entries.mapNotNull { (key, value) ->
                val title = key?.toString()?.trim().orEmpty()
                if (title.isEmpty()) null else taskOf(title, convertNode(value, depth + 1))
            }

            is List<*> -> node.flatMap { item ->
                when (item) {
                    null -> emptyList()
                    // A nested map inside a list is expanded at this same level,
                    // so `- AHU-01: [VAV1]` behaves like a normal key.
                    is Map<*, *> -> convertNode(item, depth + 1)
                    is List<*> -> convertNode(item, depth + 1)
                    else -> {
                        val title = item.toString().trim()
                        if (title.isEmpty()) emptyList() else listOf(taskOf(title, emptyList()))
                    }
                }
            }

            // A bare scalar in a child position is a single leaf task.
            else -> {
                val title = node.toString().trim()
                if (title.isEmpty()) emptyList() else listOf(taskOf(title, emptyList()))
            }
        }
    }

    private fun taskOf(title: String, children: List<Map<String, Any?>>): Map<String, Any?> {
        val task = LinkedHashMap<String, Any?>()
        task["title"] = title
        if (children.isNotEmpty()) task["tasks"] = children
        return task
    }

    private fun firstStringOf(map: Map<*, *>, keys: List<String>): String? =
        keys.firstNotNullOfOrNull { key ->
            (map[key] as? Any?)?.toString()?.trim()?.takeIf { it.isNotBlank() && it != "null" }
        }
}
