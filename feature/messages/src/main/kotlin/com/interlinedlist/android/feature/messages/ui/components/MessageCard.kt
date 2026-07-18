package com.interlinedlist.android.feature.messages.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.interlinedlist.android.feature.messages.domain.Message
import com.interlinedlist.android.feature.messages.ui.relativeTime

/** Stable test tags for the message card controls. */
object MessageCardTags {
    const val DIG = "messageDig"
    const val REPLY = "messageReply"
    const val MENU = "messageMenu"
    const val DELETE = "messageDelete"
    const val BODY = "messageBody"
}

/**
 * One message in a feed or reply list: avatar, author + relative time, body, and
 * the dig / reply engagement row. An overflow menu exposes delete for own messages.
 */
@Composable
fun MessageCard(
    message: Message,
    onClick: () -> Unit,
    onDig: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Avatar(url = message.authorAvatarUrl, label = message.authorLabel)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = message.authorLabel,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                val time = relativeTime(message.createdAt)
                if (time.isNotEmpty()) {
                    Text(
                        text = " · $time",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.weight(1f))
                if (message.mine) {
                    OwnMessageMenu(onDelete = onDelete)
                }
            }
            Spacer(Modifier.size(4.dp))
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag(MessageCardTags.BODY),
            )
            Spacer(Modifier.size(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Engagement(
                    icon = if (message.dugByMe) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    tint = if (message.dugByMe) MaterialTheme.colorScheme.secondary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    count = message.digCount,
                    contentDescription = "Dig",
                    onClick = onDig,
                    tag = MessageCardTags.DIG,
                )
                Engagement(
                    icon = Icons.Outlined.ChatBubbleOutline,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    count = message.replyCount,
                    contentDescription = "Replies",
                    onClick = onClick,
                    tag = MessageCardTags.REPLY,
                )
            }
        }
    }
}

@Composable
private fun OwnMessageMenu(onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.testTag(MessageCardTags.MENU),
        ) {
            Icon(Icons.Filled.MoreVert, contentDescription = "More options")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Delete") },
                onClick = {
                    expanded = false
                    onDelete()
                },
                modifier = Modifier.testTag(MessageCardTags.DELETE),
            )
        }
    }
}

@Composable
private fun Engagement(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    count: Int,
    contentDescription: String,
    onClick: () -> Unit,
    tag: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clickable(onClick = onClick)
            .testTag(tag),
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(18.dp))
        if (count > 0) {
            Spacer(Modifier.width(4.dp))
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = tint,
            )
        }
    }
}

@Composable
private fun Avatar(url: String?, label: String) {
    val shape = CircleShape
    if (url.isNullOrBlank()) {
        // Fallback initial monogram when the author has no avatar.
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label.take(1).uppercase(),
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    } else {
        AsyncImage(
            model = url,
            contentDescription = "$label avatar",
            modifier = Modifier
                .size(40.dp)
                .clip(shape),
        )
    }
}
