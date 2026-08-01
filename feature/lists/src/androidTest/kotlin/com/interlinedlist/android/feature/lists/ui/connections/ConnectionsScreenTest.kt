package com.interlinedlist.android.feature.lists.ui.connections

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.interlinedlist.android.core.designsystem.theme.InterlinedListTheme
import com.interlinedlist.android.feature.lists.domain.ListConnection
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies the connections screen renders connection rows and the empty state. */
@RunWith(AndroidJUnit4::class)
class ConnectionsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setScreen(state: ConnectionsUiState) {
        composeRule.setContent {
            InterlinedListTheme {
                ConnectionsScreen(
                    state = state,
                    onBack = {},
                    onCreateConnection = { _, _, _ -> },
                    onDeleteConnection = {},
                )
            }
        }
    }

    @Test
    fun rendersConnectionRows() {
        setScreen(
            ConnectionsUiState(
                connections = listOf(
                    ListConnection("c1", "l1", "l2", "blocks", "Backlog", "Roadmap"),
                ),
                isLoading = false,
            ),
        )

        composeRule.onNodeWithTag(ConnectionsTestTags.connection("c1")).assertIsDisplayed()
        composeRule.onNodeWithText("Backlog → Roadmap").assertIsDisplayed()
    }

    @Test
    fun showsEmptyState_whenNoConnections() {
        setScreen(ConnectionsUiState(connections = emptyList(), isLoading = false))

        composeRule.onNodeWithTag(ConnectionsTestTags.EMPTY).assertIsDisplayed()
    }
}
