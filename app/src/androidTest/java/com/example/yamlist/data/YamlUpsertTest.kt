package com.example.yamlist.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.yamlist.data.file.yaml.YamlComment
import com.example.yamlist.data.file.yaml.YamlExporter
import com.example.yamlist.data.file.yaml.YamlImportMode
import com.example.yamlist.data.file.yaml.YamlImportService
import com.example.yamlist.data.file.yaml.YamlImporter
import com.example.yamlist.data.file.yaml.YamlParseResult
import com.example.yamlist.data.local.YamlistDatabase
import com.example.yamlist.data.repository.YamlistRepository
import com.example.yamlist.domain.progress.TaskTreeBuilder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime

/** Export -> UUID upsert import of the same project, which used to hit the task uuid index. */
@RunWith(AndroidJUnit4::class)
class YamlUpsertTest {
    private lateinit var db: YamlistDatabase
    private lateinit var repo: YamlistRepository

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
    fun exportThenUpsert_replacesTreeAndKeepsUuidsAndComments() = runBlocking {
        val yaml = """
            schema_version: 1
            project:
              uuid: proj-1
              title: P
              tasks:
                - uuid: task-1
                  title: A
                  comments:
                    - 有効
                    - text: 取消済み
                      struck: true
        """.trimIndent()
        val service = YamlImportService(repo)
        val projectId = service.import(parsed(yaml), YamlImportMode.UUID_UPSERT)

        val exported = YamlExporter.export(
            repo.getProjectOnce(projectId)!!,
            TaskTreeBuilder.build(repo.observeTasks(projectId).first()),
            commentsByTask = repo.commentsForProject(projectId)
                .mapValues { (_, list) -> list.map { YamlComment(it.body, it.struck) } },
        )
        val secondId = service.import(parsed(exported), YamlImportMode.UUID_UPSERT)

        assertEquals(projectId, secondId)
        val tasks = db.taskDao().getByProject(projectId)
        assertEquals(listOf("task-1"), tasks.map { it.uuid })
        val comments = repo.commentsForProject(projectId).values.flatten()
        assertEquals(listOf("有効" to false, "取消済み" to true), comments.map { it.body to it.struck })
    }

    @Test
    fun upsertKeepsExistingArchiveSortOrderAndCompletionSettings() = runBlocking {
        val service = YamlImportService(repo)
        val yaml = """
            schema_version: 1
            project:
              uuid: existing-project
              title: Updated
              tasks: []
        """.trimIndent()
        val id = service.import(parsed(yaml), YamlImportMode.UUID_UPSERT)
        val completedAt = LocalDateTime.of(2025, 2, 3, 4, 5)
        val before = db.projectDao().getById(id)!!.copy(
            isArchived = true, displayOrder = 7000,
            defaultSortMode = "DUE_ASC", completedAt = completedAt,
        )
        db.projectDao().update(before)

        service.import(parsed(yaml), YamlImportMode.UUID_UPSERT)

        val after = db.projectDao().getById(id)!!
        assertEquals(true, after.isArchived)
        assertEquals(7000L, after.displayOrder)
        assertEquals("DUE_ASC", after.defaultSortMode)
        assertEquals(completedAt, after.completedAt)
        assertEquals("Updated", after.title)
    }

    private fun parsed(text: String) = when (val r = YamlImporter.parse(text)) {
        is YamlParseResult.Success -> r.project
        is YamlParseResult.Failure -> error("YAML did not parse: ${r.errors}")
    }
}
