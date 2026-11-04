package dev.nanobot.android

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
    data class Code(val language: String, val code: String) : MarkdownBlock
    data class Quote(val text: String) : MarkdownBlock
    data class ListItem(val marker: String, val text: String) : MarkdownBlock
    data class Table(
        val headers: List<String>,
        val aligns: List<TableAlign>,
        val rows: List<List<String>>,
    ) : MarkdownBlock
    data object Rule : MarkdownBlock
}

internal enum class TableAlign { Left, Center, Right }

private val headingPattern = Regex("""^(#{1,6})\s+(.*)$""")
private val rulePattern = Regex("""^\s{0,3}(?:[-*_]\s*){3,}$""")
private val listPattern = Regex("""^\s*([-*+]|\d+\.)\s+(.*)$""")
private val delimiterCellPattern = Regex(""":?-+:?""")

internal fun parseMarkdownBlocks(source: String): List<MarkdownBlock> {
    val lines = source.replace("\r\n", "\n").replace('\r', '\n').split('\n')
    val blocks = mutableListOf<MarkdownBlock>()
    var index = 0
    while (index < lines.size) {
        val line = lines[index]
        val trimmed = line.trimStart()
        when {
            trimmed.startsWith("```") -> {
                val language = trimmed.removePrefix("```").trim()
                val code = StringBuilder()
                index += 1
                while (index < lines.size && !lines[index].trimStart().startsWith("```")) {
                    if (code.isNotEmpty()) code.append('\n')
                    code.append(lines[index])
                    index += 1
                }
                if (index < lines.size) index += 1
                blocks += MarkdownBlock.Code(language, code.toString())
            }

            trimmed.startsWith(">") -> {
                val quote = mutableListOf<String>()
                while (index < lines.size && lines[index].trimStart().startsWith(">")) {
                    quote += lines[index].trimStart().removePrefix(">").removePrefix(" ")
                    index += 1
                }
                blocks += MarkdownBlock.Quote(quote.joinToString(" "))
            }

            isTableStart(lines, index) -> {
                val headers = splitTableRow(lines[index])
                val aligns = parseTableAligns(lines[index + 1])!!
                index += 2
                val rows = mutableListOf<List<String>>()
                while (index < lines.size && lines[index].isNotBlank() &&
                    lines[index].contains('|') && !isTableStart(lines, index)
                ) {
                    rows += normalizeCells(splitTableRow(lines[index]), aligns.size)
                    index += 1
                }
                blocks += MarkdownBlock.Table(headers, aligns, rows)
            }

            headingPattern.matches(line) -> {
                val match = headingPattern.matchEntire(line)!!
                blocks += MarkdownBlock.Heading(
                    level = match.groupValues[1].length,
                    text = match.groupValues[2].trim(),
                )
                index += 1
            }

            rulePattern.matches(line) -> {
                blocks += MarkdownBlock.Rule
                index += 1
            }

            listPattern.matches(line) -> {
                val match = listPattern.matchEntire(line)!!
                val marker = match.groupValues[1].let { if (it.endsWith(".")) it else "•" }
                blocks += MarkdownBlock.ListItem(
                    marker = marker,
                    text = match.groupValues[2],
                )
                index += 1
            }

            line.isBlank() -> index += 1

            else -> {
                val paragraph = mutableListOf(line.trim())
                index += 1
                while (index < lines.size && isParagraphContinuation(lines[index]) &&
                    !isTableStart(lines, index)
                ) {
                    paragraph += lines[index].trim()
                    index += 1
                }
                blocks += MarkdownBlock.Paragraph(paragraph.joinToString(" "))
            }
        }
    }
    return blocks
}

private fun isParagraphContinuation(line: String): Boolean {
    val trimmed = line.trimStart()
    if (line.isBlank()) return false
    if (trimmed.startsWith("```") || trimmed.startsWith(">")) return false
    if (headingPattern.matches(line) || rulePattern.matches(line) || listPattern.matches(line)) return false
    return true
}

/** A header row followed by a delimiter row, as in GFM tables. */
private fun isTableStart(lines: List<String>, index: Int): Boolean {
    val header = lines.getOrNull(index) ?: return false
    val delimiter = lines.getOrNull(index + 1) ?: return false
    if (header.isBlank() || !header.contains('|')) return false
    val aligns = parseTableAligns(delimiter) ?: return false
    return splitTableRow(header).size == aligns.size
}

private fun parseTableAligns(line: String): List<TableAlign>? {
    if (!line.contains('|')) return null
    val cells = splitTableRow(line)
    if (cells.isEmpty()) return null
    return cells.map { cell ->
        if (!delimiterCellPattern.matches(cell)) return null
        when {
            cell.startsWith(":") && cell.endsWith(":") -> TableAlign.Center
            cell.endsWith(":") -> TableAlign.Right
            else -> TableAlign.Left
        }
    }
}

/** Splits a GFM row on unescaped pipes, dropping the optional outer pipes. */
private fun splitTableRow(line: String): List<String> {
    val cells = mutableListOf<String>()
    val cell = StringBuilder()
    var index = 0
    while (index < line.length) {
        when {
            line[index] == '\\' && index + 1 < line.length && line[index + 1] == '|' -> {
                cell.append('|')
                index += 2
            }

            line[index] == '|' -> {
                cells += cell.toString().trim()
                cell.clear()
                index += 1
            }

            else -> {
                cell.append(line[index])
                index += 1
            }
        }
    }
    cells += cell.toString().trim()
    if (cells.firstOrNull()?.isEmpty() == true) cells.removeAt(0)
    if (cells.lastOrNull()?.isEmpty() == true) cells.removeAt(cells.lastIndex)
    return cells
}

private fun normalizeCells(cells: List<String>, size: Int): List<String> =
    if (cells.size >= size) cells.take(size) else cells + List(size - cells.size) { "" }

@Composable
internal fun MarkdownText(text: String) {
    val blocks = remember(text) { parseMarkdownBlocks(text) }
    val linkColor = MaterialTheme.colorScheme.primary
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Heading -> {
                    val style = when (block.level) {
                        1 -> MaterialTheme.typography.headlineSmall
                        2 -> MaterialTheme.typography.titleLarge
                        3 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    }
                    Text(
                        text = inlineMarkdown(block.text, linkColor, codeBackground),
                        style = style,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                is MarkdownBlock.Paragraph -> Text(
                    text = inlineMarkdown(block.text, linkColor, codeBackground),
                    style = MaterialTheme.typography.bodyMedium,
                )

                is MarkdownBlock.Code -> Surface(
                    color = codeBackground.copy(alpha = 0.7f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(10.dp)) {
                        if (block.language.isNotBlank()) {
                            Text(
                                block.language,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = block.code.ifBlank { " " },
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                        )
                    }
                }

                is MarkdownBlock.Quote -> Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                ) {
                    Box(
                        Modifier
                            .width(3.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.primary),
                    )
                    Text(
                        text = inlineMarkdown(block.text, linkColor, codeBackground),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 10.dp),
                    )
                }

                is MarkdownBlock.ListItem -> Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        block.marker,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(24.dp),
                    )
                    Text(
                        text = inlineMarkdown(block.text, linkColor, codeBackground),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                }

                is MarkdownBlock.Table -> MarkdownTable(block, linkColor, codeBackground)

                MarkdownBlock.Rule -> HorizontalDivider()
            }
        }
    }
}

@Composable
private fun MarkdownTable(
    table: MarkdownBlock.Table,
    linkColor: Color,
    codeBackground: Color,
) {
    val desired = remember(table) {
        val columns = table.headers.size.coerceAtLeast(1)
        val lengths = IntArray(columns)
        val longestWords = IntArray(columns)
        (listOf(table.headers) + table.rows).forEach { row ->
            row.forEachIndexed { index, cell ->
                if (index < columns) {
                    lengths[index] = maxOf(lengths[index], cell.length)
                    cell.split(' ', '\t', '\n').forEach { word ->
                        longestWords[index] = maxOf(longestWords[index], word.length)
                    }
                }
            }
        }
        val minimums = longestWords.map { (it * 6 + 16).coerceIn(64, 200).dp }
        // Roughly one line of bodySmall per character, clamped so long prose still wraps.
        val ideal = lengths.map { (it * 7 + 20).coerceIn(64, 200).dp }
        ideal.zip(minimums) { width, minimum -> maxOf(width, minimum) } to minimums
    }
    val baseWidths = desired.first
    val minimums = desired.second
    BoxWithConstraints {
        val baseTotal = baseWidths.fold(0.dp) { total, width -> total + width }
        val minimumTotal = minimums.fold(0.dp) { total, width -> total + width }
        val widths = when {
            baseTotal <= maxWidth -> baseWidths
            minimumTotal >= maxWidth -> minimums
            else -> {
                val factor = (maxWidth - minimumTotal).value / (baseTotal - minimumTotal).value
                baseWidths.zip(minimums) { width, minimum ->
                    minimum + (width - minimum) * factor
                }
            }
        }
        val totalWidth = widths.fold(0.dp) { total, width -> total + width }
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            shape = MaterialTheme.shapes.small,
        ) {
            Box(Modifier.horizontalScroll(rememberScrollState())) {
                Column(Modifier.width(totalWidth)) {
                    TableRow(table.headers, table.aligns, widths, header = true, linkColor, codeBackground)
                    table.rows.forEach { row ->
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        TableRow(row, table.aligns, widths, header = false, linkColor, codeBackground)
                    }
                }
            }
        }
    }
}

@Composable
private fun TableRow(
    cells: List<String>,
    aligns: List<TableAlign>,
    widths: List<Dp>,
    header: Boolean,
    linkColor: Color,
    codeBackground: Color,
) {
    Row(
        modifier = Modifier
            .width(widths.fold(0.dp) { total, width -> total + width })
            .height(IntrinsicSize.Min),
    ) {
        cells.forEachIndexed { index, cell ->
            if (index > 0) {
                Box(
                    Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.outlineVariant),
                )
            }
            Text(
                text = inlineMarkdown(cell, linkColor, codeBackground),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (header) FontWeight.SemiBold else null,
                textAlign = when (aligns.getOrElse(index) { TableAlign.Left }) {
                    TableAlign.Left -> TextAlign.Start
                    TableAlign.Center -> TextAlign.Center
                    TableAlign.Right -> TextAlign.End
                },
                modifier = Modifier
                    .width(widths.getOrElse(index) { 100.dp })
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}

internal fun inlineMarkdown(text: String, linkColor: Color, codeBackground: Color): AnnotatedString =
    buildAnnotatedString {
        appendInlineMarkdown(text, linkColor, codeBackground)
    }

private fun AnnotatedString.Builder.appendInlineMarkdown(
    text: String,
    linkColor: Color,
    codeBackground: Color,
) {
    var index = 0
    while (index < text.length) {
        val char = text[index]
        when {
            char == '\\' && index + 1 < text.length -> {
                append(text[index + 1])
                index += 2
            }

            char == '`' -> {
                val end = text.indexOf('`', index + 1)
                if (end > index) {
                    withStyle(
                        SpanStyle(
                            background = codeBackground,
                            fontFamily = FontFamily.Monospace,
                        ),
                    ) {
                        append(text.substring(index + 1, end))
                    }
                    index = end + 1
                } else {
                    append(char)
                    index += 1
                }
            }

            text.startsWith("**", index) || text.startsWith("__", index) -> {
                val marker = text.substring(index, index + 2)
                val end = text.indexOf(marker, index + 2)
                if (end > index + 2) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        appendInlineMarkdown(text.substring(index + 2, end), linkColor, codeBackground)
                    }
                    index = end + 2
                } else {
                    append(marker)
                    index += 2
                }
            }

            text.startsWith("~~", index) -> {
                val end = text.indexOf("~~", index + 2)
                if (end > index + 2) {
                    withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                        appendInlineMarkdown(text.substring(index + 2, end), linkColor, codeBackground)
                    }
                    index = end + 2
                } else {
                    append("~~")
                    index += 2
                }
            }

            char == '*' || char == '_' -> {
                val end = text.indexOf(char, index + 1)
                val opens = char == '*' || index == 0 || !text[index - 1].isLetterOrDigit()
                val closes = end + 1 >= text.length || !text[end + 1].isLetterOrDigit()
                if (end > index + 1 && opens && closes) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        appendInlineMarkdown(text.substring(index + 1, end), linkColor, codeBackground)
                    }
                    index = end + 1
                } else {
                    append(char)
                    index += 1
                }
            }

            char == '[' -> {
                val labelEnd = text.indexOf(']', index + 1)
                val urlStart = labelEnd + 1
                if (labelEnd > index && urlStart < text.length && text[urlStart] == '(') {
                    val urlEnd = text.indexOf(')', urlStart + 1)
                    if (urlEnd > urlStart) {
                        val label = text.substring(index + 1, labelEnd)
                        val url = text.substring(urlStart + 1, urlEnd)
                        withLink(LinkAnnotation.Url(url)) {
                            withStyle(
                                SpanStyle(
                                    color = linkColor,
                                    textDecoration = TextDecoration.Underline,
                                ),
                            ) {
                                append(label)
                            }
                        }
                        index = urlEnd + 1
                    } else {
                        append(char)
                        index += 1
                    }
                } else {
                    append(char)
                    index += 1
                }
            }

            else -> {
                append(char)
                index += 1
            }
        }
    }
}
