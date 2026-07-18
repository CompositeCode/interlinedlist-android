package com.interlinedlist.android.feature.notifications.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.interlinedlist.android.feature.notifications.domain.Notification
import com.interlinedlist.android.feature.notifications.domain.NotificationType
import com.interlinedlist.android.feature.notifications.ui.relativeTime

/** Stable test tags for a notification row. */
object NotificationRowTags {
    const val ROW = "notificationRow"
    const val UNREAD_DOT = "notificationUnreadDot"
    const val MENU = "notificationRowMenu"
    const val DISMISS = "notificationRowDismiss"
}

/**
 * A single notification list item: a leading type icon, the summary line and an
 * optional body/actor + relative time, an unread dot, and an overflow menu offering
 * "Dismiss". Unread rows are tinted and bold; read rows are muted. Tapping the row
 * invokes [onClick] (which the ViewModel uses to mark it read).
 */
@Composable
fun NotificationRow(
    notification: Notification,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val unread = !notification.read
    val background =
        if (unread) MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
        else MaterialTheme.colorScheme.surface

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag(NotificationRowTags.ROW),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = notification.type.icon(),
            contentDescription = null,
            tint = if (unread) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.size(12.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = notification.displayTitle(),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
            )
            notification.body?.takeIf { it.isNotBlank() }?.let { body ->
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val time = relativeTime(notification.createdAt)
            if (time.isNotBlank()) {
                Text(
                    text = time,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (unread) {
            Box(
                Modifier
                    .size(10.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                    .testTag(NotificationRowTags.UNREAD_DOT),
            )
            Spacer(Modifier.size(4.dp))
        }

        OverflowMenu(onDismiss = onDismiss)
    }
}

@Composable
private fun OverflowMenu(onDismiss: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier.testTag(NotificationRowTags.MENU),
        ) {
            Icon(Icons.Filled.MoreVert, contentDescription = "More")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Dismiss") },
                onClick = {
                    expanded = false
                    onDismiss()
                },
                modifier = Modifier.testTag(NotificationRowTags.DISMISS),
            )
        }
    }
}

/** The line shown as the row title: the server subject, or a sensible fallback. */
private fun Notification.displayTitle(): String {
    if (subject.isNotBlank()) return subject
    val who = actorLabel ?: "Someone"
    return when (type) {
        NotificationType.LIKE -> "$who liked your post"
        NotificationType.REPLY -> "$who replied to your post"
        NotificationType.MENTION -> "$who mentioned you"
        NotificationType.FOLLOW -> "$who started following you"
        NotificationType.LIST_SHARE -> "$who shared a list with you"
        NotificationType.MESSAGE -> "New message from $who"
        NotificationType.SYSTEM -> "Account notification"
        NotificationType.OTHER -> "New notification"
    }
}

/** Leading icon for a notification type. */
private fun NotificationType.icon(): ImageVector = when (this) {
    NotificationType.LIKE -> Icons.Filled.Favorite
    NotificationType.REPLY -> Icons.AutoMirrored.Filled.Reply
    NotificationType.MENTION -> Icons.AutoMirrored.Filled.Chat
    NotificationType.FOLLOW -> Icons.Filled.PersonAdd
    NotificationType.LIST_SHARE -> Icons.AutoMirrored.Filled.PlaylistAdd
    NotificationType.MESSAGE -> Icons.Filled.Campaign
    NotificationType.SYSTEM -> Icons.Filled.Settings
    NotificationType.OTHER -> Icons.Filled.Notifications
}
