package com.interlinedlist.android.feature.ai.ui

import com.google.common.truth.Truth.assertThat
import com.interlinedlist.android.feature.ai.domain.AiArtifact
import com.interlinedlist.android.feature.ai.domain.AiCreated
import com.interlinedlist.android.feature.ai.domain.AiError
import com.interlinedlist.android.feature.ai.domain.AiFeature
import com.interlinedlist.android.feature.ai.domain.AiGate
import com.interlinedlist.android.feature.ai.domain.AiGeneration
import com.interlinedlist.android.feature.ai.domain.AiPreview
import com.interlinedlist.android.feature.ai.domain.AiQuota
import com.interlinedlist.android.feature.ai.domain.AiResult
import com.interlinedlist.android.feature.ai.domain.AiSuggestInput
import com.interlinedlist.android.feature.ai.domain.FakeAiRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Test

/**
 * The preview → confirm rule, enforced once here for all five AI surfaces:
 * nothing reaches `/generate` that the user has not approved.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AiPreviewSessionTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeAiRepository()
    private val gate = AiGate(repository)

    private fun session(scope: TestScope) = AiPreviewSession(repository, gate, scope)

    private val preview = AiPreview(
        feature = AiFeature.POWERED_DOCUMENT,
        artifact = AiArtifact(buildJsonObject { put("kind", "document"); put("title", "Draft") }),
        quota = AiQuota(9, 50, 41),
    )

    @Test
    fun `confirming with nothing previewed writes nothing`() = runTest(dispatcher) {
        val session = session(this)

        val started = session.confirm()
        advanceUntilIdle()

        assertThat(started).isFalse()
        assertThat(repository.generateCalls).isEmpty()
        assertThat(session.state.value).isEqualTo(AiPreviewState.Idle)
    }

    @Test
    fun `a suggestion alone writes nothing`() = runTest(dispatcher) {
        repository.suggestResult = AiResult.Success(preview)
        val session = session(this)

        session.suggest(AiFeature.POWERED_DOCUMENT, AiSuggestInput("about widgets"))
        advanceUntilIdle()

        assertThat(session.state.value).isEqualTo(AiPreviewState.Previewing(preview))
        assertThat(repository.generateCalls).isEmpty()
    }

    @Test
    fun `confirming after a preview persists exactly that artifact`() = runTest(dispatcher) {
        repository.suggestResult = AiResult.Success(preview)
        repository.generateResult =
            AiResult.Success(AiGeneration(AiFeature.POWERED_DOCUMENT, AiCreated.DocumentCreated("doc_1")))
        val session = session(this)

        session.suggest(AiFeature.POWERED_DOCUMENT, AiSuggestInput("about widgets"))
        advanceUntilIdle()
        val started = session.confirm()
        advanceUntilIdle()

        assertThat(started).isTrue()
        assertThat(repository.generateCalls).hasSize(1)
        assertThat(repository.generateCalls.single().artifact).isEqualTo(preview.artifact)
        assertThat(repository.generateCalls.single().feature).isEqualTo(AiFeature.POWERED_DOCUMENT)
        assertThat(session.state.value)
            .isEqualTo(AiPreviewState.Generated(AiGeneration(AiFeature.POWERED_DOCUMENT, AiCreated.DocumentCreated("doc_1"))))
    }

    @Test
    fun `an edited preview is what gets written`() = runTest(dispatcher) {
        repository.suggestResult = AiResult.Success(preview)
        val edited = AiArtifact(buildJsonObject { put("kind", "document"); put("title", "My title") })
        val session = session(this)

        session.suggest(AiFeature.POWERED_DOCUMENT, AiSuggestInput("about widgets"))
        advanceUntilIdle()
        session.confirm(edited)
        advanceUntilIdle()

        assertThat(repository.generateCalls.single().artifact).isEqualTo(edited)
    }

    @Test
    fun `discarding a preview writes nothing and cannot be confirmed afterwards`() = runTest(dispatcher) {
        repository.suggestResult = AiResult.Success(preview)
        val session = session(this)

        session.suggest(AiFeature.POWERED_DOCUMENT, AiSuggestInput("about widgets"))
        advanceUntilIdle()
        session.discard()
        val started = session.confirm()
        advanceUntilIdle()

        assertThat(started).isFalse()
        assertThat(repository.generateCalls).isEmpty()
        assertThat(session.state.value).isEqualTo(AiPreviewState.Idle)
    }

    @Test
    fun `a second confirm while the first is in flight is ignored`() = runTest(dispatcher) {
        repository.suggestResult = AiResult.Success(preview)
        val session = session(this)

        session.suggest(AiFeature.POWERED_DOCUMENT, AiSuggestInput("about widgets"))
        advanceUntilIdle()
        session.confirm()
        val second = session.confirm()
        advanceUntilIdle()

        assertThat(second).isFalse()
        assertThat(repository.generateCalls).hasSize(1)
    }

    @Test
    fun `a failed suggestion surfaces the error and reports it to the gate`() = runTest(dispatcher) {
        repository.suggestResult = AiResult.Failure(AiError.QuotaExceeded("Daily AI limit reached"))
        gate.refresh()
        val session = session(this)

        session.suggest(AiFeature.WRITING_ASSIST, AiSuggestInput("draft"))
        advanceUntilIdle()

        assertThat(session.state.value)
            .isEqualTo(AiPreviewState.Failed(AiError.QuotaExceeded("Daily AI limit reached")))
        assertThat(gate.availability.value.isQuotaExhausted).isTrue()
    }

    @Test
    fun `a failed generate keeps the preview on screen`() = runTest(dispatcher) {
        repository.suggestResult = AiResult.Success(preview)
        repository.generateResult = AiResult.Failure(AiError.ProviderFailure("upstream"))
        val session = session(this)

        session.suggest(AiFeature.POWERED_DOCUMENT, AiSuggestInput("about widgets"))
        advanceUntilIdle()
        session.confirm()
        advanceUntilIdle()

        assertThat(session.state.value)
            .isEqualTo(AiPreviewState.Failed(AiError.ProviderFailure("upstream"), preview))
    }

    @Test
    fun `a fresh quota from an action reaches the gate`() = runTest(dispatcher) {
        repository.suggestResult = AiResult.Success(preview)
        gate.refresh()
        val session = session(this)

        session.suggest(AiFeature.POWERED_DOCUMENT, AiSuggestInput("about widgets"))
        advanceUntilIdle()

        assertThat(gate.availability.value).isEqualTo(
            com.interlinedlist.android.feature.ai.domain.AiAvailability.Available(AiQuota(9, 50, 41)),
        )
    }
}
