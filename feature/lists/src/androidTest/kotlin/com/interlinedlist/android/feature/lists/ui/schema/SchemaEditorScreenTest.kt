package com.interlinedlist.android.feature.lists.ui.schema

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.designsystem.theme.InterlinedListTheme
import com.interlinedlist.android.feature.lists.domain.FieldType
import com.interlinedlist.android.feature.lists.domain.ListSummary
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the schema editor renders the existing columns as editable rows and
 * exposes the add-column and save affordances.
 */
@RunWith(AndroidJUnit4::class)
class SchemaEditorScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setScreen(
        state: SchemaEditorUiState,
        onSelectParent: (String) -> Unit = {},
    ) {
        composeRule.setContent {
            InterlinedListTheme {
                SchemaEditorScreen(
                    state = state,
                    onBack = {},
                    onAddColumn = {},
                    onRemoveColumn = {},
                    onKeyChange = { _, _ -> },
                    onLabelChange = { _, _ -> },
                    onTypeChange = { _, _ -> },
                    onSave = {},
                    onSelectParent = onSelectParent,
                )
            }
        }
    }

    @Test
    fun rendersExistingColumnsAndActions() {
        setScreen(
            SchemaEditorUiState(
                columns = listOf(
                    EditableColumn(0, "title", "Title", FieldType.TEXT),
                    EditableColumn(1, "pages", "Pages", FieldType.NUMBER),
                ),
                isLoading = false,
            ),
        )

        composeRule.onNodeWithTag(SchemaEditorTestTags.column(0)).assertIsDisplayed()
        composeRule.onNodeWithTag(SchemaEditorTestTags.column(1)).assertIsDisplayed()
        composeRule.onNodeWithTag(SchemaEditorTestTags.ADD_COLUMN).assertIsDisplayed()
        composeRule.onNodeWithTag(SchemaEditorTestTags.SAVE).assertIsDisplayed()
        // The field key is rendered into its editable text field.
        composeRule.onNodeWithText("title").assertIsDisplayed()
    }

    @Test
    fun showsProgress_whenLoading() {
        setScreen(SchemaEditorUiState(isLoading = true))

        composeRule.onNodeWithTag(SchemaEditorTestTags.PROGRESS).assertIsDisplayed()
    }

    @Test
    fun locksTheEditor_forAGithubBackedList() {
        setScreen(
            SchemaEditorUiState(
                columns = listOf(
                    EditableColumn(0, "number", "Issue #", FieldType.NUMBER, readOnly = true),
                    EditableColumn(1, "title", "Title", FieldType.TEXT),
                ),
                isLoading = false,
                isSchemaLocked = true,
                githubRepo = "octocat/Hello-World",
                parentOptions = listOf(ListSummary("L2", "Projects", null, 0, null, false, null)),
            ),
        )

        // The columns are visible but there is nothing to edit or save with.
        composeRule.onNodeWithTag(SchemaEditorTestTags.LOCKED_NOTICE).assertIsDisplayed()
        composeRule.onNodeWithTag(SchemaEditorTestTags.column(0)).assertIsDisplayed()
        composeRule.onNodeWithTag(SchemaEditorTestTags.ADD_COLUMN).assertDoesNotExist()
        composeRule.onNodeWithTag(SchemaEditorTestTags.SAVE).assertDoesNotExist()
        composeRule.onNodeWithTag(SchemaEditorTestTags.key(1)).assertDoesNotExist()
    }

    @Test
    fun offersTheParentList_asTheOnlyEditOnALockedSchema() {
        var chosen: String? = null
        setScreen(
            SchemaEditorUiState(
                columns = listOf(EditableColumn(0, "title", "Title", FieldType.TEXT)),
                isLoading = false,
                isSchemaLocked = true,
                githubRepo = "octocat/Hello-World",
                parentOptions = listOf(ListSummary("L2", "Projects", null, 0, null, false, null)),
            ),
            onSelectParent = { chosen = it },
        )

        composeRule.onNodeWithTag(SchemaEditorTestTags.PARENT_PICKER).assertIsDisplayed()
        composeRule.onNodeWithTag(SchemaEditorTestTags.parentOption("L2")).performClick()

        assertThat(chosen).isEqualTo("L2")
    }

    @Test
    fun keepsTheEditorEditable_forALocalList() {
        setScreen(
            SchemaEditorUiState(
                columns = listOf(EditableColumn(0, "title", "Title", FieldType.TEXT)),
                isLoading = false,
                isSchemaLocked = false,
            ),
        )

        composeRule.onNodeWithTag(SchemaEditorTestTags.ADD_COLUMN).assertIsDisplayed()
        composeRule.onNodeWithTag(SchemaEditorTestTags.SAVE).assertIsDisplayed()
        composeRule.onNodeWithTag(SchemaEditorTestTags.key(0)).assertIsDisplayed()
        composeRule.onNodeWithTag(SchemaEditorTestTags.LOCKED_NOTICE).assertDoesNotExist()
        composeRule.onNodeWithTag(SchemaEditorTestTags.PARENT_PICKER).assertDoesNotExist()
    }
}
