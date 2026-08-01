package com.interlinedlist.android.feature.billing.data.remote.dto

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

class StripeSessionResponseTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test
    fun `resolvedUrl prefers the canonical url field`() {
        val dto = json.decodeFromString(
            StripeSessionResponse.serializer(),
            """{ "url": "https://a", "checkoutUrl": "https://b" }""",
        )
        assertThat(dto.resolvedUrl).isEqualTo("https://a")
    }

    @Test
    fun `resolvedUrl falls back to aliases when url is missing`() {
        assertThat(
            json.decodeFromString(StripeSessionResponse.serializer(), """{ "portalUrl": "https://p" }""")
                .resolvedUrl,
        ).isEqualTo("https://p")
    }

    @Test
    fun `resolvedUrl ignores blank values`() {
        val dto = json.decodeFromString(
            StripeSessionResponse.serializer(),
            """{ "url": "", "sessionUrl": "https://s" }""",
        )
        assertThat(dto.resolvedUrl).isEqualTo("https://s")
    }

    @Test
    fun `resolvedUrl is null when no url field is present`() {
        val dto = json.decodeFromString(
            StripeSessionResponse.serializer(),
            """{ "id": "cs_test_1", "object": "checkout.session" }""",
        )
        assertThat(dto.resolvedUrl).isNull()
    }
}
