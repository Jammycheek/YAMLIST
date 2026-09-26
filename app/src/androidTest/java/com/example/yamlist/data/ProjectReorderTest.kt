package com.example.yamlist.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.yamlist.data.local.YamlistDatabase
import com.example.yamlist.data.local.entity.ProjectEntity
import com.example.yamlist.data.local.entity.ProjectGroupEntity
import com.example.yamlist.data.repository.YamlistRepository
import com.example.yamlist.domain.progress.SiblingReorder
import java.time.LocalDateTime
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProjectReorderTest {
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
    fun moveProject_swapsWithinGroupAndCanBeReversed() = runBlocking {
        val dao = db.projectDao()
        val groupId = db.projectGroupDao().insert(
            ProjectGroupEntity(
                uuid = "group", title = "Group", createdAt = timestamp, updatedAt = timestamp,
            )
        )
        val first = dao.insert(project("first", null, 1000))
        val second = dao.insert(project("second", null, 2000))
        val third = dao.insert(project("third", null, 3000))
        val archived = dao.insert(project("archived", null, 1500).copy(isArchived = true))
        val groupedFirst = dao.insert(project("grouped-first", groupId, 1000))
        val groupedSecond = dao.insert(project("grouped-second", groupId, 2000))
        val repo = YamlistRepository(db)

        repo.moveProject(first, SiblingReorder.DOWN)
        assertEquals(listOf(second, first, third), dao.getGroupSiblings(null).map { it.id })
        assertEquals(listOf(groupedFirst, groupedSecond), dao.getGroupSiblings(groupId).map { it.id })
        assertEquals(1500L, dao.getById(archived)!!.displayOrder)

        repo.moveProject(first, SiblingReorder.UP)
        assertEquals(listOf(first, second, third), dao.getGroupSiblings(null).map { it.id })

        repo.moveProject(groupedFirst, SiblingReorder.DOWN)
        assertEquals(listOf(groupedSecond, groupedFirst), dao.getGroupSiblings(groupId).map { it.id })
        assertEquals(listOf(first, second, third), dao.getGroupSiblings(null).map { it.id })
    }

    private fun project(uuid: String, groupId: Long?, order: Long) = ProjectEntity(
        uuid = uuid, groupId = groupId, title = uuid, displayOrder = order,
        createdAt = timestamp, updatedAt = timestamp,
    )
}
