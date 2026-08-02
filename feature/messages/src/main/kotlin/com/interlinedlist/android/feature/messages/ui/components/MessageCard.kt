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
    const val EDIT = "messageEdit"
    const val REPORT = "messageReport"
    const val BLOCK_USER = "messageBlockUser"
    const val MUTE_USER = "messageMuteUser"
    const val REPORT_USER = "messageReportUser"
    const val BODY = "messageBody"
    const val EDITED = "messageEdited"
}

/**
 * One message in a feed or reply list: avatar, author + relative time, body,
 * attached media / link preview, and the dig / reply engagement row. An overflow
 * menu exposes delete for own messages and report for everyone else's.
 */
@Composable
fun MessageCard(
    message: Message,
    onClick: () -> Unit,
    onDig: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    onReport: () -> Unit = {},
    onEdit: () -> Unit = {},
    onBlockUser: () -> Unit = {},
    onMuteUser: () -> Unit = {},
    onReportUser: () -> Unit = {},
    onOpenLink: (String) -> Unit = {},
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
                if (message.isEdited) {
                    Text(
                        text = " · edited",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag(MessageCardTags.EDITED),
                    )
                }
                Spacer(Modifier.weight(1f))
                MessageMenu(
                    isMine = message.mine,
                    onEdit = onEdit,
                    onDelete = onDelete,
                    onReport = onReport,
                    onBlockUser = onBlockUser,
                    onMuteUser = onMuteUser,
                    onReportUser = onReportUser,
                )
            }
            Spacer(Modifier.size(4.dp))
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag(MessageCardTags.BODY),
            )
            if (message.hasMedia || message.linkPreview != null) {
                Spacer(Modifier.size(8.dp))
                MessageMedia(message = message, onOpenLink = onOpenLink)
            }
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

/**
 * Overflow menu. Own messages offer Edit + Delete. Everyone else's offer author
 * moderation — Block user, Mute user, Report user — plus the existing Report
 * (message) action.
 */
@Composable
private fun MessageMenu(
    isMine: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onReport: () -> Unit,
    onBlockUser: () -> Unit,
    onMuteUser: () -> Unit,
    onReportUser: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.testTag(MessageCardTags.MENU),
        ) {
            Icon(Icons.Filled.MoreVert, contentDescription = "More options")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (isMine) {
                DropdownMenuItem(
                    text = { Text("Edit") },
                    onClick = {
                        expanded = false
                        onEdit()
                    },
                    modifier = Modifier.testTag(MessageCardTags.EDIT),
                )
                DropdownMenuItem(
                    text = { Text("Delete") },
                    onClick = {
                        expanded = false
                        onDelete()
                    },
                    modifier = Modifier.testTag(MessageCardTags.DELETE),
                )
            } else {
                DropdownMenuItem(
                    text = { Text("Report message") },
                    onClick = {
                        expanded = false
                        onReport()
                    },
                    modifier = Modifier.testTag(MessageCardTags.REPORT),
                )
                DropdownMenuItem(
                    text = { Text("Block user") },
                    onClick = {
                        expanded = false
                        onBlockUser()
                    },
                    modifier = Modifier.testTag(MessageCardTags.BLOCK_USER),
                )
                DropdownMenuItem(
                    text = { Text("Mute user") },
                    onClick = {
                        expanded = false
                        onMuteUser()
                    },
                    modifier = Modifier.testTag(MessageCardTags.MUTE_USER),
                )
                DropdownMenuItem(
                    text = { Text("Report user") },
                    onClick = {
                        expanded = false
                        onReportUser()
                    },
                    modifier = Modifier.testTag(MessageCardTags.REPORT_USER),
                )
            }
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
