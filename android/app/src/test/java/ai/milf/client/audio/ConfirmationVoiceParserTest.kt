package ai.milf.client.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConfirmationVoiceParserTest {
    @Test
    fun parsesApprovalPhrases() {
        assertEquals(true, ConfirmationVoiceParser.parse("yes"))
        assertEquals(true, ConfirmationVoiceParser.parse("betul"))
        assertEquals(true, ConfirmationVoiceParser.parse("okay call him"))
    }

    @Test
    fun parsesDenialPhrases() {
        assertEquals(false, ConfirmationVoiceParser.parse("no"))
        assertEquals(false, ConfirmationVoiceParser.parse("tak nak"))
        assertEquals(false, ConfirmationVoiceParser.parse("cancel"))
    }

    @Test
    fun ignoresUnclearSpeech() {
        assertNull(ConfirmationVoiceParser.parse("maybe later"))
    }

    @Test
    fun ignoresEmbeddedConfirmationSyllables() {
        assertNull(ConfirmationVoiceParser.parse("candle"))
        assertNull(ConfirmationVoiceParser.parse("noise"))
    }
}
