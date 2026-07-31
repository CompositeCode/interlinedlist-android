package com.interlinedlist.android.feature.lists.ui.folders

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.interlinedlist.android.core.designsystem.theme.InterlinedListTheme
import com.interlinedlist.android.feature.lists.domain.ListFolder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the folder browser renders folders and that the rename dialog opens from
 * a folder's overflow menu, accepts a new name, and submits it to the caller.
 */
@RunWith(AndroidJUnit4::class)
class FolderBrowserScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val folders = listOf(
        ListFolder("f1", "Work", null),
        ListFolder("f2", "Personal", null),
    )

    private fun setScreen(
        onRename: (ListFolder, String) -> Unit = { _, _ -> },
        onDelete: (ListFolder) -> Unit = {},
    ) {
        composeRule.setContent {
            InterlinedListTheme {
                FolderBrowserScreen(
                    state = FolderBrowserUiState(folders = folders, isLoading = false),
                    onBack = {},
                    onRename = onRename,
                    onMove = { _, _ -> },
                    onDelete = onDelete,
                )
            }
        }
    }

    @Test
    fun rendersFolders() {
        setScreen()

        composeRule.onNodeWithTag(FolderBrowserTestTags.folder("f1")).assertIsDisplayed()
        composeRule.onNodeWithTag(FolderBrowserTestTags.folder("f2")).assertIsDisplayed()
        composeRule.onNodeWithText("Work").assertIsDisplayed()
    }

    @Test
    fun renameDialog_opensEditsAndSubmits() {
        var renamedTo: Pair<String, String>? = null
        setScreen(onRename = { folder, name -> renamedTo = folder.id to name })

        // Open the row's overflow, then the rename action.
        composeRule.onNodeWithTag(FolderBrowserTestTags.overflow("f1")).performClick()
        composeRule.onNodeWithText("Rename").performClick()

        // The rename dialog is shown with the field seeded from the folder name.
        composeRule.onNodeWithTag(FolderBrowserTestTags.RENAME_DIALOG).assertIsDisplayed()
        composeRule.onNodeWithTag(FolderBrowserTestTags.RENAME_FIELD).performTextClearance()
        composeRule.onNodeWithTag(FolderBrowserTestTags.RENAME_FIELD).performTextInput("Archive")
        composeRule.onNodeWithTag(FolderBrowserTestTags.RENAME_CONFIRM).performClick()

        assert(renamedTo == "f1" to "Archive") { "Expected rename f1 -> Archive, got $renamedTo" }
    }

    @Test
    fun deleteDialog_confirmsDeletion() {
        var deletedId: String? = null
        setScreen(onDelete = { deletedId = it.id })

        composeRule.onNodeWithTag(FolderBrowserTestTags.overflow("f2")).performClick()
        composeRule.onNodeWithText("Delete").performClick()

        composeRule.onNodeWithTag(FolderBrowserTestTags.DELETE_DIALOG).assertIsDisplayed()
        composeRule.onNodeWithTag(FolderBrowserTestTags.DELETE_CONFIRM).performClick()

        assert(deletedId == "f2") { "Expected delete f2, got $deletedId" }
    }
}
