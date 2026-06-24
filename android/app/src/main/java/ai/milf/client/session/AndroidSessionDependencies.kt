package ai.milf.client.session

import ai.milf.client.AudioRecorderLike
import ai.milf.client.NarratorLike
import ai.milf.client.accessibility.ActionDispatcher
import ai.milf.client.accessibility.MilfAccessibilityService
import ai.milf.client.audio.AudioRecorder
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
import java.util.concurrent.atomic.AtomicBoolean

fun androidSessionDependencies(application: Application): MilfSessionController.Dependencies =
    MilfSessionController.Dependencies(
        recorder = AndroidAudioRecorder(AudioRecorder(application)),
        narrator = AndroidNarrator(TtsNarrator(application)),
        clientFactory = { MilfWebSocketClient(it) },
        initialBackendUrl = application.backendPrefs()
            .getString(KEY_BACKEND_URL, DEFAULT_BACKEND_URL)
            ?: DEFAULT_BACKEND_URL,
        saveBackendUrl = { url ->
            application.backendPrefs()
                .edit()
                .putString(KEY_BACKEND_URL, url)
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

private const val DEFAULT_BACKEND_URL = "ws://10.0.2.2:8765"
private const val PREFS_NAME = "milf_setup"
private const val KEY_BACKEND_URL = "backend_url"

private fun Context.backendPrefs() =
    getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

private fun Context.hasPermission(permission: String): Boolean =
    checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

private fun Context.isMilfAssistantSelected(): Boolean {
    val selected = Settings.Secure.getString(contentResolver, "assistant") ?: return false
    return selected.contains(packageName)
}

private object WebSocketBackendChecker {
    private val client = OkHttpClient()

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

private class AndroidNarrator(
    private val narrator: TtsNarrator
) : NarratorLike {
    override fun speak(text: String, lang: String) = narrator.speak(text, lang)
    override fun stop() = narrator.stop()
    override fun shutdown() = narrator.shutdown()
}
