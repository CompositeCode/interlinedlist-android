package com.interlinedlist.android.feature.documents.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/** Renders the [parseMarkdown] block model into simple styled Compose text. */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
) {
    val blocks = parseMarkdown(markdown)
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
    ) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Heading -> Text(
                    text = block.spans.toAnnotated(),
                    style = when (block.level) {
                        1 -> MaterialTheme.typography.headlineSmall
                        2 -> MaterialTheme.typography.titleLarge
                        else -> MaterialTheme.typography.titleMedium
                    },
                    modifier = Modifier.padding(vertical = 4.dp),
                )

                is MarkdownBlock.BulletItem -> Row(modifier = Modifier.padding(vertical = 2.dp)) {
                    Text("•  ", style = MaterialTheme.typography.bodyLarge)
                    Text(block.spans.toAnnotated(), style = MaterialTheme.typography.bodyLarge)
                }

                is MarkdownBlock.NumberedItem -> Row(modifier = Modifier.padding(vertical = 2.dp)) {
                    Text("${block.number}.  ", style = MaterialTheme.typography.bodyLarge)
                    Text(block.spans.toAnnotated(), style = MaterialTheme.typography.bodyLarge)
                }

                is MarkdownBlock.Paragraph -> Text(
                    text = block.spans.toAnnotated(),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
        }
    }
}

private fun List<InlineSpan>.toAnnotated() = buildAnnotatedString {
    this@toAnnotated.forEach { span ->
        if (span.bold) {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(span.text) }
        } else {
            append(span.text)
        }
    }
}
