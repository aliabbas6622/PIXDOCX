package com.example

import com.example.ui.OfficeViewModel
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Word counts drive the "N words" badge on document cards, so the counting
 * rules are pinned here: whitespace/line-break agnostic, never negative,
 * never counting an empty string as one word.
 */
class WordCountTest {

    @Test
    fun `counts simple words`() {
        assertEquals(3, OfficeViewModel.countWords("one two three"))
    }

    @Test
    fun `collapses repeated whitespace and newlines`() {
        assertEquals(2, OfficeViewModel.countWords("one\n\n\t  two   "))
    }

    @Test
    fun `empty and blank text count zero`() {
        assertEquals(0, OfficeViewModel.countWords(""))
        assertEquals(0, OfficeViewModel.countWords("   \n\t "))
    }
}
