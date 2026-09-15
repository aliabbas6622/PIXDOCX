package com.example.data.model

import java.util.Locale

data class CellCoordinate(val col: Int, val row: Int) {
    val colName: String
        get() = getColumnName(col)
    val cellId: String
        get() = "$colName$row"

    companion object {
        fun getColumnName(colIndex: Int): String {
            var col = colIndex
            val sb = StringBuilder()
            while (col > 0) {
                val rem = (col - 1) % 26
                sb.append(('A'.code + rem).toChar())
                col = (col - 1) / 26
            }
            return sb.reverse().toString()
        }

        fun parse(cellRef: String): CellCoordinate? {
            val trimmed = cellRef.trim().uppercase(Locale.ROOT)
            val letters = trimmed.takeWhile { it in 'A'..'Z' }
            val digits = trimmed.drop(letters.length)
            if (letters.isEmpty() || digits.isEmpty()) return null
            var col = 0
            for (ch in letters) {
                col = col * 26 + (ch - 'A' + 1)
            }
            val row = digits.toIntOrNull() ?: return null
            return CellCoordinate(col, row)
        }

        fun fromCellId(cellId: String): CellCoordinate? = parse(cellId)
    }
}

data class CellStyle(
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val align: String = "LEFT", // LEFT, CENTER, RIGHT
    val format: String = "GENERAL", // GENERAL, CURRENCY, PERCENT, NUMBER
    val bgColorHex: String = "#FFFFFF"
)

data class CellData(
    val rawValue: String = "",
    val style: CellStyle = CellStyle()
)

class SpreadsheetGrid(
    val maxCols: Int = 12, // A through L
    val maxRows: Int = 40,
    val cells: MutableMap<String, CellData> = mutableMapOf()
) {
    // Evaluation cache, invalidated on every mutation. Full-grid operations
    // (CSV export, visibility recalc) evaluate overlapping ranges repeatedly,
    // so memoizing turns O(n^2) re-evaluation into O(n).
    private var evalCache: MutableMap<String, String> = mutableMapOf()

    // Active evaluation stack for cycle detection (e.g. A1 = A1+1).
    private val evaluating = HashSet<String>()

    fun getRaw(cellId: String): String = cells[cellId]?.rawValue ?: ""
    fun getStyle(cellId: String): CellStyle = cells[cellId]?.style ?: CellStyle()

    fun setCell(cellId: String, rawValue: String, style: CellStyle? = null) {
        val currentStyle = style ?: getStyle(cellId)
        evalCache.clear()
        if (rawValue.isBlank() && currentStyle == CellStyle()) {
            cells.remove(cellId)
        } else {
            cells[cellId] = CellData(rawValue = rawValue, style = currentStyle)
        }
    }

    fun updateStyle(cellId: String, update: (CellStyle) -> CellStyle) {
        val current = cells[cellId] ?: CellData()
        evalCache.clear()
        cells[cellId] = current.copy(style = update(current.style))
    }

    /**
     * Evaluates formulas like:
     * =SUM(A1:A5)
     * =AVERAGE(B1:B10)
     * =COUNT(A1:B5)
     * =MAX(A1:A5)
     * =MIN(A1:A5)
     * =A1+B1, =A1*1.2, =A1-B1, =A1/2
     */
    fun evaluateDisplayValue(cellId: String): String {
        evalCache[cellId]?.let { return it }

        // Guard against circular references: A1 = "=B1", B1 = "=A1"
        if (!evaluating.add(cellId)) return "#CIRCULAR!"

        val result = try {
            evaluateUncached(cellId)
        } finally {
            evaluating.remove(cellId)
        }

        evalCache[cellId] = result
        return result
    }

    private fun evaluateUncached(cellId: String): String {
        val raw = getRaw(cellId).trim()
        if (!raw.startsWith("=")) {
            return formatValue(raw, getStyle(cellId).format)
        }

        try {
            val formula = raw.substring(1).trim()
            val upper = formula.uppercase(Locale.ROOT)

            // Functions like SUM, AVERAGE, COUNT, MIN, MAX
            when {
                upper.startsWith("SUM(") && upper.endsWith(")") -> {
                    val rangeStr = upper.substring(4, upper.length - 1)
                    val values = getValuesInRange(rangeStr)
                    val sum = values.sum()
                    return formatNumber(sum, getStyle(cellId).format)
                }
                upper.startsWith("AVERAGE(") && upper.endsWith(")") ||
                upper.startsWith("AVG(") && upper.endsWith(")") -> {
                    val rangeStr = if (upper.startsWith("AVG(")) upper.substring(4, upper.length - 1) else upper.substring(8, upper.length - 1)
                    val values = getValuesInRange(rangeStr)
                    if (values.isEmpty()) return "0"
                    val avg = values.average()
                    return formatNumber(avg, getStyle(cellId).format)
                }
                upper.startsWith("COUNT(") && upper.endsWith(")") -> {
                    val rangeStr = upper.substring(6, upper.length - 1)
                    val values = getValuesInRange(rangeStr)
                    return values.size.toString()
                }
                upper.startsWith("MAX(") && upper.endsWith(")") -> {
                    val rangeStr = upper.substring(4, upper.length - 1)
                    val values = getValuesInRange(rangeStr)
                    return formatNumber(values.maxOrNull() ?: 0.0, getStyle(cellId).format)
                }
                upper.startsWith("MIN(") && upper.endsWith(")") -> {
                    val rangeStr = upper.substring(4, upper.length - 1)
                    val values = getValuesInRange(rangeStr)
                    return formatNumber(values.minOrNull() ?: 0.0, getStyle(cellId).format)
                }
                // Simple binary arithmetic: A1+B1, A1-B1, A1*B1, A1/B1
                upper.contains('+') -> {
                    val parts = upper.split('+')
                    val a = resolveNum(parts[0].trim())
                    val b = resolveNum(parts[1].trim())
                    return formatNumber(a + b, getStyle(cellId).format)
                }
                upper.contains('-') -> {
                    val parts = upper.split('-')
                    val a = resolveNum(parts[0].trim())
                    val b = resolveNum(parts[1].trim())
                    return formatNumber(a - b, getStyle(cellId).format)
                }
                upper.contains('*') -> {
                    val parts = upper.split('*')
                    val a = resolveNum(parts[0].trim())
                    val b = resolveNum(parts[1].trim())
                    return formatNumber(a * b, getStyle(cellId).format)
                }
                upper.contains('/') -> {
                    val parts = upper.split('/')
                    val a = resolveNum(parts[0].trim())
                    val b = resolveNum(parts[1].trim())
                    if (b == 0.0) return "#DIV/0!"
                    return formatNumber(a / b, getStyle(cellId).format)
                }
                else -> {
                    // Cell reference or literal
                    val num = resolveNum(upper)
                    return formatNumber(num, getStyle(cellId).format)
                }
            }
        } catch (e: Exception) {
            return "#VALUE!"
        }
    }

    private fun resolveNum(token: String): Double {
        val coord = CellCoordinate.parse(token)
        if (coord != null) {
            val innerVal = evaluateDisplayValue(coord.cellId).replace("$", "").replace("%", "").trim()
            return innerVal.toDoubleOrNull() ?: 0.0
        }
        return token.toDoubleOrNull() ?: 0.0
    }

    private fun getValuesInRange(rangeStr: String): List<Double> {
        val results = mutableListOf<Double>()
        if (rangeStr.contains(':')) {
            val parts = rangeStr.split(':')
            val start = CellCoordinate.parse(parts[0].trim())
            val end = CellCoordinate.parse(parts[1].trim())
            if (start != null && end != null) {
                val minCol = minOf(start.col, end.col)
                val maxCol = maxOf(start.col, end.col)
                val minRow = minOf(start.row, end.row)
                val maxRow = maxOf(start.row, end.row)

                for (r in minRow..maxRow) {
                    for (c in minCol..maxCol) {
                        val cellId = "${CellCoordinate.getColumnName(c)}$r"
                        val evaluated = evaluateDisplayValue(cellId).replace("$", "").replace("%", "").trim()
                        val num = evaluated.toDoubleOrNull()
                        if (num != null) results.add(num)
                    }
                }
            }
        } else {
            val num = resolveNum(rangeStr)
            results.add(num)
        }
        return results
    }

    private fun formatNumber(num: Double, format: String): String {
        return when (format) {
            "CURRENCY" -> String.format(Locale.US, "$%,.2f", num)
            "PERCENT" -> String.format(Locale.US, "%.1f%%", num * 100)
            "NUMBER" -> String.format(Locale.US, "%,.2f", num)
            else -> {
                if (num % 1.0 == 0.0) {
                    num.toLong().toString()
                } else {
                    String.format(Locale.US, "%.2f", num)
                }
            }
        }
    }

    private fun formatValue(value: String, format: String): String {
        val num = value.toDoubleOrNull() ?: return value
        return formatNumber(num, format)
    }

    /**
     * Compact serialization:
     * cellId=rawValue|bold|italic|align|format|bgColorHex
     */
    fun serialize(): String {
        val sb = StringBuilder()
        for ((cellId, cellData) in cells) {
            val s = cellData.style
            val b = if (s.isBold) "1" else "0"
            val i = if (s.isItalic) "1" else "0"
            // Escape delimiters
            val escapedVal = cellData.rawValue.replace("\\", "\\\\").replace(";", "\\;").replace("|", "\\|")
            sb.append("$cellId=$escapedVal|$b|$i|${s.align}|${s.format}|${s.bgColorHex};")
        }
        return sb.toString()
    }

    fun toCsv(): String {
        val sb = StringBuilder()
        for (r in 1..maxRows) {
            val rowValues = mutableListOf<String>()
            var hasDataInRow = false
            for (c in 1..maxCols) {
                val cellId = "${CellCoordinate.getColumnName(c)}$r"
                val eval = evaluateDisplayValue(cellId)
                if (eval.isNotEmpty()) hasDataInRow = true
                val escaped = if (eval.contains(",") || eval.contains("\"") || eval.contains("\n")) {
                    "\"${eval.replace("\"", "\"\"")}\""
                } else eval
                rowValues.add(escaped)
            }
            if (hasDataInRow || r <= 10) {
                sb.append(rowValues.joinToString(",")).append("\n")
            }
        }
        return sb.toString()
    }

    companion object {
        fun deserialize(serialized: String, maxCols: Int = 12, maxRows: Int = 40): SpreadsheetGrid {
            val grid = SpreadsheetGrid(maxCols, maxRows)
            if (serialized.isBlank()) return grid

            val entries = serialized.split(";")
            for (entry in entries) {
                if (entry.isBlank()) continue
                val eqIdx = entry.indexOf('=')
                if (eqIdx <= 0) continue
                val cellId = entry.substring(0, eqIdx)
                val rest = entry.substring(eqIdx + 1)
                val parts = rest.split("|")
                val rawVal = parts.getOrNull(0)?.replace("\\;", ";")?.replace("\\|", "|")?.replace("\\\\", "\\") ?: ""
                val isBold = parts.getOrNull(1) == "1"
                val isItalic = parts.getOrNull(2) == "1"
                val align = parts.getOrNull(3) ?: "LEFT"
                val format = parts.getOrNull(4) ?: "GENERAL"
                val bgColor = parts.getOrNull(5) ?: "#FFFFFF"

                grid.setCell(
                    cellId,
                    rawVal,
                    CellStyle(
                        isBold = isBold,
                        isItalic = isItalic,
                        align = align,
                        format = format,
                        bgColorHex = bgColor
                    )
                )
            }
            return grid
        }

        fun fromCsv(csvContent: String): SpreadsheetGrid {
            val grid = SpreadsheetGrid()
            val lines = csvContent.lines()
            for ((rowIndex, line) in lines.withIndex()) {
                if (line.isBlank()) continue
                val r = rowIndex + 1
                // Simple CSV split
                val tokens = parseCsvLine(line)
                for ((colIndex, token) in tokens.withIndex()) {
                    val c = colIndex + 1
                    val cellId = "${CellCoordinate.getColumnName(c)}$r"
                    grid.setCell(cellId, token)
                }
            }
            return grid
        }

        private fun parseCsvLine(line: String): List<String> {
            val result = mutableListOf<String>()
            val sb = StringBuilder()
            var inQuotes = false
            for (ch in line) {
                when {
                    ch == '\"' -> inQuotes = !inQuotes
                    ch == ',' && !inQuotes -> {
                        result.add(sb.toString().trim())
                        sb.clear()
                    }
                    else -> sb.append(ch)
                }
            }
            result.add(sb.toString().trim())
            return result
        }
    }
}
