package com.interlinedlist.android.feature.lists.ui.share

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.interlinedlist.android.core.designsystem.theme.InterlinedListTheme
import com.interlinedlist.android.feature.lists.domain.ShareRole
import com.interlinedlist.android.feature.lists.domain.SharedList
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies the "Shared with me" screen renders each list with its owner and role. */
@RunWith(AndroidJUnit4::class)
class SharedWithMeScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setContent(state: SharedWithMeUiState, onOpenList: (String) -> Unit = {}) {
        composeRule.setContent {
            InterlinedListTheme {
                SharedWithMeScreen(state = state, onBack = {}, onOpenList = onOpenList)
            }
        }
    }

    @Test
    fun rendersSharedLists_withOwnerAndRole() {
        setContent(
            SharedWithMeUiState(
                lists = listOf(
                    SharedList("w1", "Shows Upcoming", null, "Adron Hall", ShareRole.EDIT, true),
                ),
                isLoading = false,
            ),
        )

        composeRule.onNodeWithTag(SharedWithMeTestTags.row("w1")).assertIsDisplayed()
        composeRule.onNodeWithText("Shows Upcoming").assertIsDisplayed()
        composeRule.onNodeWithText("Shared by Adron Hall").assertIsDisplayed()
        // The role label is rendered as a chip.
        composeRule.onNodeWithText(ShareRole.EDIT.label).assertIsDisplayed()
    }

    @Test
    fun opensList_whenRowTapped() {
        var opened: String? = null
        setContent(
            SharedWithMeUiState(
                lists = listOf(SharedList("w9", "Videos", null, "Adron", ShareRole.VIEW, true)),
                isLoading = false,
            ),
            onOpenList = { opened = it },
        )

        composeRule.onNodeWithTag(SharedWithMeTestTags.row("w9")).performClick()
        assert(opened == "w9")
    }

    @Test
    fun emptyState_isShown_whenNothingShared() {
        setContent(SharedWithMeUiState(lists = emptyList(), isLoading = false))
        composeRule.onNodeWithTag(SharedWithMeTestTags.EMPTY).assertIsDisplayed()
    }
}
