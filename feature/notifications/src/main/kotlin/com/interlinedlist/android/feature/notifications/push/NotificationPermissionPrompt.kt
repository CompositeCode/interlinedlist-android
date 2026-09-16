package com.interlinedlist.android.feature.notifications.push

import android.os.Build
import com.interlinedlist.android.feature.notifications.domain.NotificationChannel

/**
 * Decides whether a preference toggle is the moment to ask for `POST_NOTIFICATIONS`.
 *
 * The app raises its tray notifications from the WorkManager poll
 * ([SystemNotificationPoster]), gated by [NotificationPushFilter] on the recipient's
 * per-event **push** preference. So the moment that earns the grant is the moment the
 * user switches a "Push" channel ON in Notification preferences: they have just asked,
 * in so many words, to be notified — the system dialog then answers a question they
 * themselves posed, instead of ambushing them on cold start (which burns one of
 * Android 13's two prompts on a user with no context).
 *
 * A denial changes nothing else: the preference is still saved server-side, the poll
 * still runs, the in-app tray still works, and [SystemNotificationPoster] already
 * no-ops when notifications are disabled.
 *
 * Pure function so the rule is unit-testable; [sdkInt] is a parameter for the same reason.
 */
fun shouldRequestPostNotifications(
    channel: NotificationChannel,
    enabled: Boolean,
    alreadyGranted: Boolean,
    sdkInt: Int = Build.VERSION.SDK_INT,
): Boolean =
    channel == NotificationChannel.PUSH &&
        enabled &&
        !alreadyGranted &&
        // Below Android 13 the permission does not exist and is granted implicitly.
        sdkInt >= Build.VERSION_CODES.TIRAMISU
