package ai.milf.client

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertTrue
import org.junit.Test

class DisclosureCopyTest {
    @Test
    fun audioDisclosureNamesRecordingScope() {
        val copy = stringValue("audio_recording_disclosure").lowercase()

        assertTrue(copy.contains("record"))
        assertTrue(copy.contains("one request"))
    }

    @Test
    fun accessibilityDisclosureNamesScreenReadingAndConfirmation() {
        val copy = stringValue("accessibility_control_disclosure").lowercase()

        assertTrue(copy.contains("reads supported app screens"))
        assertTrue(copy.contains("after confirmation"))
    }

    @Test
    fun accessibilityConsentNamesGesturesScreenshotsAndChoice() {
        val copy = stringValue("accessibility_consent_disclosure").lowercase()

        assertTrue(copy.contains("accessibility"))
        assertTrue(copy.contains("gestures"))
        assertTrue(copy.contains("screenshots"))
        assertTrue(copy.contains("do not enable"))
    }

    @Test
    fun accessibilityConsentControlsAreResourceBacked() {
        assertTrue(stringValue("enable_phone_control").isNotBlank())
        assertTrue(stringValue("accessibility_consent_title").isNotBlank())
        assertTrue(stringValue("accessibility_consent_accept").isNotBlank())
        assertTrue(stringValue("accessibility_consent_dismiss").isNotBlank())
    }

    @Test
    fun confirmationDisclosureNamesOneSensitiveAction() {
        val copy = stringValue("confirmation_disclosure").lowercase()

        assertTrue(copy.contains("one sensitive phone action"))
    }

    private fun stringValue(name: String): String {
        val file = listOf(
            File("src/main/res/values/strings.xml"),
            File("app/src/main/res/values/strings.xml")
        ).first { it.exists() }
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val strings = document.getElementsByTagName("string")
        for (index in 0 until strings.length) {
            val node = strings.item(index)
            if (node.attributes.getNamedItem("name").nodeValue == name) {
                return node.textContent
            }
        }
        error("Missing string resource: $name")
    }
}
