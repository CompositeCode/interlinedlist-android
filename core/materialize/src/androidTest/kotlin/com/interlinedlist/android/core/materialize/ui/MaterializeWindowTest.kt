package com.interlinedlist.android.core.materialize.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.designsystem.theme.InterlinedListTheme
import com.interlinedlist.android.core.materialize.domain.DocumentListStyle
import com.interlinedlist.android.core.materialize.domain.ListColumnType
import com.interlinedlist.android.core.materialize.domain.MaterializeColumn
import com.interlinedlist.android.core.materialize.domain.MaterializeSource
import com.interlinedlist.android.core.materialize.domain.MaterializeTarget
import com.interlinedlist.android.core.materialize.domain.MaterializedDocument
import com.interlinedlist.android.core.materialize.domain.MaterializedList
import com.interlinedlist.android.core.materialize.domain.RowDataStyle
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The window as the user sees it: the destination switcher, the list editor with
 * its live table, the document editor with its rendered preview, and the success
 * state's links into what was created.
 */
@RunWith(AndroidJUnit4::class)
class MaterializeWindowTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val preview = MaterializePreview(
        suggestedTitle = "Books to Read",
        suggestedDescription = "My reading backlog.",
        suggestedFileName = "books-to-read.md",
        columns = listOf(
            MaterializeColumn("title", "Title", ListColumnType.TEXT, sourceKey = "title"),
            MaterializeColumn("author", "Author", ListColumnType.TEXT, sourceKey = "author"),
        ),
        rows = listOf(
            MaterializePreviewRow(mapOf("title" to "The Dream Machine", "author" to "Waldrop")),
        ),
        totalRowCount = 340,
    )

    private fun state(
        target: MaterializeTarget = MaterializeTarget.LIST,
        source: MaterializeSource = MaterializeSource.Lists(listOf("lst_1")),
    ) = MaterializeWindowUiState.from(MaterializeLaunch(source, target, preview))

    private fun setWindow(
        state: MaterializeWindowUiState,
        onSelectTarget: (MaterializeTarget) -> Unit = {},
        onOpenList: (MaterializedList) -> Unit = {},
        onOpenDocument: (MaterializedDocument) -> Unit = {},
    ) {
        composeRule.setContent {
            InterlinedListTheme {
                MaterializeWindowContent(
                    state = state,
                    onSelectTarget = onSelectTarget,
                    onTitleChange = {},
                    onDescriptionChange = {},
                    onPublicChange = {},
                    onAddColumn = {},
                    onRemoveColumn = {},
                    onColumnNameChange = { _, _ -> },
                    onColumnTypeChange = { _, _ -> },
                    onFileNameChange = {},
                    onListStyleChange = {},
                    onRowDataStyleChange = {},
                    onConfirm = {},
                    onDismiss = {},
                    onOpenList = onOpenList,
                    onOpenDocument = onOpenDocument,
                    onUseDraft = {},
                )
            }
        }
    }

    @Test
    fun theListEditorShowsItsColumnsAndALiveTable() {
        val state = state()
        setWindow(state)

        composeRule.onNodeWithTag(MaterializeWindowTestTags.TITLE).assertIsDisplayed()
        state.columns.forEach {
            composeRule.onNodeWithTag(MaterializeWindowTestTags.column(it.uiId)).assertIsDisplayed()
        }
        composeRule.onNodeWithTag(MaterializeWindowTestTags.ADD_COLUMN).assertIsDisplayed()
        composeRule.onNodeWithTag(MaterializeWindowTestTags.TABLE_PREVIEW).assertIsDisplayed()
        // The preview is live data from the source, and says how much is coming.
        composeRule.onNodeWithText("The Dream Machine").assertIsDisplayed()
        composeRule.onNodeWithText("Showing 1 of 340 rows.").assertIsDisplayed()
    }

    @Test
    fun theDestinationCanBeSwitchedFromInsideTheWindow() {
        var selected: MaterializeTarget? = null
        setWindow(state(), onSelectTarget = { selected = it })

        MaterializeTarget.entries.forEach {
            composeRule.onNodeWithTag(MaterializeWindowTestTags.destination(it)).assertIsDisplayed()
        }
        composeRule.onNodeWithTag(
            MaterializeWindowTestTags.destination(MaterializeTarget.BOTH),
        ).performClick()

        assertThat(selected).isEqualTo(MaterializeTarget.BOTH)
    }

    @Test
    fun theDocumentEditorShowsItsStylesAndARenderedPreview() {
        setWindow(state(target = MaterializeTarget.DOC))

        composeRule.onNodeWithTag(MaterializeWindowTestTags.FILE_NAME).assertIsDisplayed()
        composeRule.onNodeWithTag(
            MaterializeWindowTestTags.listStyle(DocumentListStyle.NUMBERED),
        ).assertIsDisplayed()
        composeRule.onNodeWithTag(
            MaterializeWindowTestTags.rowDataStyle(RowDataStyle.SUB_ITEMS),
        ).assertIsDisplayed()
        composeRule.onNodeWithTag(MaterializeWindowTestTags.DOCUMENT_PREVIEW).assertIsDisplayed()
        // A document has no columns to edit.
        composeRule.onNodeWithTag(MaterializeWindowTestTags.ADD_COLUMN).assertDoesNotExist()
    }

    @Test
    fun aBlankListTitleDisablesCreate() {
        setWindow(state().copy(title = "   "))

        composeRule.onNodeWithTag(MaterializeWindowTestTags.CONFIRM).assertIsNotEnabled()
        composeRule.onNodeWithText("A list title is required").assertIsDisplayed()
    }

    @Test
    fun aValidListCanBeCreated() {
        setWindow(state())

        composeRule.onNodeWithTag(MaterializeWindowTestTags.CONFIRM).assertIsEnabled()
    }

    @Test
    fun bothOffersALinkToTheListAndToTheDocument() {
        var openedList: MaterializedList? = null
        var openedDocument: MaterializedDocument? = null
        setWindow(
            state(target = MaterializeTarget.BOTH).copy(
                success = MaterializeSuccess(
                    list = MaterializedList("lst_new", "Books to Read"),
                    document = MaterializedDocument("doc_new", "Books to Read"),
                ),
            ),
            onOpenList = { openedList = it },
            onOpenDocument = { openedDocument = it },
        )

        composeRule.onNodeWithTag(MaterializeWindowTestTags.SUCCESS).assertIsDisplayed()
        composeRule.onNodeWithTag(MaterializeWindowTestTags.OPEN_LIST).performClick()
        composeRule.onNodeWithTag(MaterializeWindowTestTags.OPEN_DOCUMENT).performClick()

        assertThat(openedList?.id).isEqualTo("lst_new")
        assertThat(openedDocument?.id).isEqualTo("doc_new")
    }

    @Test
    fun aMessagesSourceDoesNotOfferTheMessageDestination() {
        setWindow(state(source = MaterializeSource.Messages(listOf("msg_1"))))

        composeRule.onNodeWithTag(
            MaterializeWindowTestTags.destination(MaterializeTarget.MESSAGE),
        ).assertDoesNotExist()
    }
}
