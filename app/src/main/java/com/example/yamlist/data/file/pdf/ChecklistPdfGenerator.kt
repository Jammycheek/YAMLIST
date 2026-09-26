package com.example.yamlist.data.file.pdf

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.example.yamlist.domain.model.Project
import com.example.yamlist.domain.model.TaskNode
import com.example.yamlist.domain.model.TaskStatus
import com.example.yamlist.domain.progress.TaskTreeBuilder
import java.io.OutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Renders a project's task forest into a printable checklist PDF (spec §16).
 *
 * This is a real, layout-aware renderer, not a screenshot of the app (spec §27.5):
 *  - hierarchical numbering (1, 1.1, 1.1.1 ...) and indentation,
 *  - a checkbox per task and an optional handwriting "備考" line,
 *  - page breaks that never split a task from its note or orphan a heading (§16.3),
 *  - project name + page number on every page.
 */
class ChecklistPdfGenerator {

    data class Options(
        val includeDone: Boolean = true,
        val includeComments: Boolean = true,
        val includeWeight: Boolean = false,
        val includeDueDate: Boolean = false,
        val includeHierarchyNumber: Boolean = true,
        val noteLineWidth: Int = 260,     // width of the "備考：____" rule
        val landscape: Boolean = false,
        val doneStyle: DoneStyle = DoneStyle.CHECKED,
        val title: String? = null,
        val showCreatedDate: Boolean = true,
    )

    enum class DoneStyle { CHECKED, STRIKETHROUGH, STATUS_TEXT, SAME_AS_TODO }

    private data class Line(
        val number: String,
        val title: String,
        val depth: Int,
        val done: Boolean,
        val notes: List<String>, // task comments shown under the row, one per line
        val meta: String?,      // weight / due date suffix
        val isHeading: Boolean, // parent (has children)
    )

    fun generate(
        project: Project,
        tasks: List<com.example.yamlist.domain.model.Task>,
        commentsByTask: Map<Long, List<String>>,
        options: Options,
        out: OutputStream,
    ) {
        val forest = TaskTreeBuilder.build(tasks)
        val lines = buildLines(forest, commentsByTask, options)
        renderPdf(project, lines, options, out)
    }

    private fun buildLines(
        forest: List<TaskNode>,
        commentsByTask: Map<Long, List<String>>,
        o: Options,
    ): List<Line> {
        val out = ArrayList<Line>()
        fun walk(node: TaskNode, prefix: String, index: Int, depth: Int) {
            val number = if (prefix.isEmpty()) "${index + 1}" else "$prefix.${index + 1}"
            val t = node.task
            val done = t.status == TaskStatus.DONE
            val isHeading = node.children.isNotEmpty()
            val visible = o.includeDone || !done || isHeading
            if (visible) {
                val meta = buildString {
                    if (o.includeWeight && t.isProgressTarget && t.weight != 1.0) append("重み${trimNum(t.weight)} ")
                    if (o.includeDueDate && t.dueDate != null) append(t.dueDate.format(DATE))
                }.trim().ifBlank { null }
                out.add(
                    Line(
                        number = if (o.includeHierarchyNumber) number else "",
                        title = t.title,
                        depth = depth,
                        done = done,
                        notes = if (o.includeComments) commentsByTask[t.id].orEmpty() else emptyList(),
                        meta = meta,
                        isHeading = isHeading,
                    )
                )
            }
            node.children.forEachIndexed { i, child -> walk(child, number, i, depth + 1) }
        }
        forest.forEachIndexed { i, root -> walk(root, "", i, 0) }
        return out
    }

    private fun renderPdf(project: Project, lines: List<Line>, o: Options, out: OutputStream) {
        // A4 @ 72dpi points: portrait 595x842, landscape 842x595.
        val pageW = if (o.landscape) 842 else 595
        val pageH = if (o.landscape) 595 else 842
        val marginX = 40f
        val marginTop = 56f
        val marginBottom = 48f
        val indentStep = 18f
        val rowHeight = 26f
        val noteHeight = 22f
        val noteLineHeight = 14f

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 16f; isFakeBoldText = true }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.DKGRAY; textSize = 10f }
        val headingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 12f; isFakeBoldText = true }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 11f }
        val strikePaint = Paint(textPaint).apply { isStrikeThruText = true; color = Color.DKGRAY }
        val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.GRAY; textSize = 10f }
        val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 1f }
        val rulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.LTGRAY; strokeWidth = 1f }

        val pdf = PdfDocument()
        var pageNo = 0
        lateinit var page: PdfDocument.Page
        lateinit var canvas: Canvas
        var y = 0f

        fun startPage() {
            pageNo++
            val info = PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create()
            page = pdf.startPage(info)
            canvas = page.canvas
            // Header: project name + page number on every page (§16.3).
            val heading = o.title ?: "${project.title}　チェックシート"
            canvas.drawText(heading, marginX, marginTop - 24, titlePaint)
            if (o.showCreatedDate) {
                canvas.drawText("作成日：${LocalDate.now().format(DATE_JP)}", marginX, marginTop - 8, subPaint)
            }
            canvas.drawText("Page $pageNo", pageW - marginX - 50, marginTop - 8, subPaint)
            canvas.drawLine(marginX, marginTop + 2, pageW - marginX, marginTop + 2, rulePaint)
            y = marginTop + 20
        }

        startPage()

        for (line in lines) {
            val x = marginX + line.depth * indentStep
            val noteX = x + 20
            // Each comment starts on its own line and wraps at the right margin, so
            // long or many comments never run off the page.
            val noteLines = wrapNotes(line.notes, notePaint, pageW - marginX - noteX)
            val needsNote = noteLines.isNotEmpty() || (!line.isHeading && o.noteLineWidth > 0)
            val noteBlock = if (noteLines.size > 1) noteHeight + (noteLines.size - 1) * noteLineHeight else noteHeight
            val blockHeight = rowHeight + (if (needsNote) noteBlock else 0f)
            // Page-break guard: keep a row and its note together (§16.3). Never break
            // on an empty page, or an over-tall block would loop onto blank pages.
            if (y + blockHeight > pageH - marginBottom && y > marginTop + 20) {
                pdf.finishPage(page)
                startPage()
            }
            if (line.isHeading) {
                val label = listOfNotNull(line.number.ifBlank { null }, line.title).joinToString(" ")
                canvas.drawText("□ $label", x, y, headingPaint)
            } else {
                val boxSize = 11f
                canvas.drawRect(x, y - boxSize, x + boxSize, y, boxPaint)
                if (line.done && o.doneStyle == DoneStyle.CHECKED) {
                    canvas.drawLine(x + 1, y - 5, x + 4, y - 1, boxPaint)
                    canvas.drawLine(x + 4, y - 1, x + 10, y - 10, boxPaint)
                }
                val label = listOfNotNull(
                    line.number.ifBlank { null },
                    line.title,
                    line.meta?.let { "（$it）" },
                    if (line.done && o.doneStyle == DoneStyle.STATUS_TEXT) "[完了]" else null,
                ).joinToString(" ")
                val paint = if (line.done && o.doneStyle == DoneStyle.STRIKETHROUGH) strikePaint else textPaint
                canvas.drawText(label, x + boxSize + 6, y, paint)
            }
            y += rowHeight

            if (needsNote) {
                if (noteLines.isNotEmpty()) {
                    noteLines.forEachIndexed { i, text ->
                        canvas.drawText(text, noteX, y + i * noteLineHeight, notePaint)
                    }
                } else {
                    canvas.drawText("備考：", noteX, y, notePaint)
                    val lineStartX = noteX + 34
                    canvas.drawLine(lineStartX, y, lineStartX + o.noteLineWidth, y, rulePaint)
                }
                y += noteBlock
            }
        }

        pdf.finishPage(page)
        pdf.writeTo(out)
        pdf.close()
    }

    /**
     * "備考：" before the first comment, full-width indent before the others and
     * before wrapped continuations, capped at [MAX_NOTE_LINES] lines per task.
     */
    private fun wrapNotes(notes: List<String>, paint: Paint, maxWidth: Float): List<String> {
        val lines = ArrayList<Pair<String, String>>() // prefix to body
        var truncated = false
        notes@ for ((i, note) in notes.withIndex()) {
            var prefix = if (i == 0) NOTE_LABEL else NOTE_INDENT
            var rest = note.replace('\n', ' ')
            while (rest.isNotEmpty()) {
                if (lines.size == MAX_NOTE_LINES) {
                    truncated = true
                    break@notes
                }
                val take = fitLength(prefix, rest, paint, maxWidth)
                lines.add(prefix to rest.substring(0, take))
                rest = rest.substring(take)
                prefix = NOTE_INDENT
            }
        }
        if (truncated) {
            // Re-fit the last line so the ellipsis stays inside the margin too.
            val (prefix, body) = lines.removeAt(lines.lastIndex)
            val keep = (fitLength(prefix, body + ELLIPSIS, paint, maxWidth) - ELLIPSIS.length).coerceAtLeast(0)
            lines.add(prefix to (body.take(keep) + ELLIPSIS))
        }
        return lines.map { (prefix, body) -> prefix + body }
    }

    /** How many chars of [text] fit after [prefix] within [maxWidth]; at least one, never splitting a surrogate pair. */
    private fun fitLength(prefix: String, text: String, paint: Paint, maxWidth: Float): Int {
        val fit = paint.breakText(prefix + text, true, maxWidth, null) - prefix.length
        var take = fit.coerceIn(1, text.length)
        if (take < text.length && text[take - 1].isHighSurrogate()) {
            take = if (take > 1) take - 1 else take + 1
        }
        return take
    }

    private fun trimNum(d: Double): String =
        if (d % 1.0 == 0.0) d.toLong().toString() else d.toString()

    companion object {
        private val DATE = DateTimeFormatter.ofPattern("yyyy/MM/dd")
        private val DATE_JP = DateTimeFormatter.ofPattern("yyyy年M月d日")
        private const val NOTE_LABEL = "備考："
        private const val NOTE_INDENT = "　　　"
        private const val MAX_NOTE_LINES = 12
        private const val ELLIPSIS = "…"
    }
}
