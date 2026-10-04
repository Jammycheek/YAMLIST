package com.example.yamlist.data

import androidx.room.Room
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.yamlist.data.local.YamlistDatabase
import com.example.yamlist.data.local.entity.ProjectEntity
import com.example.yamlist.data.local.entity.TaskEntity
import com.example.yamlist.data.repository.YamlistRepository
import com.example.yamlist.domain.model.TaskStatus
import com.example.yamlist.ui.bulkchild.BulkChildCreateViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime

/**
 * Instrumented test for the recursive-CTE subtree operations (spec §18.2).
 * Runs on device/emulator against an in-memory Room DB.
 */
@RunWith(AndroidJUnit4::class)
class TaskDaoTest {

    private lateinit var db: YamlistDatabase
    private val t0 = LocalDateTime.of(2025, 1, 1, 0, 0)

    @Before
    fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, YamlistDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    private fun project() = ProjectEntity(
        uuid = "p", title = "P", createdAt = t0, updatedAt = t0,
    )

    private fun task(uuid: String, projectId: Long, parent: Long?) = TaskEntity(
        uuid = uuid, projectId = projectId, parentTaskId = parent,
        title = uuid, createdAt = t0, updatedAt = t0,
    )

    @Test
    fun softDeleteSubtree_removesDescendants_butNotSiblings() = runBlocking {
        val projectId = db.projectDao().insert(project())
        val root = db.taskDao().insert(task("root", projectId, null))
        val a = db.taskDao().insert(task("a", projectId, root))
        val b = db.taskDao().insert(task("b", projectId, a))
        val sibling = db.taskDao().insert(task("sibling", projectId, null))

        assertEquals(2, db.taskDao().countDescendants(root)) // a, b
        db.taskDao().softDeleteSubtree(root, t0)

        val live = db.taskDao().getByProject(projectId).map { it.id }.toSet()
        assertFalse(live.contains(root))
        assertFalse(live.contains(a))
        assertFalse(live.contains(b))
        assertTrue(live.contains(sibling)) // untouched
    }

    @Test
    fun descendantCountIgnoresPreviouslyDeletedChildren() = runBlocking {
        val projectId = db.projectDao().insert(project())
        val root = db.taskDao().insert(task("root", projectId, null))
        val deleted = db.taskDao().insert(task("deleted", projectId, root))
        db.taskDao().insert(task("live", projectId, root))
        db.taskDao().softDeleteOne(deleted, t0)

        assertEquals(1, db.taskDao().countDescendants(root))
    }

    @Test
    fun completedHistoryExcludesDeletedProjects() = runBlocking {
        val first = db.projectDao().insert(project())
        val second = db.projectDao().insert(project().copy(uuid = "p2"))
        db.taskDao().insert(task("hidden", first, null).copy(status = "DONE", completedAt = t0))
        db.taskDao().insert(task("visible", second, null).copy(status = "DONE", completedAt = t0))
        db.projectDao().softDelete(first, t0)

        assertEquals(listOf("visible"), db.taskDao().observeCompletedHistory().first().map { it.title })
    }

    @Test
    fun completedTaskLeavesHistoryWhenItGainsAChild() = runBlocking {
        val projectId = db.projectDao().insert(project())
        val formerLeaf = db.taskDao().insert(
            task("former-leaf", projectId, null).copy(status = "DONE", completedAt = t0)
        )
        assertEquals(listOf("former-leaf"), db.taskDao().observeCompletedHistory().first().map { it.title })

        db.taskDao().insert(task("child", projectId, formerLeaf))

        assertTrue(db.taskDao().observeCompletedHistory().first().isEmpty())
    }

    @Test
    fun parentWithoutProgressTargetsCanBeMarkedDoneAndUndone() = runBlocking {
        val projectId = db.projectDao().insert(project())
        val root = db.taskDao().insert(task("root", projectId, null))
        val child = db.taskDao().insert(task("child", projectId, root).copy(isProgressTarget = false))
        val repo = YamlistRepository(db)

        repo.setSubtreeStatus(projectId, root, TaskStatus.DONE)
        assertEquals("DONE", db.taskDao().getById(root)!!.status)
        assertEquals("DONE", db.taskDao().getById(child)!!.status)

        repo.setSubtreeStatus(projectId, root, TaskStatus.TODO)
        assertEquals("TODO", db.taskDao().getById(root)!!.status)
        assertEquals("TODO", db.taskDao().getById(child)!!.status)
    }

    @Test
    fun normalizeLegacyStatus_turnsRemovedHoldIntoTodoAndKeepsDone() = runBlocking {
        val projectId = db.projectDao().insert(project())
        val held = db.taskDao().insert(task("held", projectId, null).copy(status = "HOLD"))
        val done = db.taskDao().insert(task("done", projectId, null).copy(status = "DONE"))
        val todo = db.taskDao().insert(task("todo", projectId, null))

        assertEquals(1, db.taskDao().normalizeLegacyStatus())

        assertEquals("TODO", db.taskDao().getById(held)!!.status)
        assertEquals("DONE", db.taskDao().getById(done)!!.status)
        assertEquals("TODO", db.taskDao().getById(todo)!!.status)
        assertEquals(0, db.taskDao().normalizeLegacyStatus())
    }

    @Test
    fun nestedExcludedBranchUpdatesItsIntermediateCheckboxState() = runBlocking {
        val projectId = db.projectDao().insert(project())
        val root = db.taskDao().insert(task("root", projectId, null))
        val middle = db.taskDao().insert(task("middle", projectId, root))
        val excluded = db.taskDao().insert(
            task("excluded", projectId, middle).copy(isProgressTarget = false)
        )
        val measurable = db.taskDao().insert(task("measurable", projectId, root))
        val repo = YamlistRepository(db)

        repo.setSubtreeStatus(projectId, root, TaskStatus.DONE)
        assertEquals("TODO", db.taskDao().getById(root)!!.status)
        assertEquals("DONE", db.taskDao().getById(middle)!!.status)
        assertEquals("DONE", db.taskDao().getById(excluded)!!.status)
        assertEquals("DONE", db.taskDao().getById(measurable)!!.status)

        repo.setSubtreeStatus(projectId, root, TaskStatus.TODO)
        assertEquals("TODO", db.taskDao().getById(middle)!!.status)
        assertEquals("TODO", db.taskDao().getById(excluded)!!.status)
    }

    @Test
    fun parentWithProgressTargetsStaysOutOfCompletedHistory() = runBlocking {
        val projectId = db.projectDao().insert(project())
        val root = db.taskDao().insert(task("root", projectId, null))
        val child = db.taskDao().insert(task("child", projectId, root))
        val repo = YamlistRepository(db)

        repo.setSubtreeStatus(projectId, root, TaskStatus.DONE)

        assertEquals("TODO", db.taskDao().getById(root)!!.status)
        assertEquals("DONE", db.taskDao().getById(child)!!.status)
        assertEquals(listOf("child"), db.taskDao().observeCompletedHistory().first().map { it.title })
    }

    @Test
    fun bulkCreationOfDoneChildrenStoresCompletionTime() = runBlocking {
        val projectId = db.projectDao().insert(project())
        val root = db.taskDao().insert(task("root", projectId, null))
        val viewModel = BulkChildCreateViewModel(
            YamlistRepository(db), SavedStateHandle(mapOf("parentId" to root)),
        )
        withTimeout(5000) { viewModel.state.first { it.projectId == projectId } }
        viewModel.onInputText("child")
        viewModel.onStatus(TaskStatus.DONE)
        viewModel.onPlannedMonth("2026-09  ")
        val saved = CompletableDeferred<Unit>()

        viewModel.save { saved.complete(Unit) }
        withTimeout(5000) { saved.await() }

        val child = db.taskDao().getByProject(projectId).single { it.parentTaskId == root }
        assertEquals("DONE", child.status)
        assertTrue(child.completedAt != null)
        assertEquals("2026-09", child.plannedYearMonth)
    }

    @Test
    fun maxChildOrder_isScopedPerLevel() = runBlocking {
        val projectId = db.projectDao().insert(project())
        // Empty level reports -1 so the repository allocates the first step.
        assertEquals(-1L, db.taskDao().maxChildOrder(projectId, null))

        val r1 = db.taskDao().insert(task("r1", projectId, null).copy(displayOrder = 1000))
        db.taskDao().insert(task("r2", projectId, null).copy(displayOrder = 2000))
        assertEquals(2000L, db.taskDao().maxChildOrder(projectId, null))

        // Children of r1 are numbered independently of the root level.
        assertEquals(-1L, db.taskDao().maxChildOrder(projectId, r1))
        db.taskDao().insert(task("c1", projectId, r1).copy(displayOrder = 1000))
        assertEquals(1000L, db.taskDao().maxChildOrder(projectId, r1))
        assertEquals(2000L, db.taskDao().maxChildOrder(projectId, null))
    }

    @Test
    fun children_areReturnedInDisplayOrder() = runBlocking {
        val projectId = db.projectDao().insert(project())
        val parent = db.taskDao().insert(task("p", projectId, null))
        db.taskDao().insert(task("second", projectId, parent).copy(displayOrder = 2000))
        db.taskDao().insert(task("first", projectId, parent).copy(displayOrder = 1000))

        val titles = db.taskDao().getChildren(parent).map { it.title }
        assertEquals(listOf("first", "second"), titles)
    }

    @Test
    fun getSiblings_matchesRootLevelViaIs() = runBlocking {
        val projectId = db.projectDao().insert(project())
        db.taskDao().insert(task("r1", projectId, null).copy(displayOrder = 2000))
        db.taskDao().insert(task("r2", projectId, null).copy(displayOrder = 1000))
        val parent = db.taskDao().insert(task("p", projectId, null).copy(displayOrder = 3000))
        db.taskDao().insert(task("child", projectId, parent))

        // Root siblings: parentId = null must use IS, not =, to match.
        val roots = db.taskDao().getSiblings(projectId, null).map { it.title }
        assertEquals(listOf("r2", "r1", "p"), roots)

        val children = db.taskDao().getSiblings(projectId, parent).map { it.title }
        assertEquals(listOf("child"), children)
    }

    @Test
    fun isInSubtree_detectsSelfAndDescendants_butNotUnrelatedTasks() = runBlocking {
        val projectId = db.projectDao().insert(project())
        val root = db.taskDao().insert(task("root", projectId, null))
        val mid = db.taskDao().insert(task("mid", projectId, root))
        val leaf = db.taskDao().insert(task("leaf", projectId, mid))
        val other = db.taskDao().insert(task("other", projectId, null))

        assertTrue(db.taskDao().isInSubtree(root, root))   // itself counts (cycle guard)
        assertTrue(db.taskDao().isInSubtree(root, mid))
        assertTrue(db.taskDao().isInSubtree(root, leaf))
        assertFalse(db.taskDao().isInSubtree(root, other))
        assertFalse(db.taskDao().isInSubtree(mid, root))   // not symmetric
    }

    @Test
    fun setParentAndOrder_reparentsAndReorders() = runBlocking {
        val projectId = db.projectDao().insert(project())
        val oldParent = db.taskDao().insert(task("old", projectId, null))
        val newParent = db.taskDao().insert(task("new", projectId, null))
        val child = db.taskDao().insert(task("child", projectId, oldParent))

        db.taskDao().setParentAndOrder(child, newParent, 5000L, t0)

        val moved = db.taskDao().getById(child)!!
        assertEquals(newParent, moved.parentTaskId)
        assertEquals(5000L, moved.displayOrder)
    }

    @Test
    fun setParentAndOrder_toRootUsesNullParent() = runBlocking {
        val projectId = db.projectDao().insert(project())
        val parent = db.taskDao().insert(task("p", projectId, null))
        val child = db.taskDao().insert(task("c", projectId, parent))

        db.taskDao().setParentAndOrder(child, null, 1000L, t0)

        assertEquals(null, db.taskDao().getById(child)!!.parentTaskId)
    }

    @Test
    fun softDeleteOne_removesOnlyThatRow_childrenSurvive() = runBlocking {
        val projectId = db.projectDao().insert(project())
        val parent = db.taskDao().insert(task("parent", projectId, null))
        val child = db.taskDao().insert(task("child", projectId, parent))

        db.taskDao().softDeleteOne(parent, t0)

        val live = db.taskDao().getByProject(projectId).map { it.id }.toSet()
        assertFalse(live.contains(parent))
        assertTrue(live.contains(child)) // still present; repository re-parents it separately
    }

    @Test
    fun commentStrikethrough_andSoftDelete() = runBlocking {
        val projectId = db.projectDao().insert(project())
        val taskId = db.taskDao().insert(task("t", projectId, null))
        val commentId = db.taskCommentDao().insert(
            com.example.yamlist.data.local.entity.TaskCommentEntity(
                uuid = "c1", taskId = taskId, body = "note", createdAt = t0, updatedAt = t0,
            )
        )

        db.taskCommentDao().setStruck(commentId, true, t0)
        // Struck is not the same as deleted: it must still be counted.
        val liveAfterStrike = db.taskCommentDao().getAllOnce().first { it.id == commentId }
        assertTrue(liveAfterStrike.struck)

        db.taskCommentDao().softDelete(commentId)
        assertTrue(db.taskCommentDao().getAllOnce().none { it.id == commentId })
    }

    @Test
    fun commentCounts_groupByTask_andExcludeDeleted() = runBlocking {
        val projectId = db.projectDao().insert(project())
        val a = db.taskDao().insert(task("a", projectId, null))
        val b = db.taskDao().insert(task("b", projectId, null))
        fun comment(taskId: Long) = com.example.yamlist.data.local.entity.TaskCommentEntity(
            uuid = java.util.UUID.randomUUID().toString(), taskId = taskId, body = "x",
            createdAt = t0, updatedAt = t0,
        )
        db.taskCommentDao().insert(comment(a))
        db.taskCommentDao().insert(comment(a))
        val bComment = db.taskCommentDao().insert(comment(b))
        db.taskCommentDao().softDelete(bComment)

        val counts = db.taskCommentDao().getAllOnce().groupingBy { it.taskId }.eachCount()
        assertEquals(2, counts[a])
        assertEquals(null, counts[b]) // its only comment was deleted
    }
}
