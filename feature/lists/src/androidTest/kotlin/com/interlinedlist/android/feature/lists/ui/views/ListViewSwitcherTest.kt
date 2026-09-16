package com.interlinedlist.android.feature.lists.ui.views

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.designsystem.theme.InterlinedListTheme
import com.interlinedlist.android.feature.lists.domain.ListView
import com.interlinedlist.android.feature.lists.domain.ListViewConfig
import com.interlinedlist.android.feature.lists.domain.ListViewScope
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the saved-view switcher surfaces shared and personal views, marks the
 * default, and offers only the actions the user is allowed: somebody else's
 * shared view can be forked into a personal copy but not renamed or deleted.
 */
@RunWith(AndroidJUnit4::class)
class ListViewSwitcherTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val theirShared = ListView(
        id = "v1",
        listId = "L1",
        userId = "someone-else",
        name = "Roadmap",
        scope = ListViewScope.SHARED,
        config = ListViewConfig.DEFAULT,
        isDefault = true,
        position = 0,
    )

    private val myPersonal = theirShared.copy(
        id = "v2",
        userId = "me",
        name = "My cut",
        scope = ListViewScope.PERSONAL,
        isDefault = false,
    )

    private fun state(vararg views: ListView) = ListViewsUiState(
        views = views.toList(),
        selectedViewId = views.firstOrNull()?.id,
        currentUserId = "me",
        isLoading = false,
    )

    private fun setContent(
        state: ListViewsUiState,
        onFork: (ListView) -> Unit = {},
        onRename: (ListView) -> Unit = {},
        onDelete: (ListView) -> Unit = {},
        onSetDefault: (ListView) -> Unit = {},
        onSelect: (ListView) -> Unit = {},
        onCreate: (String, ListViewScope) -> Unit = { _, _ -> },
    ) {
        composeRule.setContent {
            InterlinedListTheme {
                ListViewSwitcherContent(
                    state = state,
                    onSelect = onSelect,
                    onSetDefault = onSetDefault,
                    onFork = onFork,
                    onRename = onRename,
                    onDelete = onDelete,
                    onCreate = onCreate,
                )
            }
        }
    }

    @Test
    fun barShowsTheSelectedViewAndItsDefaultBadge() {
        composeRule.setContent {
            InterlinedListTheme {
                ListViewSwitcherBar(state = state(theirShared, myPersonal), onOpen = {})
            }
        }

        composeRule.onNodeWithTag(ListViewSwitcherTestTags.BAR).assertIsDisplayed()
        composeRule.onNodeWithText("Roadmap").assertIsDisplayed()
        composeRule.onNodeWithText("Default").assertIsDisplayed()
    }

    @Test
    fun listsSharedAndPersonalViewsSeparately() {
        setContent(state(theirShared, myPersonal))

        composeRule.onNodeWithText("Shared").assertIsDisplayed()
        composeRule.onNodeWithText("Personal").assertIsDisplayed()
        composeRule.onNodeWithTag(ListViewSwitcherTestTags.view("v1")).assertIsDisplayed()
        composeRule.onNodeWithTag(ListViewSwitcherTestTags.view("v2")).assertIsDisplayed()
    }

    @Test
    fun someoneElsesSharedViewOffersAForkButNotRenameOrDelete() {
        var forked: ListView? = null
        setContent(state(theirShared), onFork = { forked = it })

        composeRule.onNodeWithTag(ListViewSwitcherTestTags.overflow("v1")).performClick()

        // Fork is the escape hatch, and says what it does.
        composeRule.onNodeWithTag(ListViewSwitcherTestTags.fork("v1")).assertIsDisplayed()
        composeRule.onNodeWithText("Copies this view to your own. The shared one is untouched.")
            .assertIsDisplayed()
        composeRule.onNodeWithTag(ListViewSwitcherTestTags.rename("v1")).assertDoesNotExist()
        composeRule.onNodeWithTag(ListViewSwitcherTestTags.delete("v1")).assertDoesNotExist()

        composeRule.onNodeWithTag(ListViewSwitcherTestTags.fork("v1")).performClick()
        assertThat(forked?.id).isEqualTo("v1")
    }

    @Test
    fun ownViewCanBeRenamedDeletedAndMadeDefault() {
        var renamed: ListView? = null
        var deleted: ListView? = null
        var defaulted: ListView? = null
        setContent(
            state(myPersonal),
            onRename = { renamed = it },
            onDelete = { deleted = it },
            onSetDefault = { defaulted = it },
        )

        composeRule.onNodeWithTag(ListViewSwitcherTestTags.overflow("v2")).performClick()
        composeRule.onNodeWithTag(ListViewSwitcherTestTags.setDefault("v2")).performClick()
        assertThat(defaulted?.id).isEqualTo("v2")

        composeRule.onNodeWithTag(ListViewSwitcherTestTags.overflow("v2")).performClick()
        composeRule.onNodeWithTag(ListViewSwitcherTestTags.rename("v2")).performClick()
        assertThat(renamed?.id).isEqualTo("v2")

        composeRule.onNodeWithTag(ListViewSwitcherTestTags.overflow("v2")).performClick()
        composeRule.onNodeWithTag(ListViewSwitcherTestTags.delete("v2")).performClick()
        assertThat(deleted?.id).isEqualTo("v2")
    }

    @Test
    fun creatingAViewSendsTheNameAndTheChosenScope() {
        var created: Pair<String, ListViewScope>? = null
        setContent(state(), onCreate = { name, scope -> created = name to scope })

        composeRule.onNodeWithTag(ListViewSwitcherTestTags.EMPTY).assertIsDisplayed()
        composeRule.onNodeWithTag(ListViewSwitcherTestTags.CREATE_NAME).performTextInput("By status")
        composeRule.onNodeWithTag(ListViewSwitcherTestTags.scope(ListViewScope.SHARED)).performClick()
        composeRule.onNodeWithTag(ListViewSwitcherTestTags.CREATE_SUBMIT).performClick()

        assertThat(created).isEqualTo("By status" to ListViewScope.SHARED)
    }

    @Test
    fun aRefusalFromTheServerIsShownInTheSheet() {
        setContent(
            state(theirShared).copy(errorMessage = "You cannot modify this view"),
        )

        composeRule.onNodeWithTag(ListViewSwitcherTestTags.ERROR).assertIsDisplayed()
        composeRule.onNodeWithText("You cannot modify this view").assertIsDisplayed()
    }
}
