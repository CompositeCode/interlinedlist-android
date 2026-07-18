package com.interlinedlist.android.feature.messages.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.interlinedlist.android.feature.messages.domain.LinkPreview
import com.interlinedlist.android.feature.messages.domain.Message

/** Stable test tags for message media and link-preview rendering. */
object MessageMediaTags {
    const val IMAGE = "messageMediaImage"
    const val VIDEO = "messageMediaVideo"
    const val LINK_PREVIEW = "messageLinkPreview"
}

/**
 * Renders a message's attached media (Coil images inline, a play-button
 * placeholder for videos) followed by a link-preview card when metadata exists.
 * Draws nothing when the message has neither, so callers can invoke it freely.
 */
@Composable
fun MessageMedia(
    message: Message,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasPreview = message.linkPreview != null
    if (!message.hasMedia && !hasPreview) return

    Column(modifier = modifier.fillMaxWidth()) {
        if (message.hasMedia) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(message.imageUrls, key = { "img-$it" }) { url ->
                    AsyncImage(
                        model = url,
                        contentDescription = "Attached image",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(160.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .testTag(MessageMediaTags.IMAGE),
                    )
                }
                items(message.videoUrls, key = { "vid-$it" }) {
                    VideoThumbnail()
                }
            }
        }
        message.linkPreview?.let { preview ->
            if (message.hasMedia) Spacer(Modifier.height(8.dp))
            LinkPreviewCard(preview = preview, onClick = { onOpenLink(preview.url) })
        }
    }
}

/** A simple placeholder for a video attachment (no in-app player yet). */
@Composable
private fun VideoThumbnail() {
    Box(
        modifier = Modifier
            .size(160.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .testTag(MessageMediaTags.VIDEO),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = "Video attachment",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp),
        )
    }
}

/** A tappable link-preview card built from fetched metadata. */
@Composable
private fun LinkPreviewCard(preview: LinkPreview, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .testTag(MessageMediaTags.LINK_PREVIEW),
    ) {
        if (!preview.imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = preview.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(88.dp)
                    .aspectRatio(1f),
            )
        }
        Column(Modifier.padding(12.dp)) {
            val site = preview.siteName?.takeIf { it.isNotBlank() }
            if (site != null) {
                Text(
                    text = site,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = preview.title ?: preview.url,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            preview.description?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
