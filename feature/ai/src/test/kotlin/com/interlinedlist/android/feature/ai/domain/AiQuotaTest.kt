package com.interlinedlist.android.feature.ai.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AiQuotaTest {

    @Test
    fun `remaining is derived when the server only reports usage`() {
        assertThat(AiQuota(usedToday = 7, dailyLimit = 50).remainingActions).isEqualTo(43)
    }

    @Test
    fun `an explicit remaining wins`() {
        assertThat(AiQuota(usedToday = 7, dailyLimit = 50, remaining = 41).remainingActions)
            .isEqualTo(41)
    }

    @Test
    fun `a spent allowance is exhausted`() {
        assertThat(AiQuota(usedToday = 50, dailyLimit = 50).isExhausted).isTrue()
    }

    @Test
    fun `an unknown allowance never blocks the UI`() {
        assertThat(AiQuota().remainingActions).isNull()
        assertThat(AiQuota().isExhausted).isFalse()
    }
}
