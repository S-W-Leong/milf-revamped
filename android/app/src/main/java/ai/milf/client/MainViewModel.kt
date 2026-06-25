package ai.milf.client

import ai.milf.client.protocol.ConfirmResponse
import ai.milf.client.protocol.MilfMessage
import ai.milf.client.session.AppScreen
import ai.milf.client.session.ConfigTab
import ai.milf.client.session.MilfSessionController
import ai.milf.client.session.SeniorUiState
import ai.milf.client.session.SessionSocketClient
import ai.milf.client.session.SpeechInputMode
import ai.milf.client.session.androidSessionDependencies
import ai.milf.client.session.canStartHelper
import ai.milf.client.ws.MilfWebSocketClient
import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.StateFlow

typealias MainUiState = SeniorUiState
typealias PendingConfirmation = ai.milf.client.session.PendingConfirmation

val SeniorUiState.status: String
    get() = captions

class MainViewModel(
    private val controller: MilfSessionController,
    private val ownsController: Boolean = true
) : ViewModel() {
    constructor(dependencies: Dependencies) : this(
        MilfSessionController(
            dependencies = dependencies.sessionDependencies
        ),
        ownsController = true
    )

    val uiState: StateFlow<SeniorUiState> = controller.uiState

    fun setBackendUrl(url: String) = controller.setBackendUrl(url)
    fun setLang(lang: String) = controller.setLang(lang)
    fun setSpeechInputMode(mode: SpeechInputMode) = controller.setSpeechInputMode(mode)
    fun setAgentMemoryDraft(memory: String) = controller.setAgentMemoryDraft(memory)
    fun saveAgentMemory() = controller.saveAgentMemory()
    fun setWatchMode(enabled: Boolean) = controller.setWatchMode(enabled)
    fun setDemoMode(enabled: Boolean) = controller.setDemoMode(enabled)
    fun setCommandText(text: String) = controller.setCommandText(text)
    fun submitTextCommand() = controller.submitTextCommand()
    fun stopActiveRun() = controller.stopActiveRun()
    fun clearTransientMessage() = controller.clearTransientMessage()
    fun setAppScreen(screen: AppScreen) = controller.setAppScreen(screen)
    fun setConfigTab(tab: ConfigTab) = controller.setConfigTab(tab)
    fun refreshAccessibilityStatus() = controller.refreshAccessibilityStatus()
    fun refreshSetupStatus() = controller.refreshSetupStatus()
    fun canStartHelper(): Boolean = controller.refreshSetupStatus().canStartHelper
    fun checkBackendConnection() = controller.checkBackendConnection()
    fun connectBackend() = controller.connectBackend()
    fun disconnectBackend() = controller.disconnectBackend()
    fun refreshBackendConnection() = controller.refreshBackendConnection()
    fun startRecording() = controller.beginListening()
    fun stopAndRun() = controller.finishListeningAndRun()
    fun approveConfirmation() = controller.approveConfirmation()
    fun denyConfirmation() = controller.denyConfirmation()
    fun onConfirmationSpeech(text: String) = controller.onConfirmationSpeech(text)
    fun showConfirmationForTest(id: String, summary: String, lang: String) =
        controller.showConfirmationForTest(id, summary, lang)

    override fun onCleared() {
        if (ownsController) {
            controller.shutdown()
        }
        super.onCleared()
    }

    class Dependencies(
        internal val sessionDependencies: MilfSessionController.Dependencies
    ) {
        companion object {
            fun real(application: Application): Dependencies =
                Dependencies(androidSessionDependencies(application))

            fun fake(sendConfirm: (Boolean) -> Unit): Dependencies {
                val client = object : SessionSocketClient {
                    override fun start(
                        goalAudio: ByteArray,
                        lang: String,
                        callbacks: MilfWebSocketClient.Callbacks,
                        backendSessionId: String?,
                        memory: String
                    ) = Unit

                    override fun startText(
                        goalText: String,
                        lang: String,
                        callbacks: MilfWebSocketClient.Callbacks,
                        backendSessionId: String?,
                        memory: String
                    ) = Unit

                    override fun send(message: MilfMessage): Boolean {
                        if (message is ConfirmResponse) {
                            sendConfirm(message.approved)
                        }
                        return true
                    }

                    override fun close() = Unit
                }
                return Dependencies(MilfSessionController.Dependencies.fake(client))
            }
        }
    }

    class Factory(
        private val application: Application
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MainViewModel(
                controller = (application as MilfApplication).sessionController,
                ownsController = false
            ) as T
    }
}

interface AudioRecorderLike {
    fun start()
    fun stop(): ByteArray
    fun cancel()
}

interface NarratorLike {
    fun speak(text: String, lang: String)
    fun stop()
    fun shutdown()
}
