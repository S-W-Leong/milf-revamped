package ai.milf.client.session

import ai.milf.client.AudioRecorderLike
import ai.milf.client.NarratorLike
import ai.milf.client.accessibility.ActionDispatcher
import ai.milf.client.accessibility.MilfAccessibilityService
import ai.milf.client.audio.AudioRecorder
import ai.milf.client.audio.TtsNarrator
import ai.milf.client.ws.MilfWebSocketClient
import android.app.Application

fun androidSessionDependencies(application: Application): MilfSessionController.Dependencies =
    MilfSessionController.Dependencies(
        recorder = AndroidAudioRecorder(AudioRecorder(application)),
        narrator = AndroidNarrator(TtsNarrator(application)),
        clientFactory = { MilfWebSocketClient(it) },
        accessibilityAvailable = { MilfAccessibilityService.instance != null },
        dispatch = { action ->
            ActionDispatcher(MilfAccessibilityService.instance).dispatch(action)
        }
    )

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
