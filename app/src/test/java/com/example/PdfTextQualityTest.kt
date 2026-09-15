package com.example

import com.example.util.FileImporter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * PDFs whose fonts use embedded/CID encodings extract to mojibake instead of
 * text. The app must recognise that and tell the user the truth rather than
 * showing garbage, so the threshold is pinned behaviour.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PdfTextQualityTest {

    @Test
    fun `readable prose is not flagged`() {
        val prose = "The quick brown fox jumps over the lazy dog. It was 12 degrees, " +
            "and the report (final version) listed items: 1, 2, 3.\nSecond paragraph here."
        assertFalse(FileImporter.pdfTextLooksGarbled(prose))
    }

    @Test
    fun `unmappable glyph soup is flagged`() {
        val mojibake = "\uFFFD\uFFFD\uFFFD\uFFFD\uFFFD\uFFFD\uFFFD\uFFFD\uFFFD\uFFFD" +
            "\u0001\u0002\u0003\u0004\uFFFD\uFFFD\uFFFD\uFFFD"
        assertTrue(FileImporter.pdfTextLooksGarbled(mojibake))
    }

    @Test
    fun `symbol-only extraction is flagged`() {
        assertTrue(FileImporter.pdfTextLooksGarbled("\u00A7\u00B6\u00AB\u00BB\u2020\u2021\u2020\u2021"))
    }

    @Test
    fun `empty extraction is flagged`() {
        assertTrue(FileImporter.pdfTextLooksGarbled(""))
        assertTrue(FileImporter.pdfTextLooksGarbled("   \n\n  "))
    }
}
