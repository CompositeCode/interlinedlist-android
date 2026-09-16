package com.interlinedlist.android.feature.documents.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.text.TextRange
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.designsystem.theme.InterlinedListTheme
import com.interlinedlist.android.core.materialize.domain.MaterializeTarget
import com.interlinedlist.android.feature.documents.domain.Document
import com.interlinedlist.android.feature.documents.domain.FolderContents
import com.interlinedlist.android.feature.documents.domain.FolderNode
import com.interlinedlist.android.feature.documents.domain.FolderSummary
import com.interlinedlist.android.feature.documents.ui.browser.DocumentsBrowserScreen
import com.interlinedlist.android.feature.documents.ui.browser.DocumentsBrowserUiState
import com.interlinedlist.android.feature.documents.ui.editor.DocumentEditorScreen
import com.interlinedlist.android.feature.documents.ui.editor.DocumentEditorTestTags
import com.interlinedlist.android.feature.documents.ui.editor.DocumentEditorUiState
import com.interlinedlist.android.feature.documents.ui.materialize.CreateFromTestTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The ＋ Create entry points on a document: the browser row, the editor, and the
 * editor's highlighted selection. What each one *builds* is covered by the JVM
 * tests; this covers that the controls exist and raise the destination picked.
 */
@RunWith(AndroidJUnit4::class)
class CreateFromEntryPointsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val document = Document(
        id = "d1",
        title = "Launch plan",
        content = "# Launch plan\n- Ship it",
        snippet = "Launch plan",
        folderId = null,
        folderName = null,
        isPublic = false,
        updatedAt = null,
    )

    @Test
    fun browserRow_createMenu_raisesThePickedDestination() {
        var picked: Pair<Document, MaterializeTarget>? = null
        composeRule.setContent {
            InterlinedListTheme {
                DocumentsBrowserScreen(
                    state = DocumentsBrowserUiState(
                        isLoading = false,
                        contents = FolderContents(
                            folderId = FolderNode.ROOT_ID,
                            folderName = FolderNode.ROOT_NAME,
                            parentId = null,
                            subfolders = emptyList(),
                            documents = listOf(document),
                            breadcrumb = listOf(FolderSummary(FolderNode.ROOT_ID, FolderNode.ROOT_NAME)),
                        ),
                    ),
                    onOpenFolder = {},
                    onOpenDocument = {},
                    onCreateDocument = {},
                    onCreateFolder = {},
                    onRenameFolder = { _, _ -> },
                    onDeleteFolder = {},
                    onMoveDocument = { _, _ -> },
                    onDeleteDocument = {},
                    onOpenSearch = {},
                    onCloseSearch = {},
                    onSearchQueryChange = {},
                    onBack = null,
                    onCreateFrom = { doc, target -> picked = doc to target },
                )
            }
        }

        composeRule.onNodeWithTag(CreateFromTestTags.row("d1")).performClick()
        composeRule.onNodeWithTag(CreateFromTestTags.target(MaterializeTarget.LIST)).performClick()

        assertThat(picked).isEqualTo(document to MaterializeTarget.LIST)
    }

    @Test
    fun editor_createMenu_raisesThePickedDestination() {
        var picked: MaterializeTarget? = null
        setEditorContent(onCreateFrom = { picked = it })

        composeRule.onNodeWithTag(CreateFromTestTags.EDITOR).performClick()
        composeRule.onNodeWithTag(CreateFromTestTags.target(MaterializeTarget.DOC)).performClick()

        assertThat(picked).isEqualTo(MaterializeTarget.DOC)
    }

    @Test
    fun editor_selectionAction_isHidden_untilSomethingIsHighlighted() {
        setEditorContent()

        composeRule.onAllNodesWithTag(DocumentEditorTestTags.SELECTION_BAR).assertCountEquals(0)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun editor_selectionAction_raisesTheHighlightedMarkdown() {
        var picked: Pair<String, MaterializeTarget>? = null
        setEditorContent(onCreateFromSelection = { markdown, target -> picked = markdown to target })

        // Highlight the "## Week one\n- Ship it" passage.
        val body = "# Launch plan\n\n## Week one\n- Ship it"
        val start = body.indexOf("## Week one")
        composeRule.onNodeWithTag(DocumentEditorTestTags.BODY)
            .performTextInputSelection(TextRange(start, body.length))

        composeRule.onNodeWithTag(CreateFromTestTags.SELECTION).performClick()
        composeRule.onNodeWithTag(CreateFromTestTags.target(MaterializeTarget.LIST)).performClick()

        assertThat(picked).isEqualTo("## Week one\n- Ship it" to MaterializeTarget.LIST)
    }

    private fun setEditorContent(
        onCreateFrom: (MaterializeTarget) -> Unit = {},
        onCreateFromSelection: (String, MaterializeTarget) -> Unit = { _, _ -> },
    ) {
        composeRule.setContent {
            InterlinedListTheme {
                DocumentEditorScreen(
                    state = DocumentEditorUiState(
                        documentId = "d1",
                        title = "Launch plan",
                        content = "# Launch plan\n\n## Week one\n- Ship it",
                        isLoading = false,
                    ),
                    onTitleChange = {},
                    onContentChange = {},
                    onTogglePreview = {},
                    onSave = {},
                    onDelete = {},
                    onBack = {},
                    onCreateFrom = onCreateFrom,
                    onCreateFromSelection = onCreateFromSelection,
                )
            }
        }
    }
}
