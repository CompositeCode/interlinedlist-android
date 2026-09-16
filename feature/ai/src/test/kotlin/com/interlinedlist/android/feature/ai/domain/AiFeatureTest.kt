package com.interlinedlist.android.feature.ai.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** The `feature` discriminator is the one string both write endpoints key on. */
class AiFeatureTest {

    @Test
    fun `wire values match the API's five features`() {
        assertThat(AiFeature.entries.map { it.apiValue })
            .containsExactly(
                "writing_assist",
                "powered_template",
                "powered_document",
                "message_series",
                "article_series",
            )
    }

    @Test
    fun `round-trips through its wire value`() {
        AiFeature.entries.forEach { feature ->
            assertThat(AiFeature.fromApiValue(feature.apiValue)).isEqualTo(feature)
        }
    }

    @Test
    fun `an unknown or missing value is not guessed at`() {
        assertThat(AiFeature.fromApiValue("something_new")).isNull()
        assertThat(AiFeature.fromApiValue(null)).isNull()
    }
}
