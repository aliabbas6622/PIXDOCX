package com.example

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.util.FileImporter
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.DeflaterOutputStream

/**
 * Verifies the importers against realistic files written to local storage.
 * Robolectric gives us a working ContentResolver over file:// uris.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FileImporterPdfTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun importFile(name: String, bytes: ByteArray): FileImporter.ImportResult {
        val dir = File(context.cacheDir, "import_tests").apply { mkdirs() }
        val f = File(dir, name)
        f.writeBytes(bytes)
        return FileImporter.import(context, Uri.fromFile(f))
    }

    /** Builds a minimal one-page PDF whose content stream is Flate-compressed, like real-world PDFs. */
    private fun compressedPdf(text: String): ByteArray {
        val content = "BT /F1 12 Tf 72 720 Td ($text) Tj ET"
        val compressed = ByteArrayOutputStream().also { buf ->
            DeflaterOutputStream(buf).use { it.write(content.toByteArray(Charsets.ISO_8859_1)) }
        }.toByteArray()

        return buildString {
            append("%PDF-1.4\n")
            append("1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n")
            append("2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n")
            append("3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 612 792]/Contents 4 0 R>>endobj\n")
            append("4 0 obj<</Length ${compressed.size}/Filter/FlateDecode>>\nstream\n")
        }.toByteArray(Charsets.ISO_8859_1) +
                compressed +
                "\nendstream\nendobj\ntrailer<</Root 1 0 R>>\n%%EOF".toByteArray(Charsets.ISO_8859_1)
    }

    @Test
    fun `compressed pdf text is extracted`() {
        val result = importFile("compressed.pdf", compressedPdf("Hello compressed world."))
        assertTrue(
            "Expected extracted text, got: ${result.content.take(120)}",
            result.content.contains("Hello compressed world.")
        )
    }

    @Test
    fun `csv imports as spreadsheet with values`() {
        val csv = "Name,Qty\nWidget,5\n"
        val result = importFile("inventory.csv", csv.toByteArray())
        assertTrue(result.type == com.example.data.model.DocumentType.XLS)
        assertTrue(result.content.contains("Widget"))
    }

    @Test
    fun `md imports as doc preserving markdown`() {
        val result = importFile("notes.md", "# Title\nBody".toByteArray())
        assertTrue(result.type == com.example.data.model.DocumentType.DOC)
        assertTrue(result.content.contains("# Title"))
    }

    @Test
    fun `original file is preserved in imports dir`() {
        val result = importFile("kept.pdf", compressedPdf("keep me"))
        assertTrue(result.savedFilePath.isNotBlank())
        val saved = File(result.savedFilePath)
        assertTrue(saved.exists())
        saved.delete()
    }
}
