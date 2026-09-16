package com.interlinedlist.android.feature.lists.ui.detail

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.interlinedlist.android.core.designsystem.theme.InterlinedListTheme
import com.interlinedlist.android.feature.lists.domain.FieldType
import com.interlinedlist.android.feature.lists.domain.ListPresence
import com.interlinedlist.android.feature.lists.domain.ListRow
import com.interlinedlist.android.feature.lists.domain.ListSchema
import com.interlinedlist.android.feature.lists.domain.ListSummary
import com.interlinedlist.android.feature.lists.domain.SchemaField
import com.interlinedlist.android.feature.lists.ui.presence.ListPresenceTestTags
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

    private fun setScreen(
        state: ListDetailUiState,
        onOpenShare: () -> Unit = {},
        onOpenList: (String) -> Unit = {},
        onNewChildList: () -> Unit = {},
    ) {
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
                    onOpenList = onOpenList,
                    onNewChildList = onNewChildList,
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

    @Test
    fun rendersParentBreadcrumb_andOpensAnAncestor() {
        var opened: String? = null
        setScreen(
            ListDetailUiState(
                summary = ListSummary("L1", "Chapter 3", null, 0, null, false, null, "P1"),
                schema = schema,
                rows = emptyList(),
                isLoading = false,
                breadcrumb = listOf(
                    ListSummary("ROOT", "Book", null, 0, null, false, null, null),
                    ListSummary("P1", "Part 2", null, 0, null, false, null, "ROOT"),
                ),
            ),
            onOpenList = { opened = it },
        )

        composeRule.onNodeWithTag(ListDetailTestTags.BREADCRUMB).assertIsDisplayed()
        composeRule.onNodeWithText("Book").assertIsDisplayed()
        composeRule.onNodeWithText("Part 2").assertIsDisplayed()

        composeRule.onNodeWithTag(ListDetailTestTags.crumb("ROOT")).performClick()
        assert(opened == "ROOT")
    }

    @Test
    fun hidesBreadcrumb_forARootList() {
        setScreen(
            ListDetailUiState(
                summary = ListSummary("L1", "Reading", null, 0, null, false, null),
                schema = schema,
                rows = emptyList(),
                isLoading = false,
            ),
        )

        composeRule.onNodeWithTag(ListDetailTestTags.BREADCRUMB).assertDoesNotExist()
    }

    @Test
    fun showsWhoElseIsInTheList() {
        setScreen(
            ListDetailUiState(
                summary = ListSummary("L1", "Reading", null, 0, null, false, null),
                schema = schema,
                rows = emptyList(),
                isLoading = false,
                presence = listOf(ListPresence("u2", displayName = "Casey", username = "casey")),
                isCollaborative = true,
            ),
        )

        composeRule.onNodeWithTag(ListPresenceTestTags.ROW).assertIsDisplayed()
        composeRule.onNodeWithTag(ListPresenceTestTags.avatar("u2")).assertIsDisplayed()
    }

    @Test
    fun hidesPresence_whenNobodyElseIsHere() {
        setScreen(
            ListDetailUiState(
                summary = ListSummary("L1", "Reading", null, 0, null, false, null),
                schema = schema,
                rows = emptyList(),
                isLoading = false,
            ),
        )

        composeRule.onNodeWithTag(ListPresenceTestTags.ROW).assertDoesNotExist()
    }

    @Test
    fun overflowOffersANewChildList() {
        var requested = false
        setScreen(
            ListDetailUiState(
                summary = ListSummary("L1", "Reading", null, 0, null, false, null),
                schema = schema,
                rows = emptyList(),
                isLoading = false,
            ),
            onNewChildList = { requested = true },
        )

        composeRule.onNodeWithTag(ListDetailTestTags.OVERFLOW).performClick()
        composeRule.onNodeWithTag(ListDetailTestTags.NEW_CHILD).performClick()

        assert(requested)
    }
}
