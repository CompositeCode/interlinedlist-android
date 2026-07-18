package com.interlinedlist.android.feature.documents.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.interlinedlist.android.core.designsystem.theme.InterlinedListTheme
import com.interlinedlist.android.feature.documents.domain.Document
import com.interlinedlist.android.feature.documents.domain.FolderContents
import com.interlinedlist.android.feature.documents.domain.FolderNode
import com.interlinedlist.android.feature.documents.domain.FolderSummary
import com.interlinedlist.android.feature.documents.ui.browser.DocumentsBrowserScreen
import com.interlinedlist.android.feature.documents.ui.browser.DocumentsBrowserTestTags
import com.interlinedlist.android.feature.documents.ui.browser.DocumentsBrowserUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DocumentsBrowserScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun rootContents(
        subfolders: List<FolderSummary> = emptyList(),
        documents: List<Document> = emptyList(),
    ) = FolderContents(
        folderId = FolderNode.ROOT_ID,
        folderName = FolderNode.ROOT_NAME,
        parentId = null,
        subfolders = subfolders,
        documents = documents,
        breadcrumb = listOf(FolderSummary(FolderNode.ROOT_ID, FolderNode.ROOT_NAME)),
    )

    private fun setContent(
        state: DocumentsBrowserUiState,
        onOpenFolder: (String) -> Unit = {},
        onOpenDocument: (String) -> Unit = {},
        onCreateFolder: (String) -> Unit = {},
        onSearchQueryChange: (String) -> Unit = {},
        onBack: (() -> Unit)? = null,
    ) {
        composeRule.setContent {
            InterlinedListTheme {
                DocumentsBrowserScreen(
                    state = state,
                    onOpenFolder = onOpenFolder,
                    onOpenDocument = onOpenDocument,
                    onCreateDocument = {},
                    onCreateFolder = onCreateFolder,
                    onRenameFolder = { _, _ -> },
                    onDeleteFolder = {},
                    onMoveDocument = { _, _ -> },
                    onDeleteDocument = {},
                    onOpenSearch = {},
                    onCloseSearch = {},
                    onSearchQueryChange = onSearchQueryChange,
                    onBack = onBack,
                )
            }
        }
    }

    @Test
    fun emptyState_isShown_whenFolderIsEmpty() {
        setContent(DocumentsBrowserUiState(isLoading = false, contents = rootContents()))
        composeRule.onNodeWithTag(DocumentsBrowserTestTags.EMPTY).assertIsDisplayed()
    }

    @Test
    fun documentRows_areRendered_andClickable() {
        var openedId: String? = null
        setContent(
            state = DocumentsBrowserUiState(
                isLoading = false,
                contents = rootContents(
                    documents = listOf(Document("1", "Grocery list", null, "Milk, eggs", null, null, false, null)),
                ),
            ),
            onOpenDocument = { openedId = it },
        )

        composeRule.onNodeWithTag(DocumentsBrowserTestTags.docRow("1")).assertIsDisplayed().performClick()
        assert(openedId == "1")
    }

    @Test
    fun folderRow_drillsDown_whenTapped() {
        var openedFolder: String? = null
        setContent(
            state = DocumentsBrowserUiState(
                isLoading = false,
                contents = rootContents(subfolders = listOf(FolderSummary("f1", "Work", 2, 0))),
            ),
            onOpenFolder = { openedFolder = it },
        )

        composeRule.onNodeWithTag(DocumentsBrowserTestTags.folderRow("f1")).assertIsDisplayed().performClick()
        assert(openedFolder == "f1")
    }

    @Test
    fun breadcrumb_navigatesToAncestor() {
        var openedFolder: String? = null
        val nested = FolderContents(
            folderId = "f2",
            folderName = "Reports",
            parentId = "f1",
            subfolders = emptyList(),
            documents = listOf(Document("d1", "Doc", null, "", "f2", null, false, null)),
            breadcrumb = listOf(
                FolderSummary(FolderNode.ROOT_ID, "Documents"),
                FolderSummary("f1", "Work"),
                FolderSummary("f2", "Reports"),
            ),
        )
        setContent(
            state = DocumentsBrowserUiState(isLoading = false, contents = nested),
            onOpenFolder = { openedFolder = it },
            onBack = {},
        )

        composeRule.onNodeWithTag(DocumentsBrowserTestTags.crumb("f1")).performClick()
        assert(openedFolder == "f1")
    }

    @Test
    fun createFolder_dialog_invokesCallback() {
        var createdName: String? = null
        setContent(
            state = DocumentsBrowserUiState(isLoading = false, contents = rootContents()),
            onCreateFolder = { createdName = it },
        )

        composeRule.onNodeWithTag(DocumentsBrowserTestTags.CREATE_FOLDER).performClick()
        composeRule.onNodeWithTag("dialogInput").performTextInput("Ideas")
        composeRule.onNodeWithTag("dialogConfirm").performClick()

        assert(createdName == "Ideas")
    }

    @Test
    fun searchOverlay_queriesAsYouType() {
        var lastQuery: String? = null
        setContent(
            state = DocumentsBrowserUiState(
                isLoading = false,
                contents = rootContents(),
                isSearchActive = true,
            ),
            onSearchQueryChange = { lastQuery = it },
        )

        composeRule.onNodeWithTag(DocumentsBrowserTestTags.SEARCH_FIELD).performTextInput("notes")
        assert(lastQuery == "notes")
    }

    @Test
    fun subscriptionGate_isShown_whenRequired() {
        setContent(
            DocumentsBrowserUiState(
                isLoading = false,
                subscriptionRequired = true,
                errorMessage = "Documents require an active subscription.",
                contents = rootContents(),
            ),
        )
        composeRule.onNodeWithTag(DocumentsBrowserTestTags.SUBSCRIPTION_GATE).assertIsDisplayed()
    }
}
