package com.interlinedlist.android.feature.lists.ui.schema

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.interlinedlist.android.core.designsystem.theme.InterlinedListTheme
import com.interlinedlist.android.feature.lists.domain.FieldType
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

    private fun setScreen(state: SchemaEditorUiState) {
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
}
