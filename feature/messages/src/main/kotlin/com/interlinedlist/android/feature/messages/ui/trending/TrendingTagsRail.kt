package com.interlinedlist.android.feature.messages.ui.trending

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.interlinedlist.android.core.designsystem.theme.InterlinedListTheme
import com.interlinedlist.android.feature.messages.domain.TrendingTag
import com.interlinedlist.android.feature.messages.ui.relativeTime
import java.time.Instant

/** Stable test tags for the trending-tags rail. */
object TrendingTagsRailTags {
    const val RAIL = "trendingTagsRail"
    const val TITLE = "trendingTagsTitle"
    const val CHIPS = "trendingTagsChips"
    const val PROGRESS = "trendingTagsProgress"
    const val EMPTY = "trendingTagsEmpty"
    const val ERROR = "trendingTagsError"
    const val RETRY = "trendingTagsRetry"

    /** Prefix for one trending tag chip; suffixed with the tag itself. */
    const val CHIP_PREFIX = "trendingTag_"

    fun chipTag(tag: String): String = CHIP_PREFIX + tag
}

/** What the surface says when the instance genuinely has no trending tags. */
const val NO_TRENDING_TAGS = "No trending tags yet. Tag a message to start one."

/**
 * The trending tags, as one horizontally scrolling row of doors into tag feeds.
 *
 * Placed at the top of the feed list (and inside its empty state) rather than on
 * a screen of its own: a discovery surface nobody walks past is not discovery.
 * It scrolls away with the feed because the top of this screen is already spoken
 * for by the view-preference switcher and the composer.
 *
 * Every state is rendered explicitly — an instance with nothing trending says so,
 * and a failed lookup says something different — so the rail never degenerates
 * into an unexplained blank strip.
 */
@Composable
fun TrendingTagsRail(
    state: TrendingTagsUiState,
    onOpenTag: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 8.dp)
            .testTag(TrendingTagsRailTags.RAIL),
    ) {
        Text(
            // "this week" is the window the app asked for; the response does not
            // report one, so nothing here may claim a period it did not request.
            text = state.title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .testTag(TrendingTagsRailTags.TITLE),
        )
        Spacer(Modifier.height(6.dp))
        when (state.status) {
            TrendingTagsStatus.LOADING -> Box(Modifier.padding(horizontal = 16.dp)) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier
                        .size(20.dp)
                        .testTag(TrendingTagsRailTags.PROGRESS),
                )
            }

            TrendingTagsStatus.TAGS -> LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TrendingTagsRailTags.CHIPS),
            ) {
                items(state.tags, key = { it.tag }) { trending ->
                    TrendingTagChip(trending = trending, onClick = { onOpenTag(trending.tag) })
                }
            }

            TrendingTagsStatus.EMPTY -> Text(
                text = NO_TRENDING_TAGS,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .testTag(TrendingTagsRailTags.EMPTY),
            )

            TrendingTagsStatus.ERROR -> Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
            ) {
                Text(
                    text = state.errorMessage.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .testTag(TrendingTagsRailTags.ERROR),
                )
                TextButton(
                    onClick = onRetry,
                    modifier = Modifier.testTag(TrendingTagsRailTags.RETRY),
                ) {
                    Text("Retry")
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/**
 * One tag, with its count. The label is the tag exactly as stored — free-form
 * text that may contain spaces and punctuation, never prettified into a hashtag
 * — truncated rather than allowed to push the rest of the rail off screen.
 */
@Composable
private fun TrendingTagChip(trending: TrendingTag, onClick: () -> Unit) {
    val description = trendingTagDescription(trending)
    AssistChip(
        onClick = onClick,
        label = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = trending.tag,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 180.dp),
                )
                if (trending.count > 0) {
                    Spacer(Modifier.size(6.dp))
                    Text(
                        text = trending.count.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        modifier = Modifier
            .semantics { contentDescription = description }
            .testTag(TrendingTagsRailTags.chipTag(trending.tag)),
    )
}

/**
 * Spoken label for a trending chip: the tag, how many messages carry it, and —
 * the one thing the payload reports about recency — when it was last used.
 *
 * [now] is injectable so the wording stays deterministic in tests.
 */
fun trendingTagDescription(trending: TrendingTag, now: Instant = Instant.now()): String =
    buildString {
        append(trending.tag)
        if (trending.count > 0) {
            append(", ${trending.count} ")
            append(if (trending.count == 1) "message" else "messages")
        }
        val recency = relativeTime(trending.lastUsedAt, now)
        if (recency.isNotBlank()) append(", last used $recency")
    }

@Preview(showBackground = true)
@Composable
private fun TrendingTagsRailPreview() {
    InterlinedListTheme {
        TrendingTagsRail(
            state = TrendingTagsUiState(
                tags = listOf(
                    TrendingTag("Lego", count = 2, lastUsedAt = "2026-09-12T20:40:05.777Z"),
                    TrendingTag("life is short, o brave girl", count = 1),
                ),
            ),
            onOpenTag = {},
            onRetry = {},
        )
    }
}
