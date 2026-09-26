package com.example.yamlist.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.yamlist.data.local.YamlistDatabase
import com.example.yamlist.data.local.entity.ProjectEntity
import com.example.yamlist.data.local.entity.TaskEntity
import com.example.yamlist.data.repository.TaskWeightChange
import com.example.yamlist.data.repository.YamlistRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
class WeightSheetRepositoryTest {
    private lateinit var db: YamlistDatabase
    private lateinit var repo: YamlistRepository
    private val now = LocalDateTime.of(2026, 1, 1, 0, 0)

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, YamlistDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = YamlistRepository(db)
    }

    @After fun tearDown() = db.close()

    @Test fun savesWeightAndTargetTogetherWithoutChangingOtherFields() = runBlocking {
        val projectId = db.projectDao().insert(project())
        val taskId = db.taskDao().insert(task(projectId, "a"))

        assertTrue(repo.updateTaskWeightsAndTargets(projectId, mapOf(
            taskId to TaskWeightChange(1.0, 2.5, true, false)
        )))

        val task = db.taskDao().getById(taskId)!!
        assertEquals(2.5, task.weight, 0.0)
        assertFalse(task.isProgressTarget)
        assertEquals("a", task.title)
        assertEquals("TODO", task.status)
    }

    @Test fun staleCellRejectsWholeBatchWithoutPartialSave() = runBlocking {
        val projectId = db.projectDao().insert(project())
        val first = db.taskDao().insert(task(projectId, "a"))
        val second = db.taskDao().insert(task(projectId, "b"))
        db.taskDao().setWeightAndProgressTarget(second, 7.0, true, now)

        assertFalse(repo.updateTaskWeightsAndTargets(projectId, mapOf(
            first to TaskWeightChange(1.0, 2.0, true, false),
            second to TaskWeightChange(1.0, 3.0, true, true),
        )))
        assertEquals(1.0, db.taskDao().getById(first)!!.weight, 0.0)
        assertTrue(db.taskDao().getById(first)!!.isProgressTarget)
    }

    @Test fun parentWeightCannotBeEditedFromSheet() = runBlocking {
        val projectId = db.projectDao().insert(project())
        val parent = db.taskDao().insert(task(projectId, "parent"))
        db.taskDao().insert(task(projectId, "child", parent))

        assertFalse(repo.updateTaskWeightsAndTargets(projectId, mapOf(
            parent to TaskWeightChange(1.0, 5.0, true, false)
        )))
        assertEquals(1.0, db.taskDao().getById(parent)!!.weight, 0.0)
    }

    @Test fun staleProgressTargetAlsoRejectsSave() = runBlocking {
        val projectId = db.projectDao().insert(project())
        val taskId = db.taskDao().insert(task(projectId, "a"))
        db.taskDao().setWeightAndProgressTarget(taskId, 1.0, false, now)

        assertFalse(repo.updateTaskWeightsAndTargets(projectId, mapOf(
            taskId to TaskWeightChange(1.0, 2.0, true, true)
        )))
        assertEquals(1.0, db.taskDao().getById(taskId)!!.weight, 0.0)
    }

    private fun project() = ProjectEntity(
        uuid = "project", title = "P", createdAt = now, updatedAt = now,
    )

    private fun task(projectId: Long, title: String, parentId: Long? = null) = TaskEntity(
        uuid = title, projectId = projectId, parentTaskId = parentId, title = title,
        createdAt = now, updatedAt = now,
    )
}
