package com.example.yamlist.data

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.yamlist.data.file.pdf.ChecklistPdfGenerator
import com.example.yamlist.domain.model.Project
import com.example.yamlist.domain.model.Task
import java.io.File
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChecklistPdfGeneratorTest {
    @Test
    fun generate_writesReadablePortraitPdfWithTaskComment() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = File(context.cacheDir, "checklist-test.pdf")
        val timestamp = LocalDateTime.of(2025, 1, 1, 0, 0)
        val project = Project(
            id = 1, uuid = "project", title = "試験プロジェクト",
            createdAt = timestamp, updatedAt = timestamp,
        )
        val task = Task(
            id = 1, uuid = "task", projectId = project.id, title = "試験タスク",
            createdAt = timestamp, updatedAt = timestamp,
        )

        try {
            file.outputStream().use { output ->
                ChecklistPdfGenerator().generate(
                    project = project,
                    tasks = listOf(task),
                    commentsByTask = mapOf(task.id to listOf("試験コメント")),
                    options = ChecklistPdfGenerator.Options(includeComments = true),
                    out = output,
                )
            }

            assertTrue(file.length() > 100)
            val header = ByteArray(5)
            file.inputStream().use { assertEquals(header.size, it.read(header)) }
            assertEquals("%PDF-", String(header, Charsets.US_ASCII))
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    assertEquals(1, renderer.pageCount)
                    renderer.openPage(0).use { page ->
                        assertEquals(595, page.width)
                        assertEquals(842, page.height)
                    }
                }
            }
        } finally {
            file.delete()
        }
    }
}
