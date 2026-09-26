package com.example.nestprogress.domain

import com.example.nestprogress.domain.model.Task
import com.example.nestprogress.domain.progress.HierarchyNumbering
import com.example.nestprogress.domain.progress.TaskTreeBuilder
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

/** Hierarchy numbers are derived from the tree, never stored (spec §2.7). */
class HierarchyNumberingTest {

    private val t0 = LocalDateTime.of(2025, 1, 1, 0, 0)

    private fun task(id: Long, parent: Long? = null, order: Long = id) = Task(
        id = id, uuid = "u$id", projectId = 1, parentTaskId = parent,
        title = "t$id", displayOrder = order, createdAt = t0, updatedAt = t0,
    )

    @Test
    fun `numbers follow the nesting depth`() {
        val forest = TaskTreeBuilder.build(
            listOf(
                task(1, order = 1000),
                task(2, parent = 1, order = 1000),
                task(3, parent = 1, order = 2000),
                task(4, order = 2000),
                task(5, parent = 4, order = 1000),
                task(6, parent = 5, order = 1000),
            )
        )
        val numbers = HierarchyNumbering.numbersFor(forest)
        assertEquals("1", numbers[1L])
        assertEquals("1.1", numbers[2L])
        assertEquals("1.2", numbers[3L])
        assertEquals("2", numbers[4L])
        assertEquals("2.1", numbers[5L])
        assertEquals("2.1.1", numbers[6L])
    }

    @Test
    fun `numbering follows displayOrder rather than id`() {
        val forest = TaskTreeBuilder.build(
            listOf(task(9, order = 1000), task(1, order = 2000))
        )
        val numbers = HierarchyNumbering.numbersFor(forest)
        assertEquals("1", numbers[9L])
        assertEquals("2", numbers[1L])
    }

    @Test
    fun `empty forest produces no numbers`() {
        assertEquals(emptyMap<Long, String>(), HierarchyNumbering.numbersFor(emptyList()))
    }
}
