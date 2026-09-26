package com.example.yamlist.domain

import com.example.yamlist.domain.progress.TaskOrdering
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins down the order-allocation and cycle-guard decisions made by
 * [com.example.yamlist.data.repository.YamlistRepository.deleteTaskPromotingChildren]
 * and `.moveTaskToParent`, without needing Room. The DAO-backed equivalents live
 * in the instrumented `TaskDaoTest`; this covers the same decisions purely.
 */
class PromoteAndMoveOrderingTest {

    private data class Node(var parent: Long?, var order: Long, var deleted: Boolean = false)

    private class Graph {
        val nodes = LinkedHashMap<Long, Node>()
        fun add(id: Long, parent: Long?, order: Long) { nodes[id] = Node(parent, order) }

        private fun siblings(parent: Long?) =
            nodes.entries.filter { it.value.parent == parent && !it.value.deleted }
                .sortedBy { it.value.order }.map { it.key }

        private fun maxChildOrder(parent: Long?) =
            siblings(parent).lastOrNull()?.let { nodes[it]!!.order } ?: -1L

        fun isInSubtree(rootId: Long, candidateId: Long): Boolean {
            if (rootId == candidateId) return true
            return nodes.filterValues { it.parent == rootId && !it.deleted }.keys
                .any { isInSubtree(it, candidateId) }
        }

        /** Mirrors YamlistRepository.deleteTaskPromotingChildren: delete first, then reparent. */
        fun promoteChildren(taskId: Long) {
            val task = nodes[taskId]!!
            val children = siblings(taskId)
            val parent = task.parent
            task.deleted = true
            if (children.isNotEmpty()) {
                val base = maxChildOrder(parent)
                val orders = TaskOrdering.appendOrders(base, children.size)
                children.forEachIndexed { i, id ->
                    nodes[id]!!.parent = parent
                    nodes[id]!!.order = orders[i]
                }
            }
        }

        /** Mirrors YamlistRepository.moveTaskToParent, including the cycle guard. */
        fun moveTo(taskId: Long, newParent: Long?): Boolean {
            if (newParent != null && (newParent == taskId || isInSubtree(taskId, newParent))) return false
            // Compute the order BEFORE mutating parent, mirroring the real
            // repository (which computes order, then applies parent+order in a
            // single DB statement) so the task never counts toward its own
            // destination level's max.
            val order = TaskOrdering.nextOrder(maxChildOrder(newParent))
            nodes[taskId]!!.parent = newParent
            nodes[taskId]!!.order = order
            return true
        }

        fun childrenOf(parent: Long?) = siblings(parent)
    }

    @Test
    fun `promoting children appends them after existing siblings at the grandparent level`() {
        val g = Graph()
        g.add(1, null, 1000)                  // grandparent
        g.add(2, 1, 1000)                     // parent, to be deleted
        g.add(3, 2, 1000); g.add(4, 2, 2000)  // its children
        g.add(5, 1, 2000)                     // existing sibling of the parent

        g.promoteChildren(2)

        assertEquals(listOf(5L, 3L, 4L), g.childrenOf(1))
        assertEquals(1L, g.nodes[3]!!.parent)
        assertEquals(1L, g.nodes[4]!!.parent)
        assertEquals(3000L, g.nodes[3]!!.order)
        assertEquals(4000L, g.nodes[4]!!.order)
        assertEquals(2000L, g.nodes[5]!!.order) // untouched
    }

    @Test
    fun `promoting the only root task lets its child become a fresh root`() {
        val g = Graph()
        g.add(1, null, 1000)
        g.add(2, 1, 1000)

        g.promoteChildren(1)

        assertEquals(null, g.nodes[2]!!.parent)
        // The deleted former parent no longer counts toward the level's max,
        // so the promoted child starts a fresh sequence rather than continuing past it.
        assertEquals(1000L, g.nodes[2]!!.order)
    }

    @Test
    fun `promoting a childless task is a plain delete`() {
        val g = Graph()
        g.add(1, null, 1000)
        g.promoteChildren(1)
        assertTrue(g.nodes[1]!!.deleted)
    }

    @Test
    fun `move appends the task after the destination level's current tasks`() {
        val g = Graph()
        g.add(1, null, 1000); g.add(2, null, 2000)
        g.add(3, 1, 1000)

        assertTrue(g.moveTo(3, 2))
        assertEquals(2L, g.nodes[3]!!.parent)
        assertEquals(1000L, g.nodes[3]!!.order) // first (only) child of 2
    }

    @Test
    fun `move refuses to place a task inside itself`() {
        val g = Graph()
        g.add(1, null, 1000)
        assertFalse(g.moveTo(1, 1))
    }

    @Test
    fun `move refuses to place a task inside its own descendant`() {
        val g = Graph()
        g.add(1, null, 1000); g.add(2, 1, 1000); g.add(3, 2, 1000)
        assertFalse(g.moveTo(1, 3))
        // The subtree itself is unaffected by the refused move.
        assertEquals(1L, g.nodes[2]!!.parent)
    }

    @Test
    fun `move to an unrelated node succeeds`() {
        val g = Graph()
        g.add(1, null, 1000); g.add(2, 1, 1000); g.add(3, 2, 1000)
        val other = 9L
        g.add(other, null, 5000)
        assertTrue(g.moveTo(3, other))
        assertEquals(other, g.nodes[3]!!.parent)
    }
}
