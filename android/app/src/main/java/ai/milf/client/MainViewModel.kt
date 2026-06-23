package ai.milf.client

import ai.milf.client.accessibility.ActionDispatcher
import ai.milf.client.accessibility.MilfAccessibilityService
import ai.milf.client.audio.AudioRecorder
import ai.milf.client.audio.ConfirmationVoiceParser
import ai.milf.client.audio.TtsNarrator
import ai.milf.client.protocol.Action
import ai.milf.client.protocol.ActionResult
import ai.milf.client.protocol.ConfirmResponse
import ai.milf.client.security.ClientSecurity
import ai.milf.client.ws.MilfWebSocketClient
import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class MainUiState(
    val backendUrl: String = BuildConfig.MILF_DEFAULT_BACKEND_URL,
    val lang: String = "en",
    val isRecording: Boolean = false,
    val isRunning: Boolean = false,
    val status: String = "Ready",
    val lastNarration: String? = null,
    val confirmation: PendingConfirmation? = null,
    val accessibilityEnabled: Boolean = false
)

data class PendingConfirmation(
    val id: String,
    val summary: String,
    val lang: String
)

class MainViewModel(
    private val dependencies: Dependencies
) : ViewModel() {
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    fun setBackendUrl(url: String) {
        _uiState.update { it.copy(backendUrl = url.trim()) }
    }

    fun setLang(lang: String) {
        _uiState.update { it.copy(lang = lang) }
    }

    fun refreshAccessibilityStatus() {
        _uiState.update {
            it.copy(accessibilityEnabled = MilfAccessibilityService.instance != null)
        }
    }

    fun startRecording() {
        dependencies.narrator.stop()
        dependencies.recorder.start()
        _uiState.update {
            it.copy(
                isRecording = true,
                isRunning = false,
                status = "Listening",
                lastNarration = null,
                confirmation = null
            )
        }
    }

    fun stopAndRun() {
        val state = _uiState.value
        val bytes = dependencies.recorder.stop()
        val securedUrl = runCatching { dependencies.clientSecurity.secureWebSocketUrl(state.backendUrl) }
            .getOrElse { error ->
                _uiState.update {
                    it.copy(
                        isRecording = false,
                        isRunning = false,
                        status = error.message ?: "Connection preflight failed"
                    )
                }
                return
            }
        val client = dependencies.clientFactory(securedUrl)
        dependencies.activeClient = client
        _uiState.update {
            it.copy(
                isRecording = false,
                isRunning = true,
                status = "Working"
            )
        }
        client.start(bytes, state.lang, object : MilfWebSocketClient.Callbacks {
            override suspend fun onAction(action: Action): ActionResult =
                ActionDispatcher(MilfAccessibilityService.instance).dispatch(action)

            override suspend fun onNarration(text: String, lang: String) {
                dependencies.narrator.speak(text, lang)
                _uiState.update {
                    it.copy(lastNarration = text, status = "Speaking")
                }
            }

            override suspend fun onConfirmRequest(id: String, summary: String, lang: String) {
                dependencies.narrator.speak(summary, lang)
                _uiState.update {
                    it.copy(
                        confirmation = PendingConfirmation(id, summary, lang),
                        status = "Confirm"
                    )
                }
            }

            override fun onClosed(reason: String?) {
                _uiState.update {
                    it.copy(isRunning = false, status = reason ?: "Done")
                }
            }

            override fun onFailed(message: String) {
                _uiState.update {
                    it.copy(isRunning = false, status = message)
                }
            }
        })
    }

    fun approveConfirmation() {
        respondToConfirmation(approved = true)
    }

    fun denyConfirmation() {
        respondToConfirmation(approved = false)
    }

    fun onConfirmationSpeech(text: String) {
        when (ConfirmationVoiceParser.parse(text)) {
            true -> approveConfirmation()
            false -> denyConfirmation()
            null -> _uiState.update { it.copy(status = "Please say yes or no") }
        }
    }

    private fun respondToConfirmation(approved: Boolean) {
        val pending = _uiState.value.confirmation ?: return
        dependencies.sendConfirm?.invoke(approved)
        dependencies.activeClient?.send(ConfirmResponse(pending.id, approved))
        _uiState.update {
            it.copy(
                confirmation = null,
                status = if (approved) "Continuing" else "Stopped"
            )
        }
    }

    fun showConfirmationForTest(id: String, summary: String, lang: String) {
        _uiState.update { it.copy(confirmation = PendingConfirmation(id, summary, lang)) }
    }

    override fun onCleared() {
        dependencies.recorder.cancel()
        dependencies.narrator.shutdown()
        dependencies.activeClient?.close()
        super.onCleared()
    }

    class Dependencies(
        val recorder: AudioRecorderLike,
        val narrator: NarratorLike,
        val clientFactory: (String) -> MilfWebSocketClient,
        val sendConfirm: ((Boolean) -> Unit)? = null,
        val clientSecurity: ClientSecurity = ClientSecurity()
    ) {
        var activeClient: MilfWebSocketClient? = null

        companion object {
            fun real(application: Application): Dependencies =
                Dependencies(
                    recorder = AndroidAudioRecorder(AudioRecorder(application)),
                    narrator = AndroidNarrator(TtsNarrator(application)),
                    clientFactory = { MilfWebSocketClient(it) }
                )

            fun fake(sendConfirm: (Boolean) -> Unit): Dependencies =
                Dependencies(
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
                    clientFactory = { error("not used") },
                    sendConfirm = sendConfirm
                )
        }
    }

    class Factory(
        private val application: Application
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MainViewModel(Dependencies.real(application)) as T
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

private class AndroidAudioRecorder(
    private val recorder: AudioRecorder
) : AudioRecorderLike {
    override fun start() = recorder.start()
    override fun stop(): ByteArray = recorder.stop()
    override fun cancel() = recorder.cancel()
}

private class AndroidNarrator(
    private val narrator: TtsNarrator
) : NarratorLike {
    override fun speak(text: String, lang: String) = narrator.speak(text, lang)
    override fun stop() = narrator.stop()
    override fun shutdown() = narrator.shutdown()
}
