package com.example.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.model.DocumentType
import com.example.data.model.SlideDeck
import com.example.data.model.SlideItem
import com.example.data.model.SlideLayout
import com.example.data.model.SpreadsheetGrid
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Imports real files from the device into PixDocx.
 *
 * Supported formats (no external dependencies — platform APIs only):
 * - Text: .md, .txt, .log, .json, .xml, .html, source files...
 * - CSV/TSV: .csv, .tsv  -> spreadsheet grid
 * - DOCX: .docx          -> paragraph text (Open XML word/document.xml)
 * - XLSX: .xlsx          -> worksheets as a grid (Open XML sheet XML)
 * - PPTX: .pptx          -> slides (Open XML DrawingML)
 * - PDF:  .pdf           -> best-effort text extraction of uncompressed
 *                           content streams (Tj/TJ text-show operators)
 */
object FileImporter {

    data class ImportResult(
        val title: String,
        val type: DocumentType,
        val content: String,
        val sizeLabel: String,
        /** Path of the preserved original file in app storage (PDFs, etc). */
        val savedFilePath: String = ""
    )

    private const val MAX_FILE_BYTES = 16 * 1024 * 1024 // 16 MB safety cap
    private const val MAX_ZIP_ENTRY_BYTES = 8 * 1024 * 1024

    /** Maps a file name to a document type. Falls back to text/doc for unknowns. */
    fun detectType(fileName: String): DocumentType {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "docx", "doc", "odt", "rtf" -> DocumentType.DOC
            "xlsx", "xls", "ods", "csv", "tsv" -> DocumentType.XLS
            "pptx", "ppt", "odp" -> DocumentType.PPT
            "pdf" -> DocumentType.PDF
            else -> DocumentType.DOC // md, txt and everything text-like
        }
    }

    /** Reads display name and size from the content resolver. */
    fun queryMeta(context: Context, uri: Uri): Pair<String, Long> {
        var name = "imported_file"
        var size = 0L
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIdx >= 0) cursor.getString(nameIdx)?.let { name = it }
                if (sizeIdx >= 0) size = cursor.getLong(sizeIdx)
            }
        }
        if (name == "imported_file") {
            uri.lastPathSegment?.let { name = it.substringAfterLast('/') }
        }
        return name to size
    }

    fun sizeLabel(bytes: Long): String = when {
        bytes >= 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        bytes >= 1024 -> "${(bytes + 512) / 1024} KB"
        else -> "$bytes B"
    }

    /**
     * Main entry: imports a Uri into a PixDocx [ImportResult].
     * Dispatches by extension; never throws — on failure returns a readable
     * notice so the user always gets an openable document.
     */
    fun import(context: Context, uri: Uri): ImportResult {
        val (fileName, sizeBytes) = queryMeta(context, uri)
        val ext = fileName.substringAfterLast('.', "").lowercase()
        val baseName = fileName.substringBeforeLast('.')

        // Preserve the original bytes so format-native viewers (PDF renderer)
        // can re-read the real file later, not just the extracted text.
        val savedPath = try { saveToImportsDir(context, uri, fileName) } catch (_: Exception) { "" }

        val content: String = try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                when (ext) {
                    "csv" -> importCsv(stream, ',')
                    "tsv" -> importCsv(stream, '\t')
                    "xlsx" -> withZip(stream) { entries -> parseXlsx(entries) }
                    "docx" -> withZip(stream) { entries ->
                        entries["word/document.xml"]
                            ?.let { parseDocx(it) }
                            ?: "⚠ Not a valid .docx (missing word/document.xml)."
                    }
                    "pptx" -> withZip(stream) { entries -> parsePptx(entries) }
                    "pdf" -> importPdf(stream)
                    "doc", "xls", "ppt", "odt", "ods", "odp", "rtf" ->
                        readAsTextWithFallback(stream, ext)
                    else -> readAsTextWithFallback(stream, ext)
                }
            } ?: "⚠ Could not open file."
        } catch (e: Exception) {
            "⚠ Could not parse this file as .$ext.\n\n${e.message ?: "Unknown error"}"
        }

        return ImportResult(
            title = baseName.ifBlank { fileName },
            type = detectType(fileName),
            content = content,
            sizeLabel = sizeLabel(sizeBytes),
            savedFilePath = savedPath
        )
    }

    /** Copies the source file into app-private storage so viewers can re-open it. */
    private fun saveToImportsDir(context: Context, uri: Uri, fileName: String): String {
        val dir = File(context.filesDir, "imports").apply { mkdirs() }
        // Prefix with a timestamp so same-named files never overwrite each other
        val safeName = "${System.currentTimeMillis()}_" + fileName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val target = File(dir, safeName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        return target.absolutePath
    }

    // ------------------------------------------------------------------
    // Plain text
    // ------------------------------------------------------------------

    private fun readAsTextWithFallback(stream: InputStream, ext: String): String {
        val bytes = stream.readBytes().let { if (it.size > MAX_FILE_BYTES) it.copyOf(MAX_FILE_BYTES) else it }
        val text = String(bytes, Charsets.UTF_8)
        // Heuristic: if >5% control characters, treat as binary
        val controls = text.count { it.code < 9 || (it.code in 14..31) }
        return if (text.isNotEmpty() && controls * 20 > text.length) {
            buildString {
                appendLine("⚠ Binary file — text extraction is not supported for .$ext.")
                appendLine("File size: ${sizeLabel(bytes.size.toLong())}")
                appendLine()
                append("First bytes: ")
                append(bytes.take(64).joinToString(" ") { "%02X".format(it) })
            }
        } else {
            text
        }
    }

    // ------------------------------------------------------------------
    // CSV / TSV
    // ------------------------------------------------------------------

    private fun importCsv(stream: InputStream, delimiter: Char): String {
        val bytes = stream.readBytes().let { if (it.size > MAX_FILE_BYTES) it.copyOf(MAX_FILE_BYTES) else it }
        val text = String(bytes, Charsets.UTF_8)
        val grid = SpreadsheetGrid(maxCols = 26, maxRows = maxOf(200, text.lines().size + 5))
        text.lines().forEachIndexed { rowIdx, line ->
            if (line.isBlank() && rowIdx > 0) return@forEachIndexed
            parseDelimitedLine(line, delimiter).forEachIndexed { colIdx, token ->
                val value = token.trim()
                if (value.isNotEmpty()) {
                    grid.setCell("${CellNames.name(colIdx + 1)}${rowIdx + 1}", value)
                }
            }
        }
        return grid.serialize()
    }

    private fun parseDelimitedLine(line: String, delimiter: Char): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        for (ch in line) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                ch == delimiter && !inQuotes -> {
                    result.add(sb.toString())
                    sb.clear()
                }
                else -> sb.append(ch)
            }
        }
        result.add(sb.toString())
        return result
    }

    // ------------------------------------------------------------------
    // Zip (OOXML) plumbing — single pass, all relevant entries buffered
    // ------------------------------------------------------------------

    private inline fun withZip(
        stream: InputStream,
        parse: (Map<String, String>) -> String
    ): String {
        val entries = mutableMapOf<String, String>()
        ZipInputStream(stream.buffered()).use { zin ->
            var entry: ZipEntry? = zin.nextEntry
            while (entry != null) {
                val bytes = zin.readBytes()
                if (bytes.size <= MAX_ZIP_ENTRY_BYTES) {
                    entries[entry.name] = String(bytes, Charsets.UTF_8)
                }
                entry = zin.nextEntry
            }
        }
        return parse(entries)
    }

    // ------------------------------------------------------------------
    // DOCX (word/document.xml)
    // ------------------------------------------------------------------

    /**
     * DOCX -> markdown so the DocViewer renders real formatting (headings,
     * bold/italic runs, list bullets) instead of losing it all.
     */
    private fun parseDocx(documentXml: String): String {
        val sb = StringBuilder()
        val parser = newParser(documentXml)
        var paragraphDepth = 0
        var runDepth = 0
        var styleVal = ""
        var numPr = false
        var runBold = false
        var runItalic = false
        var runUnderline = false
        var runText = ""
        // Marks whether the current paragraph still needs its markdown prefix
        // (headings / list bullets) prepended before its first run text.
        var paragraphPrefixPending = false

        fun flushRun() {
            if (runText.isNotEmpty()) {
                if (paragraphPrefixPending) {
                    val prefix = when {
                        numPr -> "- "
                        styleVal.startsWith("Heading") ->
                            "#".repeat(styleVal.takeLast(1).toIntOrNull() ?: 1) + " "
                        styleVal == "Title" -> "# "
                        else -> ""
                    }
                    sb.append(prefix)
                    paragraphPrefixPending = false
                }
                var t = runText
                if (runBold) t = "**$t**"
                if (runItalic) t = "*$t*"
                if (runUnderline) t = "<u>$t</u>"
                sb.append(t)
                runText = ""
            }
            runBold = false; runItalic = false; runUnderline = false
        }

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "p" -> {
                        paragraphDepth++
                        styleVal = parser.getAttributeValue(null, "w:pStyle") ?: ""
                        numPr = false
                        paragraphPrefixPending = true
                    }
                    "pStyle" -> styleVal = parser.getAttributeValue(null, "w:val") ?: styleVal
                    "numPr" -> numPr = true
                    "r" -> if (paragraphDepth > 0) {
                        runDepth++
                        flushRun()
                    }
                    "b" -> if (runDepth > 0) runBold = true
                    "i" -> if (runDepth > 0) runItalic = true
                    "u" -> if (runDepth > 0) runUnderline = true
                    "br" -> sb.append('\n')
                    "tab" -> sb.append('\t')
                }
                XmlPullParser.TEXT -> if (runDepth > 0) runText += parser.text
                XmlPullParser.END_TAG -> when (parser.name) {
                    "r" -> {
                        flushRun()
                        if (runDepth > 0) runDepth--
                    }
                    "p" -> {
                        flushRun()
                        if (paragraphDepth > 0) paragraphDepth--
                        sb.append('\n')
                    }
                }
            }
            parser.next()
        }
        flushRun()
        val text = sb.toString().replace(Regex("\n{3,}"), "\n\n").trim()
        return text.ifBlank { "⚠ Empty document or unsupported docx structure." }
    }

    // ------------------------------------------------------------------
    // XLSX (sharedStrings.xml + xl/worksheets/sheetN.xml)
    // ------------------------------------------------------------------

    private fun parseXlsx(entries: Map<String, String>): String {
        val shared = entries["xl/sharedStrings.xml"]?.let { parseSharedStrings(it) } ?: emptyList()
        val sheetXml = entries.entries
            .filter { it.key.matches(Regex("xl/worksheets/sheet\\d+\\.xml")) }
            .minByOrNull { it.key } // first sheet
            ?.value
            ?: return "⚠ Not a valid .xlsx (no worksheet found)."

        val grid = SpreadsheetGrid(maxCols = 26, maxRows = 500)
        val parser = newParser(sheetXml)
        var row = 0
        var col = 0
        var cellType = ""
        val cellValue = StringBuilder()
        var inValue = false

        fun commitCell() {
            if (row > 0 && col > 0 && cellValue.isNotBlank()) {
                val text = if (cellType == "s" || cellType == "str") {
                    shared.getOrNull(cellValue.toString().toIntOrNull() ?: -1) ?: cellValue.toString()
                } else {
                    cellValue.toString()
                }
                grid.setCell("${CellNames.name(col)}$row", text.trim())
            }
            cellValue.clear()
            inValue = false
        }

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "row" -> {
                        commitCell()
                        row = parser.getAttributeValue(null, "r")?.toIntOrNull() ?: (row + 1)
                        col = 0
                    }
                    "c" -> {
                        commitCell()
                        val ref = parser.getAttributeValue(null, "r") ?: ""
                        col = ref.takeWhile { it in 'A'..'Z' }
                            .fold(0) { acc, ch -> acc * 26 + (ch - 'A' + 1) }
                        cellType = parser.getAttributeValue(null, "t") ?: ""
                    }
                    "v" -> inValue = true
                    "t" -> if (inValue) inValue = true // inline string <is><t>
                }
                XmlPullParser.TEXT -> if (inValue) cellValue.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "v" -> inValue = false
                    "c" -> commitCell()
                    "row" -> commitCell()
                }
            }
            parser.next()
        }
        commitCell()
        return grid.serialize()
    }

    private fun parseSharedStrings(xml: String): List<String> {
        val result = mutableListOf<String>()
        val parser = newParser(xml)
        var inItem = false
        val sb = StringBuilder()
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> if (parser.name == "si") {
                    inItem = true
                    sb.clear()
                }
                XmlPullParser.TEXT -> if (inItem) sb.append(parser.text)
                XmlPullParser.END_TAG -> if (parser.name == "si") {
                    result.add(sb.toString())
                    inItem = false
                }
            }
            parser.next()
        }
        return result
    }

    // ------------------------------------------------------------------
    // PPTX (ppt/slides/slideN.xml)
    // ------------------------------------------------------------------

    private fun parsePptx(entries: Map<String, String>): String {
        val slideNums = entries.keys.mapNotNull { key ->
            Regex("ppt/slides/slide(\\d+)\\.xml").find(key)?.groupValues?.get(1)?.toIntOrNull()
        }.sorted()

        if (slideNums.isEmpty()) return "⚠ Not a valid .pptx (no slides found)."

        val deck = SlideDeck()
        for (slideNum in slideNums) {
            val xml = entries["ppt/slides/slide$slideNum.xml"] ?: continue
            val paragraphs = extractPptxParagraphs(xml)
            val title = paragraphs.firstOrNull()?.take(120) ?: "Slide $slideNum"
            val body = paragraphs.drop(1).joinToString("\n")
            deck.slides.add(
                SlideItem(
                    layout = if (body.isBlank()) SlideLayout.TITLE_SLIDE else SlideLayout.TITLE_AND_CONTENT,
                    title = title,
                    content = body,
                    subtitle = ""
                )
            )
        }
        return deck.serialize()
    }

    /** Extracts <a:t> text runs grouped by <a:p> paragraphs from DrawingML. */
    private fun extractPptxParagraphs(xml: String): List<String> {
        val paragraphs = mutableListOf<String>()
        val parser = newParser(xml)
        val sb = StringBuilder()
        var inParagraph = false
        var inTextRun = false
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "p" -> {
                        inParagraph = true
                        sb.clear()
                    }
                    "t" -> if (inParagraph) inTextRun = true
                }
                XmlPullParser.TEXT -> if (inTextRun) sb.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "t" -> inTextRun = false
                    "p" -> {
                        val text = sb.toString().trim()
                        if (text.isNotBlank() && inParagraph) paragraphs.add(text)
                        inParagraph = false
                    }
                }
            }
            parser.next()
        }
        return paragraphs
    }

    // ------------------------------------------------------------------
    // PDF (best-effort: uncompressed text-show operators)
    // ------------------------------------------------------------------

    private fun importPdf(stream: InputStream): String {
        val bytes = stream.readBytes().let { if (it.size > MAX_FILE_BYTES) it.copyOf(MAX_FILE_BYTES) else it }
        val raw = String(bytes, Charsets.ISO_8859_1)

        val textRegex = Regex("""\(((?:[^()\\]|\\.)*)\)\s*(Tj|'|")""")
        val arrayRegex = Regex("""\[((?:[^\[\]\\]|\\.)*)\]\s*TJ""")

        val pieces = mutableListOf<String>()

        // Collect text-show operators in document order
        val matches = sortedMapOf<Int, String>()
        for (m in textRegex.findAll(raw)) {
            matches[m.range.first] = unescapePdfString(m.groupValues[1])
        }
        for (m in arrayRegex.findAll(raw)) {
            // TJ arrays: concatenate string literals inside
            val inner = m.groupValues[1]
            val joined = Regex("""\(((?:[^()\\]|\\.)*)\)""").findAll(inner)
                .joinToString("") { unescapePdfString(it.groupValues[1]) }
            if (joined.isNotBlank()) matches[m.range.first] = joined
        }

        pieces.addAll(matches.values)

        if (pieces.isEmpty()) {
            val pageCount = Regex("/Type\\s*/Page[^s]").findAll(raw).count()
            return buildString {
                appendLine("⚠ This PDF stores text in compressed/encoded streams")
                appendLine("that PixDocx cannot decode without a full PDF engine.")
                if (pageCount > 0) appendLine("Detected pages: $pageCount")
                appendLine()
                append("The document was imported for reference; metadata may be readable in a desktop viewer.")
            }
        }

        // Heuristic line reconstruction: break after sentence enders and bullets,
        // and when a gap in byte positions suggests a positioning operator between.
        val sb = StringBuilder()
        var prevEnd = -1
        for ((pos, textPiece) in matches) {
            val gap = prevEnd >= 0 && pos - prevEnd > 60
            if (sb.isNotEmpty() && (gap || sb.lastOrNull() == '\n')) {
                // start new line if last line ended with sentence punctuation
            } else if (sb.isNotEmpty() && !sb.endsWith("\n")) {
                sb.append(' ')
            }
            sb.append(textPiece.trim())
            if (textPiece.trimEnd().endsWith(".") || textPiece.trimEnd().endsWith(":")) {
                sb.append('\n')
            }
            prevEnd = pos + textPiece.length
        }
        return sb.toString().replace(Regex("\n{3,}"), "\n\n").trim()
    }

    private fun unescapePdfString(raw: String): String = buildString {
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (c == '\\' && i + 1 < raw.length) {
                when (val n = raw[i + 1]) {
                    'n' -> append('\n')
                    'r' -> append('\r')
                    't' -> append('\t')
                    'b', 'f' -> { /* skip */ }
                    '(', ')' -> append(n)
                    '\\' -> append('\\')
                    else -> {
                        val oct = raw.drop(i + 1).takeWhile { it in '0'..'7' }
                        if (oct.isNotEmpty()) {
                            append(oct.toInt(8).toChar())
                            i += oct.length
                        } else {
                            append(n)
                        }
                    }
                }
                i += 2
            } else {
                append(c)
                i++
            }
        }
    }

    private fun newParser(xml: String): XmlPullParser {
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val parser = factory.newPullParser()
        parser.setInput(xml.reader())
        return parser
    }
}

/** Column number (1-based) to spreadsheet letters: 1->A, 26->Z, 27->AA. */
private object CellNames {
    fun name(col: Int): String {
        var c = col
        val sb = StringBuilder()
        while (c > 0) {
            val rem = (c - 1) % 26
            sb.append(('A' + rem))
            c = (c - 1) / 26
        }
        return sb.reverse().toString()
    }
}
