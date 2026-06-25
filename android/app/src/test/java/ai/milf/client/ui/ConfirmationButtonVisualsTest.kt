package ai.milf.client.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ConfirmationButtonVisualsTest {
    @Test
    fun confirmationButtonsUseCrossAndTickIconsWithVisibleTextLabels() {
        val deny = confirmationButtonVisuals(ConfirmationChoice.Deny)
        val approve = confirmationButtonVisuals(ConfirmationChoice.Approve)

        assertEquals(ConfirmationIcon.Cross, deny.icon)
        assertEquals("Deny", deny.contentDescription)
        assertEquals("No", deny.textLabel)
        assertEquals(ConfirmationIcon.Tick, approve.icon)
        assertEquals("Approve", approve.contentDescription)
        assertEquals("Yes", approve.textLabel)
    }
}
