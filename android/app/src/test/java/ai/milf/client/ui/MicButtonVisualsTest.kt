package ai.milf.client.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class MicButtonVisualsTest {
    @Test
    fun micButtonUsesWhiteIconAndBorderForBothStates() {
        val listening = micButtonVisuals(isRecording = true)
        val off = micButtonVisuals(isRecording = false)

        assertEquals(MilfColors.TextPrimary, listening.iconColor)
        assertEquals(MilfColors.CardSurface, listening.containerColor)
        assertEquals(MilfColors.BorderStrong, listening.borderColor)
        assertEquals("Mic listening", listening.contentDescription)
        assertEquals(MilfColors.TextPrimary, off.iconColor)
        assertEquals(MilfColors.CardSurface, off.containerColor)
        assertEquals(MilfColors.BorderStrong, off.borderColor)
        assertEquals("Mic off", off.contentDescription)
    }
}
