package com.example.yamlist.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.yamlist.data.local.YamlistDatabase
import com.example.yamlist.data.local.entity.ProjectEntity
import com.example.yamlist.data.repository.YamlistRepository
import com.example.yamlist.domain.progress.SiblingReorder
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
class ProjectGroupTest {
    private lateinit var db: YamlistDatabase
    private lateinit var repo: YamlistRepository
    private val timestamp = LocalDateTime.of(2025, 1, 1, 0, 0)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, YamlistDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = YamlistRepository(db)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun deleteGroup_appendsItsProjectsAfterTheUngroupedOnes() = runBlocking {
        val dao = db.projectDao()
        val a = dao.insert(project("a", null, 1))
        val b = dao.insert(project("b", null, 3001))
        val groupId = repo.createProjectGroup("G")
        val x = dao.insert(project("x", groupId, 1000))
        val y = dao.insert(project("y", groupId, 2000))

        repo.deleteProjectGroup(groupId)

        assertEquals(listOf(a, b, x, y), dao.getGroupSiblings(null).map { it.id })
    }

    @Test
    fun unarchive_putsTheProjectAtTheEndOfItsLevel() = runBlocking {
        val dao = db.projectDao()
        val p = dao.insert(project("p", null, 1000))
        val q = dao.insert(project("q", null, 2000))
        val r = dao.insert(project("r", null, 3000))

        repo.setProjectArchived(p, true)
        repo.moveProject(r, SiblingReorder.UP) // renumbers q, r without the archived p
        repo.setProjectArchived(p, false)

        assertEquals(listOf(r, q, p), dao.getGroupSiblings(null).map { it.id })
    }

    private fun project(uuid: String, groupId: Long?, order: Long) = ProjectEntity(
        uuid = uuid, groupId = groupId, title = uuid, displayOrder = order,
        createdAt = timestamp, updatedAt = timestamp,
    )
}
