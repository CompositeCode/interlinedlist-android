package com.interlinedlist.android.feature.documents.ui

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.feature.documents.ui.common.MarkdownBlock
import com.interlinedlist.android.feature.documents.ui.common.parseInline
import com.interlinedlist.android.feature.documents.ui.common.parseMarkdown
import org.junit.Test

class MarkdownTest {

    @Test
    fun `parses headings at levels one through three`() {
        val blocks = parseMarkdown("# H1\n## H2\n### H3")
        assertThat(blocks).hasSize(3)
        assertThat((blocks[0] as MarkdownBlock.Heading).level).isEqualTo(1)
        assertThat((blocks[1] as MarkdownBlock.Heading).level).isEqualTo(2)
        assertThat((blocks[2] as MarkdownBlock.Heading).level).isEqualTo(3)
    }

    @Test
    fun `parses bullet and numbered list items`() {
        val blocks = parseMarkdown("- first\n* second\n1. third")
        assertThat(blocks[0]).isInstanceOf(MarkdownBlock.BulletItem::class.java)
        assertThat(blocks[1]).isInstanceOf(MarkdownBlock.BulletItem::class.java)
        val numbered = blocks[2] as MarkdownBlock.NumberedItem
        assertThat(numbered.number).isEqualTo(1)
    }

    @Test
    fun `blank lines are skipped and plain lines become paragraphs`() {
        val blocks = parseMarkdown("hello\n\n\nworld")
        assertThat(blocks).hasSize(2)
        assertThat(blocks[0]).isInstanceOf(MarkdownBlock.Paragraph::class.java)
    }

    @Test
    fun `inline parsing splits bold runs from surrounding text`() {
        val spans = parseInline("a **bold** c")
        assertThat(spans).hasSize(3)
        assertThat(spans[0].bold).isFalse()
        assertThat(spans[1].bold).isTrue()
        assertThat(spans[1].text).isEqualTo("bold")
        assertThat(spans[2].bold).isFalse()
    }

    @Test
    fun `inline parsing returns a single span when there is no bold`() {
        val spans = parseInline("plain text")
        assertThat(spans).hasSize(1)
        assertThat(spans[0].bold).isFalse()
    }
}
