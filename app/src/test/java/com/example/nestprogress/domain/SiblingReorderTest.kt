package com.example.nestprogress.domain

import com.example.nestprogress.domain.progress.SiblingReorder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Index arithmetic for moving a task up/down among its same-level siblings. */
class SiblingReorderTest {

    private val ids = listOf(10L, 20L, 30L, 40L)

    @Test
    fun `moving a middle task up swaps with the previous one`() {
        assertEquals(1 to 0, SiblingReorder.swapIndices(ids, 20L, SiblingReorder.UP))
    }

    @Test
    fun `moving a middle task down swaps with the next one`() {
        assertEquals(1 to 2, SiblingReorder.swapIndices(ids, 20L, SiblingReorder.DOWN))
    }

    @Test
    fun `moving the first task up is a no-op`() {
        assertNull(SiblingReorder.swapIndices(ids, 10L, SiblingReorder.UP))
    }

    @Test
    fun `moving the last task down is a no-op`() {
        assertNull(SiblingReorder.swapIndices(ids, 40L, SiblingReorder.DOWN))
    }

    @Test
    fun `unknown id resolves to nothing`() {
        assertNull(SiblingReorder.swapIndices(ids, 999L, SiblingReorder.UP))
    }

    @Test
    fun `a single-element level cannot move either way`() {
        val single = listOf(5L)
        assertNull(SiblingReorder.swapIndices(single, 5L, SiblingReorder.UP))
        assertNull(SiblingReorder.swapIndices(single, 5L, SiblingReorder.DOWN))
    }
}
