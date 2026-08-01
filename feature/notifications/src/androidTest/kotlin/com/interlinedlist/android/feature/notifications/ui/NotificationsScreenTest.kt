package com.interlinedlist.android.feature.notifications.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.interlinedlist.android.core.designsystem.theme.InterlinedListTheme
import com.interlinedlist.android.feature.notifications.domain.Notification
import com.interlinedlist.android.feature.notifications.domain.NotificationType
import com.interlinedlist.android.feature.notifications.ui.components.NotificationRowTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun notification(
        id: String,
        subject: String,
        read: Boolean = false,
        type: NotificationType = NotificationType.FOLLOW,
    ) = Notification(
        id = id, type = type, actor = null, subject = subject, body = null,
        createdAt = null, read = read, target = null,
    )

    /** Hosts the stateless screen with a tiny in-memory state holder. */
    private fun setScreen(
        initial: NotificationsUiState,
        onBack: () -> Unit = {},
        onMarkAllRead: () -> Unit = {},
        onOpen: (Notification) -> Unit = {},
        onDismiss: (Notification) -> Unit = {},
    ) {
        composeRule.setContent {
            var state by mutableStateOf(initial)
            InterlinedListTheme {
                NotificationsScreen(
                    state = state,
                    onBack = onBack,
                    onRefresh = {},
                    onLoadMore = {},
                    onMarkAllRead = onMarkAllRead,
                    onOpen = onOpen,
                    onDismiss = onDismiss,
                )
            }
        }
    }

    @Test
    fun emptyState_isShown_whenThereAreNoNotifications() {
        setScreen(NotificationsUiState(notifications = emptyList()))
        composeRule.onNodeWithTag(NotificationsTags.EMPTY).assertIsDisplayed()
    }

    @Test
    fun notifications_areRendered_inTheList() {
        setScreen(NotificationsUiState(notifications = listOf(notification("1", "Amy followed you"))))
        composeRule.onNodeWithText("Amy followed you").assertIsDisplayed()
    }

    @Test
    fun back_invokesOnBack() {
        var backed = false
        setScreen(
            NotificationsUiState(notifications = listOf(notification("1", "hi"))),
            onBack = { backed = true },
        )
        composeRule.onNodeWithTag(NotificationsTags.BACK).performClick()
        assert(backed)
    }

    @Test
    fun tappingNotification_invokesOnOpen() {
        var opened: String? = null
        setScreen(
            NotificationsUiState(notifications = listOf(notification("42", "Tap me"))),
            onOpen = { opened = it.id },
        )
        composeRule.onNodeWithText("Tap me").performClick()
        assert(opened == "42")
    }

    @Test
    fun unreadDot_isShown_forUnreadRows() {
        setScreen(NotificationsUiState(notifications = listOf(notification("1", "unread", read = false))))
        composeRule.onNodeWithTag(NotificationRowTags.UNREAD_DOT).assertIsDisplayed()
    }

    @Test
    fun markAllRead_isShown_whenThereAreUnread_andInvokesCallback() {
        var marked = false
        setScreen(
            NotificationsUiState(
                notifications = listOf(notification("1", "unread", read = false)),
                unreadCount = 1,
            ),
            onMarkAllRead = { marked = true },
        )
        composeRule.onNodeWithTag(NotificationsTags.MARK_ALL_READ).performClick()
        assert(marked)
    }

    @Test
    fun overflowMenu_dismissesNotification() {
        var dismissed: String? = null
        setScreen(
            NotificationsUiState(notifications = listOf(notification("77", "bye"))),
            onDismiss = { dismissed = it.id },
        )
        composeRule.onNodeWithTag(NotificationRowTags.MENU).performClick()
        composeRule.onNodeWithTag(NotificationRowTags.DISMISS).performClick()
        assert(dismissed == "77")
    }

    @Test
    fun subscriptionGate_showsLockedState() {
        setScreen(NotificationsUiState(subscriptionRequired = true, errorMessage = "Subscribers only"))
        composeRule.onNodeWithTag(NotificationsTags.LOCKED).assertIsDisplayed()
    }
}
