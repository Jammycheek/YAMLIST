package com.example.yamlist.data.file.yaml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the loose-outline -> strict-schema normalization used by the import flow.
 * Only structure (hierarchy + titles) is expected to survive; weight/status stay
 * at their defaults and are filled in later through the app UI.
 */
class StructureYamlConverterTest {

    private fun validate(root: Any?): YamlParseResult =
        YamlImporter.mapAndValidate(StructureYamlConverter.normalize(root))

    private fun success(root: Any?): ParsedProject {
        val r = validate(root)
        assertTrue("expected success but got $r", r is YamlParseResult.Success)
        return (r as YamlParseResult.Success).project
    }

    @Test
    fun `strict document is detected and passes through unchanged`() {
        val strict = mapOf(
            "schema_version" to 1,
            "project" to mapOf("title" to "P", "tasks" to listOf(mapOf("title" to "T"))),
        )
        assertTrue(StructureYamlConverter.looksStrict(strict))
        assertSame(strict, StructureYamlConverter.normalize(strict))
        val p = success(strict)
        assertEquals("P", p.title)
        assertEquals(1, p.taskCount())
    }

    private fun assertSame(a: Any?, b: Any?) = assertTrue(a === b)

    @Test
    fun `loose document is detected as not strict`() {
        val loose = mapOf("案件名" to "現場A", "階層" to mapOf("1階" to null))
        assertFalse(StructureYamlConverter.looksStrict(loose))
    }

    @Test
    fun `map keys become titles and nest`() {
        val p = success(
            mapOf(
                "案件名" to "現場A",
                "階層" to mapOf(
                    "1階" to mapOf(
                        "東" to listOf("AHU-01", "AHU-02"),
                    ),
                ),
            )
        )
        assertEquals("現場A", p.title)
        assertEquals(1, p.tasks.size)
        val floor = p.tasks[0]
        assertEquals("1階", floor.title)
        val east = floor.children[0]
        assertEquals("東", east.title)
        assertEquals(listOf("AHU-01", "AHU-02"), east.children.map { it.title })
        assertEquals(4, p.taskCount())   // 1階 + 東 + 2 leaves
        assertEquals(3, p.maxDepth())
    }

    @Test
    fun `null value yields a childless leaf`() {
        val p = success(
            mapOf("案件名" to "P", "階層" to mapOf("中央監視" to null))
        )
        assertEquals(1, p.taskCount())
        assertTrue(p.tasks[0].children.isEmpty())
        assertEquals("中央監視", p.tasks[0].title)
    }

    @Test
    fun `map inside a list is expanded at the same level`() {
        val p = success(
            mapOf(
                "案件名" to "P",
                "階層" to mapOf(
                    "空調機" to listOf(
                        "AHU-01",
                        mapOf("AHU-02" to listOf("VAV1", "VAV2")),
                    ),
                ),
            )
        )
        val ahu = p.tasks[0]
        assertEquals(listOf("AHU-01", "AHU-02"), ahu.children.map { it.title })
        assertEquals(listOf("VAV1", "VAV2"), ahu.children[1].children.map { it.title })
    }

    @Test
    fun `kind is folded into the description`() {
        val p = success(mapOf("案件名" to "P", "種別" to "工事", "階層" to mapOf("A" to null)))
        assertEquals("種別: 工事", p.description)
    }

    @Test
    fun `structure is taken from remaining keys when no tree key is present`() {
        // No 階層 / tasks key: everything that is not metadata is the structure.
        val p = success(
            mapOf(
                "案件名" to "P",
                "1階" to listOf("AHU-01"),
                "2階" to listOf("AHU-02"),
            )
        )
        assertEquals(listOf("1階", "2階"), p.tasks.map { it.title })
        assertEquals(4, p.taskCount())
    }

    @Test
    fun `missing title falls back to a placeholder rather than failing`() {
        val p = success(mapOf("階層" to mapOf("A" to null)))
        assertEquals("無題プロジェクト", p.title)
    }

    @Test
    fun `converted tasks carry default weight and todo status`() {
        val p = success(mapOf("案件名" to "P", "階層" to listOf("A")))
        val t = p.tasks[0]
        assertEquals(1.0, t.weight, 1e-9)
        assertEquals(com.example.yamlist.domain.model.TaskStatus.TODO, t.status)
        assertTrue(t.progressTarget)
    }

    @Test
    fun `blank titles are skipped`() {
        val p = success(mapOf("案件名" to "P", "階層" to listOf("A", "", "  ")))
        assertEquals(1, p.taskCount())
    }

    @Test
    fun `english outline gets english generated labels`() {
        val root = mapOf("kind" to "Construction", "hierarchy" to mapOf("1F" to listOf("CP-01")))
        val project = (StructureYamlConverter.normalize(root) as Map<*, *>)["project"] as Map<*, *>
        assertEquals("Untitled project", project["title"])
        assertEquals("Kind: Construction", project["description"])
    }

    @Test
    fun `japanese outline keeps japanese generated labels`() {
        val root = mapOf("種別" to "工事", "階層" to mapOf("1階" to listOf("CP-01")))
        val project = (StructureYamlConverter.normalize(root) as Map<*, *>)["project"] as Map<*, *>
        assertEquals("無題プロジェクト", project["title"])
        assertEquals("種別: 工事", project["description"])
    }
}
