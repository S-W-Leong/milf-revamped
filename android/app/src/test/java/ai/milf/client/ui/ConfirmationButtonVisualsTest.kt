package ai.milf.client.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConfirmationButtonVisualsTest {
    @Test
    fun confirmationButtonsUseCrossAndTickIconsWithoutTextLabels() {
        val deny = confirmationButtonVisuals(ConfirmationChoice.Deny)
        val approve = confirmationButtonVisuals(ConfirmationChoice.Approve)

        assertEquals(ConfirmationIcon.Cross, deny.icon)
        assertEquals("Deny", deny.contentDescription)
        assertNull(deny.textLabel)
        assertEquals(ConfirmationIcon.Tick, approve.icon)
        assertEquals("Approve", approve.contentDescription)
        assertNull(approve.textLabel)
    }
}
