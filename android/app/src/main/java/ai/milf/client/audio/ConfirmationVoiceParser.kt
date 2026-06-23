package ai.milf.client.audio

import java.util.Locale

object ConfirmationVoiceParser {
    private val approve = listOf(
        "yes",
        "yeah",
        "yup",
        "ya",
        "ok",
        "okay",
        "correct",
        "betul",
        "boleh",
        "can"
    )
    private val deny = listOf(
        "no",
        "nope",
        "cancel",
        "stop",
        "tak",
        "tak nak",
        "jangan",
        "bukan",
        "cannot"
    )
    private val wordPattern = Regex("[\\p{L}\\p{N}']+")

    fun parse(text: String): Boolean? {
        val words = wordPattern
            .findAll(text.lowercase(Locale.ROOT))
            .map { it.value }
            .toList()
        if (words.isEmpty()) return null
        if (deny.any { words.containsPhrase(it) }) return false
        if (approve.any { words.containsPhrase(it) }) return true
        return null
    }

    private fun List<String>.containsPhrase(phrase: String): Boolean {
        val phraseWords = phrase.split(" ")
        if (phraseWords.size > size) return false
        return windowed(phraseWords.size).any { it == phraseWords }
    }
}
