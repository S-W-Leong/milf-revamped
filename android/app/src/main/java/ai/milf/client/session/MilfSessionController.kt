package ai.milf.client.session

import ai.milf.client.AudioRecorderLike
import ai.milf.client.NarratorLike
import ai.milf.client.audio.ConfirmationVoiceParser
import ai.milf.client.protocol.Action
import ai.milf.client.protocol.ActionResult
import ai.milf.client.protocol.ConfirmResponse
import ai.milf.client.relationship.RelationshipGraph
import ai.milf.client.ws.MilfWebSocketClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class MilfSessionController(
    private val dependencies: Dependencies,
    private val graph: RelationshipGraph = RelationshipGraph.demo()
) {
    private val _uiState = MutableStateFlow(SeniorUiState())
    val uiState: StateFlow<SeniorUiState> = _uiState.asStateFlow()
    private var sessionId = 0L

    fun setBackendUrl(url: String) {
        _uiState.update { it.copy(backendUrl = url.trim()) }
    }

    fun setLang(lang: String) {
        _uiState.update { it.copy(lang = lang) }
    }

    fun setWatchMode(enabled: Boolean) {
        _uiState.update { it.copy(watchMode = enabled) }
    }

    fun setDemoMode(enabled: Boolean) {
        _uiState.update { it.copy(demoMode = enabled, watchMode = enabled || it.watchMode) }
    }

    fun setOverlayEnabled(enabled: Boolean) {
        _uiState.update { it.copy(overlayEnabled = enabled) }
    }

    fun setAccessibilityEnabled(enabled: Boolean) {
        _uiState.update { it.copy(accessibilityEnabled = enabled) }
    }

    fun refreshAccessibilityStatus() {
        setAccessibilityEnabled(dependencies.accessibilityAvailable())
    }

    fun beginListening() {
        nextSessionId()
        closeActiveClient()
        dependencies.narrator.stop()
        dependencies.recorder.start()
        _uiState.update {
            it.copy(
                screen = SeniorUxScreen.Listening,
                isRecording = true,
                isRunning = false,
                captions = LISTENING_PROMPT,
                lastNarration = null,
                confirmation = null,
                success = null,
                failure = null
            )
        }
        dependencies.narrator.speak(LISTENING_PROMPT, _uiState.value.lang)
    }

    fun finishListeningAndRun() {
        val state = _uiState.value
        if (!state.isRecording) return

        val bytes = dependencies.recorder.stop()
        closeActiveClient()
        val client = dependencies.clientFactory(state.backendUrl)
        val callbackSessionId = sessionId
        dependencies.activeClient = client
        _uiState.update {
            it.copy(
                screen = SeniorUxScreen.Working,
                isRecording = false,
                isRunning = true,
                captions = "Working on that.",
                confirmation = null,
                success = null,
                failure = null
            )
        }
        client.start(bytes, state.lang, callbacks(callbackSessionId))
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
            null -> _uiState.update { it.copy(captions = "Please say yes or no") }
        }
    }

    fun retry() {
        beginListening()
    }

    fun callBuyer() = Unit

    fun showConfirmationForTest(id: String, summary: String, lang: String, contactId: String?) {
        _uiState.update {
            it.copy(
                screen = SeniorUxScreen.Confirming,
                isRunning = true,
                captions = summary,
                confirmation = PendingConfirmation(id, summary, lang, graph.contact(contactId))
            )
        }
    }

    fun shutdown() {
        nextSessionId()
        dependencies.recorder.cancel()
        dependencies.narrator.shutdown()
        closeActiveClient()
    }

    private fun callbacks(callbackSessionId: Long): MilfWebSocketClient.Callbacks =
        object : MilfWebSocketClient.Callbacks {
            override suspend fun onAction(action: Action): ActionResult {
                if (!isCurrentSession(callbackSessionId)) {
                    return ActionResult(action.id, ok = false, error = "stale session")
                }
                return dependencies.dispatch(action)
            }

            override suspend fun onNarration(text: String, lang: String) {
                if (!isCurrentSession(callbackSessionId)) return
                dependencies.narrator.speak(text, lang)
                _uiState.update {
                    it.copy(lastNarration = text, captions = text)
                }
            }

            override suspend fun onConfirmRequest(
                id: String,
                summary: String,
                lang: String,
                contactId: String?
            ) {
                if (!isCurrentSession(callbackSessionId)) return
                dependencies.narrator.speak(summary, lang)
                _uiState.update {
                    it.copy(
                        screen = SeniorUxScreen.Confirming,
                        isRunning = true,
                        captions = summary,
                        confirmation = PendingConfirmation(id, summary, lang, graph.contact(contactId))
                    )
                }
            }

            override suspend fun onTaskComplete(summary: String, lang: String, contactId: String?) {
                if (!isCurrentSession(callbackSessionId)) return
                dependencies.narrator.speak(summary, lang)
                _uiState.update {
                    it.copy(
                        screen = SeniorUxScreen.Success,
                        isRecording = false,
                        isRunning = false,
                        captions = summary,
                        confirmation = null,
                        success = SuccessState(summary, lang, graph.contact(contactId)),
                        failure = null
                    )
                }
            }

            override suspend fun onTaskFailure(message: String, lang: String, recoveryContactId: String?) {
                if (!isCurrentSession(callbackSessionId)) return
                dependencies.narrator.speak(message, lang)
                _uiState.update {
                    it.copy(
                        screen = SeniorUxScreen.Failure,
                        isRecording = false,
                        isRunning = false,
                        captions = message,
                        confirmation = null,
                        success = null,
                        failure = FailureState(message, lang, graph.contact(recoveryContactId))
                    )
                }
            }

            override fun onClosed(reason: String?) {
                if (!isCurrentSession(callbackSessionId)) return
                moveActiveSessionToFailure()
            }

            override fun onFailed(message: String) {
                if (!isCurrentSession(callbackSessionId)) return
                moveActiveSessionToFailure()
            }
        }

    private fun respondToConfirmation(approved: Boolean) {
        val pending = _uiState.value.confirmation ?: return
        dependencies.activeClient?.send(ConfirmResponse(pending.id, approved))
        _uiState.update {
            it.copy(
                confirmation = null,
                screen = if (approved) SeniorUxScreen.Working else SeniorUxScreen.Idle,
                isRunning = approved,
                captions = if (approved) "Continuing." else "Okay, stopped."
            )
        }
    }

    private fun nextSessionId(): Long {
        sessionId += 1
        return sessionId
    }

    private fun isCurrentSession(callbackSessionId: Long): Boolean =
        callbackSessionId == sessionId

    private fun closeActiveClient() {
        val client = dependencies.activeClient
        dependencies.activeClient = null
        client?.close()
    }

    private fun moveActiveSessionToFailure() {
        val safe = "I'm having a little trouble doing that. Want me to call your daughter to help?"
        val lang = _uiState.value.lang
        var shouldSpeak = false
        _uiState.update {
            when (it.screen) {
                SeniorUxScreen.Success,
                SeniorUxScreen.Failure -> it.copy(isRunning = false)

                SeniorUxScreen.Working,
                SeniorUxScreen.Confirming,
                SeniorUxScreen.Listening -> {
                    shouldSpeak = true
                    it.copy(
                        screen = SeniorUxScreen.Failure,
                        isRecording = false,
                        isRunning = false,
                        captions = safe,
                        confirmation = null,
                        success = null,
                        failure = FailureState(safe, lang, graph.escapeContact)
                    )
                }

                SeniorUxScreen.Idle -> it.copy(isRunning = false)
            }
        }
        if (shouldSpeak) {
            dependencies.narrator.speak(safe, lang)
        }
    }

    class Dependencies(
        val recorder: AudioRecorderLike,
        val narrator: NarratorLike,
        val clientFactory: (String) -> SessionSocketClient,
        val accessibilityAvailable: () -> Boolean,
        val dispatch: suspend (Action) -> ActionResult
    ) {
        var activeClient: SessionSocketClient? = null

        companion object {
            fun fake(client: SessionSocketClient): Dependencies =
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
                    clientFactory = { client },
                    accessibilityAvailable = { true },
                    dispatch = { action -> ActionResult(action.id, true) }
                )
        }
    }

    private companion object {
        const val LISTENING_PROMPT = "What would you like to do?"
    }
}
