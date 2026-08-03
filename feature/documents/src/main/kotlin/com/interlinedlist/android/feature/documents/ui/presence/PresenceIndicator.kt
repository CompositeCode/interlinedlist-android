package com.interlinedlist.android.feature.documents.ui.presence

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
import androidx.compose.ui.unit.dp
import com.interlinedlist.android.feature.documents.domain.Presence

object PresenceTestTags {
    const val ROW = "presenceRow"
    const val OVERFLOW = "presenceOverflow"
    fun avatar(userId: String) = "presenceAvatar_$userId"
}

/**
 * A compact "N people here" avatar cluster for the editor's top bar. Shows up to
 * [maxAvatars] overlapping avatars and a "+N" overflow chip; renders nothing when
 * no one else is present.
 */
@Composable
fun PresenceIndicator(
    participants: List<Presence>,
    modifier: Modifier = Modifier,
    maxAvatars: Int = 3,
) {
    if (participants.isEmpty()) return
    val shown = participants.take(maxAvatars)
    val overflow = participants.size - shown.size
    Row(
        modifier = modifier.testTag(PresenceTestTags.ROW),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        shown.forEachIndexed { index, person ->
            PresenceAvatar(
                initial = person.initial,
                modifier = Modifier
                    .offset(x = (index * -8).dp)
                    .testTag(PresenceTestTags.avatar(person.userId)),
            )
        }
        if (overflow > 0) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .offset(x = (shown.size * -8).dp)
                    .size(28.dp)
                    .testTag(PresenceTestTags.OVERFLOW),
            ) {
                Box(Modifier.padding(2.dp), contentAlignment = Alignment.Center) {
                    Text("+$overflow", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
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
