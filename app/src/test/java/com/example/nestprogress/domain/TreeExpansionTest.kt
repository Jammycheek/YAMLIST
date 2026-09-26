package com.example.nestprogress.domain

import com.example.nestprogress.domain.model.Task
import com.example.nestprogress.domain.model.TaskNode
import com.example.nestprogress.domain.progress.HierarchyNumbering
import com.example.nestprogress.domain.progress.TaskTreeBuilder
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

/**
 * The tree screen's expand/collapse stepping, exercised against a real forest.
 *
 * The algorithms live in TaskTreeViewModel, which needs Android to instantiate;
 * this reproduces them over the same domain types so the behaviour that matters
 * (how many rows end up visible after each action) is pinned down on the JVM.
 */
class TreeExpansionTest {

    private val t0 = LocalDateTime.of(2025, 1, 1, 0, 0)

    private fun task(id: Long, parent: Long? = null, order: Long = id) = Task(
        id = id, uuid = "u$id", projectId = 1, parentTaskId = parent,
        title = "t$id", displayOrder = order, createdAt = t0, updatedAt = t0,
    )

    /** 1 root -> 2 mid parents -> 2 leaves each: seven nodes over three levels. */
    private fun forest(): List<TaskNode> = TaskTreeBuilder.build(
        listOf(
            task(1, order = 1000),
            task(2, parent = 1, order = 1000),
            task(4, parent = 2, order = 1000),
            task(5, parent = 2, order = 2000),
            task(3, parent = 1, order = 2000),
            task(6, parent = 3, order = 1000),
            task(7, parent = 3, order = 2000),
        )
    )

    /** Mirrors the collapse-set behaviour of the tree view model. */
    private class Expansion(val forest: List<TaskNode>) {
        var collapsed: Set<Long> = emptySet()

        fun expandAll() {
            collapsed = emptySet()
        }

        fun collapseAll() {
            val parents = HashSet<Long>()
            fun rec(node: TaskNode) {
                if (!node.isLeaf) {
                    parents.add(node.task.id)
                    node.children.forEach { rec(it) }
                }
            }
            forest.forEach { rec(it) }
            collapsed = parents
        }

        fun visibleParents(expanded: Boolean): List<Pair<Long, Int>> {
            val out = ArrayList<Pair<Long, Int>>()
            fun rec(node: TaskNode, depth: Int) {
                val open = node.task.id !in collapsed
                if (!node.isLeaf && open == expanded) out.add(node.task.id to depth)
                if (open) node.children.forEach { rec(it, depth + 1) }
            }
            forest.forEach { rec(it, 0) }
            return out
        }

        fun collapseOneLevel() {
            val open = visibleParents(expanded = true)
            val deepest = open.maxOfOrNull { it.second } ?: return
            collapsed = collapsed + open.filter { it.second == deepest }.map { it.first }
        }

        fun expandOneLevel() {
            val shut = visibleParents(expanded = false)
            val shallowest = shut.minOfOrNull { it.second } ?: return
            collapsed = collapsed - shut.filter { it.second == shallowest }.map { it.first }.toSet()
        }

        fun visibleCount(): Int {
            var n = 0
            fun rec(node: TaskNode) {
                n++
                if (node.task.id !in collapsed) node.children.forEach { rec(it) }
            }
            forest.forEach { rec(it) }
            return n
        }
    }

    @Test
    fun `everything is visible before any collapsing`() {
        assertEquals(7, Expansion(forest()).visibleCount())
    }

    @Test
    fun `collapse all leaves only the roots`() {
        val e = Expansion(forest())
        e.collapseAll()
        assertEquals(1, e.visibleCount())
    }

    @Test
    fun `expand all restores every row`() {
        val e = Expansion(forest())
        e.collapseAll()
        e.expandAll()
        assertEquals(7, e.visibleCount())
    }

    @Test
    fun `collapsing one level closes the innermost open layer first`() {
        val e = Expansion(forest())
        e.collapseOneLevel()
        assertEquals(setOf(2L, 3L), e.collapsed)
        assertEquals(3, e.visibleCount())

        e.collapseOneLevel()
        assertEquals(1, e.visibleCount())
    }

    @Test
    fun `expanding one level opens the shallowest closed layer first`() {
        val e = Expansion(forest())
        e.collapseAll()

        e.expandOneLevel()
        assertEquals(3, e.visibleCount())

        e.expandOneLevel()
        assertEquals(7, e.visibleCount())
    }

    @Test
    fun `stepping past the ends does nothing`() {
        val e = Expansion(forest())
        e.expandOneLevel()
        assertEquals(7, e.visibleCount())

        e.collapseAll()
        e.collapseOneLevel()
        assertEquals(1, e.visibleCount())
    }

    @Test
    fun `local number is the position within its own level`() {
        val numbers = HierarchyNumbering.numbersFor(forest())
        assertEquals("1.2.1", numbers[6L])
        assertEquals("1", numbers[6L]!!.substringAfterLast('.'))
        assertEquals("1.2.2", numbers[7L])
        assertEquals("2", numbers[7L]!!.substringAfterLast('.'))
        // A root has no dot, so the local number is the number itself.
        assertEquals("1", numbers[1L]!!.substringAfterLast('.'))
    }
}
