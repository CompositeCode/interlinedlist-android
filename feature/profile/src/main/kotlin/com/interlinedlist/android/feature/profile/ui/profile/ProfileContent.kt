package com.interlinedlist.android.feature.profile.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.interlinedlist.android.feature.profile.domain.FollowCounts
import com.interlinedlist.android.feature.profile.domain.FollowStatus
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
    const val FOLLOW_TOGGLE = "profileFollowToggle"
    const val FOLLOWERS_COUNT = "profileFollowersCount"
    const val FOLLOWING_COUNT = "profileFollowingCount"
}

/**
 * Stateless profile body — avatar, name, @username, subscriber badge, tappable
 * follower/following counts, an optional follow button, and bio. Shared by the
 * current-user ("Account") and other-user profile screens so both render identically.
 *
 * @param counts follower / following tallies shown as tappable stats.
 * @param onOpenFollowers open the followers list for this user.
 * @param onOpenFollowing open the following list for this user.
 * @param followStatus the current user's relationship to this profile; [FollowStatus.SELF]
 *   hides the follow button (own profile).
 * @param isFollowActionInProgress disables the follow button while a toggle is in flight.
 * @param onToggleFollow follow/unfollow the viewed user.
 */
@Composable
fun ProfileContent(
    user: ProfileUser,
    modifier: Modifier = Modifier,
    counts: FollowCounts = FollowCounts(),
    onOpenFollowers: () -> Unit = {},
    onOpenFollowing: () -> Unit = {},
    followStatus: FollowStatus = FollowStatus.SELF,
    isFollowActionInProgress: Boolean = false,
    onToggleFollow: () -> Unit = {},
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

        Spacer(Modifier.height(20.dp))
        FollowCountsRow(
            counts = counts,
            onOpenFollowers = onOpenFollowers,
            onOpenFollowing = onOpenFollowing,
        )

        if (followStatus != FollowStatus.SELF) {
            Spacer(Modifier.height(16.dp))
            FollowButton(
                status = followStatus,
                inProgress = isFollowActionInProgress,
                onClick = onToggleFollow,
            )
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

/** A row of tappable follower / following stats. */
@Composable
private fun FollowCountsRow(
    counts: FollowCounts,
    onOpenFollowers: () -> Unit,
    onOpenFollowing: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.Center) {
        CountStat(
            value = counts.followers,
            label = "Followers",
            onClick = onOpenFollowers,
            modifier = Modifier.testTag(ProfileTestTags.FOLLOWERS_COUNT),
        )
        Spacer(Modifier.width(32.dp))
        CountStat(
            value = counts.following,
            label = "Following",
            onClick = onOpenFollowing,
            modifier = Modifier.testTag(ProfileTestTags.FOLLOWING_COUNT),
        )
    }
}

@Composable
private fun CountStat(
    value: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A follow / following / requested toggle button reflecting [status]. */
@Composable
private fun FollowButton(
    status: FollowStatus,
    inProgress: Boolean,
    onClick: () -> Unit,
) {
    val isFollowing = status == FollowStatus.FOLLOWING || status == FollowStatus.REQUESTED
    val label = when (status) {
        FollowStatus.FOLLOWING -> "Following"
        FollowStatus.REQUESTED -> "Requested"
        else -> "Follow"
    }
    if (isFollowing) {
        OutlinedButton(
            onClick = onClick,
            enabled = !inProgress,
            modifier = Modifier.testTag(ProfileTestTags.FOLLOW_TOGGLE),
        ) {
            Text(label)
        }
    } else {
        Button(
            onClick = onClick,
            enabled = !inProgress,
            modifier = Modifier.testTag(ProfileTestTags.FOLLOW_TOGGLE),
        ) {
            Text(label)
        }
    }
}
