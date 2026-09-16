package com.interlinedlist.android.feature.messages.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Repeat
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.interlinedlist.android.feature.messages.domain.Message
import com.interlinedlist.android.feature.messages.domain.PushedMessage
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

    /** The "Private" marker shown on the author's own non-public messages. */
    const val PRIVATE = "messagePrivate"

    /** Push (repost) action, with the message's push count beside it. */
    const val PUSH = "messagePush"
    /** Quote action: reposts with the user's own note. */
    const val QUOTE = "messageQuote"
    /** The "<author> pushed" header shown above a bare push. */
    const val PUSH_HEADER = "messagePushHeader"
    /** The embedded original rendered inside a push or a quote. */
    const val PUSHED_ORIGINAL = "messagePushedOriginal"
}

/**
 * One message in a feed or reply list: avatar, author + relative time, body,
 * attached media / link preview, and the dig / reply / push engagement row. An
 * overflow menu exposes delete for own messages and report for everyone else's.
 *
 * A **push** (repost of someone else's message with no comment) is drawn with a
 * "pushed" header and the embedded original in place of a body; a **quote**
 * shows the author's own note with the original inset beneath it.
 *
 * [onPush] / [onQuote] are null where the host screen does not wire the actions;
 * they are also withheld for a message [Message.canBePushed] rules out.
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
    onPush: (() -> Unit)? = null,
    onQuote: (() -> Unit)? = null,
    onOpenPushedMessage: (String) -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        // A bare push carries no words of its own, so say whose push this is.
        if (message.isPush) {
            PushedHeader(label = message.authorLabel)
            Spacer(Modifier.size(6.dp))
        }
        Row(Modifier.fillMaxWidth()) {
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
                    if (message.showsPrivateBadge) {
                        Spacer(Modifier.width(6.dp))
                        PrivateBadge()
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
                // A push has no body of its own; the original below is the post.
                if (message.content.isNotBlank()) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.testTag(MessageCardTags.BODY),
                    )
                }
                message.pushedMessage?.let { original ->
                    Spacer(Modifier.size(8.dp))
                    PushedOriginal(
                        original = original,
                        onClick = { onOpenPushedMessage(original.id) },
                    )
                }
                if (message.hasMedia || message.linkPreview != null) {
                    Spacer(Modifier.size(8.dp))
                    MessageMedia(message = message, onOpenLink = onOpenLink)
                }
                Spacer(Modifier.size(8.dp))
                EngagementRow(
                    message = message,
                    onDig = onDig,
                    onReply = onClick,
                    onPush = onPush,
                    onQuote = onQuote,
                )
            }
        }
    }
}

/** Dig, reply, and — where the rules allow it — push and quote. */
@Composable
private fun EngagementRow(
    message: Message,
    onDig: () -> Unit,
    onReply: () -> Unit,
    onPush: (() -> Unit)?,
    onQuote: (() -> Unit)?,
) {
    // Push and Quote are offered on the same terms: both post a pushedMessageId.
    val canPush = message.canBePushed && onPush != null
    val canQuote = message.canBePushed && onQuote != null
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
            onClick = onReply,
            tag = MessageCardTags.REPLY,
        )
        // Where a push cannot be offered, the count is still worth showing — but
        // only as a count, never as a control that would fail on tap.
        if (canPush || message.pushCount > 0) {
            Engagement(
                icon = Icons.Filled.Repeat,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                count = message.pushCount,
                contentDescription = "Push",
                onClick = onPush.takeIf { canPush },
                tag = MessageCardTags.PUSH,
            )
        }
        if (canQuote) {
            Engagement(
                icon = Icons.Filled.FormatQuote,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                count = 0,
                contentDescription = "Quote",
                onClick = onQuote,
                tag = MessageCardTags.QUOTE,
            )
        }
    }
}

/** The "<author> pushed" line that introduces a bare repost. */
@Composable
private fun PushedHeader(label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.testTag(MessageCardTags.PUSH_HEADER),
    ) {
        Icon(
            imageVector = Icons.Filled.Repeat,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "$label pushed",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The re-shared original, inset in an outlined card so it reads as somebody
 * else's post rather than part of this one. Comes straight from the feed
 * payload's nested `pushedMessage`, so no extra fetch is involved. Tapping it
 * opens the original's own page.
 */
@Composable
private fun PushedOriginal(original: PushedMessage, onClick: () -> Unit) {
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClick = onClick)
            .padding(12.dp)
            .testTag(MessageCardTags.PUSHED_ORIGINAL),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(url = original.authorAvatarUrl, label = original.authorLabel, size = 20.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                text = original.authorLabel,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            val time = relativeTime(original.createdAt)
            if (time.isNotEmpty()) {
                Text(
                    text = " · $time",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (original.content.isNotBlank()) {
            Spacer(Modifier.size(4.dp))
            Text(
                text = original.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The "Private" marker: an icon plus label making it unmistakable that only the
 * author can see this message. Public messages carry no badge at all, so the
 * marker only ever means "not on the feed".
 */
@Composable
private fun PrivateBadge() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .testTag(MessageCardTags.PRIVATE),
    ) {
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(12.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "Private",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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

/**
 * One engagement control. A null [onClick] renders the icon and count as plain
 * information — used where an action is deliberately not on offer.
 */
@Composable
private fun Engagement(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    count: Int,
    contentDescription: String,
    onClick: (() -> Unit)?,
    tag: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
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
private fun Avatar(url: String?, label: String, size: Dp = 40.dp) {
    val shape = CircleShape
    if (url.isNullOrBlank()) {
        // Fallback initial monogram when the author has no avatar.
        Box(
            modifier = Modifier
                .size(size)
                .clip(shape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label.take(1).uppercase(),
                color = MaterialTheme.colorScheme.onPrimary,
                // The inset "original" card uses a much smaller avatar.
                style = if (size < 32.dp) {
                    MaterialTheme.typography.labelSmall
                } else {
                    MaterialTheme.typography.titleMedium
                },
            )
        }
    } else {
        AsyncImage(
            model = url,
            contentDescription = "$label avatar",
            modifier = Modifier
                .size(size)
                .clip(shape),
        )
    }
}
