package com.interlinedlist.android.feature.lists.ui.share

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.interlinedlist.android.core.designsystem.theme.InterlinedListTheme
import com.interlinedlist.android.feature.lists.domain.ShareLink
import com.interlinedlist.android.feature.lists.domain.ShareRole
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies the share sheet renders existing links, a create control, and role chips. */
@RunWith(AndroidJUnit4::class)
class ShareScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setContent(
        state: ShareUiState,
        onSelectRole: (ShareRole) -> Unit = {},
        onCreate: () -> Unit = {},
        onRevoke: (ShareLink) -> Unit = {},
    ) {
        composeRule.setContent {
            InterlinedListTheme {
                ShareSheetContent(
                    state = state,
                    onSelectRole = onSelectRole,
                    onCreate = onCreate,
                    onRevoke = onRevoke,
                )
            }
        }
    }

    @Test
    fun rendersExistingLinks_andCreateControl() {
        setContent(
            ShareUiState(
                links = listOf(
                    ShareLink("1", "abc123", ShareRole.VIEW, null, null, null),
                    ShareLink("2", "def456", ShareRole.EDIT, null, null, null),
                ),
                isLoading = false,
            ),
        )

        composeRule.onNodeWithTag(ShareTestTags.CREATE).assertIsDisplayed()
        composeRule.onNodeWithTag(ShareTestTags.link("abc123")).assertIsDisplayed()
        composeRule.onNodeWithTag(ShareTestTags.link("def456")).assertIsDisplayed()
        composeRule.onNodeWithTag(ShareTestTags.copy("abc123")).assertIsDisplayed()
        composeRule.onNodeWithTag(ShareTestTags.revoke("abc123")).assertIsDisplayed()
    }

    @Test
    fun createButton_invokesCallback() {
        var created = false
        setContent(
            ShareUiState(isLoading = false),
            onCreate = { created = true },
        )

        composeRule.onNodeWithTag(ShareTestTags.CREATE).performClick()
        assert(created)
    }

    @Test
    fun roleChip_selectsRole() {
        var selected: ShareRole? = null
        setContent(
            ShareUiState(isLoading = false),
            onSelectRole = { selected = it },
        )

        composeRule.onNodeWithTag(ShareTestTags.role(ShareRole.ADMIN)).performClick()
        assert(selected == ShareRole.ADMIN)
    }

    @Test
    fun emptyState_isShown_whenNoLinks() {
        setContent(ShareUiState(links = emptyList(), isLoading = false))
        composeRule.onNodeWithTag(ShareTestTags.EMPTY).assertIsDisplayed()
    }
}
