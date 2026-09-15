package com.example.util

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.TextPaint
import com.example.data.model.CellCoordinate
import com.example.data.model.DocumentType
import com.example.data.model.OfficeDocument
import com.example.data.model.SlideDeck
import com.example.data.model.SpreadsheetGrid
import java.io.File

/**
 * Converts PixDocx documents between file formats.
 *
 * Everything runs on-device with platform APIs only (no third-party libs),
 * mirroring the dependency-free philosophy of [FileImporter].
 *
 * Supported conversions (from the internal DOC/XLS/PPT/PDF model):
 * - DOC/any  -> .txt   plain text
 * - DOC/any  -> .md    markdown (headings, bold/italic, lists preserved)
 * - DOC      -> .html  styled HTML
 * - XLS      -> .csv   evaluated cell values
 * - XLS      -> .html  HTML table
 * - PPT      -> .md    one `#` section per slide
 * - any      -> .pdf   paginated via android.graphics.pdf.PdfDocument
 */
object FileConverter {

    enum class ExportFormat(val extension: String, val mimeType: String, val label: String) {
        TXT("txt", "text/plain", "Plain text"),
        MARKDOWN("md", "text/markdown", "Markdown"),
        CSV("csv", "text/csv", "CSV"),
        HTML("html", "text/html", "HTML"),
        PDF("pdf", "application/pdf", "PDF")
    }

    /** Formats offered in the export dialog for a given document type. */
    fun supportedFormats(type: DocumentType): List<ExportFormat> = when (type) {
        DocumentType.XLS -> listOf(ExportFormat.CSV, ExportFormat.TXT, ExportFormat.PDF)
        DocumentType.PPT -> listOf(ExportFormat.MARKDOWN, ExportFormat.TXT, ExportFormat.PDF)
        DocumentType.PDF -> listOf(ExportFormat.TXT, ExportFormat.MARKDOWN, ExportFormat.HTML, ExportFormat.PDF)
        DocumentType.DOC -> listOf(ExportFormat.MARKDOWN, ExportFormat.TXT, ExportFormat.HTML, ExportFormat.PDF)
    }

    /** Default format for a document type (first entry of [supportedFormats]). */
    fun defaultFormat(type: DocumentType): ExportFormat = supportedFormats(type).first()

    /**
     * Converts [document] to [format] and writes the result to
     * `<filesDir>/exports/<timestamp>_<title>.<ext>`. Returns the written file.
     */
    fun convertToFile(context: Context, document: OfficeDocument, format: ExportFormat): File {
        val dir = File(context.filesDir, "exports").apply { mkdirs() }
        val safeTitle = document.title.replace(Regex("[^A-Za-z0-9._-]"), "_").take(60)
        val file = File(dir, "${System.currentTimeMillis()}_$safeTitle.${format.extension}")
        file.writeBytes(convertToBytes(document, format))
        return file
    }

    /** In-memory conversion — used by tests and by [convertToFile]. */
    fun convertToBytes(document: OfficeDocument, format: ExportFormat): ByteArray = when (format) {
        ExportFormat.TXT -> toPlainText(document).toByteArray(Charsets.UTF_8)
        ExportFormat.MARKDOWN -> toMarkdown(document).toByteArray(Charsets.UTF_8)
        ExportFormat.CSV -> toCsv(document).toByteArray(Charsets.UTF_8)
        ExportFormat.HTML -> toHtml(document).toByteArray(Charsets.UTF_8)
        ExportFormat.PDF -> toPdfBytes(document)
    }

    /** Shareable file name, e.g. "Budget Report.md". */
    fun shareFileName(document: OfficeDocument, format: ExportFormat): String =
        "${document.title.take(60)}.${format.extension}"

    // ------------------------------------------------------------------
    // Plain text
    // ------------------------------------------------------------------

    fun toPlainText(document: OfficeDocument): String = when (document.type) {
        DocumentType.XLS -> SpreadsheetConverter.toText(document)
        DocumentType.PPT -> SlideConverter.toText(document)
        DocumentType.PDF -> pdfFriendlyText(document)
        else -> stripMarkdown(document.content)
    }

    /**
     * PDFs imported with embedded-font encodings stored mojibake in the DB.
     * Detect it at conversion time (covers already-imported docs) and return
     * an honest notice instead of garbage bytes.
     */
    private fun pdfFriendlyText(document: OfficeDocument): String {
        val clean = stripMarkdown(document.content)
        return if (FileImporter.pdfTextLooksGarbled(clean)) {
            "\u26a0 This PDF uses embedded font encodings that PixDocx cannot " +
                "convert to selectable text. Share it as PDF to send the original file, " +
                "or open it in the built-in viewer for a faithful rendering."
        } else clean
    }

    // ------------------------------------------------------------------
    // Markdown
    // ------------------------------------------------------------------

    fun toMarkdown(document: OfficeDocument): String = when (document.type) {
        DocumentType.XLS -> SpreadsheetConverter.toMarkdown(document)
        DocumentType.PPT -> SlideConverter.toMarkdown(document)
        DocumentType.PDF -> {
            val md = normalizeMarkdown(document.content)
            if (FileImporter.pdfTextLooksGarbled(md)) {
                "> \u26a0 This PDF uses embedded font encodings. Share it as PDF to send the original file."
            } else md
        }
        else -> normalizeMarkdown(document.content)
    }

    // ------------------------------------------------------------------
    // CSV
    // ------------------------------------------------------------------

    fun toCsv(document: OfficeDocument): String = when (document.type) {
        DocumentType.XLS -> SpreadsheetGrid.deserialize(
            document.content, maxCols = gridCols(document), maxRows = gridRows(document)
        ).toCsv()
        else -> document.content.lines().joinToString(",") { csvEscape(it.trim()) }
    }

    // ------------------------------------------------------------------
    // HTML
    // ------------------------------------------------------------------

    fun toHtml(document: OfficeDocument): String = when (document.type) {
        DocumentType.XLS -> SpreadsheetConverter.toHtml(document)
        DocumentType.PPT -> SlideConverter.toHtml(document)
        DocumentType.PDF -> {
            if (FileImporter.pdfTextLooksGarbled(document.content)) {
                DocConverter.toHtml(
                    document.copy(
                        content = "\u26a0 This PDF uses embedded font encodings. " +
                            "Share it as PDF to send the original file."
                    )
                )
            } else DocConverter.toHtml(document)
        }
        else -> DocConverter.toHtml(document)
    }

    // ------------------------------------------------------------------
    // PDF (android.graphics.pdf.PdfDocument)
    // ------------------------------------------------------------------

    fun toPdfBytes(document: OfficeDocument): ByteArray {
        val pageWidth = 595 // A4 @ 72dpi
        val pageHeight = 842
        val margin = 40f
        val contentWidth = pageWidth - 2 * margin

        val lines = pdfLines(document)

        val titlePaint = TextPaint().apply {
            color = android.graphics.Color.BLACK
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK
            textSize = 11f
        }
        val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.GRAY
            textSize = 9f
        }

        // Static layout would be nicer but plain canvas text keeps this
        // dependency-free and testable; wrap manually by measured width.
        val lineHeight = bodyPaint.fontSpacing
        val maxLinesPerPage = ((pageHeight - 2 * margin - 40f) / lineHeight).toInt().coerceAtLeast(1)

        // Pre-wrap every logical line into visual lines that fit contentWidth
        data class VisualLine(val text: String, val isTitle: Boolean)
        val visualLines = mutableListOf<VisualLine>()
        for (raw in lines) {
            val (text, isTitle) = if (raw.startsWith("##TITLE##")) {
                raw.removePrefix("##TITLE##") to true
            } else {
                raw to false
            }
            val paint = if (isTitle) titlePaint else bodyPaint
            if (text.isBlank()) {
                visualLines.add(VisualLine("", false))
                continue
            }
            var remaining = text
            while (remaining.isNotEmpty()) {
                var end = paint.breakText(remaining, 0, remaining.length, true, contentWidth, null)
                // Prefer breaking on a space when mid-word cuts are ugly
                if (end < remaining.length && end > 0 && end < remaining.length) {
                    val lastSpace = remaining.lastIndexOf(' ', end - 1)
                    if (lastSpace > 0) end = lastSpace + 1
                }
                if (end <= 0) end = 1
                visualLines.add(VisualLine(remaining.substring(0, end), isTitle))
                remaining = remaining.substring(end)
            }
        }

        val pdf = PdfDocument()
        var pageNumber = 0
        var index = 0
        while (index < visualLines.size || pageNumber == 0) {
            pageNumber++
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            val page = pdf.startPage(pageInfo)
            val canvas = page.canvas

            if (pageNumber == 1) {
                canvas.drawText(document.title, margin, margin + titlePaint.textSize, titlePaint)
            }

            var y = margin + (if (pageNumber == 1) 34f else 0f)
            var drawn = 0
            while (index < visualLines.size && drawn < maxLinesPerPage) {
                val line = visualLines[index]
                val paint = if (line.isTitle) titlePaint else bodyPaint
                canvas.drawText(line.text, margin, y, paint)
                y += if (line.isTitle) titlePaint.fontSpacing + 6f else lineHeight
                if (line.isTitle) drawn += 2 else drawn++ // titles take extra room
                index++
            }

            canvas.drawText(
                "PixDocx · Page $pageNumber",
                margin,
                pageHeight - margin / 2,
                footerPaint
            )
            pdf.finishPage(page)
        }

        val out = java.io.ByteArrayOutputStream()
        pdf.writeTo(out)
        pdf.close()
        return out.toByteArray()
    }

    /** Internal: converts content into PDF-ready logical lines. `##TITLE##` marks headings. */
    internal fun pdfLines(document: OfficeDocument): List<String> = when (document.type) {
        DocumentType.XLS -> {
            val grid = SpreadsheetGrid.deserialize(document.content, maxCols = gridCols(document), maxRows = gridRows(document))
            buildList {
                for (r in 1..maxOf(1, grid.maxRows)) {
                    val cells = (1..grid.maxCols).mapNotNull { c ->
                        val id = "${CellCoordinate.getColumnName(c)}$r"
                        grid.evaluateDisplayValue(id).takeIf { it.isNotBlank() }
                    }
                    if (cells.isNotEmpty()) add(cells.joinToString("    "))
                }
            }
        }
        DocumentType.PPT -> SlideConverter.toText(document).lines()
        else -> document.content.lines().map { line ->
            when {
                line.startsWith("# ") -> "##TITLE##" + line.removePrefix("# ")
                line.startsWith("## ") -> "##TITLE##" + line.removePrefix("## ")
                line.startsWith("### ") -> "##TITLE##" + line.removePrefix("### ")
                else -> line
            }
        }
    }

    /** Number of columns/rows actually present in a serialized grid, with sane caps. */
    private fun gridCols(document: OfficeDocument): Int {
        var max = 0
        for (entry in document.content.split(';')) {
            val ref = entry.substringBefore('=').takeWhile { it in 'A'..'Z' }
            var col = 0
            for (ch in ref) col = col * 26 + (ch - 'A' + 1)
            if (col > max) max = col
        }
        return max.coerceAtLeast(1)
    }

    private fun gridRows(document: OfficeDocument): Int {
        var max = 0
        for (entry in document.content.split(';')) {
            val digits = entry.substringBefore('=').dropWhile { it in 'A'..'Z' }
            digits.toIntOrNull()?.let { if (it > max) max = it }
        }
        return max.coerceAtLeast(1)
    }

    // ------------------------------------------------------------------
    // Sub-converters
    // ------------------------------------------------------------------

    private object DocConverter {
        /** DOC content is already markdown; wrap it in a styled HTML shell. */
        fun toHtml(document: OfficeDocument): String {
            val body = markdownToHtml(normalizeMarkdown(document.content))
            return """<!DOCTYPE html>
<html>
<head><meta charset="utf-8"><title>${escapeHtml(document.title)}</title>
<style>
body { font-family: sans-serif; margin: 2em auto; max-width: 720px; line-height: 1.5; color: #1a1a1a; }
h1, h2, h3, h4 { color: #0f172a; }
table { border-collapse: collapse; } td, th { border: 1px solid #cbd5e1; padding: 4px 8px; }
code { background: #f1f5f9; padding: 1px 4px; border-radius: 3px; }
blockquote { border-left: 4px solid #94a3b8; margin-left: 0; padding-left: 12px; color: #475569; }
</style></head>
<body>
$body
</body>
</html>"""
        }

        /** Small markdown -> HTML pass covering what MarkdownText renders. */
        fun markdownToHtml(md: String): String {
            val out = StringBuilder()
            var inCodeFence = false
            var inList = false
            val table = mutableListOf<List<String>>()

            fun flushList() {
                if (inList) { out.append("</ul>\n"); inList = false }
            }
            fun flushTable() {
                if (table.isEmpty()) return
                out.append("<table>\n")
                table.forEachIndexed { i, row ->
                    out.append("<tr>")
                    val tag = if (i == 0) "th" else "td"
                    row.forEach { out.append("<$tag>${inline(it)}</$tag>") }
                    out.append("</tr>\n")
                }
                out.append("</table>\n")
                table.clear()
            }

            for (raw in md.lines()) {
                val line = raw.trimEnd()
                when {
                    line.startsWith("```") -> {
                        flushList(); flushTable()
                        out.append(if (inCodeFence) "</code></pre>\n" else "<pre><code>")
                        inCodeFence = !inCodeFence
                    }
                    inCodeFence -> out.append(escapeHtml(line)).append('\n')
                    line.startsWith("|") && line.endsWith("|") -> {
                        flushList()
                        val cells = line.trim('|').split('|').map { it.trim() }
                        if (!cells.all { it.matches(Regex("[-: ]*")) }) table.add(cells) // skip separator row
                    }
                    else -> {
                        flushTable()
                        when {
                            line.startsWith("### ") -> { flushList(); out.append("<h3>${inline(line.drop(4))}</h3>\n") }
                            line.startsWith("## ") -> { flushList(); out.append("<h2>${inline(line.drop(3))}</h2>\n") }
                            line.startsWith("# ") -> { flushList(); out.append("<h1>${inline(line.drop(2))}</h1>\n") }
                            line.startsWith("- ") || line.startsWith("* ") -> {
                                if (!inList) { out.append("<ul>\n"); inList = true }
                                out.append("<li>${inline(line.drop(2))}</li>\n")
                            }
                            line.matches(Regex("\\d+\\. .*")) -> {
                                if (!inList) { out.append("<ul>\n"); inList = true }
                                out.append("<li>${inline(line.substringAfter(". "))}</li>\n")
                            }
                            line.startsWith("> ") -> { flushList(); out.append("<blockquote>${inline(line.drop(2))}</blockquote>\n") }
                            line == "---" -> { flushList(); out.append("<hr>\n") }
                            line.isBlank() -> flushList()
                            else -> { flushList(); out.append("<p>${inline(line)}</p>\n") }
                        }
                    }
                }
            }
            flushList(); flushTable()
            if (inCodeFence) out.append("</code></pre>\n")
            return out.toString()
        }

        private fun inline(s: String): String = escapeHtml(s)
            .replace(Regex("\\*\\*(.+?)\\*\\*"), "<b>$1</b>")
            .replace(Regex("\\*(.+?)\\*"), "<i>$1</i>")
            .replace(Regex("~~(.+?)~~"), "<s>$1</s>")
            .replace(Regex("`(.+?)`"), "<code>$1</code>")

        private fun escapeHtml(s: String): String = s
            .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    }

    private object SpreadsheetConverter {
        private fun grid(document: OfficeDocument): SpreadsheetGrid =
            SpreadsheetGrid.deserialize(document.content, maxCols = gridColsSafe(document), maxRows = gridRowsSafe(document))

        private fun gridColsSafe(document: OfficeDocument): Int {
            var max = 0
            for (entry in document.content.split(';')) {
                val ref = entry.substringBefore('=').takeWhile { it in 'A'..'Z' }
                var col = 0
                for (ch in ref) col = col * 26 + (ch - 'A' + 1)
                if (col > max) max = col
            }
            return max.coerceAtLeast(1)
        }

        private fun gridRowsSafe(document: OfficeDocument): Int {
            var max = 0
            for (entry in document.content.split(';')) {
                val digits = entry.substringBefore('=').dropWhile { it in 'A'..'Z' }
                digits.toIntOrNull()?.let { if (it > max) max = it }
            }
            return max.coerceAtLeast(1)
        }

        fun toText(document: OfficeDocument): String {
            val g = grid(document)
            return buildString {
                for (r in 1..g.maxRows) {
                    val cells = (1..g.maxCols).mapNotNull { c ->
                        val id = "${CellCoordinate.getColumnName(c)}$r"
                        g.evaluateDisplayValue(id).takeIf { it.isNotBlank() }
                    }
                    if (cells.isNotEmpty()) appendLine(cells.joinToString("    "))
                }
            }.trimEnd()
        }

        fun toMarkdown(document: OfficeDocument): String {
            val g = grid(document)
            return buildString {
                for (r in 1..g.maxRows) {
                    val cells = (1..g.maxCols).map { c ->
                        val id = "${CellCoordinate.getColumnName(c)}$r"
                        g.evaluateDisplayValue(id)
                    }
                    if (cells.any { it.isNotBlank() }) {
                        append("| ").append(cells.joinToString(" | ")).append(" |\n")
                        if (r == 1) append("|").append(" --- |".repeat(cells.size)).append("\n")
                    }
                }
            }.trimEnd()
        }

        fun toHtml(document: OfficeDocument): String {
            val g = grid(document)
            return buildString {
                appendLine("<table>")
                for (r in 1..g.maxRows) {
                    val cells = (1..g.maxCols).map { c ->
                        val id = "${CellCoordinate.getColumnName(c)}$r"
                        g.evaluateDisplayValue(id)
                    }
                    if (cells.any { it.isNotBlank() }) {
                        append("<tr>")
                        cells.forEach { append("<td>").append(escape(it)).append("</td>") }
                        appendLine("</tr>")
                    }
                }
                append("</table>")
            }
        }

        private fun escape(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    }

    private object SlideConverter {
        private fun deck(document: OfficeDocument): SlideDeck = SlideDeck.deserialize(document.content)

        fun toText(document: OfficeDocument): String {
            val d = deck(document)
            return buildString {
                d.slides.forEachIndexed { i, s ->
                    appendLine("Slide ${i + 1}: ${s.title}")
                    if (s.subtitle.isNotBlank()) appendLine(s.subtitle)
                    if (s.content.isNotBlank()) appendLine(s.content)
                    if (s.secondaryContent.isNotBlank()) appendLine(s.secondaryContent)
                    if (s.statValue.isNotBlank()) appendLine("${s.statValue} ${s.statLabel}".trim())
                    if (i < d.slides.lastIndex) appendLine()
                }
            }.trimEnd()
        }

        fun toMarkdown(document: OfficeDocument): String {
            val d = deck(document)
            return buildString {
                appendLine("# ${document.title}")
                appendLine()
                d.slides.forEachIndexed { i, s ->
                    appendLine("## ${i + 1}. ${s.title}")
                    if (s.subtitle.isNotBlank()) appendLine("*${s.subtitle}*")
                    if (s.content.isNotBlank()) {
                        appendLine()
                        appendLine(s.content)
                    }
                    if (s.secondaryContent.isNotBlank()) {
                        appendLine()
                        appendLine(s.secondaryContent)
                    }
                    if (s.statValue.isNotBlank()) {
                        appendLine()
                        appendLine("**${s.statValue}** — ${s.statLabel}")
                    }
                    if (i < d.slides.lastIndex) appendLine()
                }
            }.trimEnd()
        }

        fun toHtml(document: OfficeDocument): String {
            val d = deck(document)
            return buildString {
                appendLine("<!DOCTYPE html>")
                appendLine("<html><head><meta charset=\"utf-8\"><title>${escape(document.title)}</title>")
                appendLine("<style>body{font-family:sans-serif;max-width:720px;margin:2em auto;} .slide{border:1px solid #cbd5e1;border-radius:8px;padding:16px;margin-bottom:16px;} .slide h2{margin-top:0;}</style>")
                appendLine("</head><body>")
                d.slides.forEachIndexed { i, s ->
                    appendLine("<div class=\"slide\">")
                    appendLine("<h2>${i + 1}. ${escape(s.title)}</h2>")
                    if (s.subtitle.isNotBlank()) appendLine("<p><em>${escape(s.subtitle)}</em></p>")
                    if (s.content.isNotBlank()) s.content.lines().forEach { appendLine("<p>${escape(it)}</p>") }
                    if (s.secondaryContent.isNotBlank()) s.secondaryContent.lines().forEach { appendLine("<p>${escape(it)}</p>") }
                    if (s.statValue.isNotBlank()) appendLine("<p><strong>${escape(s.statValue)}</strong> — ${escape(s.statLabel)}</p>")
                    appendLine("</div>")
                }
                append("</body></html>")
            }
        }

        private fun escape(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    }

    // ------------------------------------------------------------------
    // Markdown helpers
    // ------------------------------------------------------------------

    private fun normalizeMarkdown(content: String): String = content.trim()

    private fun stripMarkdown(md: String): String = md
        .replace(Regex("```[a-zA-Z]*\\n?"), "")
        .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
        .replace(Regex("\\*(.+?)\\*"), "$1")
        .replace(Regex("~~(.+?)~~"), "$1")
        .replace(Regex("`(.+?)`"), "$1")
        .replace(Regex("<u>(.+?)</u>"), "$1")
        .replace(Regex("^#{1,6} ", RegexOption.MULTILINE), "")
        .replace(Regex("^\\s*[-*] ", RegexOption.MULTILINE), "• ")
        .trim()

    private fun csvEscape(value: String): String =
        if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            "\"${value.replace("\"", "\"\"")}\""
        } else value
}
