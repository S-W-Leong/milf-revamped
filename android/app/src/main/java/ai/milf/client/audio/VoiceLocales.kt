package ai.milf.client.audio

import java.util.Locale

object VoiceLocales {
    fun ttsLocaleFor(lang: String): Locale =
        when (lang.lowercase(Locale.ROOT)) {
            "zh" -> Locale.CHINESE
            "yue" -> Locale.TRADITIONAL_CHINESE
            else -> Locale.ENGLISH
        }

    fun recognizerLocaleTag(lang: String): String =
        when (lang.lowercase(Locale.ROOT)) {
            "zh" -> Locale.CHINESE.toLanguageTag()
            "yue" -> Locale.TRADITIONAL_CHINESE.toLanguageTag()
            else -> Locale.ENGLISH.toLanguageTag()
        }
}
