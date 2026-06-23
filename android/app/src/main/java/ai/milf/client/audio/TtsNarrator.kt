package ai.milf.client.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class TtsNarrator(
    context: Context
) : TextToSpeech.OnInitListener {
    private val tts = TextToSpeech(context.applicationContext, this)
    private var initialized = false
    private var ready = false
    private var onFailure: (String) -> Unit = {}

    fun setFailureCallback(onFailure: (String) -> Unit) {
        this.onFailure = onFailure
    }

    override fun onInit(status: Int) {
        initialized = true
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            runCatching { tts.language = Locale.ENGLISH }
                .onFailure {
                    ready = false
                    onFailure("Audio playback unavailable")
                }
        } else {
            onFailure("Audio playback unavailable")
        }
    }

    fun speak(text: String, lang: String) {
        if (text.isBlank()) return
        if (!ready) {
            if (initialized) {
                onFailure("Audio playback unavailable")
                error("Audio playback unavailable")
            }
            return
        }

        runCatching {
            tts.language = localeFor(lang)
            val result = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "milf-${System.nanoTime()}")
            check(result != TextToSpeech.ERROR) { "Could not play audio" }
        }.getOrElse { error ->
            onFailure("Could not play audio")
            throw error
        }
    }

    fun stop() {
        runCatching {
            check(tts.stop() != TextToSpeech.ERROR) { "Could not stop audio" }
        }.getOrElse { error ->
            onFailure("Could not stop audio")
            throw error
        }
    }

    fun shutdown() {
        runCatching {
            tts.stop()
            tts.shutdown()
        }.getOrElse { error ->
            onFailure("Could not stop audio")
            throw error
        }
    }

    private fun localeFor(lang: String): Locale =
        when (lang.lowercase(Locale.ROOT)) {
            "yue" -> Locale.CHINESE
            "manglish", "ms" -> Locale("ms", "MY")
            else -> Locale.ENGLISH
        }
}
