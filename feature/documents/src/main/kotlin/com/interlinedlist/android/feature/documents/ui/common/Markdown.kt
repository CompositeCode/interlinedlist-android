package com.interlinedlist.android.feature.documents.ui.common

/**
 * A tiny, dependency-free markdown model covering the subset we render in the
 * preview: headings (`#`..`###`), unordered (`-`/`*`) and ordered (`1.`) list
 * items, and paragraphs — each with inline `**bold**` spans. Parsing is a pure
 * function so it can be unit-tested without Compose.
 */
sealed interface MarkdownBlock {
    /** [level] is 1..3 for `#`..`###`. */
    data class Heading(val level: Int, val spans: List<InlineSpan>) : MarkdownBlock
    data class BulletItem(val spans: List<InlineSpan>) : MarkdownBlock
    data class NumberedItem(val number: Int, val spans: List<InlineSpan>) : MarkdownBlock
    data class Paragraph(val spans: List<InlineSpan>) : MarkdownBlock
}

/** An inline run of text, optionally bold. */
data class InlineSpan(val text: String, val bold: Boolean = false)

/** Parses [markdown] into a flat list of blocks. Blank lines are skipped. */
fun parseMarkdown(markdown: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    for (rawLine in markdown.lines()) {
        val line = rawLine.trimEnd()
        if (line.isBlank()) continue

        val heading = Regex("^(#{1,3})\\s+(.*)$").find(line)
        if (heading != null) {
            val level = heading.groupValues[1].length
            blocks += MarkdownBlock.Heading(level, parseInline(heading.groupValues[2]))
            continue
        }

        val bullet = Regex("^\\s*[-*]\\s+(.*)$").find(line)
        if (bullet != null) {
            blocks += MarkdownBlock.BulletItem(parseInline(bullet.groupValues[1]))
            continue
        }

        val numbered = Regex("^\\s*(\\d+)\\.\\s+(.*)$").find(line)
        if (numbered != null) {
            val number = numbered.groupValues[1].toIntOrNull() ?: 1
            blocks += MarkdownBlock.NumberedItem(number, parseInline(numbered.groupValues[2]))
            continue
        }

        blocks += MarkdownBlock.Paragraph(parseInline(line))
    }
    return blocks
}

/** Splits a line into alternating normal/bold runs on `**...**` markers. */
internal fun parseInline(text: String): List<InlineSpan> {
    if (!text.contains("**")) return listOf(InlineSpan(text))
    val spans = mutableListOf<InlineSpan>()
    val matcher = Regex("\\*\\*(.+?)\\*\\*")
    var lastEnd = 0
    for (match in matcher.findAll(text)) {
        if (match.range.first > lastEnd) {
            spans += InlineSpan(text.substring(lastEnd, match.range.first))
        }
        spans += InlineSpan(match.groupValues[1], bold = true)
        lastEnd = match.range.last + 1
    }
    if (lastEnd < text.length) {
        spans += InlineSpan(text.substring(lastEnd))
    }
    return spans.ifEmpty { listOf(InlineSpan(text)) }
}
