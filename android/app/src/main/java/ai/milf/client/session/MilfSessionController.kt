package ai.milf.client.session

import ai.milf.client.AudioRecorderLike
import ai.milf.client.NarratorLike
import ai.milf.client.audio.ConfirmationVoiceParser
import ai.milf.client.protocol.Action
import ai.milf.client.protocol.ActionResult
import ai.milf.client.protocol.ConfirmResponse
import ai.milf.client.ws.MilfWebSocketClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

enum class SpeechInputMode {
    BackendAudio,
    Native
}

interface NativeSpeechRecognizerLike {
    fun start(lang: String, onText: (String) -> Unit, onError: (String) -> Unit)
    fun stop()
    fun cancel()
    fun shutdown()
}

class MilfSessionController(
    private val dependencies: Dependencies
) {
    private val _uiState = MutableStateFlow(
        SeniorUiState(
            backendUrl = dependencies.initialBackendUrl,
            speechInputMode = dependencies.speechInputMode,
            agentMemory = dependencies.initialAgentMemory,
            savedAgentMemory = dependencies.initialAgentMemory
        )
    )
    val uiState: StateFlow<SeniorUiState> = _uiState.asStateFlow()
    private val sessionId = AtomicLong(0L)
    private val backendSessionId = UUID.randomUUID().toString()

    fun setBackendUrl(url: String) {
        val trimmed = url.trim()
        dependencies.saveBackendUrl(trimmed)
        _uiState.update {
            it.copy(
                backendUrl = trimmed,
                backendConnectionStatus = BackendConnectionStatus.Unknown,
                backendConnectionRequested = true
            )
        }
    }

    fun checkBackendConnection() = connectBackend()

    fun connectBackend() {
        _uiState.update { it.copy(backendConnectionRequested = true) }
        refreshBackendConnection()
    }

    fun disconnectBackend() {
        _uiState.update {
            it.copy(
                backendConnectionRequested = false,
                backendConnectionStatus = BackendConnectionStatus.Disconnected
            )
        }
    }

    fun refreshBackendConnection() {
        val state = _uiState.value
        if (!state.backendConnectionRequested) return

        val url = state.backendUrl
        if (url.isBlank()) {
            setBackendConnectionStatus(BackendConnectionStatus.Failed)
            return
        }

        if (state.backendConnectionStatus != BackendConnectionStatus.Connected) {
            setBackendConnectionStatus(BackendConnectionStatus.Checking)
        }
        dependencies.checkBackendConnection(url) { status ->
            _uiState.update {
                if (it.backendUrl == url && it.backendConnectionRequested) {
                    it.copy(backendConnectionStatus = status)
                } else {
                    it
                }
            }
        }
    }

    fun setBackendConnectionStatus(status: BackendConnectionStatus) {
        _uiState.update { it.copy(backendConnectionStatus = status) }
    }

    fun setLang(lang: String) {
        _uiState.update { it.copy(lang = lang) }
    }

    fun setSpeechInputMode(mode: SpeechInputMode) {
        dependencies.saveSpeechInputMode(mode)
        _uiState.update { it.copy(speechInputMode = mode) }
    }

    fun setAgentMemoryDraft(memory: String) {
        _uiState.update {
            it.copy(
                agentMemory = memory,
                agentMemorySaveStatus = if (memory == it.savedAgentMemory) {
                    AgentMemorySaveStatus.Saved
                } else {
                    AgentMemorySaveStatus.Unsaved
                }
            )
        }
    }

    fun saveAgentMemory() {
        val memory = _uiState.value.agentMemory
        val status = runCatching {
            dependencies.saveAgentMemory(memory)
        }.fold(
            onSuccess = { AgentMemorySaveStatus.Saved },
            onFailure = { AgentMemorySaveStatus.Failed }
        )
        _uiState.update {
            if (status == AgentMemorySaveStatus.Saved) {
                it.copy(
                    savedAgentMemory = memory,
                    agentMemorySaveStatus = status
                )
            } else {
                it.copy(agentMemorySaveStatus = status)
            }
        }
    }

    fun setCommandText(text: String) {
        _uiState.update { it.copy(commandText = text) }
    }

    fun setAppScreen(screen: AppScreen) {
        _uiState.update { it.copy(appScreen = screen) }
    }

    fun setConfigTab(tab: ConfigTab) {
        _uiState.update { it.copy(appScreen = AppScreen.Config, selectedConfigTab = tab) }
    }

    fun setWatchMode(enabled: Boolean) {
        if (enabled) expandOverlay() else collapseOverlay()
    }

    fun setDemoMode(enabled: Boolean) {
        if (enabled) expandOverlay()
    }

    fun setOverlayEnabled(enabled: Boolean) {
        _uiState.update { it.copy(overlayEnabled = enabled) }
    }

    fun setAccessibilityEnabled(enabled: Boolean) {
        _uiState.update { it.copy(accessibilityEnabled = enabled) }
    }

    fun refreshAccessibilityStatus() {
        refreshSetupStatus()
    }

    fun refreshSetupStatus(): SeniorUiState {
        val status = dependencies.setupStatus()
        _uiState.update {
            it.copy(
                microphonePermissionGranted = status.microphoneGranted,
                callPhonePermissionGranted = status.callPhoneGranted,
                overlayPermissionGranted = status.overlayGranted,
                accessibilityEnabled = status.accessibilityEnabled,
                assistantSelected = status.assistantSelected
            )
        }
        return _uiState.value
    }

    fun beginListening() {
        val callbackSessionId = nextSessionId()
        closeActiveClient()
        dependencies.narrator.stop()
        _uiState.update {
            it.copy(
                screen = SeniorUxScreen.Listening,
                isRecording = true,
                isRunning = false,
                captions = LISTENING_PROMPT,
                commandText = "",
                lastNarration = null,
                confirmation = null,
                success = null,
                failure = null,
                actionTarget = null,
                isCollapsed = false
            )
        }
        val started = if (_uiState.value.speechInputMode == SpeechInputMode.Native) {
            runCatching {
                dependencies.speechRecognizer.start(
                    lang = _uiState.value.lang,
                    onText = { text -> onNativeSpeechText(callbackSessionId, text) },
                    onError = { onNativeSpeechError(callbackSessionId) }
                )
            }.isSuccess
        } else {
            runCatching {
                dependencies.recorder.start()
            }.isSuccess
        }
        if (!started) {
            moveLocalSessionToFailure()
            return
        }
    }

    fun finishListeningAndRun() {
        val state = _uiState.value
        if (!state.isRecording) return

        if (state.speechInputMode == SpeechInputMode.Native) {
            val stopped = runCatching {
                dependencies.speechRecognizer.stop()
            }.isSuccess
            if (!stopped) {
                moveLocalSessionToFailure()
                return
            }
            _uiState.update {
                it.copy(
                    screen = SeniorUxScreen.Thinking,
                    isRecording = false,
                    isRunning = true,
                    captions = THINKING_PROMPT,
                    confirmation = null,
                    success = null,
                    failure = null,
                    actionTarget = null,
                    isCollapsed = false
                )
            }
            return
        }

        val bytes = runCatching {
            dependencies.recorder.stop()
        }.getOrElse {
            moveLocalSessionToFailure()
            return
        }
        closeActiveClient()
        val client = try {
            dependencies.clientFactory(state.backendUrl)
        } catch (exception: RuntimeException) {
            moveLocalSessionToFailure()
            return
        }
        val callbackSessionId = sessionId.get()
        dependencies.activeClient = client
        _uiState.update {
            it.copy(
                screen = SeniorUxScreen.Thinking,
                isRecording = false,
                isRunning = true,
                captions = THINKING_PROMPT,
                commandText = "",
                confirmation = null,
                success = null,
                failure = null,
                actionTarget = null,
                isCollapsed = false
            )
        }
        try {
            client.start(
                bytes,
                state.lang,
                callbacks(callbackSessionId, client),
                backendSessionId = backendSessionId,
                memory = state.savedAgentMemory
            )
        } catch (exception: RuntimeException) {
            moveLocalSessionToFailure(expectedClient = client)
        }
    }

    fun submitTextCommand() {
        val state = _uiState.value
        val goal = state.commandText.trim()
        if (goal.isBlank() || state.isRunning || state.isRecording) return

        nextSessionId()
        closeActiveClient()
        dependencies.narrator.stop()
        val client = try {
            dependencies.clientFactory(state.backendUrl)
        } catch (exception: RuntimeException) {
            moveLocalSessionToFailure()
            return
        }
        val callbackSessionId = sessionId.get()
        dependencies.activeClient = client
        _uiState.update {
            it.copy(
                screen = SeniorUxScreen.Thinking,
                isRecording = false,
                isRunning = true,
                captions = THINKING_PROMPT,
                commandText = "",
                confirmation = null,
                success = null,
                failure = null,
                actionTarget = null,
                isCollapsed = false
            )
        }
        try {
            client.startText(
                goal,
                state.lang,
                callbacks(callbackSessionId, client),
                backendSessionId = backendSessionId,
                memory = state.savedAgentMemory
            )
        } catch (exception: RuntimeException) {
            moveLocalSessionToFailure(expectedClient = client)
        }
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

    fun stopActiveRun() {
        cancelActiveSession()
    }

    fun clearTransientMessage() {
        _uiState.update {
            if (it.screen == SeniorUxScreen.Failure) {
                it.copy(
                    screen = SeniorUxScreen.Idle,
                    captions = READY_PROMPT,
                    failure = null,
                    actionTarget = null
                )
            } else {
                it
            }
        }
    }

    fun collapseOverlay() {
        _uiState.update { it.copy(isCollapsed = true) }
    }

    fun expandOverlay() {
        _uiState.update { it.copy(isCollapsed = false) }
    }

    fun cancelActiveSession() {
        nextSessionId()
        dependencies.recorder.cancel()
        dependencies.speechRecognizer.cancel()
        dependencies.narrator.stop()
        closeActiveClient()
        _uiState.update {
            it.copy(
                screen = SeniorUxScreen.Idle,
                isRecording = false,
                isRunning = false,
                captions = READY_PROMPT,
                commandText = "",
                lastNarration = null,
                confirmation = null,
                success = null,
                failure = null,
                actionTarget = null
            )
        }
    }

    fun showConfirmationForTest(id: String, summary: String, lang: String) {
        _uiState.update {
            it.copy(
                screen = SeniorUxScreen.Confirming,
                isRunning = true,
                captions = summary,
                confirmation = PendingConfirmation(id, summary, lang),
                isCollapsed = false
            )
        }
    }

    fun shutdown() {
        nextSessionId()
        dependencies.recorder.cancel()
        dependencies.speechRecognizer.shutdown()
        dependencies.narrator.shutdown()
        closeActiveClient()
    }

    private fun callbacks(
        callbackSessionId: Long,
        client: SessionSocketClient
    ): MilfWebSocketClient.Callbacks =
        object : MilfWebSocketClient.Callbacks {
            override suspend fun onAction(action: Action): ActionResult {
                if (!isCurrentSession(callbackSessionId)) {
                    return ActionResult(action.id, ok = false, error = "stale session")
                }
                _uiState.update {
                    if (!isCurrentSession(callbackSessionId)) {
                        it
                    } else {
                        it.copy(
                            screen = SeniorUxScreen.Acting,
                            captions = ACTING_PROMPT,
                            actionTarget = ActionTarget.from(action),
                            isCollapsed = false
                        )
                    }
                }
                return dependencies.dispatch(action)
            }

            override suspend fun onNarration(text: String, lang: String) {
                var updated = false
                _uiState.update {
                    if (!isCurrentSession(callbackSessionId)) {
                        it
                    } else {
                        updated = true
                        it.copy(lastNarration = text)
                    }
                }
                if (updated) {
                    dependencies.narrator.speak(text, lang)
                }
            }

            override suspend fun onConfirmRequest(
                id: String,
                summary: String,
                lang: String,
                contactId: String?
            ) {
                var updated = false
                _uiState.update {
                    if (!isCurrentSession(callbackSessionId)) {
                        it
                    } else {
                        updated = true
                        it.copy(
                            screen = SeniorUxScreen.Confirming,
                            isRunning = true,
                            captions = summary,
                            confirmation = PendingConfirmation(id, summary, lang),
                            isCollapsed = false
                        )
                    }
                }
                if (updated) {
                    dependencies.narrator.speak(summary, lang)
                }
            }

            override suspend fun onTaskComplete(summary: String, lang: String, contactId: String?) {
                if (!claimCurrentSession(callbackSessionId)) return
                var shouldSpeak = false
                _uiState.update {
                    shouldSpeak = summary.isNotBlank() && summary != it.lastNarration
                    it.copy(
                        screen = SeniorUxScreen.Idle,
                        isRecording = false,
                        isRunning = false,
                        captions = terminalCaption(summary),
                        confirmation = null,
                        success = null,
                        failure = null,
                        actionTarget = null
                    )
                }
                closeActiveClient(client)
                if (shouldSpeak) {
                    dependencies.narrator.speak(summary, lang)
                }
            }

            override suspend fun onTaskFailure(message: String, lang: String) {
                if (!claimCurrentSession(callbackSessionId)) return
                _uiState.update {
                    it.copy(
                        screen = SeniorUxScreen.Failure,
                        isRecording = false,
                        isRunning = false,
                        captions = message,
                        confirmation = null,
                        success = null,
                        failure = FailureState(message, lang),
                        actionTarget = null
                    )
                }
                closeActiveClient(client)
                dependencies.narrator.speak(message, lang)
            }

            override fun onClosed(reason: String?) {
                moveActiveSessionToFailure(callbackSessionId, client)
            }

            override fun onFailed(message: String) {
                moveActiveSessionToFailure(callbackSessionId, client)
            }
        }

    private fun respondToConfirmation(approved: Boolean) {
        val pending = _uiState.value.confirmation ?: return
        if (approved) {
            _uiState.update {
                it.copy(
                    confirmation = null,
                    screen = SeniorUxScreen.Acting,
                    isRunning = true,
                    captions = ACTING_PROMPT,
                    actionTarget = it.actionTarget,
                    isCollapsed = true
                )
            }
            dependencies.activeClient?.send(ConfirmResponse(pending.id, approved = true))
            return
        }

        dependencies.activeClient?.send(ConfirmResponse(pending.id, approved))
        nextSessionId()
        closeActiveClient()
        _uiState.update {
            it.copy(
                confirmation = null,
                screen = SeniorUxScreen.Idle,
                isRunning = false,
                captions = READY_PROMPT,
                actionTarget = null
            )
        }
    }

    private fun onNativeSpeechText(callbackSessionId: Long, text: String) {
        if (!isCurrentSession(callbackSessionId)) return
        val goal = text.trim()
        if (goal.isBlank()) {
            moveNativeSpeechNoInputToIdle(callbackSessionId)
            return
        }
        val state = _uiState.value
        val client = try {
            dependencies.clientFactory(state.backendUrl)
        } catch (exception: RuntimeException) {
            moveLocalSessionToFailure()
            return
        }
        dependencies.activeClient = client
        _uiState.update {
            if (!isCurrentSession(callbackSessionId)) {
                it
            } else {
                it.copy(
                    screen = SeniorUxScreen.Thinking,
                    isRecording = false,
                    isRunning = true,
                    captions = THINKING_PROMPT,
                    commandText = "",
                    confirmation = null,
                    success = null,
                    failure = null,
                    actionTarget = null,
                    isCollapsed = false
                )
            }
        }
        try {
            client.startText(
                goal,
                state.lang,
                callbacks(callbackSessionId, client),
                backendSessionId = backendSessionId,
                memory = state.savedAgentMemory
            )
        } catch (exception: RuntimeException) {
            moveLocalSessionToFailure(expectedClient = client)
        }
    }

    private fun onNativeSpeechError(callbackSessionId: Long) {
        moveNativeSpeechNoInputToIdle(callbackSessionId)
    }

    private fun moveNativeSpeechNoInputToIdle(callbackSessionId: Long) {
        if (!claimCurrentSession(callbackSessionId)) return

        dependencies.speechRecognizer.cancel()
        dependencies.narrator.stop()
        closeActiveClient()
        _uiState.update {
            it.copy(
                screen = SeniorUxScreen.Idle,
                isRecording = false,
                isRunning = false,
                captions = NO_SPEECH_PROMPT,
                commandText = "",
                lastNarration = null,
                confirmation = null,
                success = null,
                failure = null,
                actionTarget = null,
                isCollapsed = false
            )
        }
    }

    private fun nextSessionId(): Long {
        return sessionId.incrementAndGet()
    }

    private fun isCurrentSession(callbackSessionId: Long): Boolean =
        callbackSessionId == sessionId.get()

    private fun claimCurrentSession(callbackSessionId: Long): Boolean =
        sessionId.compareAndSet(callbackSessionId, callbackSessionId + 1)

    private fun closeActiveClient(expectedClient: SessionSocketClient? = null) {
        val client = dependencies.activeClient
        if (expectedClient != null && client !== expectedClient) return

        dependencies.activeClient = null
        client?.close()
    }

    private fun moveActiveSessionToFailure(
        callbackSessionId: Long,
        client: SessionSocketClient
    ) {
        if (!claimCurrentSession(callbackSessionId)) return

        val safe = SAFE_FAILURE
        val lang = _uiState.value.lang
        var shouldSpeak = false
        _uiState.update {
            when (it.screen) {
                SeniorUxScreen.Failure -> it.copy(isRunning = false, actionTarget = null)

                SeniorUxScreen.Thinking,
                SeniorUxScreen.Acting,
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
                        failure = FailureState(safe, lang),
                        actionTarget = null
                    )
                }

                SeniorUxScreen.Idle -> it.copy(isRunning = false, actionTarget = null)
            }
        }
        closeActiveClient(client)
        if (shouldSpeak) {
            dependencies.narrator.speak(safe, lang)
        }
    }

    private fun moveLocalSessionToFailure(expectedClient: SessionSocketClient? = null) {
        nextSessionId()
        dependencies.recorder.cancel()
        dependencies.speechRecognizer.cancel()
        closeActiveClient(expectedClient)
        val safe = SAFE_FAILURE
        val lang = _uiState.value.lang
        _uiState.update {
            it.copy(
                screen = SeniorUxScreen.Failure,
                isRecording = false,
                isRunning = false,
                captions = safe,
                confirmation = null,
                success = null,
                failure = FailureState(safe, lang),
                actionTarget = null
            )
        }
        dependencies.narrator.speak(safe, lang)
    }

    class Dependencies(
        val recorder: AudioRecorderLike,
        val speechRecognizer: NativeSpeechRecognizerLike,
        val speechInputMode: SpeechInputMode = SpeechInputMode.BackendAudio,
        val initialAgentMemory: String = "",
        val narrator: NarratorLike,
        val clientFactory: (String) -> SessionSocketClient,
        val initialBackendUrl: String = DEFAULT_BACKEND_URL,
        val saveBackendUrl: (String) -> Unit = {},
        val saveSpeechInputMode: (SpeechInputMode) -> Unit = {},
        val saveAgentMemory: (String) -> Unit = {},
        val checkBackendConnection: (String, (BackendConnectionStatus) -> Unit) -> Unit = { _, callback ->
            callback(BackendConnectionStatus.Connected)
        },
        val setupStatus: () -> SetupStatus,
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
                    speechRecognizer = object : NativeSpeechRecognizerLike {
                        override fun start(
                            lang: String,
                            onText: (String) -> Unit,
                            onError: (String) -> Unit
                        ) = Unit

                        override fun stop() = Unit
                        override fun cancel() = Unit
                        override fun shutdown() = Unit
                    },
                    narrator = object : NarratorLike {
                        override fun speak(text: String, lang: String) = Unit
                        override fun stop() = Unit
                        override fun shutdown() = Unit
                    },
                    clientFactory = { client },
                    setupStatus = {
                        SetupStatus(
                            microphoneGranted = true,
                            callPhoneGranted = true,
                            overlayGranted = true,
                            accessibilityEnabled = true,
                            assistantSelected = true
                        )
                    },
                    dispatch = { action -> ActionResult(action.id, true) }
                )
        }
    }

    private companion object {
        const val DEFAULT_BACKEND_URL = "ws://10.0.2.2:8765"
        const val SAFE_FAILURE = "I'm having a little trouble with that. Please try again."
        const val NO_SPEECH_PROMPT = "I didn't hear anything. Please try again."
    }
}

private fun ActionTarget.Companion.from(action: Action): ActionTarget? = when (action.name) {
    "tap" -> {
        val x = action.args["x"]?.numberToInt() ?: return null
        val y = action.args["y"]?.numberToInt() ?: return null
        ActionTarget(x = x - 48, y = y - 32, width = 96, height = 64)
    }

    "swipe" -> {
        val x1 = action.args["x1"]?.numberToInt() ?: return null
        val y1 = action.args["y1"]?.numberToInt() ?: return null
        val x2 = action.args["x2"]?.numberToInt() ?: return null
        val y2 = action.args["y2"]?.numberToInt() ?: return null
        val left = minOf(x1, x2) - 24
        val top = minOf(y1, y2) - 24
        ActionTarget(
            x = left,
            y = top,
            width = abs(x2 - x1) + 48,
            height = abs(y2 - y1) + 48
        )
    }

    else -> null
}

private fun terminalCaption(summary: String): String {
    val trimmed = summary.trim()
    return if (trimmed.endsWith("?") || trimmed.endsWith("？")) trimmed else READY_PROMPT
}

private fun Any.numberToInt(): Int? = when (this) {
    is Int -> this
    is Long -> toInt()
    is Double -> toInt()
    is Float -> toInt()
    is Number -> toInt()
    else -> null
}
