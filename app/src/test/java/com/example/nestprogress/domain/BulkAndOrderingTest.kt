package com.example.nestprogress.domain

import com.example.nestprogress.domain.bulk.BulkTitleParser
import com.example.nestprogress.domain.bulk.BulkTitleParser.Problem
import com.example.nestprogress.domain.progress.TaskOrdering
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Covers the line-handling rules of spec §1.4 and the limits of spec §1.8. */
class BulkTitleParserTest {

    @Test
    fun `each line becomes one title`() {
        val r = BulkTitleParser.parse("図面確認\nI/Oリスト確認\n盤内機器確認")
        assertEquals(listOf("図面確認", "I/Oリスト確認", "盤内機器確認"), r.titles)
        assertTrue(r.isValid)
    }

    @Test
    fun `spec example with bullets and a blank line yields three`() {
        val r = BulkTitleParser.parse("- 図面確認\n- I/Oリスト確認\n\n- 試験要領書作成")
        assertEquals(listOf("図面確認", "I/Oリスト確認", "試験要領書作成"), r.titles)
    }

    @Test
    fun `blank and whitespace-only lines are ignored`() {
        assertEquals(listOf("A", "B"), BulkTitleParser.parse("A\n\n \t\nB").titles)
    }

    @Test
    fun `ideographic space only line counts as blank`() {
        assertEquals(listOf("A", "B"), BulkTitleParser.parse("A\n\u3000\u3000\nB").titles)
    }

    @Test
    fun `surrounding whitespace is trimmed`() {
        assertEquals(listOf("A", "B"), BulkTitleParser.parse("  A  \n\tB\t").titles)
    }

    @Test
    fun `nakaguro bullet is stripped`() {
        assertEquals(listOf("図面確認"), BulkTitleParser.parse("・図面確認").titles)
    }

    @Test
    fun `bullets are kept when stripping is disabled`() {
        assertEquals(listOf("- A"), BulkTitleParser.parse("- A", stripBullets = false).titles)
    }

    @Test
    fun `duplicate titles are allowed`() {
        assertEquals(listOf("A", "A"), BulkTitleParser.parse("A\nA").titles)
    }

    @Test
    fun `no usable line reports empty`() {
        val r = BulkTitleParser.parse("\n  \n\u3000")
        assertFalse(r.isValid)
        assertTrue(r.problems.any { it is Problem.Empty })
    }

    @Test
    fun `title over 200 characters is rejected with its line number`() {
        val r = BulkTitleParser.parse("ok\n" + "A".repeat(201))
        val problem = r.problems.filterIsInstance<Problem.TooLong>().single()
        assertEquals(2, problem.lineNumber)
        assertEquals(listOf("ok"), r.titles)
    }

    @Test
    fun `exactly 200 characters is accepted`() {
        assertTrue(BulkTitleParser.parse("A".repeat(200)).isValid)
    }

    @Test
    fun `over 100 titles is rejected`() {
        val r = BulkTitleParser.parse((1..101).joinToString("\n") { "T$it" })
        assertTrue(r.problems.any { it is Problem.TooMany })
    }

    @Test
    fun `exactly 100 titles is accepted`() {
        assertTrue(BulkTitleParser.parse((1..100).joinToString("\n") { "T$it" }).isValid)
    }
}

/** Covers the 1000-step allocation policy of spec §2.5–§2.8. */
class TaskOrderingTest {

    @Test
    fun `first task in an empty level gets one step`() {
        assertEquals(1000L, TaskOrdering.nextOrder(-1L))
    }

    @Test
    fun `next order continues after the current maximum`() {
        assertEquals(3000L, TaskOrdering.nextOrder(2000L))
    }

    @Test
    fun `bulk append continues the sequence`() {
        assertEquals(listOf(3000L, 4000L, 5000L), TaskOrdering.appendOrders(2000L, 3))
    }

    @Test
    fun `bulk append on an empty level starts at one step`() {
        assertEquals(listOf(1000L, 2000L), TaskOrdering.appendOrders(-1L, 2))
    }

    @Test
    fun `renumber respaces a level to 1000 steps in the given order`() {
        assertEquals(
            listOf(7L to 1000L, 3L to 2000L, 5L to 3000L),
            TaskOrdering.renumber(listOf(7L, 3L, 5L)),
        )
    }
}
