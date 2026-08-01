package com.interlinedlist.android.feature.lists.ui.watchers

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.interlinedlist.android.core.designsystem.theme.InterlinedListTheme
import com.interlinedlist.android.feature.lists.domain.Watcher
import com.interlinedlist.android.feature.lists.domain.WatcherRole
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies the watchers screen renders watcher rows and the empty state. */
@RunWith(AndroidJUnit4::class)
class WatchersScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setScreen(state: WatchersUiState) {
        composeRule.setContent {
            InterlinedListTheme {
                WatchersScreen(
                    state = state,
                    onBack = {},
                    onSearchQueryChange = {},
                    onAddCandidate = {},
                    onChangeRole = { _, _ -> },
                    onRemoveWatcher = {},
                )
            }
        }
    }

    @Test
    fun rendersWatcherRows() {
        setScreen(
            WatchersUiState(
                watchers = listOf(
                    Watcher("u1", "ada", "Ada Lovelace", null, WatcherRole.EDITOR),
                ),
                isLoading = false,
            ),
        )

        composeRule.onNodeWithTag(WatchersTestTags.watcher("u1")).assertIsDisplayed()
        composeRule.onNodeWithText("Ada Lovelace").assertIsDisplayed()
        composeRule.onNodeWithText("@ada").assertIsDisplayed()
    }

    @Test
    fun showsEmptyState_whenNoWatchers() {
        setScreen(WatchersUiState(watchers = emptyList(), isLoading = false))

        composeRule.onNodeWithTag(WatchersTestTags.EMPTY).assertIsDisplayed()
    }
}
