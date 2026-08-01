package com.interlinedlist.android.feature.lists.ui.detail

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.interlinedlist.android.core.designsystem.theme.InterlinedListTheme
import com.interlinedlist.android.feature.lists.domain.FieldType
import com.interlinedlist.android.feature.lists.domain.ListRow
import com.interlinedlist.android.feature.lists.domain.ListSchema
import com.interlinedlist.android.feature.lists.domain.ListSummary
import com.interlinedlist.android.feature.lists.domain.SchemaField
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the detail screen renders rows generically against the schema: the
 * column labels and cell values come from the (dynamic) schema + row data, not
 * from hardcoded columns.
 */
@RunWith(AndroidJUnit4::class)
class ListDetailScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val schema = ListSchema(
        listOf(
            SchemaField("title", "Title", FieldType.TEXT),
            SchemaField("pages", "Pages", FieldType.NUMBER),
        ),
    )

    private fun setScreen(state: ListDetailUiState, onOpenShare: () -> Unit = {}) {
        composeRule.setContent {
            InterlinedListTheme {
                ListDetailScreen(
                    state = state,
                    onBack = {},
                    onAddRow = {},
                    onEditRow = {},
                    onDeleteRow = {},
                    onDeleteList = {},
                    onOpenShare = onOpenShare,
                )
            }
        }
    }

    @Test
    fun rendersSchemaColumnsAndRowValues() {
        setScreen(
            ListDetailUiState(
                summary = ListSummary("L1", "Reading", null, 1, null, false, null),
                schema = schema,
                rows = listOf(ListRow("r1", mapOf("title" to "Dune", "pages" to "412"))),
                isLoading = false,
            ),
        )

        composeRule.onNodeWithTag(ListDetailTestTags.row("r1")).assertIsDisplayed()
        // Column labels are derived from the schema.
        composeRule.onNodeWithText("Title").assertIsDisplayed()
        composeRule.onNodeWithText("Pages").assertIsDisplayed()
        // Values are projected from the dynamic row data.
        composeRule.onNodeWithText("Dune").assertIsDisplayed()
        composeRule.onNodeWithText("412").assertIsDisplayed()
    }

    @Test
    fun showsEmptyState_whenNoRows() {
        setScreen(
            ListDetailUiState(
                summary = ListSummary("L1", "Reading", null, 0, null, false, null),
                schema = schema,
                rows = emptyList(),
                isLoading = false,
            ),
        )

        composeRule.onNodeWithTag(ListDetailTestTags.EMPTY).assertIsDisplayed()
    }

    @Test
    fun overflowMenu_exposesShare_andInvokesCallback() {
        var shared = false
        setScreen(
            ListDetailUiState(
                summary = ListSummary("L1", "Reading", null, 1, null, false, null),
                schema = schema,
                rows = listOf(ListRow("r1", mapOf("title" to "Dune"))),
                isLoading = false,
            ),
            onOpenShare = { shared = true },
        )

        composeRule.onNodeWithTag(ListDetailTestTags.OVERFLOW).performClick()
        composeRule.onNodeWithTag(ListDetailTestTags.SHARE).assertIsDisplayed().performClick()
        assert(shared)
    }
}
