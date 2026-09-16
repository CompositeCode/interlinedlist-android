package com.interlinedlist.android.feature.notifications.push

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PushEnvironmentTest {

    @Test
    fun `debuggable builds register against the sandbox`() {
        assertThat(PushEnvironment.fromDebuggable(true)).isEqualTo(PushEnvironment.SANDBOX)
        assertThat(PushEnvironment.fromDebuggable(true).apiValue).isEqualTo("sandbox")
    }

    @Test
    fun `release builds register against production`() {
        assertThat(PushEnvironment.fromDebuggable(false)).isEqualTo(PushEnvironment.PRODUCTION)
        assertThat(PushEnvironment.fromDebuggable(false).apiValue).isEqualTo("production")
    }
}
