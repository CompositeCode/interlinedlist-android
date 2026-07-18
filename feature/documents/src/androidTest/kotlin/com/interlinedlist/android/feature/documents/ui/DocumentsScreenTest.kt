package com.interlinedlist.android.feature.documents.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.interlinedlist.android.core.designsystem.theme.InterlinedListTheme
import com.interlinedlist.android.feature.documents.domain.Document
import com.interlinedlist.android.feature.documents.domain.DocumentFolder
import com.interlinedlist.android.feature.documents.ui.index.DocumentsScreen
import com.interlinedlist.android.feature.documents.ui.index.DocumentsTestTags
import com.interlinedlist.android.feature.documents.ui.index.DocumentsUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DocumentsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setContent(
        state: DocumentsUiState,
        onOpenDocument: (String) -> Unit = {},
        onCreateDocument: () -> Unit = {},
        onSelectFolder: (String?) -> Unit = {},
    ) {
        composeRule.setContent {
            InterlinedListTheme {
                DocumentsScreen(
                    state = state,
                    onSelectFolder = onSelectFolder,
                    onOpenDocument = onOpenDocument,
                    onCreateDocument = onCreateDocument,
                    onLoadMore = {},
                    onSearch = {},
                )
            }
        }
    }

    @Test
    fun emptyState_isShown_whenNoDocuments() {
        setContent(DocumentsUiState(isLoading = false))
        composeRule.onNodeWithTag(DocumentsTestTags.EMPTY).assertIsDisplayed()
    }

    @Test
    fun documentRows_areRendered_andClickable() {
        var openedId: String? = null
        setContent(
            state = DocumentsUiState(
                documents = listOf(
                    Document("1", "Grocery list", null, "Milk, eggs", null, null, false, null),
                ),
            ),
            onOpenDocument = { openedId = it },
        )

        composeRule.onNodeWithTag(DocumentsTestTags.row("1")).assertIsDisplayed().performClick()
        assert(openedId == "1")
    }

    @Test
    fun createFab_invokesCallback() {
        var created = false
        setContent(state = DocumentsUiState(isLoading = false), onCreateDocument = { created = true })
        composeRule.onNodeWithTag(DocumentsTestTags.CREATE_FAB).performClick()
        assert(created)
    }

    @Test
    fun folderChip_selectsFolder() {
        var selected: String? = "sentinel"
        setContent(
            state = DocumentsUiState(
                documents = listOf(Document("1", "Doc", null, "", "f1", "Work", false, null)),
                folders = listOf(DocumentFolder("f1", "Work", null)),
            ),
            onSelectFolder = { selected = it },
        )
        composeRule.onNodeWithTag(DocumentsTestTags.folderChip("f1")).performClick()
        assert(selected == "f1")
    }

    @Test
    fun subscriptionGate_isShown_whenRequired() {
        setContent(
            DocumentsUiState(
                isLoading = false,
                subscriptionRequired = true,
                errorMessage = "Documents require an active subscription.",
            ),
        )
        composeRule.onNodeWithTag(DocumentsTestTags.SUBSCRIPTION_GATE).assertIsDisplayed()
    }
}
