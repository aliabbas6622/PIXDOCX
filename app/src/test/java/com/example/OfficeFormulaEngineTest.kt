package com.example

import com.example.data.model.CellCoordinate
import com.example.data.model.SlideDeck
import com.example.data.model.SlideItem
import com.example.data.model.SlideLayout
import com.example.data.model.SpreadsheetGrid
import org.junit.Assert.assertEquals
import org.junit.Test

class OfficeFormulaEngineTest {

    @Test
    fun testCellCoordinateParsing() {
        val coord = CellCoordinate.fromCellId("B12")
        assertEquals(2, coord?.col)
        assertEquals(12, coord?.row)

        assertEquals("B", CellCoordinate.getColumnName(2))
        assertEquals("AA", CellCoordinate.getColumnName(27))
    }

    @Test
    fun testSpreadsheetSumFormula() {
        val grid = SpreadsheetGrid()
        grid.setCell("A1", "10")
        grid.setCell("A2", "25")
        grid.setCell("A3", "15")
        grid.setCell("A4", "=SUM(A1:A3)")

        val displayVal = grid.evaluateDisplayValue("A4")
        assertEquals("50", displayVal)
    }

    @Test
    fun testSpreadsheetArithmeticFormulas() {
        val grid = SpreadsheetGrid()
        grid.setCell("B1", "100")
        grid.setCell("B2", "0.2")
        grid.setCell("B3", "=B1*B2")
        grid.setCell("B4", "=B1+50")

        assertEquals("20", grid.evaluateDisplayValue("B3"))
        assertEquals("150", grid.evaluateDisplayValue("B4"))
    }

    @Test
    fun testSpreadsheetAverageFormula() {
        val grid = SpreadsheetGrid()
        grid.setCell("C1", "10")
        grid.setCell("C2", "20")
        grid.setCell("C3", "30")
        grid.setCell("C4", "=AVERAGE(C1:C3)")

        assertEquals("20", grid.evaluateDisplayValue("C4"))
    }

    @Test
    fun testSlideDeckSerialization() {
        val deck = SlideDeck(themeColorHex = "#0F172A", accentColorHex = "#EA580C")
        deck.slides.add(
            SlideItem(
                layout = SlideLayout.TITLE_SLIDE,
                title = "Test Keynote",
                subtitle = "Presenter"
            )
        )
        val serialized = deck.serialize()
        val restored = SlideDeck.deserialize(serialized)

        assertEquals(1, restored.slides.size)
        assertEquals("Test Keynote", restored.slides[0].title)
        assertEquals(SlideLayout.TITLE_SLIDE, restored.slides[0].layout)
    }
}
