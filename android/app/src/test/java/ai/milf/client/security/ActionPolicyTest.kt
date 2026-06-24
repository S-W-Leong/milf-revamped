package ai.milf.client.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionPolicyTest {
    private var nowMs = 1_000L

    @Test
    fun getUiTreeIsAllowedBeforeConfirmation() {
        val policy = policy()

        val decision = policy.authorize("get_ui_tree")

        assertEquals(ActionPolicy.Decision.Allowed, decision)
    }

    @Test
    fun sensitiveActionsAreRejectedBeforeConfirmationWithoutNamingArguments() {
        val policy = policy()
        val sensitiveActions = listOf(
            "tap",
            "swipe",
            "input_text",
            "press_button",
            "start_app",
            "screenshot"
        )

        sensitiveActions.forEach { actionName ->
            val decision = policy.authorize(actionName)

            assertTrue(decision is ActionPolicy.Decision.Rejected)
            val error = (decision as ActionPolicy.Decision.Rejected).error
            assertEquals("Local confirmation is required before this action", error)
            assertFalse(error.contains(actionName))
        }
    }

    @Test
    fun approvalAllowsOneSensitiveActionWithinFreshnessThenConsumesApproval() {
        val policy = policy()

        policy.recordApproval()

        assertEquals(ActionPolicy.Decision.Allowed, policy.authorize("tap"))
        val secondDecision = policy.authorize("swipe")

        assertTrue(secondDecision is ActionPolicy.Decision.Rejected)
        assertEquals(
            "Local confirmation is required before this action",
            (secondDecision as ActionPolicy.Decision.Rejected).error
        )
    }

    @Test
    fun staleApprovalRejectsSensitiveAction() {
        val policy = policy(freshnessWindowMs = 5_000L)

        policy.recordApproval()
        nowMs += 5_001L

        val decision = policy.authorize("tap")

        assertTrue(decision is ActionPolicy.Decision.Rejected)
        assertEquals(
            "Local confirmation expired before this action",
            (decision as ActionPolicy.Decision.Rejected).error
        )
    }

    @Test
    fun denialClearsApproval() {
        val policy = policy()

        policy.recordApproval()
        policy.recordDenial()

        val decision = policy.authorize("tap")

        assertTrue(decision is ActionPolicy.Decision.Rejected)
        assertEquals(
            "Local confirmation is required before this action",
            (decision as ActionPolicy.Decision.Rejected).error
        )
    }

    private fun policy(freshnessWindowMs: Long = 5_000L): ActionPolicy =
        ActionPolicy(
            freshnessWindowMs = freshnessWindowMs,
            clock = { nowMs }
        )
}
