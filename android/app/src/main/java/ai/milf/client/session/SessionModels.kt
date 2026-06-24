package ai.milf.client.session

import ai.milf.client.relationship.RelationshipContact

enum class SeniorUxScreen {
    Idle,
    Listening,
    Thinking,
    Acting,
    Confirming,
    Failure
}

enum class BackendConnectionStatus {
    Unknown,
    Checking,
    Connected,
    Disconnected,
    Failed
}

enum class AppScreen {
    Main,
    Config
}

enum class ConfigTab {
    Permissions,
    Backend,
    Agent,
    Logs
}

data class ActionTarget(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
) {
    companion object
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

data class SetupStatus(
    val microphoneGranted: Boolean = false,
    val callPhoneGranted: Boolean = false,
    val overlayGranted: Boolean = false,
    val accessibilityEnabled: Boolean = false,
    val assistantSelected: Boolean = false
)

data class SeniorUiState(
    val backendUrl: String = "ws://10.0.2.2:8765",
    val lang: String = "en",
    val screen: SeniorUxScreen = SeniorUxScreen.Idle,
    val isRecording: Boolean = false,
    val isRunning: Boolean = false,
    val captions: String = READY_PROMPT,
    val commandText: String = "",
    val lastNarration: String? = null,
    val confirmation: PendingConfirmation? = null,
    val success: SuccessState? = null,
    val failure: FailureState? = null,
    val actionTarget: ActionTarget? = null,
    val backendConnectionStatus: BackendConnectionStatus = BackendConnectionStatus.Unknown,
    val backendConnectionRequested: Boolean = true,
    val microphonePermissionGranted: Boolean = false,
    val callPhonePermissionGranted: Boolean = false,
    val overlayPermissionGranted: Boolean = false,
    val accessibilityEnabled: Boolean = false,
    val assistantSelected: Boolean = false,
    val overlayEnabled: Boolean = false,
    val isCollapsed: Boolean = false,
    val speechInputMode: SpeechInputMode = SpeechInputMode.Native,
    val appScreen: AppScreen = AppScreen.Main,
    val selectedConfigTab: ConfigTab = ConfigTab.Permissions
)

val SeniorUiState.canStartHelper: Boolean
    get() = backendUrl.isNotBlank() &&
        lang.isNotBlank() &&
        backendConnectionRequested &&
        backendConnectionStatus == BackendConnectionStatus.Connected &&
        microphonePermissionGranted &&
        callPhonePermissionGranted &&
        overlayPermissionGranted &&
        accessibilityEnabled &&
        assistantSelected

const val READY_PROMPT = "Ask MILF to do something"
const val LISTENING_PROMPT = "Listening..."
const val THINKING_PROMPT = "Thinking..."
const val ACTING_PROMPT = "Acting..."
