package ai.milf.client

import ai.milf.client.protocol.Action
import ai.milf.client.protocol.ActionResult
import ai.milf.client.protocol.ConfirmRequest
import ai.milf.client.protocol.MilfProtocol
import ai.milf.client.protocol.Narration
import ai.milf.client.security.ActionPolicy
import ai.milf.client.security.ClientSecurity
import ai.milf.client.ws.MilfWebSocketClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MainViewModelTest {
    @Test
    fun microphonePermissionDenialSetsVisibleStatus() = runTest {
        val viewModel = MainViewModel(
            dependencies = testDependencies()
        )

        viewModel.onMicrophonePermissionDenied()

        assertFalse(viewModel.uiState.value.isRecording)
        assertFalse(viewModel.uiState.value.isRunning)
        assertEquals("Microphone permission needed", viewModel.uiState.value.status)
    }

    @Test
    fun recorderStartFailureSetsVisibleStatusWithoutRecording() = runTest {
        val viewModel = MainViewModel(
            dependencies = testDependencies(
                recorder = FakeRecorder(startFailure = IllegalStateException("mic busy"))
            )
        )

        val result = runCatching { viewModel.startRecording() }

        assertTrue(result.isSuccess)
        assertFalse(viewModel.uiState.value.isRecording)
        assertFalse(viewModel.uiState.value.isRunning)
        assertEquals("Could not start microphone", viewModel.uiState.value.status)
    }

    @Test
    fun narratorStopFailureDoesNotBlockRecording() = runTest {
        val viewModel = MainViewModel(
            dependencies = testDependencies(
                narrator = FakeNarrator(stopFailure = IllegalStateException("tts stop failed"))
            )
        )

        val result = runCatching { viewModel.startRecording() }

        assertTrue(result.isSuccess)
        assertTrue(viewModel.uiState.value.isRecording)
        assertEquals("Listening", viewModel.uiState.value.status)
    }

    @Test
    fun recorderStopFailureClearsRecordingAndStaleClient() = runTest {
        val previousFactory = CloseTrackingSocketFactory()
        val previousClient = MilfWebSocketClient(
            url = "ws://localhost:8765",
            socketFactory = previousFactory,
            clientSecurity = ClientSecurity(
                isDebugBuild = true,
                defaultBackendUrl = "ws://localhost:8765",
                deviceToken = "old-token"
            ),
            audioEncoder = { "audio" }
        )
        previousClient.start(
            goalAudio = byteArrayOf(1),
            lang = "en",
            callbacks = noOpCallbacks()
        )
        val dependencies = testDependencies(
            recorder = FakeRecorder(stopFailure = IllegalStateException("stop failed"))
        )
        dependencies.activeClient = previousClient
        val viewModel = MainViewModel(dependencies)

        viewModel.startRecording()
        val result = runCatching { viewModel.stopAndRun() }

        assertTrue(result.isSuccess)
        assertEquals(true, previousFactory.socket?.closed)
        assertNull(dependencies.activeClient)
        assertFalse(viewModel.uiState.value.isRecording)
        assertFalse(viewModel.uiState.value.isRunning)
        assertEquals("Could not finish recording", viewModel.uiState.value.status)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun narratorSpeakFailureSetsVisibleStatus() = runTest {
        lateinit var client: MilfWebSocketClient
        val dependencies = testDependencies(
            narrator = FakeNarrator(speakFailure = IllegalStateException("speaker failed")),
            clientFactory = { url ->
                MilfWebSocketClient(
                    url = url,
                    socketFactory = CloseTrackingSocketFactory(),
                    scope = this,
                    clientSecurity = ClientSecurity(
                        isDebugBuild = true,
                        defaultBackendUrl = "ws://10.0.2.2:8765",
                        deviceToken = "dev-token"
                    ),
                    audioEncoder = { "audio" }
                ).also { client = it }
            }
        )
        val viewModel = MainViewModel(dependencies)

        viewModel.stopAndRun()
        client.handleText(MilfProtocol.encode(Narration("Hello", "en")))
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isRecording)
        assertFalse(viewModel.uiState.value.isRunning)
        assertEquals("Could not play audio", viewModel.uiState.value.status)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun confirmationSpeakFailureKeepsButtonsAvailable() = runTest {
        lateinit var client: MilfWebSocketClient
        val dependencies = testDependencies(
            narrator = FakeNarrator(speakFailure = IllegalStateException("speaker failed")),
            clientFactory = { url ->
                MilfWebSocketClient(
                    url = url,
                    socketFactory = CloseTrackingSocketFactory(),
                    scope = this,
                    clientSecurity = ClientSecurity(
                        isDebugBuild = true,
                        defaultBackendUrl = "ws://10.0.2.2:8765",
                        deviceToken = "dev-token"
                    ),
                    audioEncoder = { "audio" }
                ).also { client = it }
            }
        )
        val viewModel = MainViewModel(dependencies)

        viewModel.stopAndRun()
        client.handleText(MilfProtocol.encode(ConfirmRequest("c1", "Call Wei now?", "en")))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isRunning)
        assertEquals(PendingConfirmation("c1", "Call Wei now?", "en"), viewModel.uiState.value.confirmation)
        assertEquals("Could not play audio", viewModel.uiState.value.status)
    }

    @Test
    fun websocketFailureClearsPendingConfirmation() = runTest {
        lateinit var client: MilfWebSocketClient
        val dependencies = testDependencies(
            clientFactory = { url ->
                MilfWebSocketClient(
                    url = url,
                    socketFactory = CloseTrackingSocketFactory(),
                    clientSecurity = ClientSecurity(
                        isDebugBuild = true,
                        defaultBackendUrl = "ws://10.0.2.2:8765",
                        deviceToken = "dev-token"
                    ),
                    audioEncoder = { "audio" }
                ).also { client = it }
            }
        )
        val viewModel = MainViewModel(dependencies)

        viewModel.stopAndRun()
        viewModel.showConfirmationForTest("c1", "Call Wei now?", "en")
        client.handleText("{")

        assertNull(viewModel.uiState.value.confirmation)
        assertFalse(viewModel.uiState.value.isRunning)
    }

    @Test
    fun websocketCloseClearsPendingConfirmation() = runTest {
        val socketFactory = CloseTrackingSocketFactory()
        val dependencies = testDependencies(
            clientFactory = { url ->
                MilfWebSocketClient(
                    url = url,
                    socketFactory = socketFactory,
                    clientSecurity = ClientSecurity(
                        isDebugBuild = true,
                        defaultBackendUrl = "ws://10.0.2.2:8765",
                        deviceToken = "dev-token"
                    ),
                    audioEncoder = { "audio" }
                )
            }
        )
        val viewModel = MainViewModel(dependencies)

        viewModel.stopAndRun()
        viewModel.showConfirmationForTest("c1", "Call Wei now?", "en")
        socketFactory.listener?.onClosed("server closed")

        assertNull(viewModel.uiState.value.confirmation)
        assertFalse(viewModel.uiState.value.isRunning)
        assertEquals("server closed", viewModel.uiState.value.status)
    }

    @Test
    fun confirmationSpeechErrorSetsVisibleStatus() = runTest {
        val viewModel = MainViewModel(
            dependencies = testDependencies()
        )

        viewModel.onConfirmationSpeechError("Could not hear confirmation")

        assertEquals("Could not hear confirmation", viewModel.uiState.value.status)
    }

    @Test
    fun websocketStartFailureSetsVisibleStatusAndClearsClient() = runTest {
        val dependencies = testDependencies(
            clientFactory = { url ->
                MilfWebSocketClient(
                    url = url,
                    socketFactory = ThrowingSocketFactory(),
                    clientSecurity = ClientSecurity(
                        isDebugBuild = true,
                        defaultBackendUrl = "ws://10.0.2.2:8765",
                        deviceToken = "dev-token"
                    ),
                    audioEncoder = { "audio" }
                )
            }
        )
        val viewModel = MainViewModel(dependencies)

        val result = runCatching { viewModel.stopAndRun() }

        assertTrue(result.isSuccess)
        assertNull(dependencies.activeClient)
        assertFalse(viewModel.uiState.value.isRecording)
        assertFalse(viewModel.uiState.value.isRunning)
        assertEquals("Could not connect", viewModel.uiState.value.status)
    }

    @Test
    fun missingDeviceTokenFailsBeforeCreatingClient() = runTest {
        var createdClient = false
        val viewModel = MainViewModel(
            dependencies = testDependencies(
                security = ClientSecurity(
                    isDebugBuild = true,
                    defaultBackendUrl = "ws://10.0.2.2:8765",
                    deviceToken = ""
                ),
                clientFactory = {
                    createdClient = true
                    error("client should not be created")
                }
            )
        )

        viewModel.stopAndRun()

        assertFalse(createdClient)
        assertFalse(viewModel.uiState.value.isRunning)
        assertEquals("Device token is required before connecting", viewModel.uiState.value.status)
    }

    @Test
    fun invalidBackendUrlFailsBeforeCreatingClient() = runTest {
        var createdClient = false
        val viewModel = MainViewModel(
            dependencies = testDependencies(
                security = ClientSecurity(
                    isDebugBuild = true,
                    defaultBackendUrl = "ws://10.0.2.2:8765",
                    deviceToken = "dev-token"
                ),
                clientFactory = {
                    createdClient = true
                    error("client should not be created")
                }
            )
        )

        viewModel.setBackendUrl("http://10.0.2.2:8765")
        viewModel.stopAndRun()

        assertFalse(createdClient)
        assertFalse(viewModel.uiState.value.isRunning)
        assertEquals("Websocket URL must start with ws:// or wss://", viewModel.uiState.value.status)
    }

    @Test
    fun preflightFailureClosesPreviousActiveClient() = runTest {
        val previousFactory = CloseTrackingSocketFactory()
        val previousClient = MilfWebSocketClient(
            url = "ws://localhost:8765",
            socketFactory = previousFactory,
            clientSecurity = ClientSecurity(
                isDebugBuild = true,
                defaultBackendUrl = "ws://localhost:8765",
                deviceToken = "old-token"
            ),
            audioEncoder = { "audio" }
        )
        previousClient.start(
            goalAudio = byteArrayOf(1),
            lang = "en",
            callbacks = noOpCallbacks()
        )
        val dependencies = testDependencies(
            security = ClientSecurity(
                isDebugBuild = true,
                defaultBackendUrl = "ws://10.0.2.2:8765",
                deviceToken = "dev-token"
            ),
            clientFactory = { error("client should not be created") }
        )
        dependencies.activeClient = previousClient
        val viewModel = MainViewModel(dependencies)

        viewModel.setBackendUrl("http://10.0.2.2:8765")
        viewModel.stopAndRun()

        assertEquals(true, previousFactory.socket?.closed)
        assertNull(dependencies.activeClient)
        assertFalse(viewModel.uiState.value.isRunning)
    }

    @Test
    fun approveConfirmationClearsPendingRequest() = runTest {
        val sent = mutableListOf<Boolean>()
        val actionPolicy = ActionPolicy(clock = { 1_000L })
        val dependencies = MainViewModel.Dependencies.fake(
            sendConfirm = { approved -> sent += approved },
            actionPolicy = actionPolicy
        )
        dependencies.activeClient = clientWithSendResult(true)
        val viewModel = MainViewModel(dependencies)

        viewModel.showConfirmationForTest("c1", "Call Wei now?", "en")
        viewModel.approveConfirmation()

        assertEquals(listOf(true), sent)
        assertEquals(null, viewModel.uiState.value.confirmation)
        assertEquals(ActionPolicy.Decision.Allowed, actionPolicy.authorize("tap"))
    }

    @Test
    fun failedConfirmationSendDoesNotRecordLocalApproval() = runTest {
        val actionPolicy = ActionPolicy(clock = { 1_000L })
        val dependencies = MainViewModel.Dependencies.fake(
            sendConfirm = {},
            actionPolicy = actionPolicy
        )
        dependencies.activeClient = clientWithSendResult(false)
        val viewModel = MainViewModel(dependencies)

        viewModel.showConfirmationForTest("c1", "Call Wei now?", "en")
        viewModel.approveConfirmation()

        assertEquals(
            ActionPolicy.Decision.Rejected("Local confirmation is required before this action"),
            actionPolicy.authorize("tap")
        )
        assertEquals(null, viewModel.uiState.value.confirmation)
        assertEquals("Could not send confirmation", viewModel.uiState.value.status)
    }

    @Test
    fun denyConfirmationClearsLocalActionPolicyApproval() = runTest {
        val sent = mutableListOf<Boolean>()
        val actionPolicy = ActionPolicy(clock = { 1_000L }).also { it.recordApproval() }
        val viewModel = MainViewModel(
            dependencies = MainViewModel.Dependencies.fake(
                sendConfirm = { approved -> sent += approved },
                actionPolicy = actionPolicy
            )
        )

        viewModel.showConfirmationForTest("c1", "Call Wei now?", "en")
        viewModel.denyConfirmation()

        val decision = actionPolicy.authorize("tap")
        assertEquals(listOf(false), sent)
        assertEquals(null, viewModel.uiState.value.confirmation)
        assertEquals(
            ActionPolicy.Decision.Rejected("Local confirmation is required before this action"),
            decision
        )
    }

    @Test
    fun confirmationSpeechSendsDecision() = runTest {
        val sent = mutableListOf<Boolean>()
        val viewModel = MainViewModel(
            dependencies = MainViewModel.Dependencies.fake(
                sendConfirm = { approved -> sent += approved }
            )
        )

        viewModel.showConfirmationForTest("c1", "Call Wei now?", "en")
        viewModel.onConfirmationSpeech("tak nak")

        assertEquals(listOf(false), sent)
        assertEquals(null, viewModel.uiState.value.confirmation)
    }
}

private fun testDependencies(
    security: ClientSecurity = ClientSecurity(
        isDebugBuild = true,
        defaultBackendUrl = "ws://10.0.2.2:8765",
        deviceToken = "dev-token"
    ),
    recorder: AudioRecorderLike = FakeRecorder(),
    narrator: NarratorLike = FakeNarrator(),
    clientFactory: (String) -> ai.milf.client.ws.MilfWebSocketClient = { error("not used") }
): MainViewModel.Dependencies =
    MainViewModel.Dependencies(
        recorder = recorder,
        narrator = narrator,
        clientFactory = clientFactory,
        clientSecurity = security
    )

private class FakeRecorder(
    private val startFailure: RuntimeException? = null,
    private val stopFailure: RuntimeException? = null
) : AudioRecorderLike {
    override fun start() {
        startFailure?.let { throw it }
    }

    override fun stop(): ByteArray {
        stopFailure?.let { throw it }
        return byteArrayOf(1)
    }

    override fun cancel() = Unit
}

private class FakeNarrator(
    private val speakFailure: RuntimeException? = null,
    private val stopFailure: RuntimeException? = null
) : NarratorLike {
    override fun speak(text: String, lang: String) {
        speakFailure?.let { throw it }
    }

    override fun stop() {
        stopFailure?.let { throw it }
    }

    override fun shutdown() = Unit
}

private class CloseTrackingSocketFactory(
    private val sendResult: Boolean = true
) : MilfWebSocketClient.SocketFactory {
    var socket: CloseTrackingSocket? = null
    var listener: MilfWebSocketClient.TextListener? = null

    override fun open(
        url: String,
        listener: MilfWebSocketClient.TextListener
    ): MilfWebSocketClient.Socket =
        CloseTrackingSocket(sendResult).also {
            this.listener = listener
            socket = it
        }
}

private class CloseTrackingSocket(
    private val sendResult: Boolean = true
) : MilfWebSocketClient.Socket {
    var closed = false

    override fun send(text: String): Boolean = sendResult

    override fun close() {
        closed = true
    }
}

private class ThrowingSocketFactory : MilfWebSocketClient.SocketFactory {
    override fun open(
        url: String,
        listener: MilfWebSocketClient.TextListener
    ): MilfWebSocketClient.Socket {
        throw IllegalStateException("socket open failed")
    }
}

private fun clientWithSendResult(sendResult: Boolean): MilfWebSocketClient =
    MilfWebSocketClient(
        url = "ws://localhost:8765",
        socketFactory = CloseTrackingSocketFactory(sendResult),
        clientSecurity = ClientSecurity(
            isDebugBuild = true,
            defaultBackendUrl = "ws://localhost:8765",
            deviceToken = "test-token"
        ),
        audioEncoder = { "audio" }
    ).also {
        it.start(
            goalAudio = byteArrayOf(1),
            lang = "en",
            callbacks = noOpCallbacks()
        )
    }

private fun noOpCallbacks(): MilfWebSocketClient.Callbacks =
    object : MilfWebSocketClient.Callbacks {
        override suspend fun onAction(action: Action): ActionResult =
            ActionResult(id = action.id, ok = true)

        override suspend fun onNarration(text: String, lang: String) = Unit
        override suspend fun onConfirmRequest(id: String, summary: String, lang: String) = Unit
        override fun onClosed(reason: String?) = Unit
        override fun onFailed(message: String) = Unit
    }
