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

    private fun yamlWithTask(taskYaml: String): String =
        "schema_version: 1\nproject:\n  title: P\n  tasks:\n" +
            taskYaml.trimIndent().lines().joinToString("\n") { "    $it" }

    private fun parseSingleTask(taskYaml: String): ParsedTask {
        val r = YamlImporter.parse(yamlWithTask(taskYaml))
        assertTrue("expected success but got $r", r is YamlParseResult.Success)
        return (r as YamlParseResult.Success).project.tasks.single()
    }

    private fun texts(t: ParsedTask) = t.comments.map { it.text }

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
        assertEquals(listOf("最新版Rev.4を使用", "2行目: コロン入り"), texts(t))
        assertTrue(t.comments.none { it.struck })
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
        assertEquals(listOf("説明文", "固定メモ", "追加"), texts(t))
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
        assertEquals(listOf("x"), texts(t))
    }

    @Test
    fun `unquoted date comment keeps its written form`() {
        val t = parseSingleTask(
            """
            - title: A
              comments:
                - 2024-01-01
            """
        )
        assertEquals(listOf("2024-01-01"), texts(t))
    }

    @Test
    fun `unquoted colon in a comment is an error, not a silent drop`() {
        val r = YamlImporter.parse(
            yamlWithTask(
                """
                - title: A
                  comments:
                    - 注意: Rev.4を使用
                """
            )
        )
        assertTrue(r is YamlParseResult.Failure)
        assertTrue((r as YamlParseResult.Failure).errors.any { it.locator.contains("comments[#1]") })
    }

    @Test
    fun `struck comment map is imported`() {
        val t = parseSingleTask(
            """
            - title: A
              comments:
                - 有効
                - text: 取消済み
                  struck: true
            """
        )
        assertEquals(listOf(YamlComment("有効"), YamlComment("取消済み", struck = true)), t.comments)
    }

    @Test
    fun `comment map with unknown keys or non boolean struck fails`() {
        for (item in listOf(mapOf("text" to "a", "extra" to 1), mapOf("text" to "a", "struck" to "yes"))) {
            val r = YamlImporter.mapAndValidate(
                mapOf(
                    "schema_version" to 1,
                    "project" to mapOf(
                        "title" to "P",
                        "tasks" to listOf(mapOf("title" to "A", "comments" to listOf(item))),
                    ),
                )
            )
            assertTrue("expected failure for $item", r is YamlParseResult.Failure)
        }
    }

    @Test
    fun `export writes comments, including struck ones, and they survive a round trip`() {
        val now = LocalDateTime.of(2026, 1, 1, 0, 0)
        val project = Project(uuid = "p", title = "P", createdAt = now, updatedAt = now)
        val task = Task(id = 7, uuid = "t", projectId = 1, title = "A", createdAt = now, updatedAt = now)
        val other = Task(id = 8, uuid = "u", projectId = 1, title = "B", createdAt = now, updatedAt = now)
        val comments = listOf(YamlComment("一つ目"), YamlComment("注意: 取消", struck = true))

        val yaml = YamlExporter.export(
            project,
            listOf(TaskNode(task), TaskNode(other)),
            commentsByTask = mapOf(7L to comments),
        )
        assertTrue(yaml.contains("comments:"))
        assertFalse(yaml.contains("description:"))

        val parsed = (YamlImporter.parse(yaml) as YamlParseResult.Success).project
        assertEquals(comments, parsed.tasks[0].comments)
        assertEquals(emptyList<YamlComment>(), parsed.tasks[1].comments)
    }
}
