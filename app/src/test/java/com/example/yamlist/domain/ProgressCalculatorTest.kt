package com.example.yamlist.domain

import com.example.yamlist.domain.model.Task
import com.example.yamlist.domain.model.TaskStatus
import com.example.yamlist.domain.progress.ProgressCalculator
import com.example.yamlist.domain.progress.TaskTreeBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/**
 * Verifies the progress rules of spec §8 and §26.2. These are the port of the
 * kotlinc harness used during development; they run on the plain JVM (no Android).
 */
class ProgressCalculatorTest {

    private val t0 = LocalDateTime.of(2025, 1, 1, 0, 0)

    private fun task(
        id: Long,
        parent: Long? = null,
        status: TaskStatus = TaskStatus.TODO,
        weight: Double = 1.0,
        target: Boolean = true,
        deleted: Boolean = false,
    ) = Task(
        id = id,
        uuid = "u$id",
        projectId = 1,
        parentTaskId = parent,
        title = "t$id",
        status = status,
        weight = weight,
        isProgressTarget = target,
        isDeleted = deleted,
        displayOrder = id,
        createdAt = t0,
        updatedAt = t0,
    )

    private fun progressOf(tasks: List<Task>) =
        ProgressCalculator.forForest(TaskTreeBuilder.build(tasks))

    @Test
    fun `legacy non finite weights do not crash progress`() {
        val p = progressOf(listOf(task(1, weight = Double.NaN), task(2, weight = 2.0)))
        assertEquals(1, p.countTotal)
        assertEquals(2.0, p.weightTotal, 0.0)
    }

    @Test
    fun `leaf count and weight aggregate correctly`() {
        val p = progressOf(
            listOf(
                task(1, weight = 1.0, status = TaskStatus.DONE),
                task(2, weight = 3.0, status = TaskStatus.TODO),
            )
        )
        assertEquals(2, p.countTotal)
        assertEquals(1, p.countDone)
        assertEquals(4.0, p.weightTotal, 1e-9)
        assertEquals(1.0, p.weightDone, 1e-9)
        assertEquals(50, p.countPercent)         // 1/2
        assertEquals(25, p.weightPercent)        // 1/4
    }

    @Test
    fun `parent does not double count its own weight`() {
        // parent(1) has two leaf children; parent must contribute nothing itself.
        val p = progressOf(
            listOf(
                task(1, weight = 99.0),                        // parent, weight ignored
                task(2, parent = 1, weight = 1.0, status = TaskStatus.DONE),
                task(3, parent = 1, weight = 1.0, status = TaskStatus.TODO),
            )
        )
        assertEquals(2, p.countTotal)
        assertEquals(2.0, p.weightTotal, 1e-9)   // 99 not included
        assertEquals(50, p.weightPercent)
    }

    @Test
    fun `weight zero or negative is excluded from numerator and denominator`() {
        val p = progressOf(
            listOf(
                task(1, weight = 0.0, status = TaskStatus.DONE),   // excluded
                task(2, weight = -5.0, status = TaskStatus.DONE),  // excluded
                task(3, weight = 2.0, status = TaskStatus.DONE),
            )
        )
        assertEquals(1, p.countTotal)
        assertEquals(2.0, p.weightTotal, 1e-9)
        assertEquals(100, p.weightPercent)
    }

    @Test
    fun `unfinished task is in denominator but never numerator`() {
        val p = progressOf(
            listOf(
                task(1, weight = 1.0, status = TaskStatus.TODO),
                task(2, weight = 1.0, status = TaskStatus.DONE),
            )
        )
        assertEquals(2, p.countTotal)
        assertEquals(1, p.countDone)             // TODO not counted done
        assertEquals(50, p.countPercent)
    }

    @Test
    fun `no targets yields zero percent and no targets flag`() {
        val p = progressOf(
            listOf(
                task(1, target = false, status = TaskStatus.DONE),
                task(2, weight = 0.0, status = TaskStatus.DONE),
            )
        )
        assertFalse(p.hasTargets)
        assertEquals(0, p.countPercent)
        assertEquals(0, p.weightPercent)
    }

    @Test
    fun `deleted tasks are excluded`() {
        val p = progressOf(
            listOf(
                task(1, weight = 1.0, status = TaskStatus.DONE),
                task(2, weight = 1.0, status = TaskStatus.TODO, deleted = true),
            )
        )
        assertEquals(1, p.countTotal)
        assertEquals(100, p.countPercent)
    }

    @Test
    fun `three level nesting counts only leaves`() {
        val p = progressOf(
            listOf(
                task(1),                                   // root parent
                task(2, parent = 1),                       // mid parent
                task(3, parent = 2, status = TaskStatus.DONE),
                task(4, parent = 2, status = TaskStatus.TODO),
                task(5, parent = 1, status = TaskStatus.DONE),
            )
        )
        // leaves: 3,4,5 -> 2 done / 3
        assertEquals(3, p.countTotal)
        assertEquals(2, p.countDone)
        assertEquals(67, p.countPercent)         // 2/3 = 66.67 -> 67 half-up
    }

    @Test
    fun `half up rounding at the boundary`() {
        // 1 of 8 done => 12.5% => 13 half-up
        val tasks = (1L..8L).map {
            task(it, weight = 1.0, status = if (it == 1L) TaskStatus.DONE else TaskStatus.TODO)
        }
        val p = progressOf(tasks)
        assertEquals(13, p.countPercent)
    }

    @Test
    fun `empty forest is zero and has no targets`() {
        val p = progressOf(emptyList())
        assertEquals(0, p.countTotal)
        assertFalse(p.hasTargets)
        assertTrue(p.weightFraction == 0.0)
    }
}
