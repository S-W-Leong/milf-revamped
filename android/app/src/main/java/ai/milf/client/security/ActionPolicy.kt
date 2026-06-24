package ai.milf.client.security

class ActionPolicy(
    private val freshnessWindowMs: Long = DEFAULT_FRESHNESS_WINDOW_MS,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    private var approvedAtMs: Long? = null

    sealed interface Decision {
        data object Allowed : Decision
        data class Rejected(val error: String) : Decision
    }

    @Synchronized
    fun recordApproval() {
        approvedAtMs = clock()
    }

    @Synchronized
    fun recordDenial() {
        approvedAtMs = null
    }

    @Synchronized
    fun authorize(actionName: String): Decision {
        if (!requiresConfirmation(actionName)) {
            return Decision.Allowed
        }

        val approvedAt = approvedAtMs
            ?: return Decision.Rejected(ERROR_CONFIRMATION_REQUIRED)
        val approvalAgeMs = clock() - approvedAt
        if (approvalAgeMs < 0L || approvalAgeMs > freshnessWindowMs) {
            approvedAtMs = null
            return Decision.Rejected(ERROR_CONFIRMATION_EXPIRED)
        }

        approvedAtMs = null
        return Decision.Allowed
    }

    private fun requiresConfirmation(actionName: String): Boolean =
        actionName in sensitiveActions

    private companion object {
        const val DEFAULT_FRESHNESS_WINDOW_MS = 30_000L
        const val ERROR_CONFIRMATION_REQUIRED = "Local confirmation is required before this action"
        const val ERROR_CONFIRMATION_EXPIRED = "Local confirmation expired before this action"

        val sensitiveActions = setOf(
            "tap",
            "swipe",
            "input_text",
            "press_button",
            "start_app",
            "screenshot"
        )
    }
}
