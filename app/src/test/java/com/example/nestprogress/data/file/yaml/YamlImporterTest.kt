package com.example.nestprogress.data.file.yaml

import com.example.nestprogress.domain.model.ProgressMode
import com.example.nestprogress.domain.model.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Validation coverage for spec §15. Most cases drive [YamlImporter.mapAndValidate]
 * with hand-built object graphs so they are independent of the SnakeYAML parser;
 * a couple exercise the full [YamlImporter.parse] text path.
 */
class YamlImporterTest {

    private fun root(
        schema: Any? = 1,
        project: Map<String, Any?>? = mapOf(
            "title" to "P",
            "progress_mode" to "both",
            "tasks" to emptyList<Any?>(),
        ),
    ): Map<String, Any?> = buildMap {
        put("schema_version", schema)
        if (project != null) put("project", project)
    }

    private fun errorsOf(r: YamlParseResult): List<YamlError> =
        (r as? YamlParseResult.Failure)?.errors ?: emptyList()

    @Test
    fun `minimal valid project succeeds`() {
        val r = YamlImporter.mapAndValidate(root())
        assertTrue(r is YamlParseResult.Success)
        val p = (r as YamlParseResult.Success).project
        assertEquals("P", p.title)
        assertEquals(ProgressMode.BOTH, p.progressMode)
    }

    @Test
    fun `missing schema_version fails`() {
        val r = YamlImporter.mapAndValidate(root(schema = null))
        assertTrue(errorsOf(r).any { it.locator == "schema_version" })
    }

    @Test
    fun `unsupported schema_version fails`() {
        val r = YamlImporter.mapAndValidate(root(schema = 2))
        assertTrue(errorsOf(r).any { it.locator == "schema_version" })
    }

    @Test
    fun `missing project fails`() {
        val r = YamlImporter.mapAndValidate(root(project = null))
        assertTrue(errorsOf(r).any { it.locator == "project" })
    }

    @Test
    fun `blank project title fails`() {
        val r = YamlImporter.mapAndValidate(
            root(project = mapOf("title" to "  ", "tasks" to emptyList<Any?>()))
        )
        assertTrue(errorsOf(r).any { it.locator == "project.title" })
    }

    @Test
    fun `invalid progress_mode fails`() {
        val r = YamlImporter.mapAndValidate(
            root(project = mapOf("title" to "P", "progress_mode" to "xyz", "tasks" to emptyList<Any?>()))
        )
        assertTrue(errorsOf(r).any { it.locator == "project.progress_mode" })
    }

    @Test
    fun `invalid task status fails`() {
        val r = YamlImporter.mapAndValidate(
            root(project = mapOf(
                "title" to "P",
                "tasks" to listOf(mapOf("title" to "T", "status" to "sideways")),
            ))
        )
        assertTrue(errorsOf(r).any { it.message.contains("status") })
    }

    @Test
    fun `negative weight fails`() {
        val r = YamlImporter.mapAndValidate(
            root(project = mapOf(
                "title" to "P",
                "tasks" to listOf(mapOf("title" to "T", "weight" to -1)),
            ))
        )
        assertTrue(errorsOf(r).any { it.message.contains("weight") })
    }

    @Test
    fun `bad planned_month format fails`() {
        val r = YamlImporter.mapAndValidate(
            root(project = mapOf(
                "title" to "P",
                "tasks" to listOf(mapOf("title" to "T", "planned_month" to "2025/01")),
            ))
        )
        assertTrue(errorsOf(r).any { it.message.contains("planned_month") })
    }

    @Test
    fun `duplicate uuid fails`() {
        val r = YamlImporter.mapAndValidate(
            root(project = mapOf(
                "title" to "P",
                "tasks" to listOf(
                    mapOf("title" to "A", "uuid" to "dup"),
                    mapOf("title" to "B", "uuid" to "dup"),
                ),
            ))
        )
        assertTrue(errorsOf(r).any { it.message.contains("UUID") })
    }

    @Test
    fun `nested tasks parse and count`() {
        val r = YamlImporter.mapAndValidate(
            root(project = mapOf(
                "title" to "P",
                "tasks" to listOf(
                    mapOf(
                        "title" to "parent",
                        "tasks" to listOf(
                            mapOf("title" to "child", "status" to "done", "weight" to 2),
                        ),
                    ),
                ),
            ))
        )
        assertTrue(r is YamlParseResult.Success)
        val p = (r as YamlParseResult.Success).project
        assertEquals(2, p.taskCount())
        assertEquals(2, p.maxDepth())
        val child = p.tasks[0].children[0]
        assertEquals(TaskStatus.DONE, child.status)
        assertEquals(2.0, child.weight, 1e-9)
    }

    @Test
    fun `full parse of yaml text succeeds`() {
        val text = """
            schema_version: 1
            project:
              title: "現場A"
              progress_mode: count
              tasks:
                - title: "準備"
                  tasks:
                    - title: "図面確認"
                      status: done
                    - title: "工程確認"
                      status: todo
        """.trimIndent()
        val r = YamlImporter.parse(text)
        assertTrue(r is YamlParseResult.Success)
        val p = (r as YamlParseResult.Success).project
        assertEquals("現場A", p.title)
        assertEquals(ProgressMode.COUNT, p.progressMode)
        assertEquals(3, p.taskCount())
    }

    @Test
    fun `explicit order is parsed`() {
        val r = YamlImporter.mapAndValidate(
            root(project = mapOf("title" to "P", "tasks" to listOf(mapOf("title" to "A", "order" to 5000))))
        )
        assertEquals(5000L, (r as YamlParseResult.Success).project.tasks[0].order)
    }

    @Test
    fun `order is null when the key is absent`() {
        val r = YamlImporter.mapAndValidate(
            root(project = mapOf("title" to "P", "tasks" to listOf(mapOf("title" to "A"))))
        )
        assertNull((r as YamlParseResult.Success).project.tasks[0].order)
    }

    @Test
    fun `negative order fails`() {
        val r = YamlImporter.mapAndValidate(
            root(project = mapOf("title" to "P", "tasks" to listOf(mapOf("title" to "A", "order" to -1))))
        )
        assertTrue(errorsOf(r).any { it.message.contains("order") })
    }

    @Test
    fun `non integer order fails`() {
        val r = YamlImporter.mapAndValidate(
            root(project = mapOf("title" to "P", "tasks" to listOf(mapOf("title" to "A", "order" to "abc"))))
        )
        assertTrue(errorsOf(r).any { it.message.contains("order") })
    }

    @Test
    fun `malformed yaml text returns failure not exception`() {
        val text = "schema_version: 1\nproject: : : oops"
        val r = YamlImporter.parse(text)
        assertTrue(r is YamlParseResult.Failure)
    }
}
