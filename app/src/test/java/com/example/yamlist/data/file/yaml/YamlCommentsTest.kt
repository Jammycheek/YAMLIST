package com.example.yamlist.data.file.yaml

import com.example.yamlist.domain.model.Project
import com.example.yamlist.domain.model.Task
import com.example.yamlist.domain.model.TaskNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/** Task comments in YAML: `comments:` lists, legacy `description`/`comment` keys, and export. */
class YamlCommentsTest {

    private fun parseSingleTask(taskYaml: String): ParsedTask {
        val text = "schema_version: 1\nproject:\n  title: P\n  tasks:\n" +
            taskYaml.trimIndent().lines().joinToString("\n") { "    $it" }
        val r = YamlImporter.parse(text)
        assertTrue("expected success but got $r", r is YamlParseResult.Success)
        return (r as YamlParseResult.Success).project.tasks.single()
    }

    @Test
    fun `comments list is imported in order`() {
        val t = parseSingleTask(
            """
            - title: A
              comments:
                - 最新版Rev.4を使用
                - "2行目: コロン入り"
            """
        )
        assertEquals(listOf("最新版Rev.4を使用", "2行目: コロン入り"), t.comments)
    }

    @Test
    fun `legacy description and comment keys become comments`() {
        val t = parseSingleTask(
            """
            - title: A
              description: 説明文
              comment: 固定メモ
              comments: [追加]
            """
        )
        assertEquals(listOf("説明文", "固定メモ", "追加"), t.comments)
    }

    @Test
    fun `blank comments are dropped`() {
        val t = parseSingleTask(
            """
            - title: A
              comment: "   "
              comments: ["", x]
            """
        )
        assertEquals(listOf("x"), t.comments)
    }

    @Test
    fun `non list comments value fails validation`() {
        val r = YamlImporter.mapAndValidate(
            mapOf(
                "schema_version" to 1,
                "project" to mapOf(
                    "title" to "P",
                    "tasks" to listOf(mapOf("title" to "A", "comments" to mapOf("k" to "v"))),
                ),
            )
        )
        assertTrue(r is YamlParseResult.Failure)
    }

    @Test
    fun `export writes comments and they survive a round trip`() {
        val now = LocalDateTime.of(2026, 1, 1, 0, 0)
        val project = Project(uuid = "p", title = "P", createdAt = now, updatedAt = now)
        val task = Task(id = 7, uuid = "t", projectId = 1, title = "A", createdAt = now, updatedAt = now)
        val other = Task(id = 8, uuid = "u", projectId = 1, title = "B", createdAt = now, updatedAt = now)

        val yaml = YamlExporter.export(
            project,
            listOf(TaskNode(task), TaskNode(other)),
            commentsByTask = mapOf(7L to listOf("一つ目", "二つ目")),
        )
        assertTrue(yaml.contains("comments:"))
        assertFalse(yaml.contains("description:"))

        val parsed = (YamlImporter.parse(yaml) as YamlParseResult.Success).project
        assertEquals(listOf("一つ目", "二つ目"), parsed.tasks[0].comments)
        assertEquals(emptyList<String>(), parsed.tasks[1].comments)
    }
}
