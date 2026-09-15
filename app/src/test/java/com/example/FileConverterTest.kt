package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.DocumentType
import com.example.data.model.OfficeDocument
import com.example.data.model.SlideDeck
import com.example.data.model.SlideItem
import com.example.data.model.SlideLayout
import com.example.data.model.SpreadsheetGrid
import com.example.util.FileConverter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Robolectric is needed for the PDF tests (android.graphics.pdf.PdfDocument and
 * android.graphics.Paint are real Android framework classes).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FileConverterTest {

    private fun doc(type: DocumentType, content: String, title: String = "Test Doc") =
        OfficeDocument(title = title, type = type, content = content)

    // ------------------------------------------------------------------
    // Markdown
    // ------------------------------------------------------------------

    @Test
    fun `doc markdown preserves headings and formatting`() {
        val d = doc(DocumentType.DOC, "# Title\n\nSome **bold** and *italic* text\n- item one\n- item two")
        val md = FileConverter.toMarkdown(d)
        assertTrue(md.contains("# Title"))
        assertTrue(md.contains("**bold**"))
        assertTrue(md.contains("*italic*"))
        assertTrue(md.contains("- item one"))
    }

    @Test
    fun `spreadsheet markdown produces pipe table with header separator`() {
        val grid = SpreadsheetGrid()
        grid.setCell("A1", "Name")
        grid.setCell("B1", "Qty")
        grid.setCell("A2", "Widget")
        grid.setCell("B2", "5")
        val md = FileConverter.toMarkdown(doc(DocumentType.XLS, grid.serialize()))
        assertTrue(md.contains("| Name | Qty |"))
        assertTrue(md.contains("---"))
        assertTrue(md.contains("| Widget | 5 |"))
    }

    @Test
    fun `slides markdown numbers each slide`() {
        val deck = SlideDeck(
            slides = mutableListOf(
                SlideItem(layout = SlideLayout.TITLE_SLIDE, title = "Intro", content = ""),
                SlideItem(title = "Details", content = "• point a\n• point b")
            )
        )
        val md = FileConverter.toMarkdown(doc(DocumentType.PPT, deck.serialize()))
        assertTrue(md.startsWith("# Test Doc"))
        assertTrue(md.contains("## 1. Intro"))
        assertTrue(md.contains("## 2. Details"))
        assertTrue(md.contains("• point a"))
    }

    // ------------------------------------------------------------------
    // Plain text
    // ------------------------------------------------------------------

    @Test
    fun `doc plain text strips markdown syntax`() {
        val d = doc(DocumentType.DOC, "# Heading\n**bold** and `code`\n- bullet")
        val txt = FileConverter.toPlainText(d)
        assertFalse(txt.contains("#"))
        assertFalse(txt.contains("**"))
        assertFalse(txt.contains("`"))
        assertTrue(txt.contains("Heading"))
        assertTrue(txt.contains("bold"))
        assertTrue(txt.contains("• bullet"))
    }

    // ------------------------------------------------------------------
    // CSV
    // ------------------------------------------------------------------

    @Test
    fun `spreadsheet csv evaluates formulas and escapes commas`() {
        val grid = SpreadsheetGrid()
        grid.setCell("A1", "Item")
        grid.setCell("B1", "Total")
        grid.setCell("A2", "A,B")
        grid.setCell("B2", "=1+2")
        val csv = FileConverter.toCsv(doc(DocumentType.XLS, grid.serialize()))
        val lines = csv.trim().lines()
        assertEquals("Item,Total", lines[0])
        assertTrue(lines[1].startsWith("\"A,B\""))
        assertTrue(lines[1].endsWith("3"))
    }

    @Test
    fun `non-spreadsheet csv is one column of lines`() {
        val csv = FileConverter.toCsv(doc(DocumentType.DOC, "line one\nline two"))
        assertEquals("line one,line two", csv.trim())
    }

    // ------------------------------------------------------------------
    // HTML
    // ------------------------------------------------------------------

    @Test
    fun `doc html escapes and inlines markdown`() {
        val d = doc(DocumentType.DOC, "# Heading <tag>\n**bold** text")
        val html = FileConverter.toHtml(d)
        assertTrue(html.startsWith("<!DOCTYPE html>"))
        assertTrue(html.contains("<h1>"))
        assertTrue(html.contains("&lt;tag&gt;"))
        assertTrue(html.contains("<b>bold</b>"))
    }

    @Test
    fun `spreadsheet html renders table rows`() {
        val grid = SpreadsheetGrid()
        grid.setCell("A1", "H")
        grid.setCell("B1", "<x>")
        val html = FileConverter.toHtml(doc(DocumentType.XLS, grid.serialize()))
        assertTrue(html.contains("<table>"))
        assertTrue(html.contains("<td>H</td>"))
        assertTrue(html.contains("&lt;x&gt;"))
    }

    @Test
    fun `slides html renders slide sections`() {
        val deck = SlideDeck(slides = mutableListOf(SlideItem(title = "S1", content = "body")))
        val html = FileConverter.toHtml(doc(DocumentType.PPT, deck.serialize()))
        assertTrue(html.contains("S1"))
        assertTrue(html.contains("body"))
        assertTrue(html.contains("class=\"slide\""))
    }

    // ------------------------------------------------------------------
    // PDF layout (byte-level PdfDocument output needs a real device;
    // Robolectric's shadow cannot execute the native PDF writer on the JVM)
    // ------------------------------------------------------------------

    @Test
    fun `pdf line layout marks headings for larger rendering`() {
        val d = doc(DocumentType.DOC, "# Report\n\nBody paragraph.")
        val lines = FileConverter.pdfLines(d)
        assertTrue(lines.contains("##TITLE##Report"))
        assertTrue(lines.contains("Body paragraph."))
    }

    @Test
    fun `pdf line layout flattens spreadsheet rows`() {
        val grid = SpreadsheetGrid()
        grid.setCell("A1", "X")
        grid.setCell("B1", "42")
        val lines = FileConverter.pdfLines(doc(DocumentType.XLS, grid.serialize()))
        assertEquals(1, lines.size)
        assertTrue(lines[0].contains("X"))
        assertTrue(lines[0].contains("42"))
    }

    // ------------------------------------------------------------------
    // Format selection
    // ------------------------------------------------------------------

    @Test
    fun `supported formats match document type`() {
        assertTrue(FileConverter.supportedFormats(DocumentType.XLS).contains(FileConverter.ExportFormat.CSV))
        assertTrue(FileConverter.supportedFormats(DocumentType.PPT).contains(FileConverter.ExportFormat.MARKDOWN))
        assertTrue(FileConverter.supportedFormats(DocumentType.DOC).contains(FileConverter.ExportFormat.HTML))
        assertEquals(FileConverter.ExportFormat.CSV, FileConverter.defaultFormat(DocumentType.XLS))
    }

    @Test
    fun `share file name uses title and extension`() {
        val name = FileConverter.shareFileName(
            doc(DocumentType.DOC, "x", title = "My Report"),
            FileConverter.ExportFormat.MARKDOWN
        )
        assertEquals("My Report.md", name)
    }

    // ------------------------------------------------------------------
    // File round trip
    // ------------------------------------------------------------------

    @Test
    fun `convertToFile writes export file under filesDir-exports`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val d = doc(DocumentType.DOC, "# Hello\nWorld", title = "Round Trip")
        val file = FileConverter.convertToFile(context, d, FileConverter.ExportFormat.MARKDOWN)
        try {
            assertTrue(file.exists())
            assertTrue(file.absolutePath.contains("exports"))
            assertTrue(file.name.endsWith(".md"))
            assertTrue(String(file.readBytes(), Charsets.UTF_8).contains("# Hello"))
        } finally {
            file.delete()
        }
    }
}
