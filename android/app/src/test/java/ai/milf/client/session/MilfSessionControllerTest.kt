package ai.milf.client.session

import ai.milf.client.protocol.ConfirmResponse
import ai.milf.client.protocol.MilfMessage
import ai.milf.client.relationship.RelationshipGraph
import ai.milf.client.ws.MilfWebSocketClient
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

        controller.showConfirmationForTest("c1", "Calling Wei?", "en", "wei-grandson")
        controller.approveConfirmation()

        assertEquals(listOf(ConfirmResponse("c1", true)), sent)
        assertEquals(SeniorUxScreen.Working, controller.uiState.value.screen)
        assertEquals(null, controller.uiState.value.confirmation)
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

    private fun fakeController(client: FakeClient = FakeClient()): MilfSessionController =
        MilfSessionController(
            dependencies = MilfSessionController.Dependencies.fake(client),
            graph = RelationshipGraph.demo()
        )
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

    override fun close() = Unit
}
