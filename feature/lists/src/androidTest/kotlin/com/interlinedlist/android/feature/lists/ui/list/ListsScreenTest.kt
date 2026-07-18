package com.interlinedlist.android.feature.lists.ui.list

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.interlinedlist.android.core.designsystem.theme.InterlinedListTheme
import com.interlinedlist.android.feature.lists.domain.ListSummary
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose UI coverage for the stateless [ListsScreen]. Runs on-device; the
 * orchestrator executes instrumented tests after merge (no emulator in the
 * worktree), so this is written to compile and be correct.
 */
@RunWith(AndroidJUnit4::class)
class ListsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setScreen(
        state: ListsUiState,
        onOpenList: (String) -> Unit = {},
    ) {
        composeRule.setContent {
            InterlinedListTheme {
                ListsScreen(
                    state = state,
                    onOpenList = onOpenList,
                    onSearchQueryChange = {},
                    onLoadMore = {},
                    onCreateList = {},
                )
            }
        }
    }

    @Test
    fun rendersCards_andOpensListOnTap() {
        var opened: String? = null
        setScreen(
            state = ListsUiState(
                lists = listOf(
                    ListSummary("1", "Reading list", "Books", 3, null, false, null),
                    ListSummary("2", "Trips", null, 0, null, true, null),
                ),
                isRefreshing = false,
            ),
            onOpenList = { opened = it },
        )

        composeRule.onNodeWithTag(ListsTestTags.row("1")).assertIsDisplayed()
        composeRule.onNodeWithTag(ListsTestTags.row("2")).assertIsDisplayed()

        composeRule.onNodeWithTag(ListsTestTags.row("1")).performClick()
        assert(opened == "1")
    }

    @Test
    fun showsEmptyState_whenNoLists() {
        setScreen(state = ListsUiState(lists = emptyList(), isRefreshing = false))

        composeRule.onNodeWithTag(ListsTestTags.EMPTY).assertIsDisplayed()
    }

    @Test
    fun showsSubscriptionGate_whenRequired() {
        setScreen(
            state = ListsUiState(
                subscriptionRequired = true,
                errorMessage = "Lists require an active subscription",
            ),
        )

        composeRule.onNodeWithTag(ListsTestTags.SUBSCRIPTION).assertIsDisplayed()
    }
}
