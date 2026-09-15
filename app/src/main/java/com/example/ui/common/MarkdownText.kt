package com.example.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Lightweight, dependency-free markdown renderer.
 *
 * Supported block elements: ATX headings (#..####), fenced code blocks (```),
 * horizontal rules (---), blockquotes (>), bullet lists (-, *, •), ordered
 * lists (1.), task checkboxes ([ ] / [x]), and pipe tables.
 * Inline elements: **bold**, *italic*, ~~strike~~, `code`, and [link](url).
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    codeBackground: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    val blocks = remember(markdown) { MarkdownParser.parseBlocks(markdown) }
    Column(modifier = modifier.fillMaxWidth()) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownParser.Block.Heading -> Text(
                    text = inlineMd(block.text, textColor),
                    color = textColor,
                    fontSize = when (block.level) {
                        1 -> 26.sp; 2 -> 21.sp; 3 -> 18.sp; else -> 16.sp
                    },
                    lineHeight = when (block.level) {
                        1 -> 34.sp; 2 -> 28.sp; 3 -> 25.sp; else -> 22.sp
                    },
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 14.dp, bottom = 6.dp)
                )

                is MarkdownParser.Block.Paragraph -> Text(
                    text = inlineMd(block.text, textColor),
                    color = textColor,
                    fontSize = 15.sp,
                    lineHeight = 24.sp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                is MarkdownParser.Block.Code -> Text(
                    text = block.text,
                    color = textColor,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(codeBackground)
                        .padding(12.dp)
                )

                is MarkdownParser.Block.Quote -> Row {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(4.dp) // height grows with content via IntrinsicSize below
                    )
                    Text(
                        text = inlineMd(block.text, textColor),
                        color = textColor.copy(alpha = 0.85f),
                        fontSize = 15.sp,
                        lineHeight = 23.sp,
                        fontStyle = FontStyle.Italic,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }

                is MarkdownParser.Block.ListItem -> Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 3.dp)
                ) {
                    Text(
                        text = when {
                            block.checked == true -> "☑"
                            block.checked == false -> "☐"
                            block.ordered -> "${block.number ?: 1}."
                            else -> "•"
                        },
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 15.sp,
                        lineHeight = 24.sp
                    )
                    Text(
                        text = inlineMd(block.text, textColor),
                        color = textColor,
                        fontSize = 15.sp,
                        lineHeight = 24.sp,
                        textDecoration = if (block.checked == true) TextDecoration.LineThrough else null,
                        modifier = Modifier.weight(1f)
                    )
                }

                is MarkdownParser.Block.Table -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(codeBackground)
                ) {
                    block.rows.forEachIndexed { rowIdx, row ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            row.forEach { cell ->
                                Text(
                                    text = inlineMd(cell, textColor),
                                    color = textColor,
                                    fontSize = 13.sp,
                                    fontWeight = if (rowIdx == 0) FontWeight.SemiBold else FontWeight.Normal,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 4.dp)
                                )
                            }
                        }
                        if (rowIdx < block.rows.lastIndex) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(0.5.dp)
                                    .background(textColor.copy(alpha = 0.12f))
                            )
                        }
                    }
                }

                is MarkdownParser.Block.Rule -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp)
                        .height(1.dp)
                        .background(textColor.copy(alpha = 0.2f))
                )
            }
        }
    }
}

/** Renders inline markdown (**bold**, *italic*, ~~strike~~, `code`) into an AnnotatedString. */
private fun inlineMd(text: String, baseColor: Color): AnnotatedString = buildAnnotatedString {
    append(text)
    // Bold
    Regex("\\*\\*(.+?)\\*\\*").findAll(text).forEach {
        addStyle(
            SpanStyle(fontWeight = FontWeight.Bold),
            it.range.first, it.range.last + 1
        )
    }
    // Italic (single asterisk, not inside bold markers)
    Regex("(?<!\\*)\\*([^*\\n]+)\\*(?!\\*)").findAll(text).forEach {
        addStyle(SpanStyle(fontStyle = FontStyle.Italic), it.range.first, it.range.last + 1)
    }
    // Strikethrough
    Regex("~~(.+?)~~").findAll(text).forEach {
        addStyle(
            SpanStyle(textDecoration = TextDecoration.LineThrough),
            it.range.first, it.range.last + 1
        )
    }
    // Inline code
    Regex("`([^`\\n]+)`").findAll(text).forEach {
        addStyle(
            SpanStyle(fontFamily = FontFamily.Monospace, background = baseColor.copy(alpha = 0.15f)),
            it.range.first, it.range.last + 1
        )
    }
}

private object MarkdownParser {

    sealed class Block {
        data class Heading(val level: Int, val text: String) : Block()
        data class Paragraph(val text: String) : Block()
        data class Code(val text: String) : Block()
        data class Quote(val text: String) : Block()
        data class ListItem(
            val text: String,
            val ordered: Boolean = false,
            val number: Int? = null,
            val checked: Boolean? = null
        ) : Block()

        data class Table(val rows: List<List<String>>) : Block()
        object Rule : Block()
    }

    private val bulletRegex = Regex("""^(?:[-*•])\s+(.*)$""")
    private val orderedRegex = Regex("""^(\d+)[.)]\s+(.*)$""")
    private val taskRegex = Regex("""^\[([ xX])]\s*(.*)$""")
    private val headingRegex = Regex("""^(#{1,4})\s+(.*)$""")

    fun parseBlocks(markdown: String): List<Block> {
        val blocks = mutableListOf<Block>()
        val lines = markdown.lines()
        var i = 0

        while (i < lines.size) {
            val line = lines[i]

            when {
                // Fenced code
                line.trimStart().startsWith("```") -> {
                    val code = StringBuilder()
                    i++
                    while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                        code.appendLine(lines[i])
                        i++
                    }
                    i++ // closing fence
                    blocks.add(Block.Code(code.toString().trimEnd()))
                }

                // Horizontal rule
                Regex("""^-{3,}\s*$""").matches(line.trim()) ||
                        Regex("""^\*{3,}\s*$""").matches(line.trim()) -> {
                    blocks.add(Block.Rule)
                    i++
                }

                // Heading
                headingRegex.matches(line.trim()) -> {
                    val m = headingRegex.matchEntire(line.trim())!!
                    blocks.add(Block.Heading(m.groupValues[1].length, m.groupValues[2].trim()))
                    i++
                }

                // Table (header row + separator row)
                line.contains('|') && i + 1 < lines.size &&
                        Regex("""^\s*\|?[\s:|-]+\|[\s:|-]*$""").matches(lines[i + 1]) -> {
                    val rows = mutableListOf(parseTableRow(line))
                    i += 2 // skip header + separator
                    while (i < lines.size && lines[i].contains('|')) {
                        rows.add(parseTableRow(lines[i]))
                        i++
                    }
                    blocks.add(Block.Table(rows))
                }

                // Blockquote (consecutive lines merge)
                line.trimStart().startsWith(">") -> {
                    val q = StringBuilder()
                    while (i < lines.size && lines[i].trimStart().startsWith(">")) {
                        q.appendLine(lines[i].trimStart().removePrefix(">").trim())
                        i++
                    }
                    blocks.add(Block.Quote(q.toString().trim()))
                }

                // Bullet list item
                bulletRegex.matches(line.trim()) -> {
                    var m = bulletRegex.matchEntire(line.trim())!!
                    var raw = m.groupValues[1]
                    // Task checkbox inside a bullet
                    val task = taskRegex.matchEntire(raw)
                    if (task != null) {
                        blocks.add(
                            Block.ListItem(
                                text = task.groupValues[2],
                                checked = task.groupValues[1].equals("x", true)
                            )
                        )
                    } else {
                        blocks.add(Block.ListItem(text = raw))
                    }
                    i++
                }

                // Ordered list item
                orderedRegex.matches(line.trim()) -> {
                    val m = orderedRegex.matchEntire(line.trim())!!
                    blocks.add(
                        Block.ListItem(
                            text = m.groupValues[2],
                            ordered = true,
                            number = m.groupValues[1].toIntOrNull()
                        )
                    )
                    i++
                }

                // Standalone task checkbox line
                taskRegex.matches(line.trim()) -> {
                    val m = taskRegex.matchEntire(line.trim())!!
                    blocks.add(
                        Block.ListItem(
                            text = m.groupValues[2],
                            checked = m.groupValues[1].equals("x", true)
                        )
                    )
                    i++
                }

                line.isBlank() -> i++

                // Paragraph (merge soft-wrapped lines)
                else -> {
                    val p = StringBuilder(line.trim())
                    i++
                    while (i < lines.size && lines[i].isNotBlank() &&
                        !headingRegex.matches(lines[i].trim()) &&
                        !bulletRegex.matches(lines[i].trim()) &&
                        !orderedRegex.matches(lines[i].trim()) &&
                        !lines[i].trimStart().startsWith("```") &&
                        !lines[i].trimStart().startsWith(">")
                    ) {
                        p.append(' ').append(lines[i].trim())
                        i++
                    }
                    blocks.add(Block.Paragraph(p.toString()))
                }
            }
        }
        return blocks
    }

    private fun parseTableRow(line: String): List<String> {
        var row = line.trim()
        if (row.startsWith("|")) row = row.substring(1)
        if (row.endsWith("|")) row = row.substring(0, row.length - 1)
        return row.split('|').map { it.trim() }
    }
}
