package com.example

import com.example.data.model.DocumentType
import com.example.ui.ImportOutcome
import com.example.ui.importSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The import snackbar is the only feedback the user gets, so its wording is
 * pinned: one sentence per batch, never a wall of duplicates.
 */
class ImportSummaryTest {

    @Test
    fun `empty batch says nothing`() {
        assertNull(importSummary(emptyList()))
    }

    @Test
    fun `single success names the document`() {
        val summary = importSummary(listOf(ImportOutcome.Imported("Quarterly Report", DocumentType.DOC)))
        assertEquals("Imported \"Quarterly Report\"", summary)
    }

    @Test
    fun `single duplicate says the file is already there`() {
        val summary = importSummary(listOf(ImportOutcome.SkippedDuplicate("notes")))
        assertEquals("\"notes\" is already in PixDocx", summary)
    }

    @Test
    fun `single failure includes the reason`() {
        val summary = importSummary(listOf(ImportOutcome.Failed("broken.pdf", "stream closed")))
        assertEquals("Couldn't import \"broken.pdf\": stream closed", summary)
    }

    @Test
    fun `failure without a reason still reads as a sentence`() {
        val summary = importSummary(listOf(ImportOutcome.Failed("broken.pdf", null)))
        assertEquals("Couldn't import \"broken.pdf\"", summary)
    }

    @Test
    fun `mixed batch is one sentence with plural files`() {
        val summary = importSummary(
            listOf(
                ImportOutcome.Imported("a.md", DocumentType.DOC),
                ImportOutcome.Imported("b.md", DocumentType.DOC),
                ImportOutcome.SkippedDuplicate("c.md"),
                ImportOutcome.Failed("d.pdf", null)
            )
        )
        assertEquals("Imported 2 files \u00b7 Skipped 1 duplicate \u00b7 Failed 1 file", summary)
    }

    @Test
    fun `batch keeps singular wording for one file`() {
        val summary = importSummary(
            listOf(
                ImportOutcome.Imported("a.md", DocumentType.DOC),
                ImportOutcome.Failed("b.pdf", "no such file")
            )
        )
        assertEquals("Imported 1 file \u00b7 Failed 1 file", summary)
    }
}
