package ai.milf.client.audio

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class VoiceLocalesTest {
    @Test
    fun unsupportedLanguageFallsBackToEnglishForTts() {
        assertEquals(Locale.ENGLISH, VoiceLocales.ttsLocaleFor("unknown"))
    }

    @Test
    fun chineseUsesChineseForTtsAndRecognition() {
        assertEquals(Locale.CHINESE, VoiceLocales.ttsLocaleFor("zh"))
        assertEquals(Locale.CHINESE.toLanguageTag(), VoiceLocales.recognizerLocaleTag("zh"))
    }
}
