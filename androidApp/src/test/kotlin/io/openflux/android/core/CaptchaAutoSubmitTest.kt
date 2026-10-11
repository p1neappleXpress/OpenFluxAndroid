package io.openflux.android.core

import io.openflux.desktop.model.CaptchaPrompt
import io.openflux.desktop.model.YandexDisk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

@OptIn(ExperimentalCoroutinesApi::class)
class CaptchaAutoSubmitTest {
    private val regularUrl = "https://docs.yandex.ru/"
    private val checkpointUrl = "https://yandex.ru/showcaptcha"

    @Test
    fun remoteRegularPageNeverAutoSubmits() = runTest {
        var submissions = 0
        launch {
            monitorCaptchaAutoSubmit(false, { true }, { settled(regularUrl) }, { currentTime + 1 }) {
                submissions++
            }
        }
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(0, submissions)
    }

    @Test
    fun remoteCheckpointNeverAutoSubmits() = runTest {
        var submissions = 0
        launch {
            monitorCaptchaAutoSubmit(false, { true }, { settled(checkpointUrl) }, { currentTime + 1 }) {
                submissions++
            }
        }
        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(0, submissions)
    }

    @Test
    fun localRegularPageStillAutoSubmitsOnceAfterSettling() = runTest {
        var submissions = 0
        launch {
            monitorCaptchaAutoSubmit(true, { true }, { settled(regularUrl) }, { currentTime + 1 }) {
                submissions++
            }
        }
        advanceTimeBy(1499)
        assertEquals(0, submissions)
        advanceTimeBy(8501)
        runCurrent()
        assertEquals(1, submissions)
    }

    @Test
    fun remotePromptRemainsAvailableForExplicitSubmission() = runTest {
        val original = CaptchaPrompt(regularUrl, "smartcaptcha", remote = true)
        var prompt: CaptchaPrompt? = original
        var submissions = 0
        val submit = { submissions++; prompt = null }
        launch {
            monitorCaptchaAutoSubmit(!original.remote, { prompt != null }, { settled(regularUrl) }, { currentTime + 1 }, submit)
        }
        advanceTimeBy(10_000)
        runCurrent()
        assertSame(original, prompt)
        assertEquals(0, submissions)
        submit() // The same callback remains available to the manual action.
        assertEquals(1, submissions)
        assertNull(prompt)
    }

    @Test
    fun localCheckpointAndLoadingResetTheSettleWindow() = runTest {
        var url = regularUrl
        var loading = false
        var submissions = 0
        launch {
            monitorCaptchaAutoSubmit(true, { true }, { !loading && settled(url) }, { currentTime + 1 }) {
                submissions++
            }
        }
        advanceTimeBy(900)
        url = checkpointUrl
        advanceTimeBy(10_000)
        assertEquals(0, submissions)
        url = regularUrl
        loading = true
        advanceTimeBy(3000)
        assertEquals(0, submissions)
        loading = false
        advanceTimeBy(1200)
        assertEquals(0, submissions)
        advanceTimeBy(1000)
        assertEquals(1, submissions)
    }

    @Test
    fun closedPageDoesNotSubmit() = runTest {
        var submissions = 0
        monitorCaptchaAutoSubmit(true, { false }, { settled(regularUrl) }, { currentTime + 1 }) { submissions++ }
        assertEquals(0, submissions)
    }

    private fun settled(url: String) = url.startsWith("https://") && !YandexDisk.isCheckpoint(url)
}
