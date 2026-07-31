package com.interlinedlist.android.feature.directmessages.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.interlinedlist.android.feature.directmessages.ui.inbox.InboxRoute
import com.interlinedlist.android.feature.directmessages.ui.newmessage.NewMessageRoute
import com.interlinedlist.android.feature.directmessages.ui.thread.ThreadRoute

/** Route keys and argument names for the Direct Messages graph. */
object DirectMessagesDestinations {
    /** Conversations / inbox list — the graph's entry route. */
    const val INBOX = "dm/inbox"

    /** Recipient picker to start a new conversation. */
    const val NEW_MESSAGE = "dm/new"

    const val ARG_USERNAME = "username"

    /** Thread with a specific user; navigate via [threadRoute]. */
    const val THREAD = "dm/thread/{$ARG_USERNAME}"

    /** Builds a concrete thread route for [username]. */
    fun threadRoute(username: String): String = "dm/thread/$username"
}

/** Convenience navigation helpers so callers don't hand-build route strings. */
fun NavController.navigateToDmInbox() = navigate(DirectMessagesDestinations.INBOX)
fun NavController.navigateToDmThread(username: String) =
    navigate(DirectMessagesDestinations.threadRoute(username))
fun NavController.navigateToNewDm() = navigate(DirectMessagesDestinations.NEW_MESSAGE)

/**
 * Registers the Direct Messages destinations into the host graph.
 *
 * The app wires this into its top-level NavHost (see the module's report for the
 * exact snippet). [onBack] pops the current destination; [onOpenThread] lets the
 * host decide how threads are pushed.
 */
fun NavGraphBuilder.directMessagesGraph(
    onBack: () -> Unit,
    onOpenThread: (username: String) -> Unit,
    onComposeNew: () -> Unit,
) {
    composable(DirectMessagesDestinations.INBOX) {
        InboxRoute(
            onOpenThread = onOpenThread,
            onComposeNew = onComposeNew,
        )
    }

    composable(
        route = DirectMessagesDestinations.THREAD,
        arguments = listOf(
            navArgument(DirectMessagesDestinations.ARG_USERNAME) { type = NavType.StringType },
        ),
    ) {
        ThreadRoute(onBack = onBack)
    }

    composable(DirectMessagesDestinations.NEW_MESSAGE) {
        NewMessageRoute(
            onBack = onBack,
            onRecipientChosen = onOpenThread,
        )
    }
}
