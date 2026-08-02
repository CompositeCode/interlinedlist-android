package com.interlinedlist.android.feature.messages.ui.feed

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.feature.messages.domain.ReportReason
import com.interlinedlist.android.feature.messages.ui.FakeMessagesRepository
import com.interlinedlist.android.feature.messages.ui.sampleMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

// PendingAttachment and MessagesFeedUiState live in this package.

@OptIn(ExperimentalCoroutinesApi::class)
class MessagesFeedViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `feed emits cached messages from the repository`() = runTest(dispatcher) {
        val repo = FakeMessagesRepository()
        repo.emitFeed(listOf(sampleMessage(id = "1"), sampleMessage(id = "2")))
        val vm = MessagesFeedViewModel(repo)

        vm.uiState.test {
            advanceUntilIdle()
            val state = expectMostRecentItem()
            assertThat(state.messages.map { it.id }).containsExactly("1", "2").inOrder()
            assertThat(state.isRefreshing).isFalse()
        }
    }

    @Test
    fun `refresh runs on init and toggles the refreshing flag`() = runTest(dispatcher) {
        val repo = FakeMessagesRepository().apply { refreshResult = ApiResult.Success(true) }
        val vm = MessagesFeedViewModel(repo)

        vm.uiState.test {
            advanceUntilIdle()
            val state = expectMostRecentItem()
            assertThat(repo.refreshCount).isEqualTo(1)
            assertThat(state.isRefreshing).isFalse()
            assertThat(state.canLoadMore).isTrue()
        }
    }

    @Test
    fun `refresh failure surfaces a mapped error`() = runTest(dispatcher) {
        val repo = FakeMessagesRepository().apply {
            refreshResult = ApiResult.Failure(AppError.Network("offline"))
        }
        val vm = MessagesFeedViewModel(repo)

        vm.uiState.test {
            advanceUntilIdle()
            val state = expectMostRecentItem()
            assertThat(state.errorMessage).isEqualTo("No connection. Check your network and try again.")
            assertThat(state.subscriptionRequired).isFalse()
        }
    }

    @Test
    fun `subscription-gated refresh sets the locked flag`() = runTest(dispatcher) {
        val repo = FakeMessagesRepository().apply {
            refreshResult = ApiResult.Failure(AppError.SubscriptionRequired("Subscribers only"))
        }
        val vm = MessagesFeedViewModel(repo)

        vm.uiState.test {
            advanceUntilIdle()
            val state = expectMostRecentItem()
            assertThat(state.subscriptionRequired).isTrue()
            assertThat(state.errorMessage).isEqualTo("Subscribers only")
        }
    }

    @Test
    fun `loadMore is a no-op when there are no more pages`() = runTest(dispatcher) {
        val repo = FakeMessagesRepository().apply { refreshResult = ApiResult.Success(false) }
        val vm = MessagesFeedViewModel(repo)
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.loadMore()
        advanceUntilIdle()

        assertThat(repo.loadMoreCount).isEqualTo(0)
    }

    @Test
    fun `loadMore fetches the next page when more are available`() = runTest(dispatcher) {
        val repo = FakeMessagesRepository().apply {
            refreshResult = ApiResult.Success(true)
            loadMoreResult = ApiResult.Success(false)
        }
        val vm = MessagesFeedViewModel(repo)
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.loadMore()
        advanceUntilIdle()

        assertThat(repo.loadMoreCount).isEqualTo(1)
        assertThat(vm.uiState.value.canLoadMore).isFalse()
    }

    @Test
    fun `post creates a message and closes the compose sheet`() = runTest(dispatcher) {
        val repo = FakeMessagesRepository().apply {
            createResult = ApiResult.Success(sampleMessage(id = "new"))
        }
        val vm = MessagesFeedViewModel(repo)
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.openCompose()
        vm.onComposeTextChange("hello world")
        vm.post()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertThat(state.isComposeOpen).isFalse()
        assertThat(state.composeText).isEmpty()
        assertThat(state.isPosting).isFalse()
    }

    @Test
    fun `post failure keeps the sheet open and shows an error`() = runTest(dispatcher) {
        val repo = FakeMessagesRepository().apply {
            createResult = ApiResult.Failure(AppError.Server("nope"))
        }
        val vm = MessagesFeedViewModel(repo)
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.openCompose()
        vm.onComposeTextChange("hello")
        vm.post()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertThat(state.isPosting).isFalse()
        assertThat(state.errorMessage).isNotEmpty()
    }

    @Test
    fun `dig delegates to the repository with the toggled value`() = runTest(dispatcher) {
        val repo = FakeMessagesRepository()
        val vm = MessagesFeedViewModel(repo)
        advanceUntilIdle()

        vm.onDig(sampleMessage(id = "42", dugByMe = false))
        advanceUntilIdle()

        assertThat(repo.lastSetDug).isEqualTo("42" to true)
    }

    @Test
    fun `delete delegates to the repository`() = runTest(dispatcher) {
        val repo = FakeMessagesRepository()
        val vm = MessagesFeedViewModel(repo)
        advanceUntilIdle()

        vm.onDelete(sampleMessage(id = "9", mine = true))
        advanceUntilIdle()

        assertThat(repo.deletedIds).containsExactly("9")
    }

    @Test
    fun `attaching media uploads and records the hosted url`() = runTest(dispatcher) {
        val repo = FakeMessagesRepository().apply {
            uploadImageResult = ApiResult.Success("https://cdn/a.png")
        }
        val vm = MessagesFeedViewModel(repo)
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.openCompose()
        vm.onAttachMedia("bytes".toByteArray(), "a.png", "image/png", isVideo = false)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertThat(repo.uploadedImages).isEqualTo(1)
        assertThat(state.attachments).hasSize(1)
        assertThat(state.attachments.first().hostedUrl).isEqualTo("https://cdn/a.png")
        assertThat(state.attachments.first().isUploading).isFalse()
    }

    @Test
    fun `a failed upload is dropped and surfaces an error`() = runTest(dispatcher) {
        val repo = FakeMessagesRepository().apply {
            uploadImageResult = ApiResult.Failure(AppError.Server("boom"))
        }
        val vm = MessagesFeedViewModel(repo)
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.openCompose()
        vm.onAttachMedia("bytes".toByteArray(), "a.png", "image/png", isVideo = false)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertThat(state.attachments).isEmpty()
        assertThat(state.errorMessage).isNotEmpty()
    }

    @Test
    fun `post forwards attached media and schedule to the repository`() = runTest(dispatcher) {
        val repo = FakeMessagesRepository().apply {
            uploadImageResult = ApiResult.Success("https://cdn/a.png")
            createResult = ApiResult.Success(sampleMessage(id = "new"))
        }
        val vm = MessagesFeedViewModel(repo)
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.openCompose()
        vm.onComposeTextChange("with media")
        vm.onAttachMedia("bytes".toByteArray(), "a.png", "image/png", isVideo = false)
        vm.onScheduleChange("2026-07-19T09:00:00Z")
        advanceUntilIdle()
        vm.post()
        advanceUntilIdle()

        val create = repo.lastCreate!!
        assertThat(create.content).isEqualTo("with media")
        assertThat(create.imageUrls).containsExactly("https://cdn/a.png")
        assertThat(create.scheduledAt).isEqualTo("2026-07-19T09:00:00Z")
        // Compose is reset after a successful post.
        assertThat(vm.uiState.value.attachments).isEmpty()
        assertThat(vm.uiState.value.scheduledAt).isNull()
    }

    @Test
    fun `canPost is false while an attachment is still uploading`() {
        // Pure state logic: an in-flight upload blocks posting even with text.
        val uploading = MessagesFeedUiState(
            composeText = "text",
            attachments = listOf(PendingAttachment(fileName = "a.png", isVideo = false, isUploading = true)),
        )
        assertThat(uploading.isUploading).isTrue()
        assertThat(uploading.canPost).isFalse()

        // Once the upload completes, posting is allowed.
        val ready = uploading.copy(
            attachments = listOf(
                PendingAttachment(fileName = "a.png", isVideo = false, hostedUrl = "u", isUploading = false),
            ),
        )
        assertThat(ready.canPost).isTrue()
    }

    @Test
    fun `report opens the dialog and submits the chosen reason`() = runTest(dispatcher) {
        val repo = FakeMessagesRepository()
        val vm = MessagesFeedViewModel(repo)
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        val target = sampleMessage(id = "abusive")
        vm.openReport(target)
        advanceUntilIdle()
        assertThat(vm.uiState.value.reportTarget?.id).isEqualTo("abusive")

        vm.submitReport(ReportReason.HARASSMENT, "please review")
        advanceUntilIdle()

        val report = repo.lastReport!!
        assertThat(report.messageId).isEqualTo("abusive")
        assertThat(report.reason).isEqualTo(ReportReason.HARASSMENT)
        assertThat(report.detail).isEqualTo("please review")
        assertThat(vm.uiState.value.reportTarget).isNull()
    }

    @Test
    fun `edit seeds the sheet and saves the new content marking it edited`() = runTest(dispatcher) {
        val repo = FakeMessagesRepository().apply {
            editResult = ApiResult.Success(
                sampleMessage(id = "own", content = "updated body", mine = true, editedAt = "2026-07-31T12:00:00Z"),
            )
        }
        repo.emitFeed(listOf(sampleMessage(id = "own", content = "original body", mine = true)))
        val vm = MessagesFeedViewModel(repo)
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.openEdit(sampleMessage(id = "own", content = "original body", mine = true))
        advanceUntilIdle()
        // Editor is seeded with the current content.
        assertThat(vm.uiState.value.editTarget?.id).isEqualTo("own")
        assertThat(vm.uiState.value.editText).isEqualTo("original body")

        vm.onEditTextChange("updated body")
        vm.saveEdit()
        advanceUntilIdle()

        assertThat(repo.lastEdit).isEqualTo("own" to "updated body")
        // Sheet closed and the feed reflects the edited, marked message.
        assertThat(vm.uiState.value.editTarget).isNull()
        val edited = vm.uiState.value.messages.first { it.id == "own" }
        assertThat(edited.content).isEqualTo("updated body")
        assertThat(edited.isEdited).isTrue()
    }

    @Test
    fun `edit failure keeps the sheet open and surfaces an error`() = runTest(dispatcher) {
        val repo = FakeMessagesRepository().apply {
            editResult = ApiResult.Failure(AppError.Server("nope"))
        }
        val vm = MessagesFeedViewModel(repo)
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.openEdit(sampleMessage(id = "own", content = "original", mine = true))
        vm.onEditTextChange("changed")
        vm.saveEdit()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertThat(state.editTarget?.id).isEqualTo("own")
        assertThat(state.isSavingEdit).isFalse()
        assertThat(state.errorMessage).isNotEmpty()
    }

    @Test
    fun `block hides the author's messages from the feed`() = runTest(dispatcher) {
        val repo = FakeMessagesRepository()
        repo.emitFeed(
            listOf(
                sampleMessage(id = "1", authorUsername = "amy"),
                sampleMessage(id = "2", authorUsername = "bob"),
            ),
        )
        val vm = MessagesFeedViewModel(repo)
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.openModeration(sampleMessage(id = "1", authorUsername = "amy"), ModerationAction.BLOCK)
        advanceUntilIdle()
        assertThat(vm.uiState.value.moderationTarget?.username).isEqualTo("amy")

        vm.confirmModeration()
        advanceUntilIdle()

        assertThat(repo.blockedUsernames).containsExactly("amy")
        assertThat(vm.uiState.value.moderationTarget).isNull()
        assertThat(vm.uiState.value.messages.map { it.id }).containsExactly("2")
    }

    @Test
    fun `block failure surfaces an error and keeps the feed`() = runTest(dispatcher) {
        val repo = FakeMessagesRepository().apply {
            blockResult = ApiResult.Failure(AppError.Server("boom"))
        }
        repo.emitFeed(listOf(sampleMessage(id = "1", authorUsername = "amy")))
        val vm = MessagesFeedViewModel(repo)
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.openModeration(sampleMessage(id = "1", authorUsername = "amy"), ModerationAction.BLOCK)
        vm.confirmModeration()
        advanceUntilIdle()

        assertThat(vm.uiState.value.errorMessage).isNotEmpty()
        assertThat(vm.uiState.value.messages.map { it.id }).containsExactly("1")
    }

    @Test
    fun `mute delegates to the repository`() = runTest(dispatcher) {
        val repo = FakeMessagesRepository()
        repo.emitFeed(listOf(sampleMessage(id = "1", authorUsername = "amy")))
        val vm = MessagesFeedViewModel(repo)
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.openModeration(sampleMessage(id = "1", authorUsername = "amy"), ModerationAction.MUTE)
        vm.confirmModeration()
        advanceUntilIdle()

        assertThat(repo.mutedUsernames).containsExactly("amy")
        assertThat(vm.uiState.value.messages).isEmpty()
    }

    @Test
    fun `report user submits the chosen reason and detail`() = runTest(dispatcher) {
        val repo = FakeMessagesRepository()
        val vm = MessagesFeedViewModel(repo)
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.openModeration(sampleMessage(id = "1", authorUsername = "amy"), ModerationAction.REPORT)
        vm.confirmModeration(ReportReason.HARASSMENT, "abusive")
        advanceUntilIdle()

        val report = repo.lastReportUser!!
        assertThat(report.username).isEqualTo("amy")
        assertThat(report.reason).isEqualTo(ReportReason.HARASSMENT)
        assertThat(report.detail).isEqualTo("abusive")
        assertThat(vm.uiState.value.moderationTarget).isNull()
    }

    @Test
    fun `fetchMetadata delegates to the repository`() = runTest(dispatcher) {
        val repo = FakeMessagesRepository().apply {
            metadataResult = ApiResult.Success(sampleMessage(id = "m1"))
        }
        val vm = MessagesFeedViewModel(repo)
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()

        vm.onFetchMetadata(sampleMessage(id = "m1"))
        advanceUntilIdle()

        assertThat(repo.metadataFetchedIds).containsExactly("m1")
    }
}
