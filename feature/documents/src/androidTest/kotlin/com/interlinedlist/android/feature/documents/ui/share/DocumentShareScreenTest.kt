package com.interlinedlist.android.feature.documents.ui.share

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.interlinedlist.android.core.designsystem.theme.InterlinedListTheme
import com.interlinedlist.android.feature.documents.domain.ShareLink
import com.interlinedlist.android.feature.documents.domain.ShareRole
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies the document share sheet renders existing links, role chips, and a create control. */
@RunWith(AndroidJUnit4::class)
class DocumentShareScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setContent(
        state: DocumentShareUiState,
        onSelectRole: (ShareRole) -> Unit = {},
        onCreate: () -> Unit = {},
        onRevoke: (ShareLink) -> Unit = {},
    ) {
        composeRule.setContent {
            InterlinedListTheme {
                DocumentShareSheetContent(
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
            DocumentShareUiState(
                links = listOf(
                    ShareLink("1", "abc123", ShareRole.VIEW, null, null, null),
                    ShareLink("2", "def456", ShareRole.EDIT, null, null, null),
                ),
                isLoading = false,
            ),
        )

        composeRule.onNodeWithTag(DocumentShareTestTags.CREATE).assertIsDisplayed()
        composeRule.onNodeWithTag(DocumentShareTestTags.link("abc123")).assertIsDisplayed()
        composeRule.onNodeWithTag(DocumentShareTestTags.link("def456")).assertIsDisplayed()
        composeRule.onNodeWithTag(DocumentShareTestTags.copy("abc123")).assertIsDisplayed()
        composeRule.onNodeWithTag(DocumentShareTestTags.revoke("abc123")).assertIsDisplayed()
    }

    @Test
    fun createButton_invokesCallback() {
        var created = false
        setContent(DocumentShareUiState(isLoading = false), onCreate = { created = true })

        composeRule.onNodeWithTag(DocumentShareTestTags.CREATE).performClick()
        assert(created)
    }

    @Test
    fun roleChip_selectsRole() {
        var selected: ShareRole? = null
        setContent(DocumentShareUiState(isLoading = false), onSelectRole = { selected = it })

        composeRule.onNodeWithTag(DocumentShareTestTags.role(ShareRole.ADMIN)).performClick()
        assert(selected == ShareRole.ADMIN)
    }

    @Test
    fun emptyState_isShown_whenNoLinks() {
        setContent(DocumentShareUiState(links = emptyList(), isLoading = false))
        composeRule.onNodeWithTag(DocumentShareTestTags.EMPTY).assertIsDisplayed()
    }
}
