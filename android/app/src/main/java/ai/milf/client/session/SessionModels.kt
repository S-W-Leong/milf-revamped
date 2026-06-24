package ai.milf.client.session

import ai.milf.client.relationship.RelationshipContact

enum class SeniorUxScreen {
    Idle,
    Listening,
    Confirming,
    Working,
    Success,
    Failure
}

data class PendingConfirmation(
    val id: String,
    val summary: String,
    val lang: String,
    val contact: RelationshipContact?
)

data class SuccessState(
    val summary: String,
    val lang: String,
    val contact: RelationshipContact?
)

data class FailureState(
    val message: String,
    val lang: String,
    val recoveryContact: RelationshipContact?
)

data class SeniorUiState(
    val backendUrl: String = "ws://10.0.2.2:8765",
    val lang: String = "en",
    val screen: SeniorUxScreen = SeniorUxScreen.Idle,
    val isRecording: Boolean = false,
    val isRunning: Boolean = false,
    val captions: String = "Ready",
    val lastNarration: String? = null,
    val confirmation: PendingConfirmation? = null,
    val success: SuccessState? = null,
    val failure: FailureState? = null,
    val accessibilityEnabled: Boolean = false,
    val overlayEnabled: Boolean = false,
    val watchMode: Boolean = false,
    val demoMode: Boolean = false
)
