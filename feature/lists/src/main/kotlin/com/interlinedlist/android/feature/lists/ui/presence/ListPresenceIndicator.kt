package com.interlinedlist.android.feature.lists.ui.presence

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.interlinedlist.android.feature.lists.domain.ListPresence

/** Stable test tags for the list presence cluster. */
object ListPresenceTestTags {
    const val ROW = "listPresenceRow"
    const val OVERFLOW = "listPresenceOverflow"
    fun avatar(userId: String) = "listPresenceAvatar_$userId"
}

/**
 * A compact "who else is here" avatar cluster for the list detail top bar.
 *
 * Deliberately the same shape as the documents editor's presence indicator so the
 * two features read identically; it is a separate copy rather than a shared one
 * because features do not depend on each other. Renders nothing when nobody else
 * is present, which is the normal case.
 */
@Composable
fun ListPresenceIndicator(
    participants: List<ListPresence>,
    modifier: Modifier = Modifier,
    maxAvatars: Int = 3,
) {
    if (participants.isEmpty()) return
    val shown = participants.take(maxAvatars)
    val overflow = participants.size - shown.size
    Row(
        modifier = modifier
            .testTag(ListPresenceTestTags.ROW)
            .semantics { contentDescription = describe(participants) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        shown.forEachIndexed { index, person ->
            PresenceAvatar(
                initial = person.initial,
                modifier = Modifier
                    .offset(x = (index * -8).dp)
                    .testTag(ListPresenceTestTags.avatar(person.userId)),
            )
        }
        if (overflow > 0) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .offset(x = (shown.size * -8).dp)
                    .size(28.dp)
                    .testTag(ListPresenceTestTags.OVERFLOW),
            ) {
                Box(Modifier.padding(2.dp), contentAlignment = Alignment.Center) {
                    Text("+$overflow", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

/** Screen-reader text for the cluster: names the people, not the avatars. */
internal fun describe(participants: List<ListPresence>): String = when (participants.size) {
    0 -> ""
    1 -> "${participants.first().label} is also here"
    else -> participants.joinToString(", ") { it.label } + " are also here"
}

@Composable
private fun PresenceAvatar(initial: String, modifier: Modifier = Modifier) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = modifier
            .size(28.dp)
            .border(1.dp, MaterialTheme.colorScheme.surface, CircleShape),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = initial,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}
