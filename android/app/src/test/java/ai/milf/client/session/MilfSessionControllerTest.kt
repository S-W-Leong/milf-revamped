package ai.milf.client.session

import ai.milf.client.protocol.ConfirmResponse
import ai.milf.client.protocol.MilfMessage
import ai.milf.client.relationship.RelationshipGraph
import ai.milf.client.ws.MilfWebSocketClient
import ai.milf.client.AudioRecorderLike
import ai.milf.client.NarratorLike
import ai.milf.client.protocol.ActionResult
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class MilfSessionControllerTest {
    @Test
    fun beginListeningMovesToListeningState() = runTest {
        val controller = fakeController()

        controller.beginListening()

        assertEquals(SeniorUxScreen.Listening, controller.uiState.value.screen)
        assertEquals(true, controller.uiState.value.isRecording)
        assertEquals("What would you like to do?", controller.uiState.value.captions)
    }

    @Test
    fun confirmationRequestShowsContactAwareGate() = runTest {
        val client = FakeClient()
        val controller = fakeController(client = client)

        controller.beginListening()
        controller.finishListeningAndRun()
        client.callbacks?.onConfirmRequest(
            id = "c1",
            summary = "Calling Wei, your grandson?",
            lang = "en",
            contactId = "wei-grandson"
        )

        val state = controller.uiState.value
        assertEquals(SeniorUxScreen.Confirming, state.screen)
        assertEquals("Wei", state.confirmation?.contact?.displayName)
    }

    @Test
    fun approveConfirmationSendsResponseAndReturnsToWorking() = runTest {
        val sent = mutableListOf<ConfirmResponse>()
        val client = FakeClient(onSendConfirm = { sent += it })
        val controller = fakeController(client = client)

        controller.beginListening()
        controller.finishListeningAndRun()
        controller.showConfirmationForTest("c1", "Calling Wei?", "en", "wei-grandson")
        controller.approveConfirmation()

        assertEquals(listOf(ConfirmResponse("c1", true)), sent)
        assertEquals(SeniorUxScreen.Working, controller.uiState.value.screen)
        assertEquals(null, controller.uiState.value.confirmation)
    }

    @Test
    fun denyConfirmationInvalidatesAndClosesActiveSession() = runTest {
        val sent = mutableListOf<ConfirmResponse>()
        val client = FakeClient(onSendConfirm = { sent += it })
        val controller = fakeController(client = client)

        controller.beginListening()
        controller.finishListeningAndRun()
        controller.showConfirmationForTest("c1", "Calling Wei?", "en", "wei-grandson")
        controller.denyConfirmation()
        client.callbacks?.onTaskComplete("Old success.", "en", "wei-grandson")

        val state = controller.uiState.value
        assertEquals(listOf(ConfirmResponse("c1", false)), sent)
        assertEquals(true, client.closed)
        assertEquals(SeniorUxScreen.Idle, state.screen)
        assertEquals(null, state.success)
        assertEquals(null, state.failure)
    }

    @Test
    fun taskFailureShowsRecoveryContact() = runTest {
        val client = FakeClient()
        val controller = fakeController(client = client)

        controller.beginListening()
        controller.finishListeningAndRun()
        client.callbacks?.onTaskFailure(
            message = "I'm having a little trouble doing that. Want me to call your daughter to help?",
            lang = "en",
            recoveryContactId = "buyer-daughter"
        )

        val state = controller.uiState.value
        assertEquals(SeniorUxScreen.Failure, state.screen)
        assertEquals("Daughter", state.failure?.recoveryContact?.displayName)
    }

    @Test
    fun taskCompleteLocksSuccessAgainstLaterTaskFailure() = runTest {
        val client = FakeClient()
        val controller = fakeController(client = client)

        controller.beginListening()
        controller.finishListeningAndRun()
        client.callbacks?.onTaskComplete("Connected to Wei.", "en", "wei-grandson")
        client.callbacks?.onTaskFailure("Old failure.", "en", "buyer-daughter")

        val state = controller.uiState.value
        assertEquals(true, client.closed)
        assertEquals(SeniorUxScreen.Success, state.screen)
        assertEquals("Connected to Wei.", state.success?.summary)
        assertEquals(null, state.failure)
    }

    @Test
    fun closedSessionLocksFailureAgainstLaterTaskComplete() = runTest {
        val client = FakeClient()
        val controller = fakeController(client = client)

        controller.beginListening()
        controller.finishListeningAndRun()
        client.callbacks?.onClosed("socket closed")
        client.callbacks?.onTaskComplete("Old success.", "en", "wei-grandson")

        val state = controller.uiState.value
        assertEquals(true, client.closed)
        assertEquals(SeniorUxScreen.Failure, state.screen)
        assertEquals("Daughter", state.failure?.recoveryContact?.displayName)
        assertEquals(null, state.success)
    }

    @Test
    fun failedSessionLocksFailureAgainstLaterTaskComplete() = runTest {
        val client = FakeClient()
        val controller = fakeController(client = client)

        controller.beginListening()
        controller.finishListeningAndRun()
        client.callbacks?.onFailed("websocket failed")
        client.callbacks?.onTaskComplete("Old success.", "en", "wei-grandson")

        val state = controller.uiState.value
        assertEquals(true, client.closed)
        assertEquals(SeniorUxScreen.Failure, state.screen)
        assertEquals("Daughter", state.failure?.recoveryContact?.displayName)
        assertEquals(null, state.success)
    }

    @Test
    fun confirmRequestDoesNotOverwriteSuccessIfSessionBecomesStaleDuringSpeech() = runTest {
        val client = FakeClient()
        val narrator = FakeNarrator()
        val controller = fakeController(client = client, narrator = narrator)

        controller.beginListening()
        controller.finishListeningAndRun()
        narrator.onSpeak = { text, _ ->
            if (text == "Calling Wei?") {
                runBlocking {
                    client.callbacks?.onTaskComplete("Connected to Wei.", "en", "wei-grandson")
                }
            }
        }
        client.callbacks?.onConfirmRequest("c1", "Calling Wei?", "en", "wei-grandson")

        val state = controller.uiState.value
        assertEquals(SeniorUxScreen.Success, state.screen)
        assertEquals("Connected to Wei.", state.captions)
        assertEquals(null, state.confirmation)
    }

    @Test
    fun narrationDoesNotOverwriteFailureIfSessionBecomesStaleDuringSpeech() = runTest {
        val client = FakeClient()
        val narrator = FakeNarrator()
        val controller = fakeController(client = client, narrator = narrator)

        controller.beginListening()
        controller.finishListeningAndRun()
        narrator.onSpeak = { text, _ ->
            if (text == "Still working.") {
                runBlocking {
                    client.callbacks?.onTaskFailure("Need help.", "en", "buyer-daughter")
                }
            }
        }
        client.callbacks?.onNarration("Still working.", "en")

        val state = controller.uiState.value
        assertEquals(SeniorUxScreen.Failure, state.screen)
        assertEquals("Need help.", state.captions)
    }

    @Test
    fun oldTerminalCallbackDoesNotCloseNewActiveClient() = runTest {
        val clientA = FakeClient()
        val clientB = FakeClient()
        val narrator = FakeNarrator()
        val controller = fakeController(clients = listOf(clientA, clientB), narrator = narrator)

        controller.beginListening()
        controller.finishListeningAndRun()
        narrator.onSpeak = { text, _ ->
            if (text == "Connected to Wei.") {
                controller.beginListening()
                controller.finishListeningAndRun()
            }
        }
        clientA.callbacks?.onTaskComplete("Connected to Wei.", "en", "wei-grandson")

        assertEquals(true, clientA.closed)
        assertEquals(false, clientB.closed)
    }

    @Test
    fun staleCallbacksFromPreviousRunDoNotOverwriteCurrentRun() = runTest {
        val clientA = FakeClient()
        val clientB = FakeClient()
        val controller = fakeController(clients = listOf(clientA, clientB))

        controller.beginListening()
        controller.finishListeningAndRun()
        controller.beginListening()
        controller.finishListeningAndRun()
        clientA.callbacks?.onTaskComplete("Old success.", "en", "wei-grandson")
        clientA.callbacks?.onTaskFailure("Old failure.", "en", "buyer-daughter")

        val state = controller.uiState.value
        assertEquals(true, clientA.closed)
        assertEquals(SeniorUxScreen.Working, state.screen)
        assertEquals("Working on that.", state.captions)
        assertEquals(null, state.success)
        assertEquals(null, state.failure)
    }

    @Test
    fun finishWhenNotRecordingDoesNotStopRecorder() = runTest {
        val recorder = FakeRecorder()
        val controller = fakeController(recorder = recorder)

        controller.finishListeningAndRun()

        assertEquals(0, recorder.stopCalls)
        assertEquals(SeniorUxScreen.Idle, controller.uiState.value.screen)
    }

    @Test
    fun recorderStartFailureMovesToSafeFailure() = runTest {
        val recorder = FakeRecorder(failStart = true)
        val controller = fakeController(recorder = recorder)

        controller.beginListening()

        val state = controller.uiState.value
        assertEquals(SeniorUxScreen.Failure, state.screen)
        assertEquals(false, state.isRecording)
        assertEquals(false, state.isRunning)
        assertEquals("Daughter", state.failure?.recoveryContact?.displayName)
    }

    @Test
    fun recorderStopFailureMovesToSafeFailure() = runTest {
        val recorder = FakeRecorder(failStop = true)
        val controller = fakeController(recorder = recorder)

        controller.beginListening()
        controller.finishListeningAndRun()

        val state = controller.uiState.value
        assertEquals(SeniorUxScreen.Failure, state.screen)
        assertEquals(false, state.isRecording)
        assertEquals(false, state.isRunning)
        assertEquals("Daughter", state.failure?.recoveryContact?.displayName)
    }

    @Test
    fun clientStartFailureMovesToSafeFailureAndClosesClient() = runTest {
        val client = FakeClient(failStart = true)
        val controller = fakeController(client)

        controller.beginListening()
        controller.finishListeningAndRun()

        val state = controller.uiState.value
        assertEquals(true, client.closed)
        assertEquals(SeniorUxScreen.Failure, state.screen)
        assertEquals(false, state.isRecording)
        assertEquals(false, state.isRunning)
        assertEquals("Daughter", state.failure?.recoveryContact?.displayName)
    }

    @Test
    fun activeCloseWhileWorkingMovesToFailure() = runTest {
        val client = FakeClient()
        val controller = fakeController(client = client)

        controller.beginListening()
        controller.finishListeningAndRun()
        client.callbacks?.onClosed("socket closed")

        val state = controller.uiState.value
        assertEquals(SeniorUxScreen.Failure, state.screen)
        assertEquals(false, state.isRunning)
        assertEquals("Daughter", state.failure?.recoveryContact?.displayName)
    }

    @Test
    fun cancelActiveSessionStopsWorkAndReturnsToIdle() = runTest {
        val client = FakeClient()
        val recorder = FakeRecorder()
        val narrator = FakeNarrator()
        val controller = fakeController(
            clients = listOf(client),
            recorder = recorder,
            narrator = narrator
        )

        controller.beginListening()
        controller.finishListeningAndRun()
        controller.showConfirmationForTest("c1", "Calling Wei?", "en", "wei-grandson")
        val stopCallsBeforeCancel = narrator.stopCalls
        controller.cancelActiveSession()
        client.callbacks?.onTaskComplete("Old success.", "en", "wei-grandson")

        val state = controller.uiState.value
        assertEquals(true, client.closed)
        assertEquals(true, recorder.cancelled)
        assertEquals(stopCallsBeforeCancel + 1, narrator.stopCalls)
        assertEquals(SeniorUxScreen.Idle, state.screen)
        assertEquals(false, state.isRecording)
        assertEquals(false, state.isRunning)
        assertEquals(null, state.confirmation)
        assertEquals(null, state.success)
        assertEquals(null, state.failure)
    }

    @Test
    fun demoModeForcesWatchModeDuringWorking() = runTest {
        val client = FakeClient()
        val controller = fakeController(client)

        controller.setDemoMode(true)
        controller.beginListening()
        controller.finishListeningAndRun()

        val state = controller.uiState.value
        assertEquals(SeniorUxScreen.Working, state.screen)
        assertEquals(true, state.demoMode)
        assertEquals(true, state.watchMode)
    }

    @Test
    fun seniorModeCanReturnToCalmBubbleDuringWorking() = runTest {
        val client = FakeClient()
        val controller = fakeController(client)

        controller.setDemoMode(true)
        controller.setDemoMode(false)
        controller.beginListening()
        controller.finishListeningAndRun()

        val state = controller.uiState.value
        assertEquals(SeniorUxScreen.Working, state.screen)
        assertEquals(false, state.demoMode)
        assertEquals(false, state.watchMode)
    }

    private fun fakeController(
        client: FakeClient = FakeClient(),
        narrator: FakeNarrator = FakeNarrator()
    ): MilfSessionController =
        fakeController(clients = listOf(client), narrator = narrator)

    private fun fakeController(
        clients: List<FakeClient>,
        recorder: FakeRecorder = FakeRecorder(),
        narrator: FakeNarrator = FakeNarrator()
    ): MilfSessionController =
        MilfSessionController(
            dependencies = testDependencies(clients, recorder, narrator),
            graph = RelationshipGraph.demo()
        )

    private fun fakeController(recorder: FakeRecorder): MilfSessionController =
        fakeController(clients = listOf(FakeClient()), recorder = recorder)
}

private class FakeNarrator : NarratorLike {
    var onSpeak: (String, String) -> Unit = { _, _ -> }
    var stopCalls = 0

    override fun speak(text: String, lang: String) {
        onSpeak(text, lang)
    }

    override fun stop() {
        stopCalls += 1
    }

    override fun shutdown() = Unit
}

private class FakeClient(
    private val onSendConfirm: (ConfirmResponse) -> Unit = {},
    private val failStart: Boolean = false
) : SessionSocketClient {
    var callbacks: MilfWebSocketClient.Callbacks? = null

    override fun start(goalAudio: ByteArray, lang: String, callbacks: MilfWebSocketClient.Callbacks) {
        check(!failStart) { "client start failed" }
        this.callbacks = callbacks
    }

    override fun send(message: MilfMessage): Boolean {
        if (message is ConfirmResponse) {
            onSendConfirm(message)
        }
        return true
    }

    var closed = false

    override fun close() {
        closed = true
    }
}

private class FakeRecorder(
    private val failStart: Boolean = false,
    private val failStop: Boolean = false
) : AudioRecorderLike {
    var isRecording = false
    var stopCalls = 0
    var cancelled = false

    override fun start() {
        check(!failStart) { "start failed" }
        isRecording = true
        cancelled = false
    }

    override fun stop(): ByteArray {
        check(isRecording) { "stop called while not recording" }
        check(!failStop) { "stop failed" }
        stopCalls += 1
        isRecording = false
        return byteArrayOf(1)
    }

    override fun cancel() {
        cancelled = true
        isRecording = false
    }
}

private fun testDependencies(
    clients: List<FakeClient>,
    recorder: FakeRecorder = FakeRecorder(),
    narrator: FakeNarrator = FakeNarrator()
): MilfSessionController.Dependencies {
    var nextClient = 0
    return MilfSessionController.Dependencies(
        recorder = recorder,
        narrator = narrator,
        clientFactory = {
            clients.getOrElse(nextClient++) { clients.last() }
        },
        accessibilityAvailable = { true },
        dispatch = { action -> ActionResult(action.id, true) }
    )
}
