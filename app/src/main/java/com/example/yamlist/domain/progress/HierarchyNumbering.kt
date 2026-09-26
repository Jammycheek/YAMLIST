package com.example.yamlist.domain.progress

import com.example.yamlist.domain.model.TaskNode

/**
 * Generates hierarchy display numbers ("1", "1.1", "2.3.1") from the tree shape
 * (spec §2.3, §2.7).
 *
 * These numbers are deliberately **not** persisted. They are a view of the
 * current parent/child relationships and displayOrder, so deleting or reordering
 * a task renumbers its siblings automatically with no migration or fix-up pass.
 */
object HierarchyNumbering {

    /** Maps task id -> display number for the whole forest. */
    fun numbersFor(forest: List<TaskNode>): Map<Long, String> {
        val out = LinkedHashMap<Long, String>()
        assign(forest, prefix = "", out = out)
        return out
    }

    /** Display number for a single node given its ancestors' prefix. */
    fun numberOf(prefix: String, indexInLevel: Int): String =
        if (prefix.isEmpty()) "${indexInLevel + 1}" else "$prefix.${indexInLevel + 1}"

    private fun assign(nodes: List<TaskNode>, prefix: String, out: MutableMap<Long, String>) {
        nodes.forEachIndexed { index, node ->
            val number = numberOf(prefix, index)
            out[node.task.id] = number
            assign(node.children, number, out)
        }
    }
}
