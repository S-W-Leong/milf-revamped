package ai.milf.client.session

import ai.milf.client.AudioRecorderLike
import ai.milf.client.NarratorLike
import ai.milf.client.accessibility.ActionDispatcher
import ai.milf.client.accessibility.MilfAccessibilityService
import ai.milf.client.audio.AudioRecorder
import ai.milf.client.audio.GoalSpeechRecognizer
import ai.milf.client.audio.TtsNarrator
import ai.milf.client.ws.MilfWebSocketClient
import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

fun androidSessionDependencies(application: Application): MilfSessionController.Dependencies =
    MilfSessionController.Dependencies(
        recorder = AndroidAudioRecorder(AudioRecorder(application)),
        speechRecognizer = AndroidNativeSpeechRecognizer(GoalSpeechRecognizer(application)),
        speechInputMode = application.savedSpeechInputMode(),
        initialAgentMemory = application.backendPrefs()
            .getString(KEY_AGENT_MEMORY, "")
            ?: "",
        narrator = AndroidNarrator(TtsNarrator(application)),
        clientFactory = { MilfWebSocketClient(it) },
        initialBackendTarget = application.savedBackendTarget(),
        initialCustomBackendUrl = application.backendPrefs()
            .getString(KEY_BACKEND_URL, DEFAULT_CUSTOM_BACKEND_URL)
            ?: DEFAULT_CUSTOM_BACKEND_URL,
        saveBackendUrl = { url ->
            application.backendPrefs()
                .edit()
                .putString(KEY_BACKEND_URL, url)
                .apply()
        },
        saveBackendTarget = { target ->
            application.backendPrefs()
                .edit()
                .putString(KEY_BACKEND_TARGET, target.name)
                .apply()
        },
        saveSpeechInputMode = { mode ->
            application.backendPrefs()
                .edit()
                .putString(KEY_SPEECH_INPUT_MODE, mode.name)
                .apply()
        },
        saveAgentMemory = { memory ->
            application.backendPrefs()
                .edit()
                .putString(KEY_AGENT_MEMORY, memory)
                .apply()
        },
        checkBackendConnection = { url, callback ->
            WebSocketBackendChecker.check(url, callback)
        },
        setupStatus = {
            SetupStatus(
                microphoneGranted = application.hasPermission(Manifest.permission.RECORD_AUDIO),
                callPhoneGranted = application.hasPermission(Manifest.permission.CALL_PHONE),
                overlayGranted = Settings.canDrawOverlays(application),
                accessibilityEnabled = MilfAccessibilityService.instance != null,
                assistantSelected = application.isMilfAssistantSelected()
            )
        },
        dispatch = { action ->
            ActionDispatcher(MilfAccessibilityService.instance).dispatch(action)
        }
    )

private const val PREFS_NAME = "milf_setup"
private const val KEY_BACKEND_URL = "backend_url"
private const val KEY_BACKEND_TARGET = "backend_target"
private const val KEY_SPEECH_INPUT_MODE = "speech_input_mode"
private const val KEY_AGENT_MEMORY = "agent_memory"

private fun Context.backendPrefs() =
    getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

private fun Context.hasPermission(permission: String): Boolean =
    checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

private fun Context.savedSpeechInputMode(): SpeechInputMode {
    val value = backendPrefs().getString(KEY_SPEECH_INPUT_MODE, null) ?: return SpeechInputMode.Native
    return runCatching { SpeechInputMode.valueOf(value) }.getOrDefault(SpeechInputMode.Native)
}

private fun Context.savedBackendTarget(): BackendTarget {
    val value = backendPrefs().getString(KEY_BACKEND_TARGET, null) ?: return BackendTarget.Deployed
    return runCatching { BackendTarget.valueOf(value) }.getOrDefault(BackendTarget.Deployed)
}

private fun Context.isMilfAssistantSelected(): Boolean {
    val selected = Settings.Secure.getString(contentResolver, "assistant") ?: return false
    return selected.contains(packageName)
}

private object WebSocketBackendChecker {
    private val client = OkHttpClient.Builder()
        .connectTimeout(75, TimeUnit.SECONDS)
        .build()

    fun check(url: String, callback: (BackendConnectionStatus) -> Unit) {
        val called = AtomicBoolean(false)
        fun report(status: BackendConnectionStatus) {
            if (called.compareAndSet(false, true)) {
                callback(status)
            }
        }

        val request = try {
            Request.Builder().url(url).build()
        } catch (_: RuntimeException) {
            report(BackendConnectionStatus.Failed)
            return
        }

        client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.close(1000, "setup check")
                report(BackendConnectionStatus.Connected)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                report(BackendConnectionStatus.Failed)
            }
        })
    }
}

private class AndroidAudioRecorder(
    private val recorder: AudioRecorder
) : AudioRecorderLike {
    override fun start() = recorder.start()
    override fun stop(): ByteArray = recorder.stop()
    override fun cancel() = recorder.cancel()
}

private class AndroidNativeSpeechRecognizer(
    private val recognizer: GoalSpeechRecognizer
) : NativeSpeechRecognizerLike {
    override fun start(lang: String, onText: (String) -> Unit, onError: (String) -> Unit) =
        recognizer.start(lang, onText, onError)

    override fun stop() = recognizer.stop()
    override fun cancel() = recognizer.cancel()
    override fun shutdown() = recognizer.destroy()
}

private class AndroidNarrator(
    private val narrator: TtsNarrator
) : NarratorLike {
    override fun speak(text: String, lang: String) = narrator.speak(text, lang)
    override fun stop() = narrator.stop()
    override fun shutdown() = narrator.shutdown()
}
