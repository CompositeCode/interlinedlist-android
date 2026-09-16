package com.interlinedlist.android.feature.documents.ui.powered

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.feature.ai.domain.AiArtifact
import com.interlinedlist.android.feature.ai.domain.AiAvailability
import com.interlinedlist.android.feature.ai.domain.AiCreated
import com.interlinedlist.android.feature.ai.domain.AiError
import com.interlinedlist.android.feature.ai.domain.AiFeature
import com.interlinedlist.android.feature.ai.domain.AiGate
import com.interlinedlist.android.feature.ai.domain.AiGeneration
import com.interlinedlist.android.feature.ai.domain.AiPreview
import com.interlinedlist.android.feature.ai.domain.AiQuota
import com.interlinedlist.android.feature.ai.domain.AiResult
import com.interlinedlist.android.feature.ai.domain.AiSuggestInput
import com.interlinedlist.android.feature.documents.domain.ListSource
import com.interlinedlist.android.feature.documents.ui.FakeDocumentsRepository
import com.interlinedlist.android.feature.documents.ui.testDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * The Powered Document flow: pick a mode and its source, preview, then confirm.
 * The load-bearing assertions are that `/generate` is reached only through an
 * approved preview, and that anything refusable is refused before a request (and
 * therefore a quota unit) is spent.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PoweredDocumentViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var docs: FakeDocumentsRepository
    private lateinit var lists: FakeListSourcesRepository
    private lateinit var ai: FakeAiRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        docs = FakeDocumentsRepository()
        lists = FakeListSourcesRepository()
        ai = FakeAiRepository()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = PoweredDocumentViewModel(docs, lists, ai, AiGate(ai))

    private fun draft(title: String = "Getting Started with Widgets") = AiPreview(
        feature = AiFeature.POWERED_DOCUMENT,
        artifact = AiArtifact(
            buildJsonObject {
                put("kind", "document")
                put("title", title)
                put("markdown", "# $title\n\nBody.")
            },
        ),
        quota = AiQuota(usedToday = 1, dailyLimit = 50, remaining = 49),
    )

    private fun lastContext(): JsonObject? = ai.suggestCalls.last().second.context

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.content

    // --- One test per mode: source selection -> suggest -> confirm ----------

    @Test
    fun `standalone article mode previews from the topic then persists on confirm`() =
        runTest(dispatcher) {
            ai.suggestResult = AiResult.Success(draft())
            val vm = viewModel()
            advanceUntilIdle()

            vm.selectMode(PoweredDocumentMode.ARTICLE)
            vm.onInstructionChange("An intro to widgets for new users")
            assertThat(vm.uiState.value.canSuggest).isTrue()
            vm.suggest()
            advanceUntilIdle()

            assertThat(ai.suggestCalls).hasSize(1)
            assertThat(ai.suggestCalls.single().first).isEqualTo(AiFeature.POWERED_DOCUMENT)
            assertThat(lastContext()?.str("mode")).isEqualTo("article")
            assertThat(ai.suggestCalls.single().second.input)
                .isEqualTo("An intro to widgets for new users")
            // A suggestion alone writes nothing.
            assertThat(ai.generateCalls).isEmpty()
            assertThat(vm.uiState.value.previewing).isNotNull()

            vm.confirm()
            advanceUntilIdle()

            assertThat(ai.generateCalls).hasSize(1)
            assertThat(ai.generateCalls.single().feature).isEqualTo(AiFeature.POWERED_DOCUMENT)
            assertThat(vm.uiState.value.createdDocumentId).isEqualTo("doc-new")
            assertThat(docs.refreshTreeCount).isAtLeast(1)
        }

    @Test
    fun `from list mode sends the picked list id then persists on confirm`() =
        runTest(dispatcher) {
            lists.result = ApiResult.Success(
                listOf(ListSource("list-7", "Reading list", "Books to read")),
            )
            ai.suggestResult = AiResult.Success(draft("My Reading Overview"))
            val vm = viewModel()
            advanceUntilIdle()

            vm.selectMode(PoweredDocumentMode.FROM_LIST)
            vm.openListPicker()
            advanceUntilIdle()
            assertThat(vm.uiState.value.listSources.map { it.id }).containsExactly("list-7")

            vm.selectList(vm.uiState.value.listSources.single())
            assertThat(vm.uiState.value.picker).isEqualTo(SourcePicker.NONE)
            assertThat(vm.uiState.value.canSuggest).isTrue()

            vm.suggest()
            advanceUntilIdle()

            assertThat(lastContext()?.str("mode")).isEqualTo("from_list")
            assertThat(lastContext()?.str("listId")).isEqualTo("list-7")
            // Blank instruction falls back to the mode's default, never an empty input.
            assertThat(ai.suggestCalls.single().second.input).isNotEmpty()
            assertThat(ai.generateCalls).isEmpty()

            vm.confirm()
            advanceUntilIdle()

            assertThat(ai.generateCalls).hasSize(1)
            assertThat(vm.uiState.value.createdDocumentId).isEqualTo("doc-new")
        }

    @Test
    fun `from article mode sends the picked document id then persists on confirm`() =
        runTest(dispatcher) {
            docs.searchResult = ApiResult.Success(listOf(testDocument("doc-42", title = "Widgets 101")))
            ai.suggestResult = AiResult.Success(draft())
            val vm = viewModel()
            advanceUntilIdle()

            vm.selectMode(PoweredDocumentMode.FROM_ARTICLE)
            vm.openDocumentPicker()
            vm.onDocumentQueryChange("widgets")
            advanceUntilIdle()
            assertThat(vm.uiState.value.documentResults.map { it.id }).containsExactly("doc-42")

            vm.selectDocument("doc-42")
            assertThat(vm.uiState.value.selectedDocument?.title).isEqualTo("Widgets 101")
            assertThat(vm.uiState.value.canSuggest).isTrue()

            vm.suggest()
            advanceUntilIdle()

            assertThat(lastContext()?.str("mode")).isEqualTo("from_article")
            assertThat(lastContext()?.str("documentId")).isEqualTo("doc-42")
            assertThat(ai.generateCalls).isEmpty()

            vm.confirm()
            advanceUntilIdle()

            assertThat(ai.generateCalls).hasSize(1)
            assertThat(vm.uiState.value.createdDocumentId).isEqualTo("doc-new")
        }

    @Test
    fun `research url mode sends the url then persists on confirm`() = runTest(dispatcher) {
        ai.suggestResult = AiResult.Success(draft())
        val vm = viewModel()
        advanceUntilIdle()

        vm.selectMode(PoweredDocumentMode.RESEARCH_URL)
        vm.onResearchUrlChange("https://example.com/widgets")
        assertThat(vm.uiState.value.canSuggest).isTrue()

        vm.suggest()
        advanceUntilIdle()

        assertThat(lastContext()?.str("mode")).isEqualTo("research_url")
        assertThat(lastContext()?.str("url")).isEqualTo("https://example.com/widgets")
        assertThat(ai.generateCalls).isEmpty()

        vm.confirm()
        advanceUntilIdle()

        assertThat(ai.generateCalls).hasSize(1)
        assertThat(vm.uiState.value.createdDocumentId).isEqualTo("doc-new")
    }

    // --- Nothing is written without approval --------------------------------

    @Test
    fun `backing out of a preview persists nothing`() = runTest(dispatcher) {
        ai.suggestResult = AiResult.Success(draft())
        val vm = viewModel()
        advanceUntilIdle()

        vm.onInstructionChange("An intro to widgets")
        vm.suggest()
        advanceUntilIdle()
        assertThat(vm.uiState.value.previewing).isNotNull()

        vm.discard()
        advanceUntilIdle()

        assertThat(ai.generateCalls).isEmpty()
        assertThat(vm.uiState.value.previewing).isNull()

        // And a confirm after backing out still writes nothing.
        vm.confirm()
        advanceUntilIdle()
        assertThat(ai.generateCalls).isEmpty()
    }

    @Test
    fun `confirming sends the user's edited title with the artifact`() = runTest(dispatcher) {
        ai.suggestResult = AiResult.Success(draft("Draft title"))
        val vm = viewModel()
        advanceUntilIdle()

        vm.onInstructionChange("An intro to widgets")
        vm.suggest()
        advanceUntilIdle()
        assertThat(vm.uiState.value.editedTitle).isEqualTo("Draft title")

        vm.onEditedTitleChange("Widgets, properly explained")
        vm.confirm()
        advanceUntilIdle()

        val confirmed = ai.generateCalls.single()
        assertThat(confirmed.artifact.documentTitle).isEqualTo("Widgets, properly explained")
        // The rest of the envelope round-trips untouched.
        assertThat(confirmed.artifact.documentMarkdown).contains("Draft title")
    }

    // --- Gating -------------------------------------------------------------

    @Test
    fun `a free account sees no control and can issue nothing`() = runTest(dispatcher) {
        ai.availability = AiAvailability.NotSubscribed
        val vm = viewModel()
        advanceUntilIdle()

        assertThat(vm.uiState.value.isAiEnabled).isFalse()

        vm.onInstructionChange("An intro to widgets")
        assertThat(vm.uiState.value.canSuggest).isFalse()
        vm.suggest()
        advanceUntilIdle()

        assertThat(ai.suggestCalls).isEmpty()
        assertThat(ai.generateCalls).isEmpty()
    }

    @Test
    fun `an unconfigured deployment sees no control and can issue nothing`() = runTest(dispatcher) {
        ai.availability = AiAvailability.Unavailable
        val vm = viewModel()
        advanceUntilIdle()

        assertThat(vm.uiState.value.isAiEnabled).isFalse()
        vm.onInstructionChange("An intro to widgets")
        vm.suggest()
        advanceUntilIdle()

        assertThat(ai.suggestCalls).isEmpty()
    }

    @Test
    fun `the remaining daily allowance is surfaced`() = runTest(dispatcher) {
        ai.availability = AiAvailability.Available(AiQuota(usedToday = 8, dailyLimit = 50, remaining = 42))
        val vm = viewModel()
        advanceUntilIdle()

        assertThat(vm.uiState.value.quota?.remainingActions).isEqualTo(42)
        assertThat(vm.uiState.value.isQuotaExhausted).isFalse()
    }

    // --- Local validation, before a request is spent -------------------------

    @Test
    fun `an invalid research url is refused before any request`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.selectMode(PoweredDocumentMode.RESEARCH_URL)
        vm.onResearchUrlChange("not a url")
        assertThat(vm.uiState.value.canSuggest).isFalse()

        vm.suggest()
        advanceUntilIdle()

        assertThat(ai.suggestCalls).isEmpty()
        assertThat(vm.uiState.value.validationMessage).contains("http")
    }

    @Test
    fun `a non-http research url is refused before any request`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.selectMode(PoweredDocumentMode.RESEARCH_URL)
        vm.onResearchUrlChange("ftp://example.com/file.txt")

        vm.suggest()
        advanceUntilIdle()

        assertThat(ai.suggestCalls).isEmpty()
        assertThat(vm.uiState.value.errorMessage).isNotNull()
    }

    @Test
    fun `a derived mode with no source picked issues nothing`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.selectMode(PoweredDocumentMode.FROM_LIST)
        vm.suggest()
        advanceUntilIdle()

        assertThat(ai.suggestCalls).isEmpty()
        assertThat(vm.uiState.value.validationMessage).isEqualTo("Choose a list to write from.")
    }

    @Test
    fun `an over-length instruction is refused before any request`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onInstructionChange(List(501) { "word" }.joinToString(" "))
        vm.suggest()
        advanceUntilIdle()

        assertThat(ai.suggestCalls).isEmpty()
        assertThat(vm.uiState.value.validationMessage).contains("500 words")
    }

    // --- Error wording ------------------------------------------------------

    @Test
    fun `quota exceeded reads as the daily limit, not a generic failure`() = runTest(dispatcher) {
        ai.suggestResult = AiResult.Failure(AiError.QuotaExceeded("Daily quota reached"))
        val vm = viewModel()
        advanceUntilIdle()

        vm.onInstructionChange("An intro to widgets")
        vm.suggest()
        advanceUntilIdle()

        assertThat(vm.uiState.value.errorMessage).isEqualTo("Daily AI limit reached. Try again tomorrow.")
        assertThat(vm.uiState.value.previewing).isNull()
        assertThat(ai.generateCalls).isEmpty()
    }

    @Test
    fun `a rate limit keeps its own wording and the retry delay`() = runTest(dispatcher) {
        ai.suggestResult = AiResult.Failure(AiError.RateLimited(retryAfterSeconds = 30))
        val vm = viewModel()
        advanceUntilIdle()

        vm.onInstructionChange("An intro to widgets")
        vm.suggest()
        advanceUntilIdle()

        assertThat(vm.uiState.value.errorMessage).contains("30 seconds")
    }

    @Test
    fun `a quota exceeded on confirm keeps the draft on screen unwritten`() = runTest(dispatcher) {
        ai.suggestResult = AiResult.Success(draft())
        ai.generateResult = AiResult.Failure(AiError.QuotaExceeded(null))
        val vm = viewModel()
        advanceUntilIdle()

        vm.onInstructionChange("An intro to widgets")
        vm.suggest()
        advanceUntilIdle()
        vm.confirm()
        advanceUntilIdle()

        assertThat(vm.uiState.value.errorMessage).isEqualTo("Daily AI limit reached. Try again tomorrow.")
        assertThat(vm.uiState.value.createdDocumentId).isNull()
    }

    @Test
    fun `an unrecognised created payload does not claim a document was opened`() =
        runTest(dispatcher) {
            ai.suggestResult = AiResult.Success(draft())
            ai.generateResult =
                AiResult.Success(AiGeneration(AiFeature.POWERED_DOCUMENT, AiCreated.Unrecognised))
            val vm = viewModel()
            advanceUntilIdle()

            vm.onInstructionChange("An intro to widgets")
            vm.suggest()
            advanceUntilIdle()
            vm.confirm()
            advanceUntilIdle()

            assertThat(ai.generateCalls).hasSize(1)
            assertThat(vm.uiState.value.createdDocumentId).isNull()
        }

    // --- Source picker failures --------------------------------------------

    @Test
    fun `a failure loading lists is reported without blocking the other modes`() =
        runTest(dispatcher) {
            lists.failWith(com.interlinedlist.android.core.common.result.AppError.Network(null))
            val vm = viewModel()
            advanceUntilIdle()

            vm.selectMode(PoweredDocumentMode.FROM_LIST)
            vm.openListPicker()
            advanceUntilIdle()

            assertThat(vm.uiState.value.errorMessage).contains("No connection")
            assertThat(ai.suggestCalls).isEmpty()
        }

    @Test
    fun `suggest is ignored while a call is already in flight`() = runTest(dispatcher) {
        ai.suggestResult = AiResult.Success(draft())
        val vm = viewModel()
        advanceUntilIdle()

        vm.onInstructionChange("An intro to widgets")
        vm.suggest()
        vm.suggest()
        advanceUntilIdle()

        assertThat(ai.suggestCalls).hasSize(1)
    }

    @Test
    fun `an empty suggest input is never sent`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.selectMode(PoweredDocumentMode.ARTICLE)
        vm.suggest()
        advanceUntilIdle()

        assertThat(ai.suggestCalls).isEmpty()
        assertThat(vm.uiState.value.validationMessage).isNotNull()
    }

    @Test
    fun `suggest input honours the endpoint's shape`() = runTest(dispatcher) {
        ai.suggestResult = AiResult.Success(draft())
        val vm = viewModel()
        advanceUntilIdle()

        vm.onInstructionChange("  An intro to widgets  ")
        vm.suggest()
        advanceUntilIdle()

        val sent: AiSuggestInput = ai.suggestCalls.single().second
        assertThat(sent.input).isEqualTo("An intro to widgets")
        assertThat(sent.context).isNotNull()
    }
}
