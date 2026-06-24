package ai.milf.client.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

class GoalSpeechRecognizer(
    context: Context
) : RecognitionListener {
    private val recognizer = SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
    private var onText: ((String) -> Unit)? = null
    private var onError: ((String) -> Unit)? = null

    init {
        recognizer.setRecognitionListener(this)
    }

    fun start(lang: String, onText: (String) -> Unit, onError: (String) -> Unit) {
        this.onText = onText
        this.onError = onError
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, VoiceLocales.recognizerLocaleTag(lang))
        recognizer.startListening(intent)
    }

    fun stop() {
        recognizer.stopListening()
    }

    fun cancel() {
        recognizer.cancel()
    }

    fun destroy() {
        recognizer.destroy()
    }

    override fun onResults(results: Bundle) {
        val text = results
            .getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
        if (text.isNullOrBlank()) {
            onError?.invoke("No speech heard")
        } else {
            onText?.invoke(text)
        }
    }

    override fun onError(error: Int) {
        onError?.invoke("Could not hear command")
    }

    override fun onReadyForSpeech(params: Bundle?) = Unit
    override fun onBeginningOfSpeech() = Unit
    override fun onRmsChanged(rmsdB: Float) = Unit
    override fun onBufferReceived(buffer: ByteArray?) = Unit
    override fun onEndOfSpeech() = Unit
    override fun onPartialResults(partialResults: Bundle?) = Unit
    override fun onEvent(eventType: Int, params: Bundle?) = Unit
}
