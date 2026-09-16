package com.interlinedlist.android.blog.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.interlinedlist.android.blog.BlogRoutes
import com.interlinedlist.android.blog.BlogSubscriptionAction
import com.interlinedlist.android.blog.BlogSubscriptionOutcome
import com.interlinedlist.android.blog.data.BlogSubscriptionRepository
import com.interlinedlist.android.core.common.result.ApiResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Where the blog email-list screen stands. */
enum class BlogSubscriptionStatus {
    /** Showing the subscribe form; nothing has been sent. */
    EDITING,

    /** A request is in flight. */
    WORKING,

    /**
     * The subscribe request was accepted. **Not** subscribed: the address only joins
     * the list once the confirmation email is opened.
     */
    CHECK_YOUR_EMAIL,

    /** A confirmation link completed the double opt-in. */
    CONFIRMED,

    /** An unsubscribe link removed the address. */
    UNSUBSCRIBED,

    /** The tapped link carried no usable token, or the server rejected it. */
    INVALID_LINK,

    /** The request failed; [BlogSubscriptionUiState.message] says how. */
    FAILED,
}

/** UI state for the blog email-list screen. */
data class BlogSubscriptionUiState(
    /** Non-null when the screen was opened by a tapped link from a blog email. */
    val linkAction: BlogSubscriptionAction? = null,
    val status: BlogSubscriptionStatus = BlogSubscriptionStatus.EDITING,
    /** The address in the form; prefilled from the signed-in account. */
    val email: String = "",
    /** The address the confirmation email was sent to, once one has been requested. */
    val submittedEmail: String = "",
    /** Server-provided (or mapped) detail shown under the heading. */
    val message: String? = null,
) {
    /** True while the screen is showing the subscribe form rather than a link result. */
    val isSubscribeForm: Boolean get() = linkAction == null

    /** The Subscribe button is live only for a non-blank address and no request in flight. */
    val canSubmit: Boolean
        get() = status != BlogSubscriptionStatus.WORKING && email.isNotBlank()
}

/**
 * Drives the blog email-list screen: the subscribe form, and both links a blog email
 * can send the user back with.
 *
 * The subscribe endpoint is **enumeration-safe** — it answers identically whether the
 * address is new, already pending, already confirmed or rate-limited — so a successful
 * call says exactly one thing: a confirmation email was requested. Reporting that as
 * "subscribed" would be a lie in three of those four cases and, more importantly, would
 * leave a user who never opens the email wondering why no posts ever arrive. Hence
 * [BlogSubscriptionStatus.CHECK_YOUR_EMAIL] rather than a "subscribed" state.
 *
 * A tapped link with no token never reaches the network: it reports
 * [BlogSubscriptionStatus.INVALID_LINK], so a truncated or hand-copied URL cannot be
 * mistaken for a success.
 */
@HiltViewModel
class BlogSubscriptionViewModel(
    private val repository: BlogSubscriptionRepository,
    linkAction: BlogSubscriptionAction?,
    token: String?,
) : ViewModel() {

    /** Hilt entry point: reads the action + token from the deep-link nav arguments. */
    @Inject
    constructor(
        repository: BlogSubscriptionRepository,
        savedStateHandle: SavedStateHandle,
    ) : this(
        repository = repository,
        linkAction = actionFrom(savedStateHandle.get<String>(BlogRoutes.ACTION_ARG)),
        token = savedStateHandle.get<String>(BlogRoutes.TOKEN_ARG),
    )

    private val _uiState = MutableStateFlow(BlogSubscriptionUiState(linkAction = linkAction))
    val uiState: StateFlow<BlogSubscriptionUiState> = _uiState.asStateFlow()

    init {
        if (linkAction == null) {
            prefillAccountEmail()
        } else {
            complete(linkAction, token?.trim().orEmpty())
        }
    }

    fun onEmailChange(email: String) {
        _uiState.update {
            it.copy(
                email = email,
                // Typing is how the user retries a rejected address, so clear the
                // previous verdict rather than leaving a stale error under the field.
                status = if (it.status == BlogSubscriptionStatus.FAILED) {
                    BlogSubscriptionStatus.EDITING
                } else {
                    it.status
                },
                message = null,
            )
        }
    }

    /** Returns to the form from the "check your email" panel, e.g. after a typo. */
    fun onUseDifferentEmail() {
        _uiState.update {
            it.copy(status = BlogSubscriptionStatus.EDITING, message = null)
        }
    }

    fun subscribe() {
        val email = _uiState.value.email.trim()
        if (email.isEmpty() || _uiState.value.status == BlogSubscriptionStatus.WORKING) return
        _uiState.update { it.copy(status = BlogSubscriptionStatus.WORKING, message = null) }
        viewModelScope.launch {
            when (val result = repository.subscribe(email)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(
                        status = BlogSubscriptionStatus.CHECK_YOUR_EMAIL,
                        submittedEmail = email,
                        message = result.data.takeIf { message -> message.isNotBlank() },
                    )
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(
                        status = BlogSubscriptionStatus.FAILED,
                        message = result.error.toUserMessage(),
                    )
                }
            }
        }
    }

    private fun prefillAccountEmail() {
        viewModelScope.launch {
            val email = repository.accountEmail() ?: return@launch
            // Never clobber something the user has already typed.
            _uiState.update { if (it.email.isEmpty()) it.copy(email = email) else it }
        }
    }

    private fun complete(action: BlogSubscriptionAction, token: String) {
        if (token.isEmpty()) {
            _uiState.update { it.copy(status = BlogSubscriptionStatus.INVALID_LINK) }
            return
        }
        _uiState.update { it.copy(status = BlogSubscriptionStatus.WORKING, message = null) }
        viewModelScope.launch {
            val result = when (action) {
                BlogSubscriptionAction.CONFIRM -> repository.confirm(token)
                BlogSubscriptionAction.UNSUBSCRIBE -> repository.unsubscribe(token)
            }
            when (result) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(status = statusFor(result.data))
                }
                is ApiResult.Failure -> _uiState.update {
                    it.copy(
                        status = BlogSubscriptionStatus.FAILED,
                        message = result.error.toUserMessage(),
                    )
                }
            }
        }
    }

    private companion object {
        /** A missing/unknown nav value means the screen was opened to subscribe. */
        fun actionFrom(raw: String?): BlogSubscriptionAction? =
            BlogSubscriptionAction.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }

        fun statusFor(outcome: BlogSubscriptionOutcome): BlogSubscriptionStatus = when (outcome) {
            BlogSubscriptionOutcome.CONFIRMED -> BlogSubscriptionStatus.CONFIRMED
            BlogSubscriptionOutcome.UNSUBSCRIBED -> BlogSubscriptionStatus.UNSUBSCRIBED
            BlogSubscriptionOutcome.INVALID -> BlogSubscriptionStatus.INVALID_LINK
        }
    }
}
