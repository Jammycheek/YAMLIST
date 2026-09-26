package com.example.nestprogress.domain.progress

import com.example.nestprogress.domain.model.Task
import com.example.nestprogress.domain.model.TaskNode

/**
 * Assembles a flat list of [Task] into a forest of [TaskNode].
 *
 * Rules:
 *  - Deleted tasks are dropped (spec: logical delete excluded everywhere).
 *  - A task whose [Task.parentTaskId] does not resolve to a live task in the same
 *    list is treated as a root (defensive against dangling references).
 *  - Children within each parent are ordered by [Task.displayOrder] then id, so the
 *    forest is deterministic regardless of DB fetch order.
 *  - Cycles (should never occur given the edit-time guard in spec §7.3) are broken
 *    defensively: a node is attached at most once.
 */
object TaskTreeBuilder {

    fun build(tasks: List<Task>): List<TaskNode> {
        val live = tasks.filterNot { it.isDeleted }
        val byId = live.associateBy { it.id }
        val childrenByParent = HashMap<Long?, MutableList<Task>>()
        for (t in live) {
            val parentKey = t.parentTaskId?.takeIf { byId.containsKey(it) }
            childrenByParent.getOrPut(parentKey) { mutableListOf() }.add(t)
        }
        val visited = HashSet<Long>()

        fun nodeOf(task: Task): TaskNode {
            // Cycle guard: if already visited, treat as leaf to avoid infinite recursion.
            if (!visited.add(task.id)) return TaskNode(task, emptyList())
            val kids = (childrenByParent[task.id] ?: emptyList())
                .sortedWith(compareBy({ it.displayOrder }, { it.id }))
                .map { nodeOf(it) }
            return TaskNode(task, kids)
        }

        return (childrenByParent[null] ?: emptyList())
            .sortedWith(compareBy({ it.displayOrder }, { it.id }))
            .map { nodeOf(it) }
    }

    /** Flatten a forest depth-first, carrying each node's depth (root = 0). */
    fun flatten(forest: List<TaskNode>): List<Pair<TaskNode, Int>> {
        val out = ArrayList<Pair<TaskNode, Int>>()
        fun walk(node: TaskNode, depth: Int) {
            out.add(node to depth)
            node.children.forEach { walk(it, depth + 1) }
        }
        forest.forEach { walk(it, 0) }
        return out
    }

    /** Maximum nesting depth in a forest (single root with no children = depth 1). */
    fun maxDepth(forest: List<TaskNode>): Int {
        fun depth(node: TaskNode): Int =
            1 + (node.children.maxOfOrNull { depth(it) } ?: 0)
        return forest.maxOfOrNull { depth(it) } ?: 0
    }
}
