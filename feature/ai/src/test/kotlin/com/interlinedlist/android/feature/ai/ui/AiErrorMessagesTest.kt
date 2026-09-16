package com.interlinedlist.android.feature.ai.ui

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.feature.ai.domain.AiError
import org.junit.Test

class AiErrorMessagesTest {

    @Test
    fun `a spent allowance says so in the words the help centre uses`() {
        assertThat(AiError.QuotaExceeded().toUserMessage()).isEqualTo("Daily AI limit reached. Try again tomorrow.")
    }

    @Test
    fun `a rate limit tells the user how long to wait when the server said`() {
        assertThat(AiError.RateLimited(retryAfterSeconds = 30).toUserMessage()).contains("30 seconds")
        assertThat(AiError.RateLimited().toUserMessage()).doesNotContain("seconds.")
    }

    @Test
    fun `an unconfigured provider never blames the user or offers an upsell`() {
        val message = AiError.ProviderUnconfigured("no key").toUserMessage()

        assertThat(message).isEqualTo("AI writing assistance is unavailable right now.")
        assertThat(message).doesNotContain("subscription")
    }

    @Test
    fun `the API's own wording is preferred where it is user-facing`() {
        assertThat(AiError.NotSubscribed("Subscribe to use AI.").toUserMessage())
            .isEqualTo("Subscribe to use AI.")
    }
}
