package com.example.yamlist.domain

import com.example.yamlist.domain.progress.ParentFirstOrder
import com.example.yamlist.domain.progress.SiblingReorder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParentFirstOrderTest {

    private data class Row(val id: Long, val parent: Long?)

    private fun sort(rows: List<Row>) = ParentFirstOrder.sort(rows, { it.id }, { it.parent })

    @Test
    fun `child listed before a newer parent is moved after it`() {
        // Task 3 was moved under task 10, so id order puts the child first.
        val result = sort(listOf(Row(1, null), Row(3, 10), Row(10, 1)))
        assertEquals(listOf(1L, 10L, 3L), result.map { it.first.id })
        assertEquals(listOf(false, false, false), result.map { it.second })
    }

    @Test
    fun `every row appears once and after its parent`() {
        val rows = listOf(Row(5, 4), Row(4, 2), Row(2, null), Row(3, 2), Row(1, null))
        val order = sort(rows).map { it.first.id }
        assertEquals(rows.size, order.toSet().size)
        rows.filter { it.parent != null }.forEach { r ->
            assertTrue("$r before its parent in $order", order.indexOf(r.parent) < order.indexOf(r.id))
        }
    }

    @Test
    fun `missing parent is detached`() {
        val result = sort(listOf(Row(1, 99), Row(2, 1)))
        assertEquals(listOf(1L to true, 2L to false), result.map { it.first.id to it.second })
    }

    @Test
    fun `cycle is broken by detaching one member`() {
        val result = sort(listOf(Row(1, 2), Row(2, 1), Row(3, null)))
        assertEquals(setOf(1L, 2L, 3L), result.map { it.first.id }.toSet())
        assertEquals(1, result.count { it.second })
    }

    @Test
    fun `moved shifts one step and stops at the ends`() {
        assertEquals(listOf(20L, 10L, 30L), SiblingReorder.moved(listOf(10L, 20L, 30L), 10L, SiblingReorder.DOWN))
        assertEquals(listOf(10L, 30L, 20L), SiblingReorder.moved(listOf(10L, 20L, 30L), 30L, SiblingReorder.UP))
        assertNull(SiblingReorder.moved(listOf(10L, 20L), 10L, SiblingReorder.UP))
    }
}
