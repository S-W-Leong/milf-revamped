package ai.milf.client.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class TtsNarrator(
    context: Context
) : TextToSpeech.OnInitListener {
    private val tts = TextToSpeech(context.applicationContext, this)
    private var ready = false

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            tts.language = Locale.ENGLISH
        }
    }

    fun speak(text: String, lang: String) {
        if (!ready || text.isBlank()) return

        tts.language = VoiceLocales.ttsLocaleFor(lang)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "milf-${System.nanoTime()}")
    }

    fun stop() {
        tts.stop()
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }

}
