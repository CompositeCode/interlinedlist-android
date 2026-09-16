package com.interlinedlist.android.feature.documents.domain

/**
 * The block kinds "Create from…" recognises in a document body.
 *
 * [apiValue] is what the `type` source attribute reads as in a materialized
 * list, so the Type column previews the same word the server stores.
 */
enum class DocumentElementType(val apiValue: String) {
    HEADING("heading"),
    LIST_ITEM("list-item"),
    PARAGRAPH("paragraph"),
    QUOTE("quote"),
    CODE("code"),
}

/**
 * One parsed block of a document body.
 *
 * [section] is the heading this block sits under (empty above the first
 * heading), which is what lets a materialized row say where it came from.
 * [level] is the heading depth (1..6) or the bullet's nesting depth (1 for a
 * top-level bullet); it is 0 for blocks that cannot nest.
 */
data class DocumentElement(
    val type: DocumentElementType,
    val level: Int,
    val text: String,
    val section: String,
)

/**
 * Parses a document body into the blocks "Create from…" works with.
 *
 * Pure and dependency-free so both the row mapping and the plain-text
 * conversion are unit-testable without Compose or a device. It is deliberately
 * a *small* markdown subset — headings, list items, quotes, code blocks and
 * paragraphs — because that is all the conversions distinguish.
 *
 * Nothing parsed here is ever sent: `POST /api/materialize` takes ids, and the
 * server re-derives the rows from its own copy of the document. This exists so
 * the preview can show that mapping before anything is created.
 */
fun parseDocumentElements(markdown: String?): List<DocumentElement> {
    val lines = markdown.orEmpty().replace("\r\n", "\n").split("\n")
    val elements = mutableListOf<DocumentElement>()
    val paragraph = mutableListOf<String>()
    var section = ""

    fun flushParagraph() {
        if (paragraph.isEmpty()) return
        val text = paragraph.joinToString(" ").trim()
        paragraph.clear()
        if (text.isNotEmpty()) {
            elements += DocumentElement(DocumentElementType.PARAGRAPH, 0, text, section)
        }
    }

    var index = 0
    while (index < lines.size) {
        val line = lines[index]

        val fence = FENCE.find(line)
        if (fence != null) {
            flushParagraph()
            val closing = if (fence.groupValues[1].startsWith("`")) BACKTICK_CLOSE else TILDE_CLOSE
            val body = mutableListOf<String>()
            index++
            while (index < lines.size && !closing.matches(lines[index])) {
                body += lines[index]
                index++
            }
            // Past the closing fence, or past the end for an unterminated block.
            index++
            elements += DocumentElement(DocumentElementType.CODE, 0, body.joinToString("\n"), section)
            continue
        }

        if (line.isBlank()) {
            flushParagraph()
            index++
            continue
        }

        val heading = HEADING.find(line)
        if (heading != null) {
            flushParagraph()
            val text = heading.groupValues[2].trim()
            // Everything after this heading belongs to its section.
            section = text
            elements += DocumentElement(
                type = DocumentElementType.HEADING,
                level = heading.groupValues[1].length,
                text = text,
                section = text,
            )
            index++
            continue
        }

        // Checked before the indented-code rule so a nested bullet stays a bullet.
        val item = LIST_ITEM.find(line)
        if (item != null) {
            flushParagraph()
            val indent = item.groupValues[1].replace("\t", "  ").length
            elements += DocumentElement(
                type = DocumentElementType.LIST_ITEM,
                level = indent / INDENT_UNIT + 1,
                text = item.groupValues[2].trim(),
                section = section,
            )
            index++
            continue
        }

        val quote = QUOTE.find(line)
        if (quote != null) {
            flushParagraph()
            elements += DocumentElement(
                type = DocumentElementType.QUOTE,
                level = 0,
                text = quote.groupValues[1].trim(),
                section = section,
            )
            index++
            continue
        }

        // An indented code block, which — as in CommonMark — cannot interrupt a
        // paragraph: it has to start a block of its own.
        if (paragraph.isEmpty() && line.isIndentedCode() && startsBlock(lines, index)) {
            val body = mutableListOf<String>()
            while (
                index < lines.size &&
                (lines[index].isIndentedCode() || (lines[index].isBlank() && continuesIndentedCode(lines, index)))
            ) {
                body += lines[index].withoutCodeIndent()
                index++
            }
            elements += DocumentElement(
                type = DocumentElementType.CODE,
                level = 0,
                text = body.joinToString("\n").trim('\n'),
                section = section,
            )
            continue
        }

        paragraph += line.trim()
        index++
    }
    flushParagraph()
    return elements
}

/**
 * The blocks that become rows when a document is materialized as a list:
 * **each heading and bullet point becomes its own row**.
 *
 * A document with neither is not left with an empty list — every block it does
 * have becomes a row instead, which is the only reading that turns a plain
 * prose document into something.
 */
fun documentListRows(markdown: String?): List<DocumentElement> {
    val elements = parseDocumentElements(markdown)
    val headingsAndBullets = elements.filter {
        it.type == DocumentElementType.HEADING || it.type == DocumentElementType.LIST_ITEM
    }
    return headingsAndBullets.ifEmpty { elements }
}

/**
 * Converts a document body to the plain text a message destination reads as:
 * **paragraphs preserved, code blocks dropped**.
 *
 * Blocks are separated by a blank line so the paragraph structure survives,
 * bullets keep a `•` marker so a list still reads as a list, and inline
 * markdown (links, emphasis, code spans, HTML) is flattened to its text. Code
 * blocks — fenced or indented — are removed entirely rather than pasted into a
 * post that cannot render them.
 *
 * This is a preview: the body that actually reaches the composer is built and
 * size-checked by the server from its own copy of the source.
 */
fun markdownToPlainText(markdown: String?): String {
    val elements = parseDocumentElements(markdown)
    if (elements.isEmpty()) return stripInlineMarkdown(markdown.orEmpty())
    return elements
        .asSequence()
        .filterNot { it.type == DocumentElementType.CODE }
        .mapNotNull { element ->
            stripInlineMarkdown(element.text)
                .takeIf { it.isNotEmpty() }
                ?.let { if (element.type == DocumentElementType.LIST_ITEM) "$BULLET $it" else it }
        }
        .joinToString("\n\n")
        .trim()
}

/**
 * Flattens inline markdown to plain text: images dropped, links reduced to
 * their label, emphasis/strikethrough/code-span markers removed, HTML tags and
 * the handful of entities a body picks up resolved, and whitespace collapsed.
 */
internal fun stripInlineMarkdown(text: String): String {
    if (text.isEmpty()) return ""
    var result = text.replace("\r\n", "\n")
    result = IMAGE.replace(result, "")
    result = INLINE_LINK.replace(result, "$1")
    result = REFERENCE_LINK.replace(result, "$1")
    result = LINK_DEFINITION.replace(result, "")
    result = HEADING_MARKER.replace(result, "")
    result = QUOTE_MARKER.replace(result, "")
    result = LIST_MARKER.replace(result, "")
    result = STRONG.replace(result, "$2")
    result = EMPHASIS.replace(result, "$2")
    result = STRIKETHROUGH.replace(result, "$1")
    result = CODE_SPAN.replace(result, "$1")
    result = HTML_TAG.replace(result, "")
    ENTITIES.forEach { (entity, replacement) -> result = result.replace(entity, replacement) }
    return WHITESPACE.replace(result, " ").trim()
}

/** The marker a bullet keeps once its markdown is gone. */
private const val BULLET = "•"

/** Two spaces of leading indentation is one level of bullet nesting. */
private const val INDENT_UNIT = 2

private val HEADING = Regex("^(#{1,6})\\s+(.*)$")
private val LIST_ITEM = Regex("^(\\s*)(?:[-*+]|\\d+[.)])\\s+(.*)$")
private val QUOTE = Regex("^\\s*>\\s?(.*)$")
private val FENCE = Regex("^\\s*(`{3,}|~{3,})")
private val BACKTICK_CLOSE = Regex("^\\s*`{3,}\\s*$")
private val TILDE_CLOSE = Regex("^\\s*~{3,}\\s*$")

private val IMAGE = Regex("!\\[[^\\]]*\\]\\([^)]*\\)")
private val INLINE_LINK = Regex("\\[([^\\]]*)\\]\\([^)]*\\)")
private val REFERENCE_LINK = Regex("\\[([^\\]]+)\\]\\[[^\\]]*\\]")
private val LINK_DEFINITION = Regex("^\\s*\\[[^\\]]+\\]:\\s*\\S+.*$", RegexOption.MULTILINE)
private val HEADING_MARKER = Regex("^\\s{0,3}#{1,6}\\s+", RegexOption.MULTILINE)
private val QUOTE_MARKER = Regex("^\\s*>\\s?", RegexOption.MULTILINE)
private val LIST_MARKER = Regex("^\\s*(?:[-*+]|\\d+[.)])\\s+", RegexOption.MULTILINE)
private val STRONG = Regex("(\\*\\*|__)(.+?)\\1")
private val EMPHASIS = Regex("(\\*|_)(.+?)\\1")
private val STRIKETHROUGH = Regex("~~(.+?)~~")
private val CODE_SPAN = Regex("`([^`]+)`")
private val HTML_TAG = Regex("<[^>]+>")
private val WHITESPACE = Regex("\\s+")

private val ENTITIES = listOf(
    "&nbsp;" to " ",
    "&amp;" to "&",
    "&lt;" to "<",
    "&gt;" to ">",
    "&quot;" to "\"",
    "&#39;" to "'",
)

private fun String.isIndentedCode(): Boolean = startsWith("    ") || startsWith("\t")

private fun String.withoutCodeIndent(): String = when {
    startsWith("    ") -> substring(4)
    startsWith("\t") -> substring(1)
    else -> trim()
}

/** True when the line at [index] opens a block rather than continuing prose. */
private fun startsBlock(lines: List<String>, index: Int): Boolean =
    index == 0 || lines[index - 1].isBlank()

/** True when more indented code follows the blank line at [index]. */
private fun continuesIndentedCode(lines: List<String>, index: Int): Boolean {
    var next = index
    while (next < lines.size && lines[next].isBlank()) next++
    return next < lines.size && lines[next].isIndentedCode()
}
