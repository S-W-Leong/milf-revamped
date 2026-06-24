package ai.milf.client.session

import ai.milf.client.protocol.ConfirmResponse
import ai.milf.client.protocol.MilfMessage
import ai.milf.client.relationship.RelationshipGraph
import ai.milf.client.ws.MilfWebSocketClient
import ai.milf.client.AudioRecorderLike
import ai.milf.client.NarratorLike
import ai.milf.client.protocol.ActionResult
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

    private fun fakeController(client: FakeClient = FakeClient()): MilfSessionController =
        fakeController(clients = listOf(client))

    private fun fakeController(
        clients: List<FakeClient>,
        recorder: FakeRecorder = FakeRecorder()
    ): MilfSessionController =
        MilfSessionController(
            dependencies = testDependencies(clients, recorder),
            graph = RelationshipGraph.demo()
        )

    private fun fakeController(recorder: FakeRecorder): MilfSessionController =
        fakeController(clients = listOf(FakeClient()), recorder = recorder)
}

private class FakeClient(
    private val onSendConfirm: (ConfirmResponse) -> Unit = {}
) : SessionSocketClient {
    var callbacks: MilfWebSocketClient.Callbacks? = null

    override fun start(goalAudio: ByteArray, lang: String, callbacks: MilfWebSocketClient.Callbacks) {
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

private class FakeRecorder : AudioRecorderLike {
    var isRecording = false
    var stopCalls = 0

    override fun start() {
        isRecording = true
    }

    override fun stop(): ByteArray {
        check(isRecording) { "stop called while not recording" }
        stopCalls += 1
        isRecording = false
        return byteArrayOf(1)
    }

    override fun cancel() {
        isRecording = false
    }
}

private fun testDependencies(
    clients: List<FakeClient>,
    recorder: FakeRecorder = FakeRecorder()
): MilfSessionController.Dependencies {
    var nextClient = 0
    return MilfSessionController.Dependencies(
        recorder = recorder,
        narrator = object : NarratorLike {
            override fun speak(text: String, lang: String) = Unit
            override fun stop() = Unit
            override fun shutdown() = Unit
        },
        clientFactory = {
            clients.getOrElse(nextClient++) { clients.last() }
        },
        accessibilityAvailable = { true },
        dispatch = { action -> ActionResult(action.id, true) }
    )
}
