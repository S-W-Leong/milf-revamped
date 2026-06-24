package ai.milf.client.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MicButtonVisualsTest {
    @Test
    fun micButtonUsesSageWhileListeningAndGreyWhileOff() {
        val listening = micButtonVisuals(isRecording = true)
        val off = micButtonVisuals(isRecording = false)

        assertEquals(MilfColors.Sage, listening.iconColor)
        assertEquals(MilfColors.SageDim, listening.containerColor)
        assertEquals("Mic listening", listening.contentDescription)
        assertEquals(MilfColors.MicOffGrey, off.iconColor)
        assertEquals(MilfColors.MicOffGreyDim, off.containerColor)
        assertEquals("Mic off", off.contentDescription)
        assertNotEquals(listening.iconColor, off.iconColor)
        assertNotEquals(listening.containerColor, off.containerColor)
    }
}
