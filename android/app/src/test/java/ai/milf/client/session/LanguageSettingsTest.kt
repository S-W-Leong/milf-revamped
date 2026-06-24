package ai.milf.client.session

import org.junit.Assert.assertEquals
import org.junit.Test

class LanguageSettingsTest {
    @Test
    fun selectableLanguagesAreEnglishChineseAndCantonese() {
        assertEquals(
            listOf(
                LanguageOption("en", "English"),
                LanguageOption("zh", "Chinese"),
                LanguageOption("yue", "Cantonese")
            ),
            selectableLanguages
        )
    }
}
