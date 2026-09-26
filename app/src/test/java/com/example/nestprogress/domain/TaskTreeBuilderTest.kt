package com.example.nestprogress.domain

import com.example.nestprogress.domain.model.Task
import com.example.nestprogress.domain.progress.TaskTreeBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class TaskTreeBuilderTest {

    private val t0 = LocalDateTime.of(2025, 1, 1, 0, 0)

    private fun task(id: Long, parent: Long? = null, order: Long = id, deleted: Boolean = false) =
        Task(
            id = id, uuid = "u$id", projectId = 1, parentTaskId = parent,
            title = "t$id", displayOrder = order, isDeleted = deleted,
            createdAt = t0, updatedAt = t0,
        )

    @Test
    fun `children are ordered by displayOrder then id`() {
        val forest = TaskTreeBuilder.build(
            listOf(
                task(1),
                task(3, parent = 1, order = 1),
                task(2, parent = 1, order = 2),
            )
        )
        assertEquals(1, forest.size)
        val childIds = forest[0].children.map { it.task.id }
        assertEquals(listOf(3L, 2L), childIds)   // order 1 before order 2
    }

    @Test
    fun `dangling parent is treated as root`() {
        val forest = TaskTreeBuilder.build(
            listOf(task(2, parent = 999)) // 999 not present
        )
        assertEquals(1, forest.size)
        assertEquals(2L, forest[0].task.id)
    }

    @Test
    fun `deleted tasks are dropped`() {
        val forest = TaskTreeBuilder.build(
            listOf(
                task(1),
                task(2, parent = 1, deleted = true),
            )
        )
        assertTrue(forest[0].children.isEmpty())
    }

    @Test
    fun `maxDepth counts levels`() {
        val forest = TaskTreeBuilder.build(
            listOf(
                task(1),
                task(2, parent = 1),
                task(3, parent = 2),
            )
        )
        assertEquals(3, TaskTreeBuilder.maxDepth(forest))
    }

    @Test
    fun `flatten is depth first with correct depth`() {
        val forest = TaskTreeBuilder.build(
            listOf(
                task(1),
                task(2, parent = 1),
                task(3),
            )
        )
        val flat = TaskTreeBuilder.flatten(forest).map { it.first.task.id to it.second }
        assertEquals(listOf(1L to 0, 2L to 1, 3L to 0), flat)
    }
}
