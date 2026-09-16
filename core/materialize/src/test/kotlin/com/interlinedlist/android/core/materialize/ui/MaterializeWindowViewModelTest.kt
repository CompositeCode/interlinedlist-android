package com.interlinedlist.android.core.materialize.ui

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.core.materialize.data.gateFor
import com.interlinedlist.android.core.materialize.data.repositoryFor
import com.interlinedlist.android.core.materialize.domain.DocumentListStyle
import com.interlinedlist.android.core.materialize.domain.ListColumnType
import com.interlinedlist.android.core.materialize.domain.MaterializeColumn
import com.interlinedlist.android.core.materialize.domain.MaterializeOutcome
import com.interlinedlist.android.core.materialize.domain.MaterializeRequest
import com.interlinedlist.android.core.materialize.domain.MaterializeSource
import com.interlinedlist.android.core.materialize.domain.MaterializeTarget
import com.interlinedlist.android.core.materialize.domain.MaterializedDocument
import com.interlinedlist.android.core.materialize.domain.MaterializedList
import com.interlinedlist.android.core.materialize.domain.MessageDraft
import com.interlinedlist.android.core.materialize.domain.RowDataStyle
import com.interlinedlist.android.core.model.CustomerStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * The preview / edit / confirm window.
 *
 * The window's contract is the request it hands the repository, so these tests
 * assert on that request; how it serialises is covered end-to-end against
 * MockWebServer by `MaterializeRequestBodyTest`. The one test that must prove
 * *nothing at all* went out uses the real repository over a real socket.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MaterializeWindowViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeMaterializeRepository()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val source = MaterializeSource.Lists(listOf("lst_source"))

    private fun preview() = MaterializePreview(
        suggestedTitle = "Books to Read",
        suggestedDescription = "My reading backlog.",
        suggestedFileName = "books-to-read.md",
        columns = listOf(
            MaterializeColumn("title", "Title", ListColumnType.TEXT, sourceKey = "title"),
            MaterializeColumn("author", "Author", ListColumnType.TEXT, sourceKey = "author"),
            MaterializeColumn("year", "Year", ListColumnType.NUMBER, sourceKey = "year"),
        ),
        rows = listOf(
            MaterializePreviewRow(
                mapOf("title" to "The Dream Machine", "author" to "Waldrop", "year" to "2001"),
            ),
        ),
        totalRowCount = 340,
    )

    private fun viewModel(
        target: MaterializeTarget = MaterializeTarget.LIST,
        source: MaterializeSource = this.source,
    ): MaterializeWindowViewModel = MaterializeWindowViewModel(repository).also {
        it.start(MaterializeLaunch(source, target, preview()))
    }

    private val MaterializeWindowViewModel.state: MaterializeWindowUiState
        get() = requireNotNull(uiState.value) { "the window was never started" }

    private fun sentRequest(): MaterializeRequest {
        assertThat(repository.requests).hasSize(1)
        return repository.requests.single()
    }

    // ------------------------------------------------------------- the editor

    @Test
    fun `the window opens on the seeded defaults without creating anything`() = runTest(dispatcher) {
        val vm = viewModel()

        assertThat(vm.state.title).isEqualTo("Books to Read")
        assertThat(vm.state.description).isEqualTo("My reading backlog.")
        assertThat(vm.state.fileName).isEqualTo("books-to-read.md")
        assertThat(vm.state.columns.map { it.name })
            .containsExactly("Title", "Author", "Year").inOrder()
        // Preview first: nothing is written until the user confirms.
        assertThat(repository.requests).isEmpty()
    }

    @Test
    fun `column edits survive into the create request`() = runTest(dispatcher) {
        val vm = viewModel()
        val seeded = vm.state.columns

        vm.renameColumn(seeded[0].uiId, "Book")
        vm.changeColumnType(seeded[2].uiId, ListColumnType.DATE)
        vm.removeColumn(seeded[1].uiId)
        vm.addColumn()
        val added = vm.state.columns.last()
        vm.renameColumn(added.uiId, "Notes")
        vm.changeColumnType(added.uiId, ListColumnType.TEXTAREA)

        vm.confirm()
        advanceUntilIdle()

        val fields = (sentRequest() as MaterializeRequest.ToList).listConfig.fields.orEmpty()
        assertThat(fields.map { it.propertyName }).containsExactly("Book", "Year", "Notes").inOrder()
        assertThat(fields.map { it.propertyType }).containsExactly(
            ListColumnType.TEXT,
            ListColumnType.DATE,
            ListColumnType.TEXTAREA,
        ).inOrder()
        // A rename keeps the source mapping; the removed column is gone.
        assertThat(fields[0].sourceKey).isEqualTo("title")
        assertThat(fields[1].sourceKey).isEqualTo("year")
        // A user-added column has no source attribute: it is created empty.
        assertThat(fields[2].sourceKey).isNull()
        assertThat(fields[2].propertyKey).isEqualTo("notes")
    }

    @Test
    fun `title description and visibility edits reach the list config`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.updateTitle("Reading backlog")
        vm.updateDescription("Everything still queued up.")
        vm.setPublic(true)
        vm.confirm()
        advanceUntilIdle()

        val config = (sentRequest() as MaterializeRequest.ToList).listConfig
        assertThat(config.title).isEqualTo("Reading backlog")
        assertThat(config.description).isEqualTo("Everything still queued up.")
        assertThat(config.isPublic).isTrue()
    }

    @Test
    fun `an unnamed column is dropped rather than created blank`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.addColumn() // added and never named
        vm.confirm()
        advanceUntilIdle()

        val fields = (sentRequest() as MaterializeRequest.ToList).listConfig.fields.orEmpty()
        assertThat(fields.map { it.propertyName })
            .containsExactly("Title", "Author", "Year").inOrder()
    }

    // -------------------------------------------------- switching destinations

    @Test
    fun `switching to both keeps every list edit and adds the document half`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.updateTitle("Reading backlog")
            vm.renameColumn(vm.state.columns[0].uiId, "Book")

            vm.selectTarget(MaterializeTarget.BOTH)

            // The edits are still on screen after the switch.
            assertThat(vm.state.title).isEqualTo("Reading backlog")
            assertThat(vm.state.columns.map { it.name }).contains("Book")

            vm.confirm()
            advanceUntilIdle()

            val request = sentRequest() as MaterializeRequest.ToListAndDocument
            assertThat(request.listConfig.fields!!.first().propertyName).isEqualTo("Book")
            // The document half is configured too, and shares the title.
            assertThat(request.docConfig?.title).isEqualTo("Reading backlog")
        }

    @Test
    fun `switching to a document keeps the title but discards what a document cannot use`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.updateTitle("Reading backlog")
            vm.updateDescription("Everything still queued up.")
            vm.renameColumn(vm.state.columns[0].uiId, "Book")

            vm.selectTarget(MaterializeTarget.DOC)
            vm.updateFileName("reading-backlog.md")
            vm.selectListStyle(DocumentListStyle.NUMBERED)
            vm.selectRowDataStyle(RowDataStyle.SUB_ITEMS)
            vm.confirm()
            advanceUntilIdle()

            // A document has no columns and no list description: neither is sent.
            val request = sentRequest()
            assertThat(request).isInstanceOf(MaterializeRequest.ToDocument::class.java)
            val config = (request as MaterializeRequest.ToDocument).docConfig
            // The title still applies, so it carried over unchanged.
            assertThat(config?.title).isEqualTo("Reading backlog")
            assertThat(config?.relativePath).isEqualTo("reading-backlog.md")
            assertThat(config?.listStyle).isEqualTo(DocumentListStyle.NUMBERED)
            assertThat(config?.rowDataStyle).isEqualTo(RowDataStyle.SUB_ITEMS)
        }

    @Test
    fun `switching back to a list restores the column edits the document could not use`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.renameColumn(vm.state.columns[0].uiId, "Book")
            vm.removeColumn(vm.state.columns[1].uiId)

            vm.selectTarget(MaterializeTarget.DOC)
            vm.selectTarget(MaterializeTarget.LIST)

            assertThat(vm.state.columns.map { it.name }).containsExactly("Book", "Year").inOrder()

            vm.confirm()
            advanceUntilIdle()

            val fields = (sentRequest() as MaterializeRequest.ToList).listConfig.fields.orEmpty()
            assertThat(fields.map { it.propertyName }).containsExactly("Book", "Year").inOrder()
        }

    @Test
    fun `switching to a message sends neither config because a draft uses neither`() =
        runTest(dispatcher) {
            repository.result = ApiResult.Success(
                MaterializeOutcome.DraftReady(
                    MessageDraft("Books to Read", listOf("Books to Read"), isThread = false, charLimit = 300),
                ),
            )
            val vm = viewModel()
            vm.renameColumn(vm.state.columns[0].uiId, "Book")

            vm.selectTarget(MaterializeTarget.MESSAGE)
            vm.confirm()
            advanceUntilIdle()

            val request = sentRequest()
            assertThat(request).isInstanceOf(MaterializeRequest.ToMessageDraft::class.java)
            assertThat(request.target).isEqualTo(MaterializeTarget.MESSAGE)
            // The draft is handed back for the composer; nothing was created.
            val success = requireNotNull(vm.state.success)
            assertThat(success.draft?.content).isEqualTo("Books to Read")
            assertThat(success.canOpenList).isFalse()
            assertThat(success.canOpenDocument).isFalse()
        }

    @Test
    fun `a messages source does not offer the message destination`() = runTest(dispatcher) {
        val vm = viewModel(source = MaterializeSource.Messages(listOf("msg_1")))

        // "A message can't be converted to a message; use Quote or Push."
        assertThat(vm.state.availableTargets).containsExactly(
            MaterializeTarget.LIST,
            MaterializeTarget.DOC,
            MaterializeTarget.BOTH,
        ).inOrder()
    }

    // ---------------------------------------------------------------- success

    @Test
    fun `both exposes the list and the document navigation targets on success`() =
        runTest(dispatcher) {
            repository.result = ApiResult.Success(
                MaterializeOutcome.ListAndDocumentCreated(
                    list = MaterializedList("lst_new", "Books to Read"),
                    document = MaterializedDocument("doc_new", "Books to Read"),
                ),
            )
            val vm = viewModel(target = MaterializeTarget.BOTH)

            vm.confirm()
            advanceUntilIdle()

            val success = requireNotNull(vm.state.success)
            assertThat(success.list?.id).isEqualTo("lst_new")
            assertThat(success.document?.id).isEqualTo("doc_new")
            assertThat(success.canOpenList).isTrue()
            assertThat(success.canOpenDocument).isTrue()
            assertThat(vm.state.isSubmitting).isFalse()
        }

    @Test
    fun `a list only conversion exposes only the list target`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.confirm()
        advanceUntilIdle()

        val success = requireNotNull(vm.state.success)
        assertThat(success.list?.id).isEqualTo("lst_new")
        assertThat(success.canOpenDocument).isFalse()
    }

    @Test
    fun `a confirmed window does not create a second copy`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.confirm()
        advanceUntilIdle()
        vm.confirm()
        advanceUntilIdle()

        assertThat(repository.requests).hasSize(1)
    }

    // ------------------------------------------------------------- refusals

    @Test
    fun `a blank list title is refused before any request is made`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.updateTitle("   ")

        assertThat(vm.state.canConfirm).isFalse()
        assertThat(vm.state.titleError).isEqualTo("A list title is required")

        vm.confirm()
        advanceUntilIdle()

        assertThat(repository.requests).isEmpty()
        assertThat(vm.state.success).isNull()
        assertThat(vm.state.errorMessage).isEqualTo("A list title is required")
    }

    @Test
    fun `a blank title is fine for a document because the server derives one`() =
        runTest(dispatcher) {
            repository.result = ApiResult.Success(
                MaterializeOutcome.DocumentCreated(MaterializedDocument("doc_new", "Books to Read")),
            )
            val vm = viewModel(target = MaterializeTarget.DOC)

            vm.updateTitle("")

            assertThat(vm.state.titleError).isNull()
            assertThat(vm.state.canConfirm).isTrue()

            vm.confirm()
            advanceUntilIdle()

            assertThat((sentRequest() as MaterializeRequest.ToDocument).docConfig?.title).isNull()
        }

    @Test
    fun `a subscription refusal routes to the subscription handling`() = runTest(dispatcher) {
        repository.result = ApiResult.Failure(AppError.SubscriptionRequired(null))
        val vm = viewModel()

        vm.confirm()
        advanceUntilIdle()

        assertThat(vm.state.subscriptionRequired).isTrue()
        assertThat(vm.state.success).isNull()
        assertThat(vm.state.isSubmitting).isFalse()
        assertThat(vm.state.errorMessage)
            .isEqualTo("Creating lists and documents requires an active subscription.")
    }

    @Test
    fun `a server failure surfaces its own words and leaves the edits intact`() =
        runTest(dispatcher) {
            repository.result = ApiResult.Failure(AppError.Unknown("Field 'year' has invalid type"))
            val vm = viewModel()
            vm.renameColumn(vm.state.columns[0].uiId, "Book")

            vm.confirm()
            advanceUntilIdle()

            assertThat(vm.state.errorMessage).isEqualTo("Field 'year' has invalid type")
            assertThat(vm.state.success).isNull()
            assertThat(vm.state.columns.map { it.name }).contains("Book")
        }

    /**
     * The one claim a fake cannot make: a free account's confirm must not put a
     * single byte on the wire. This runs the real repository and the real gate
     * over a real socket and asserts the socket never saw anything.
     */
    @Test
    fun `a free account confirming issues no write at all`() = runTest(dispatcher) {
        val server = MockWebServer().also { it.start() }
        try {
            val gate = gateFor(server, dispatcher).also { it.record(CustomerStatus.FREE) }
            val vm = MaterializeWindowViewModel(repositoryFor(server, dispatcher, gate)).also {
                it.start(MaterializeLaunch(source, MaterializeTarget.LIST, preview()))
            }

            vm.confirm()
            advanceUntilIdle()

            assertThat(server.requestCount).isEqualTo(0)
            assertThat(vm.state.subscriptionRequired).isTrue()
            assertThat(vm.state.success).isNull()
        } finally {
            server.shutdown()
        }
    }
}
