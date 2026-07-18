package com.interlinedlist.android.feature.profile.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.interlinedlist.android.feature.profile.domain.ProfileUser
import com.interlinedlist.android.feature.profile.ui.common.SubscriberBadge
import com.interlinedlist.android.feature.profile.ui.common.UserAvatar

/** Stable test tags shared by the profile screens. */
object ProfileTestTags {
    const val AVATAR = "profileAvatar"
    const val DISPLAY_NAME = "profileDisplayName"
    const val USERNAME = "profileUsername"
    const val BIO = "profileBio"
    const val SUBSCRIBER_BADGE = "profileSubscriberBadge"
    const val EDIT = "profileEdit"
    const val SIGN_OUT = "profileSignOut"
    const val PROGRESS = "profileProgress"
    const val ERROR = "profileError"
    const val SEARCH = "profileSearch"
    const val BACK = "profileBack"
}

/**
 * Stateless profile body — avatar, name, @username, subscriber badge, and bio.
 * Shared by the current-user ("Account") and other-user profile screens so both
 * render identically.
 */
@Composable
fun ProfileContent(
    user: ProfileUser,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        UserAvatar(
            avatarUrl = user.avatarUrl,
            seedLabel = user.displayLabel,
            modifier = Modifier.testTag(ProfileTestTags.AVATAR),
        )
        Spacer(Modifier.height(16.dp))

        Text(
            text = user.displayLabel,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag(ProfileTestTags.DISPLAY_NAME),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "@${user.username}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(ProfileTestTags.USERNAME),
        )

        if (user.isSubscriber) {
            Spacer(Modifier.height(12.dp))
            SubscriberBadge(modifier = Modifier.testTag(ProfileTestTags.SUBSCRIBER_BADGE))
        }

        if (!user.bio.isNullOrBlank()) {
            Spacer(Modifier.height(20.dp))
            Text(
                text = user.bio,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(ProfileTestTags.BIO),
            )
        }
    }
}
