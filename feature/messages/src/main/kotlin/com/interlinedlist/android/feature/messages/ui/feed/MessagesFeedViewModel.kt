package com.interlinedlist.android.feature.messages.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.interlinedlist.android.core.common.result.ApiResult
import com.interlinedlist.android.core.common.result.AppError
import com.interlinedlist.android.core.model.ViewingPreference
import com.interlinedlist.android.feature.messages.data.MessagesRepository
import com.interlinedlist.android.feature.messages.domain.CrossPostSelection
import com.interlinedlist.android.feature.messages.domain.CrossPostStatus
import com.interlinedlist.android.feature.messages.domain.LinkedNetwork
import com.interlinedlist.android.feature.messages.domain.Message
import com.interlinedlist.android.feature.messages.domain.MessageVisibility
import com.interlinedlist.android.feature.messages.domain.ReportReason
import com.interlinedlist.android.feature.messages.domain.TagSuggestion
import com.interlinedlist.android.feature.messages.ui.isSubscriptionGate
import com.interlinedlist.android.feature.messages.ui.toUserMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A pending media attachment being uploaded, or already uploaded, for a compose. */
data class PendingAttachment(
    val fileName: String,
    val isVideo: Boolean,
    /** Set once the upload finishes; null while [isUploading]. */
    val hostedUrl: String? = null,
    val isUploading: Boolean = true,
)

/** Feed screen state: the cached messages plus transient network/compose flags. */
data class MessagesFeedUiState(
    val messages: List<Message> = emptyList(),
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val canLoadMore: Boolean = false,
    val errorMessage: String? = null,
    /** True when the failure is a subscription gate — render an upsell instead. */
    val subscriptionRequired: Boolean = false,
    /** Which messages the feed is showing; drives the in-feed switcher. */
    val viewingPreference: ViewingPreference = ViewingPreference.DEFAULT,
    /** True while a switcher choice is being saved to the account. */
    val isChangingViewingPreference: Boolean = false,
    val isComposeOpen: Boolean = false,
    val composeText: String = "",
    val isPosting: Boolean = false,
    /** Media attached to the in-progress compose. */
    val attachments: List<PendingAttachment> = emptyList(),
    /** Optional future send time (ISO-8601) for the in-progress compose. */
    val scheduledAt: String? = null,
    /** Tags already committed to the in-progress compose, in the order added. */
    val composeTags: List<String> = emptyList(),
    /** What the user has typed into the tag field but not yet committed. */
    val tagQuery: String = "",
    /** Server-supplied prefix suggestions for [tagQuery], in the server's order. */
    val tagSuggestions: List<TagSuggestion> = emptyList(),
    /** True while a suggestion lookup is pending or in flight. */
    val isLoadingTagSuggestions: Boolean = false,
    /**
     * Visibility the in-progress compose will post with: the account's
     * `defaultPubliclyVisible` preference unless the user overrode it here — or
     * [MessageVisibility.PUSH_OR_QUOTE] when a quote is attached.
     */
    val composeVisibility: MessageVisibility = MessageVisibility.PUBLIC,
    /**
     * The message the open composer will quote, if any. Its presence turns the
     * compose into a quote post: it sends `pushedMessageId` alongside the note.
     */
    val quoteTarget: Message? = null,
    /** The caller's already-linked networks, offered as cross-post destinations. */
    val linkedNetworks: List<LinkedNetwork> = emptyList(),
    /** Ids of the linked networks currently selected as cross-post targets. */
    val selectedNetworkIds: Set<String> = emptySet(),
    /** Per-network delivery statuses from the last successful post, if any. */
    val crossPostStatuses: List<CrossPostStatus> = emptyList(),
    /** The message currently being reported (drives the report dialog), if any. */
    val reportTarget: Message? = null,
    val isReporting: Boolean = false,
    /** The message currently being edited in-place (drives the edit sheet), if any. */
    val editTarget: Message? = null,
    /** The working edit text seeded from [editTarget]'s content. */
    val editText: String = "",
    val isSavingEdit: Boolean = false,
    /** A pending author-moderation action awaiting confirmation, if any. */
    val moderationTarget: ModerationTarget? = null,
    val isModerating: Boolean = false,
) {
    val isEmpty: Boolean get() = messages.isEmpty()

    /** True while the composer is writing a quote of [quoteTarget]. */
    val isQuoting: Boolean get() = quoteTarget != null

    /**
     * False while quoting: a push/quote is always public, so the composer locks
     * the visibility control instead of offering Private.
     */
    val canChangeVisibility: Boolean get() = !isQuoting

    val hasAttachments: Boolean get() = attachments.isNotEmpty()
    val isUploading: Boolean get() = attachments.any { it.isUploading }
    val isScheduled: Boolean get() = scheduledAt != null
    val canPost: Boolean
        get() = (composeText.isNotBlank() || attachments.any { it.hostedUrl != null }) &&
            !isPosting && !isUploading

    /** A tag can be committed when the field holds something new and non-blank. */
    val canCommitTag: Boolean
        get() = tagQuery.trim().let { it.isNotEmpty() && it !in composeTags }

    /** True when the account has no linked networks to cross-post to. */
    val hasNoLinkedNetworks: Boolean get() = linkedNetworks.isEmpty()

    /** Edit can be saved when the text is non-blank and not currently saving. */
    val canSaveEdit: Boolean get() = editText.isNotBlank() && !isSavingEdit
}

/**
 * A pending author-moderation action, captured when the user taps Block / Mute /
 * Report on someone else's message. Drives a confirm dialog before the call runs.
 */
data class ModerationTarget(
    val message: Message,
    val action: ModerationAction,
) {
    val username: String get() = message.authorUsername
    val authorLabel: String get() = message.authorLabel
}

/** The three author-level moderation actions (distinct from reporting a message). */
enum class ModerationAction { BLOCK, MUTE, REPORT }

/** Transient (non-cached) UI flags kept separate from the Room-backed message list. */
private data class FeedTransientState(
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    /**
     * Opaque keyset cursor for the next feed page, or null at the end of the feed
     * (and until the first refresh returns). Held verbatim: never parsed or built.
     */
    val nextCursor: String? = null,
    val errorMessage: String? = null,
    val subscriptionRequired: Boolean = false,
    /**
     * The account's feed view preference. Seeded from `GET /api/user` and changed
     * by the switcher; the server scopes the feed by it, so it is also the value
     * every feed request runs under.
     */
    val viewingPreference: ViewingPreference = ViewingPreference.DEFAULT,
    val isChangingViewingPreference: Boolean = false,
    val isComposeOpen: Boolean = false,
    val composeText: String = "",
    val isPosting: Boolean = false,
    val attachments: List<PendingAttachment> = emptyList(),
    val scheduledAt: String? = null,
    val composeTags: List<String> = emptyList(),
    val tagQuery: String = "",
    val tagSuggestions: List<TagSuggestion> = emptyList(),
    val isLoadingTagSuggestions: Boolean = false,
    /** The account preference; the fallback until/unless the user overrides it. */
    val defaultVisibility: MessageVisibility = MessageVisibility.PUBLIC,
    /** The user's per-message choice for the open composer; null = use the default. */
    val visibilityOverride: MessageVisibility? = null,
    /** The message the open composer is quoting, if any. */
    val quoteTarget: Message? = null,
    /**
     * Ids of pushes currently in flight, so a double tap cannot post the same
     * repost twice. Purely a guard: the count itself comes from the server.
     */
    val pushesInFlight: Set<String> = emptySet(),
    val linkedNetworks: List<LinkedNetwork> = emptyList(),
    val selectedNetworkIds: Set<String> = emptySet(),
    val crossPostStatuses: List<CrossPostStatus> = emptyList(),
    val reportTarget: Message? = null,
    val isReporting: Boolean = false,
    val editTarget: Message? = null,
    val editText: String = "",
    val isSavingEdit: Boolean = false,
    val moderationTarget: ModerationTarget? = null,
    val isModerating: Boolean = false,
) {
    /**
     * A quote is always public — [MessageVisibility.PUSH_OR_QUOTE] outranks both
     * the per-message override and the account default. Otherwise an override
     * wins over the default.
     */
    val composeVisibility: MessageVisibility
        get() = if (quoteTarget != null) {
            MessageVisibility.PUSH_OR_QUOTE
        } else {
            visibilityOverride ?: defaultVisibility
        }

    /** More pages remain exactly while the server handed back a cursor. */
    val canLoadMore: Boolean get() = nextCursor != null

    /**
     * The tags to send with the post: the committed ones, plus whatever is still
     * typed in the field. Requiring a separate commit tap before posting would
     * silently drop a tag the user clearly intended.
     */
    val tagsToPost: List<String>
        get() = tagQuery.trim().let { pending ->
            if (pending.isEmpty() || pending in composeTags) composeTags else composeTags + pending
        }
}

@HiltViewModel
class MessagesFeedViewModel @Inject constructor(
    private val repository: MessagesRepository,
) : ViewModel() {

    private val transient = MutableStateFlow(FeedTransientState())

    /**
     * The one in-flight tag-suggestion lookup, held so the next keystroke can
     * cancel it. Cancelling covers both halves of the work: the pending debounce
     * delay and, if it already started, the network request itself.
     */
    private var tagSuggestionJob: Job? = null

    /**
     * Room is the source of truth: the feed list comes from the cache Flow and is
     * combined with transient flags into a single [MessagesFeedUiState].
     */
    val uiState: StateFlow<MessagesFeedUiState> =
        combine(repository.observeFeed(), transient) { messages, t ->
            MessagesFeedUiState(
                messages = messages,
                isRefreshing = t.isRefreshing,
                isLoadingMore = t.isLoadingMore,
                canLoadMore = t.canLoadMore,
                errorMessage = t.errorMessage,
                subscriptionRequired = t.subscriptionRequired,
                viewingPreference = t.viewingPreference,
                isChangingViewingPreference = t.isChangingViewingPreference,
                isComposeOpen = t.isComposeOpen,
                composeText = t.composeText,
                isPosting = t.isPosting,
                attachments = t.attachments,
                scheduledAt = t.scheduledAt,
                composeTags = t.composeTags,
                tagQuery = t.tagQuery,
                tagSuggestions = t.tagSuggestions,
                isLoadingTagSuggestions = t.isLoadingTagSuggestions,
                composeVisibility = t.composeVisibility,
                quoteTarget = t.quoteTarget,
                linkedNetworks = t.linkedNetworks,
                selectedNetworkIds = t.selectedNetworkIds,
                crossPostStatuses = t.crossPostStatuses,
                reportTarget = t.reportTarget,
                isReporting = t.isReporting,
                editTarget = t.editTarget,
                editText = t.editText,
                isSavingEdit = t.isSavingEdit,
                moderationTarget = t.moderationTarget,
                isModerating = t.isModerating,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = MessagesFeedUiState(),
        )

    init {
        loadViewingPreferenceThenRefresh()
        loadLinkedNetworks()
        loadDefaultVisibility()
    }

    /**
     * Reads the account's saved view preference and only then loads the feed, so
     * the first request already runs under the right view instead of briefly
     * showing All Messages. A failed read leaves the default (All Messages) in
     * place and still loads the feed — an unreadable preference must not leave the
     * user staring at an empty screen.
     */
    private fun loadViewingPreferenceThenRefresh() {
        // Set up front so the feed shows its loading state, not its empty state,
        // while the preference is being read.
        transient.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            val result = repository.getViewingPreference()
            if (result is ApiResult.Success) {
                transient.update { it.copy(viewingPreference = result.data) }
            }
            refresh()
        }
    }

    /**
     * Switches the feed to [preference]: saves it to the account first (so the
     * choice persists and the web agrees), then reloads the feed from the top.
     *
     * The selection updates immediately for responsiveness but is **rolled back**
     * if the save fails — the switcher must never show a view the account never
     * stored. The feed is only reloaded once the save succeeded, because the
     * server scopes the feed from the saved preference.
     */
    fun onViewingPreferenceChange(preference: ViewingPreference) {
        val current = transient.value
        if (preference == current.viewingPreference || current.isChangingViewingPreference) return
        val previous = current.viewingPreference
        transient.update {
            it.copy(
                viewingPreference = preference,
                isChangingViewingPreference = true,
                errorMessage = null,
                subscriptionRequired = false,
            )
        }
        viewModelScope.launch {
            when (val result = repository.setViewingPreference(preference)) {
                is ApiResult.Success -> {
                    // Server truth wins over the tapped value.
                    transient.update {
                        it.copy(viewingPreference = result.data, isChangingViewingPreference = false)
                    }
                    refresh()
                }
                is ApiResult.Failure -> transient.update {
                    it.copy(viewingPreference = previous, isChangingViewingPreference = false)
                        .withError(result.error)
                }
            }
        }
    }

    /**
     * Loads the account's default post visibility so the composer opens on the
     * user's preference. Best-effort: a failure leaves the default at public and
     * does not surface a feed-level error. A per-message override is preserved.
     */
    private fun loadDefaultVisibility() {
        viewModelScope.launch {
            when (val result = repository.getDefaultVisibility()) {
                is ApiResult.Success -> transient.update { it.copy(defaultVisibility = result.data) }
                is ApiResult.Failure -> Unit
            }
        }
    }

    /**
     * Loads the caller's already-linked networks so the composer can offer them as
     * cross-post destinations. Best-effort: a failure just leaves the list empty
     * (the composer then shows the "link accounts on the web" hint) and does not
     * surface a feed-level error.
     */
    fun loadLinkedNetworks() {
        viewModelScope.launch {
            when (val result = repository.getLinkedNetworks()) {
                is ApiResult.Success -> transient.update { state ->
                    // Prune any selections whose network is no longer linked.
                    val liveIds = result.data.map { it.id }.toSet()
                    state.copy(
                        linkedNetworks = result.data,
                        selectedNetworkIds = state.selectedNetworkIds.intersect(liveIds),
                    )
                }
                is ApiResult.Failure -> Unit
            }
        }
    }

    /** Reloads the head of the feed, discarding the stored paging cursor. */
    fun refresh() {
        transient.update {
            it.copy(
                isRefreshing = true,
                // Paging restarts from the top; the old cursor no longer applies.
                nextCursor = null,
                errorMessage = null,
                subscriptionRequired = false,
            )
        }
        viewModelScope.launch {
            when (val result = repository.refreshFeed(transient.value.viewingPreference)) {
                is ApiResult.Success -> transient.update {
                    it.copy(isRefreshing = false, nextCursor = result.data)
                }
                is ApiResult.Failure -> transient.update {
                    it.copy(isRefreshing = false).withError(result.error)
                }
            }
        }
    }

    /** Appends the page after the stored cursor; a no-op at the end of the feed. */
    fun loadMore() {
        val current = transient.value
        val cursor = current.nextCursor ?: return
        if (current.isLoadingMore || current.isRefreshing) return
        transient.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            when (val result = repository.loadMoreFeed(cursor, current.viewingPreference)) {
                is ApiResult.Success -> transient.update {
                    it.copy(isLoadingMore = false, nextCursor = result.data)
                }
                is ApiResult.Failure -> transient.update {
                    it.copy(isLoadingMore = false).withError(result.error)
                }
            }
        }
    }

    fun onDig(message: Message) {
        viewModelScope.launch {
            // Repository updates the cache optimistically and rolls back on failure.
            val result = repository.setDug(message.id, dug = !message.dugByMe)
            if (result is ApiResult.Failure) {
                transient.update { it.withError(result.error) }
            }
        }
    }

    fun onDelete(message: Message) {
        viewModelScope.launch {
            val result = repository.deleteMessage(message.id)
            if (result is ApiResult.Failure) {
                transient.update { it.withError(result.error) }
            }
        }
    }

    // --- push / quote ------------------------------------------------------

    /**
     * Pushes (reposts) [message] straight away: no composer, no comment, always
     * public. Ignored for a message the rules say cannot be pushed (your own, a
     * private one, or a re-share) — the card does not offer the action there
     * either — and while an earlier push of the same message is still in flight.
     * A server rejection is surfaced verbatim.
     */
    fun onPush(message: Message) {
        if (!message.canBePushed) return
        if (message.id in transient.value.pushesInFlight) return
        transient.update {
            it.copy(pushesInFlight = it.pushesInFlight + message.id, errorMessage = null)
        }
        viewModelScope.launch {
            val result = repository.pushMessage(message.id)
            transient.update { state ->
                val cleared = state.copy(pushesInFlight = state.pushesInFlight - message.id)
                if (result is ApiResult.Failure) cleared.withError(result.error) else cleared
            }
        }
    }

    /**
     * Opens the normal composer with [message] attached as a quote. The post then
     * carries both the user's note and `pushedMessageId`, and is always public.
     */
    fun openQuote(message: Message) {
        if (!message.canBePushed) return
        transient.update {
            it.copy(
                isComposeOpen = true,
                quoteTarget = message,
                // A quote's visibility is fixed; any earlier override is moot.
                visibilityOverride = null,
                // The API rejects scheduledAt together with pushedMessageId, so a
                // quote is never scheduled — and the composer hides the chip.
                scheduledAt = null,
                errorMessage = null,
                crossPostStatuses = emptyList(),
            )
        }
    }

    // --- compose sheet -----------------------------------------------------

    fun openCompose() = transient.update {
        it.copy(isComposeOpen = true, errorMessage = null, crossPostStatuses = emptyList())
    }

    fun dismissCompose() {
        // Nothing left to suggest for: drop the pending/in-flight lookup.
        tagSuggestionJob?.cancel()
        transient.update {
            it.copy(
                isComposeOpen = false,
                composeText = "",
                attachments = emptyList(),
                scheduledAt = null,
                composeTags = emptyList(),
                tagQuery = "",
                tagSuggestions = emptyList(),
                isLoadingTagSuggestions = false,
                // Drop the per-message override; the next compose starts from the default.
                visibilityOverride = null,
                quoteTarget = null,
                selectedNetworkIds = emptySet(),
            )
        }
    }

    /**
     * Overrides the account default for this message only. Ignored while quoting:
     * a push/quote is always public, and the composer offers no other choice.
     */
    fun onVisibilityChange(visibility: MessageVisibility) = transient.update {
        if (it.quoteTarget != null) it else it.copy(visibilityOverride = visibility)
    }

    fun onComposeTextChange(value: String) = transient.update { it.copy(composeText = value) }

    /**
     * Toggles a linked network as a cross-post target for the in-progress compose.
     * No-ops for an id that isn't currently linked.
     */
    fun onToggleNetwork(networkId: String) = transient.update { state ->
        if (state.linkedNetworks.none { it.id == networkId }) return@update state
        val selected = if (networkId in state.selectedNetworkIds) {
            state.selectedNetworkIds - networkId
        } else {
            state.selectedNetworkIds + networkId
        }
        state.copy(selectedNetworkIds = selected)
    }

    /** Sets (or clears with null) the future send time for the in-progress compose. */
    fun onScheduleChange(isoTimestamp: String?) = transient.update { it.copy(scheduledAt = isoTimestamp) }

    /**
     * Uploads a picked media file and attaches it to the compose. [bytes] and the
     * file metadata come from the platform picker at the UI layer, keeping this
     * ViewModel free of Android URI/ContentResolver dependencies.
     */
    fun onAttachMedia(bytes: ByteArray, fileName: String, mimeType: String, isVideo: Boolean) {
        val placeholder = PendingAttachment(fileName = fileName, isVideo = isVideo)
        transient.update { it.copy(attachments = it.attachments + placeholder, errorMessage = null) }
        viewModelScope.launch {
            val result = if (isVideo) {
                repository.uploadVideo(bytes, fileName, mimeType)
            } else {
                repository.uploadImage(bytes, fileName, mimeType)
            }
            when (result) {
                is ApiResult.Success -> transient.update { state ->
                    state.copy(
                        attachments = state.attachments.map {
                            if (it === placeholder || (it.fileName == fileName && it.isUploading)) {
                                it.copy(hostedUrl = result.data, isUploading = false)
                            } else {
                                it
                            }
                        },
                    )
                }
                is ApiResult.Failure -> transient.update { state ->
                    // Drop the failed placeholder and surface the error.
                    state.copy(
                        attachments = state.attachments.filterNot {
                            it.fileName == fileName && it.isUploading
                        },
                    ).withError(result.error)
                }
            }
        }
    }

    /** Removes a not-yet-posted attachment from the compose. */
    fun onRemoveAttachment(attachment: PendingAttachment) = transient.update {
        it.copy(attachments = it.attachments - attachment)
    }

    // --- tags --------------------------------------------------------------

    /**
     * Records the in-progress tag text and asks the server for suggestions.
     *
     * Every keystroke **supersedes** the last one: the previous lookup is
     * cancelled — whether it is still waiting out the debounce or already has a
     * request in flight — so a fast typist produces one request, not a pile of
     * concurrent ones. Only after [TAG_SUGGESTION_DEBOUNCE_MS] of quiet does the
     * call actually go out.
     *
     * The response is applied only while it still answers the current text. A
     * reply that arrives after the user has typed on is dropped, so a slow
     * response for an older prefix can never overwrite a newer one.
     */
    fun onTagQueryChange(value: String) {
        transient.update { it.copy(tagQuery = value) }
        tagSuggestionJob?.cancel()
        // The server 400s on an empty `q`, and there is nothing to complete.
        val query = value.trim()
        if (query.isEmpty()) {
            transient.update { it.copy(tagSuggestions = emptyList(), isLoadingTagSuggestions = false) }
            return
        }
        transient.update { it.copy(isLoadingTagSuggestions = true) }
        tagSuggestionJob = viewModelScope.launch {
            delay(TAG_SUGGESTION_DEBOUNCE_MS)
            val result = repository.autocompleteTags(query)
            // Guard against a stale answer: by the time a response lands the user
            // may have typed on, and the newer lookup's answer is the right one.
            if (transient.value.tagQuery.trim() != query) return@launch
            transient.update {
                it.copy(
                    // Suggestions are an assist, not the task: a failed lookup
                    // just leaves the user typing their own tag, with no error.
                    tagSuggestions = (result as? ApiResult.Success)?.data.orEmpty(),
                    isLoadingTagSuggestions = false,
                )
            }
        }
    }

    /**
     * Commits whatever is in the tag field as a tag. Only surrounding whitespace
     * is trimmed: a tag is a free-form label ("life is short, o brave girl" is a
     * real one), so the text is never split on spaces or otherwise rewritten.
     * Duplicates are ignored — tags are case-sensitive, so only an exact repeat
     * counts as one.
     */
    fun commitTag() = addTag(transient.value.tagQuery)

    /** Adds a suggestion the user tapped, exactly as the server spelled it. */
    fun onSelectTagSuggestion(suggestion: TagSuggestion) = addTag(suggestion.tag)

    /** Removes an already-committed tag from the in-progress compose. */
    fun onRemoveTag(tag: String) = transient.update {
        it.copy(composeTags = it.composeTags - tag)
    }

    private fun addTag(raw: String) {
        val tag = raw.trim()
        if (tag.isEmpty()) return
        // The field is now empty, so there is nothing left to suggest for.
        tagSuggestionJob?.cancel()
        transient.update {
            it.copy(
                composeTags = if (tag in it.composeTags) it.composeTags else it.composeTags + tag,
                tagQuery = "",
                tagSuggestions = emptyList(),
                isLoadingTagSuggestions = false,
            )
        }
    }

    fun post() {
        val snapshot = transient.value
        val text = snapshot.composeText.trim()
        val ready = snapshot.attachments.mapNotNull { it.hostedUrl }
        if (text.isBlank() && ready.isEmpty()) return
        if (snapshot.attachments.any { it.isUploading }) return
        val images = snapshot.attachments.filterNot { it.isVideo }.mapNotNull { it.hostedUrl }
        val videos = snapshot.attachments.filter { it.isVideo }.mapNotNull { it.hostedUrl }
        // Fold the selected linked networks into the cross-post request fields.
        val selected = snapshot.linkedNetworks.filter { it.id in snapshot.selectedNetworkIds }
        val crossPost = CrossPostSelection.from(selected)
        // The compose is closing either way; a suggestion lookup is now moot.
        tagSuggestionJob?.cancel()
        transient.update { it.copy(isPosting = true, errorMessage = null, crossPostStatuses = emptyList()) }
        viewModelScope.launch {
            when (
                val result = repository.createMessage(
                    content = text,
                    imageUrls = images,
                    videoUrls = videos,
                    scheduledAt = snapshot.scheduledAt,
                    crossPost = crossPost,
                    visibility = snapshot.composeVisibility,
                    // Present only for a quote; the repository forces it public.
                    pushedMessageId = snapshot.quoteTarget?.id,
                    // Committed chips, plus anything still sitting uncommitted in
                    // the field — the user meant that word as a tag too.
                    tags = snapshot.tagsToPost,
                )
            ) {
                is ApiResult.Success -> transient.update {
                    it.copy(
                        isPosting = false,
                        isComposeOpen = false,
                        composeText = "",
                        attachments = emptyList(),
                        scheduledAt = null,
                        composeTags = emptyList(),
                        tagQuery = "",
                        tagSuggestions = emptyList(),
                        isLoadingTagSuggestions = false,
                        visibilityOverride = null,
                        quoteTarget = null,
                        selectedNetworkIds = emptySet(),
                        crossPostStatuses = result.data.crossPosts,
                    )
                }
                is ApiResult.Failure -> transient.update {
                    it.copy(isPosting = false).withError(result.error)
                }
            }
        }
    }

    /** Dismisses the post-send cross-post status banner. */
    fun dismissCrossPostStatuses() = transient.update { it.copy(crossPostStatuses = emptyList()) }

    // --- report ------------------------------------------------------------

    fun openReport(message: Message) = transient.update { it.copy(reportTarget = message, errorMessage = null) }

    fun dismissReport() = transient.update { it.copy(reportTarget = null, isReporting = false) }

    fun submitReport(reason: ReportReason, detail: String) {
        val target = transient.value.reportTarget ?: return
        transient.update { it.copy(isReporting = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = repository.report(target.id, reason, detail)) {
                is ApiResult.Success -> transient.update {
                    it.copy(isReporting = false, reportTarget = null)
                }
                is ApiResult.Failure -> transient.update {
                    it.copy(isReporting = false, reportTarget = null).withError(result.error)
                }
            }
        }
    }

    // --- edit own message --------------------------------------------------

    /** Opens the in-place editor seeded with [message]'s current content. */
    fun openEdit(message: Message) = transient.update {
        it.copy(editTarget = message, editText = message.content, errorMessage = null)
    }

    fun onEditTextChange(value: String) = transient.update { it.copy(editText = value) }

    fun dismissEdit() = transient.update {
        it.copy(editTarget = null, editText = "", isSavingEdit = false)
    }

    /** Saves the edit: PATCHes the new content (repository updates the cache). */
    fun saveEdit() {
        val target = transient.value.editTarget ?: return
        val text = transient.value.editText.trim()
        if (text.isBlank()) return
        transient.update { it.copy(isSavingEdit = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = repository.editMessage(target.id, text)) {
                is ApiResult.Success -> transient.update {
                    it.copy(isSavingEdit = false, editTarget = null, editText = "")
                }
                is ApiResult.Failure -> transient.update {
                    it.copy(isSavingEdit = false).withError(result.error)
                }
            }
        }
    }

    // --- author moderation -------------------------------------------------

    /** Queues a Block / Mute / Report-user action for confirmation. */
    fun openModeration(message: Message, action: ModerationAction) = transient.update {
        it.copy(moderationTarget = ModerationTarget(message, action), errorMessage = null)
    }

    fun dismissModeration() = transient.update {
        it.copy(moderationTarget = null, isModerating = false)
    }

    /**
     * Confirms the queued moderation action. Block/Mute additionally hide the
     * author's messages from the local feed (handled in the repository); Report
     * carries the chosen [reason] and optional [detail].
     */
    fun confirmModeration(reason: ReportReason? = null, detail: String = "") {
        val target = transient.value.moderationTarget ?: return
        transient.update { it.copy(isModerating = true, errorMessage = null) }
        viewModelScope.launch {
            val result = when (target.action) {
                ModerationAction.BLOCK -> repository.blockUser(target.username)
                ModerationAction.MUTE -> repository.muteUser(target.username)
                ModerationAction.REPORT ->
                    repository.reportUser(target.username, reason ?: ReportReason.OTHER, detail)
            }
            when (result) {
                is ApiResult.Success -> transient.update {
                    it.copy(isModerating = false, moderationTarget = null)
                }
                is ApiResult.Failure -> transient.update {
                    it.copy(isModerating = false, moderationTarget = null).withError(result.error)
                }
            }
        }
    }

    // --- link metadata -----------------------------------------------------

    /** Fetches link-preview metadata for a message; the cache Flow re-emits it. */
    fun onFetchMetadata(message: Message) {
        viewModelScope.launch {
            val result = repository.fetchMetadata(message.id)
            if (result is ApiResult.Failure) {
                transient.update { it.withError(result.error) }
            }
        }
    }

    fun dismissError() = transient.update { it.copy(errorMessage = null, subscriptionRequired = false) }

    private fun FeedTransientState.withError(error: AppError?): FeedTransientState =
        if (error == null) this
        else copy(errorMessage = error.toUserMessage(), subscriptionRequired = error.isSubscriptionGate)

    companion object {
        /**
         * Quiet period before a tag prefix is looked up. Long enough that typing
         * a word straight through costs one request, short enough that the
         * suggestions still feel live.
         */
        const val TAG_SUGGESTION_DEBOUNCE_MS = 300L
    }
}
