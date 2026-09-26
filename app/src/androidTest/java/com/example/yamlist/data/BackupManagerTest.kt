package com.example.yamlist.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.yamlist.data.file.backup.BackupManager
import com.example.yamlist.data.local.YamlistDatabase
import com.example.yamlist.data.local.entity.ProjectEntity
import com.example.yamlist.data.local.entity.TaskCommentEntity
import com.example.yamlist.data.local.entity.TaskEntity
import com.example.yamlist.data.repository.YamlistRepository
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupManagerTest {
    private lateinit var db: YamlistDatabase
    private val timestamp = LocalDateTime.of(2025, 1, 1, 0, 0)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, YamlistDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun backupAfterSoftDeletes_restoresOnlyCommentsWhoseTasksAreIncluded() = runBlocking {
        val projectDao = db.projectDao()
        val taskDao = db.taskDao()
        val commentDao = db.taskCommentDao()
        val activeProject = projectDao.insert(project("active-project"))
        val deletedProject = projectDao.insert(project("deleted-project"))
        val activeTask = taskDao.insert(task("active-task", activeProject))
        val deletedTask = taskDao.insert(task("deleted-task", activeProject))
        val taskInDeletedProject = taskDao.insert(task("hidden-task", deletedProject))

        commentDao.insert(comment("kept", activeTask).copy(struck = true))
        commentDao.insert(comment("deleted-task-comment", deletedTask))
        commentDao.insert(comment("deleted-project-comment", taskInDeletedProject))
        val deletedComment = commentDao.insert(comment("deleted-comment", activeTask))
        commentDao.softDelete(deletedComment)
        taskDao.softDeleteOne(deletedTask, timestamp)
        projectDao.softDelete(deletedProject, timestamp)

        val manager = BackupManager(YamlistRepository(db))
        val output = ByteArrayOutputStream()
        manager.createBackup(output)
        val (result, archive) = manager.inspect(ByteArrayInputStream(output.toByteArray()))
        assertTrue(result is BackupManager.RestoreResult.Preview)
        val manifest = (result as BackupManager.RestoreResult.Preview).manifest
        assertEquals(1, manifest.projectCount)
        assertEquals(1, manifest.taskCount)
        assertEquals(1, manifest.commentCount)

        manager.restore(archive ?: error("Backup inspection did not return an archive"))

        assertEquals(1, projectDao.getAllOnce().size)
        assertEquals(listOf(activeTask), taskDao.getByProject(activeProject).map { it.id })
        val restoredComments = commentDao.getAllOnce()
        assertEquals(1, restoredComments.size)
        assertEquals(activeTask, restoredComments.single().taskId)
        assertTrue(restoredComments.single().struck)
        assertFalse(restoredComments.single().isDeleted)
    }

    private fun project(uuid: String) = ProjectEntity(
        uuid = uuid, title = uuid, createdAt = timestamp, updatedAt = timestamp,
    )

    private fun task(uuid: String, projectId: Long) = TaskEntity(
        uuid = uuid, projectId = projectId, title = uuid,
        createdAt = timestamp, updatedAt = timestamp,
    )

    private fun comment(uuid: String, taskId: Long) = TaskCommentEntity(
        uuid = uuid, taskId = taskId, body = uuid,
        createdAt = timestamp, updatedAt = timestamp,
    )
}
