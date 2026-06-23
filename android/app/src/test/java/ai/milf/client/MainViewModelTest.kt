package ai.milf.client

import ai.milf.client.security.ClientSecurity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MainViewModelTest {
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
    fun approveConfirmationClearsPendingRequest() = runTest {
        val sent = mutableListOf<Boolean>()
        val viewModel = MainViewModel(
            dependencies = MainViewModel.Dependencies.fake(
                sendConfirm = { approved -> sent += approved }
            )
        )

        viewModel.showConfirmationForTest("c1", "Call Wei now?", "en")
        viewModel.approveConfirmation()

        assertEquals(listOf(true), sent)
        assertEquals(null, viewModel.uiState.value.confirmation)
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
    security: ClientSecurity,
    clientFactory: (String) -> ai.milf.client.ws.MilfWebSocketClient
): MainViewModel.Dependencies =
    MainViewModel.Dependencies(
        recorder = object : AudioRecorderLike {
            override fun start() = Unit
            override fun stop(): ByteArray = byteArrayOf(1)
            override fun cancel() = Unit
        },
        narrator = object : NarratorLike {
            override fun speak(text: String, lang: String) = Unit
            override fun stop() = Unit
            override fun shutdown() = Unit
        },
        clientFactory = clientFactory,
        clientSecurity = security
    )
