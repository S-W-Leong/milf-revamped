package ai.milf.client

import ai.milf.client.session.AppScreen
import ai.milf.client.session.AgentMemorySaveStatus
import ai.milf.client.session.BackendConnectionStatus
import ai.milf.client.session.BackendTarget
import ai.milf.client.session.ConfigTab
import ai.milf.client.session.SeniorUiState
import ai.milf.client.session.SpeechInputMode
import ai.milf.client.session.canStartHelper
import ai.milf.client.session.selectableLanguages
import ai.milf.client.ui.MilfColors
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MilfUi(
    state: SeniorUiState,
    onBackendUrlChange: (String) -> Unit,
    onBackendTargetChange: (BackendTarget) -> Unit,
    onConnectBackend: () -> Unit,
    onDisconnectBackend: () -> Unit,
    onLangChange: (String) -> Unit,
    onSpeechInputModeChange: (SpeechInputMode) -> Unit,
    onAgentMemoryChange: (String) -> Unit,
    onAgentMemorySave: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenOverlayPermission: () -> Unit,
    onOpenAssistSettings: () -> Unit,
    onRequestAudioPermission: () -> Unit,
    onRequestCallPermission: () -> Unit,
    onStartOverlay: () -> Unit,
    onSetAppScreen: (AppScreen) -> Unit,
    onConfigTabChange: (ConfigTab) -> Unit
) {
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MilfColors.Obsidian
        ) {
            if (state.appScreen == AppScreen.Main) {
                MainScreen(
                    state = state,
                    onStartOverlay = onStartOverlay,
                    onConfigClick = { onSetAppScreen(AppScreen.Config) }
                )
            } else {
                ConfigScreen(
                    state = state,
                    onBackendUrlChange = onBackendUrlChange,
                    onBackendTargetChange = onBackendTargetChange,
                    onConnectBackend = onConnectBackend,
                    onDisconnectBackend = onDisconnectBackend,
                    onLangChange = onLangChange,
                    onSpeechInputModeChange = onSpeechInputModeChange,
                    onAgentMemoryChange = onAgentMemoryChange,
                    onAgentMemorySave = onAgentMemorySave,
                    onOpenAccessibility = onOpenAccessibility,
                    onOpenOverlayPermission = onOpenOverlayPermission,
                    onOpenAssistSettings = onOpenAssistSettings,
                    onRequestAudioPermission = onRequestAudioPermission,
                    onRequestCallPermission = onRequestCallPermission,
                    onSetAppScreen = onSetAppScreen,
                    onConfigTabChange = onConfigTabChange
                )
            }
        }
    }
}

@Composable
private fun MainScreen(
    state: SeniorUiState,
    onStartOverlay: () -> Unit,
    onConfigClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("MILF", color = MilfColors.TextPrimary, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Make Interfaces Less Frustrating",
                    color = MilfColors.Sage,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            OutlinedButton(
                onClick = onConfigClick,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, MilfColors.BorderStrong),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MilfColors.TextPrimary)
            ) {
                Text("Config")
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            "Ready to hold the phone's mental model.",
            color = MilfColors.TextPrimary,
            fontSize = 26.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            "Start Agent unlocks once permissions, backend, voice, calls, and language are ready.",
            color = MilfColors.TextSecondary,
            fontSize = 15.sp,
            lineHeight = 21.sp
        )

        ReadinessPanel(state)

        Button(
            onClick = onStartOverlay,
            enabled = state.canStartHelper,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MilfColors.SageDim,
                contentColor = MilfColors.Sage,
                disabledContainerColor = MilfColors.CardSurface,
                disabledContentColor = MilfColors.TextMuted
            )
        ) {
            Text("Start Agent", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ConfigScreen(
    state: SeniorUiState,
    onBackendUrlChange: (String) -> Unit,
    onBackendTargetChange: (BackendTarget) -> Unit,
    onConnectBackend: () -> Unit,
    onDisconnectBackend: () -> Unit,
    onLangChange: (String) -> Unit,
    onSpeechInputModeChange: (SpeechInputMode) -> Unit,
    onAgentMemoryChange: (String) -> Unit,
    onAgentMemorySave: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenOverlayPermission: () -> Unit,
    onOpenAssistSettings: () -> Unit,
    onRequestAudioPermission: () -> Unit,
    onRequestCallPermission: () -> Unit,
    onSetAppScreen: (AppScreen) -> Unit,
    onConfigTabChange: (ConfigTab) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Config", color = MilfColors.TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            OutlinedButton(
                onClick = { onSetAppScreen(AppScreen.Main) },
                border = BorderStroke(1.dp, MilfColors.BorderStrong),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MilfColors.TextPrimary),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Main")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ConfigTab.entries.forEach { tab ->
                val selected = state.selectedConfigTab == tab
                Button(
                    onClick = { onConfigTabChange(tab) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selected) MilfColors.SageDim else MilfColors.DarkSurface,
                        contentColor = if (selected) MilfColors.Sage else MilfColors.TextSecondary
                    ),
                    contentPadding = ButtonDefaults.TextButtonContentPadding
                ) {
                    Text(tab.name, fontSize = 12.sp, textAlign = TextAlign.Center)
                }
            }
        }

        when (state.selectedConfigTab) {
            ConfigTab.Permissions -> PermissionsTab(
                state = state,
                onRequestAudioPermission = onRequestAudioPermission,
                onRequestCallPermission = onRequestCallPermission,
                onOpenOverlayPermission = onOpenOverlayPermission,
                onOpenAccessibility = onOpenAccessibility,
                onOpenAssistSettings = onOpenAssistSettings
            )

            ConfigTab.Backend -> BackendTab(
                state = state,
                onBackendUrlChange = onBackendUrlChange,
                onBackendTargetChange = onBackendTargetChange,
                onConnectBackend = onConnectBackend,
                onDisconnectBackend = onDisconnectBackend
            )

            ConfigTab.Agent -> AgentTab(
                state = state,
                onLangChange = onLangChange,
                onSpeechInputModeChange = onSpeechInputModeChange,
                onAgentMemoryChange = onAgentMemoryChange,
                onAgentMemorySave = onAgentMemorySave
            )

            ConfigTab.Logs -> LogsTab(state)
        }
    }
}

@Composable
private fun ReadinessPanel(state: SeniorUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        state.readinessRows().forEach { row ->
            ReadinessLine(row)
        }
    }
}

@Composable
private fun PermissionsTab(
    state: SeniorUiState,
    onRequestAudioPermission: () -> Unit,
    onRequestCallPermission: () -> Unit,
    onOpenOverlayPermission: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenAssistSettings: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ConfigAction("Microphone", state.microphonePermissionGranted, onRequestAudioPermission)
        ConfigAction("Phone calls", state.callPhonePermissionGranted, onRequestCallPermission)
        ConfigAction("Overlay", state.overlayPermissionGranted, onOpenOverlayPermission)
        ConfigAction("Accessibility", state.accessibilityEnabled, onOpenAccessibility)
        ConfigAction("Assistant shortcut (optional)", state.assistantSelected, onOpenAssistSettings)
    }
}

@Composable
private fun BackendTab(
    state: SeniorUiState,
    onBackendUrlChange: (String) -> Unit,
    onBackendTargetChange: (BackendTarget) -> Unit,
    onConnectBackend: () -> Unit,
    onDisconnectBackend: () -> Unit
) {
    val shouldDisconnect = state.backendConnectionRequested &&
        state.backendConnectionStatus in setOf(
            BackendConnectionStatus.Checking,
            BackendConnectionStatus.Connected
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BackendTargetRow(
            selected = state.backendTarget,
            onChange = onBackendTargetChange
        )
        OutlinedTextField(
            value = if (state.backendTarget == BackendTarget.Custom) {
                state.customBackendUrl
            } else {
                state.backendUrl
            },
            onValueChange = onBackendUrlChange,
            enabled = state.backendTarget == BackendTarget.Custom,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Backend websocket") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MilfColors.TextPrimary,
                unfocusedTextColor = MilfColors.TextPrimary,
                disabledTextColor = MilfColors.TextSecondary,
                focusedBorderColor = MilfColors.Sage,
                unfocusedBorderColor = MilfColors.BorderStrong,
                disabledBorderColor = MilfColors.BorderStrong,
                focusedLabelColor = MilfColors.Sage,
                unfocusedLabelColor = MilfColors.TextSecondary,
                disabledLabelColor = MilfColors.TextSecondary,
                cursorColor = MilfColors.Sage
            )
        )
        Button(
            onClick = if (shouldDisconnect) onDisconnectBackend else onConnectBackend,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MilfColors.SageDim,
                contentColor = MilfColors.Sage
            )
        ) {
            Text(if (shouldDisconnect) "Disconnect" else "Connect")
        }
        ReadinessLine(
            ReadinessRow(
                "Backend",
                state.backendConnectionStatus == BackendConnectionStatus.Connected,
                readyText = "Connected",
                missingText = backendStatusText(state.backendConnectionStatus)
            )
        )
    }
}

@Composable
private fun BackendTargetRow(
    selected: BackendTarget,
    onChange: (BackendTarget) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        BackendTargetButton(
            label = "Deployed",
            selected = selected == BackendTarget.Deployed,
            onClick = { onChange(BackendTarget.Deployed) },
            modifier = Modifier.weight(1f)
        )
        BackendTargetButton(
            label = "Custom",
            selected = selected == BackendTarget.Custom,
            onClick = { onChange(BackendTarget.Custom) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun BackendTargetButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = modifier.height(44.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MilfColors.SageDim,
                contentColor = MilfColors.Sage
            )
        ) {
            Text(label)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier.height(44.dp),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, MilfColors.BorderStrong),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MilfColors.TextSecondary
            )
        ) {
            Text(label)
        }
    }
}

@Composable
private fun AgentTab(
    state: SeniorUiState,
    onLangChange: (String) -> Unit,
    onSpeechInputModeChange: (SpeechInputMode) -> Unit,
    onAgentMemoryChange: (String) -> Unit,
    onAgentMemorySave: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LanguageRow(state.lang, onLangChange)
        SpeechInputModeRow(state.speechInputMode, onSpeechInputModeChange)
        OutlinedTextField(
            value = state.agentMemory,
            onValueChange = onAgentMemoryChange,
            modifier = Modifier.fillMaxWidth(),
            minLines = 4,
            label = { Text("Memory") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MilfColors.TextPrimary,
                unfocusedTextColor = MilfColors.TextPrimary,
                focusedBorderColor = MilfColors.Sage,
                unfocusedBorderColor = MilfColors.BorderStrong,
                focusedLabelColor = MilfColors.Sage,
                unfocusedLabelColor = MilfColors.TextSecondary,
                cursorColor = MilfColors.Sage
            )
        )
        Text(
            text = when (state.agentMemorySaveStatus) {
                AgentMemorySaveStatus.Unsaved -> "Unsaved"
                AgentMemorySaveStatus.Saved -> "Saved"
                AgentMemorySaveStatus.Failed -> "Save failed"
            },
            color = when (state.agentMemorySaveStatus) {
                AgentMemorySaveStatus.Unsaved -> MilfColors.TextSecondary
                AgentMemorySaveStatus.Saved -> MilfColors.Sage
                AgentMemorySaveStatus.Failed -> MilfColors.NoRed
            },
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
        Button(
            onClick = onAgentMemorySave,
            enabled = state.agentMemorySaveStatus != AgentMemorySaveStatus.Saved,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MilfColors.SageDim,
                contentColor = MilfColors.Sage,
                disabledContainerColor = MilfColors.CardSurface,
                disabledContentColor = MilfColors.TextMuted
            )
        ) {
            Text("Save memory")
        }
    }
}

@Composable
private fun LogsTab(state: SeniorUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LogLine("Caption", state.captions)
        LogLine("Narration", state.lastNarration ?: "No narration yet")
        LogLine("Backend", state.backendConnectionStatus.name)
    }
}

@Composable
private fun ConfigAction(
    label: String,
    ready: Boolean,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MilfColors.BorderStrong),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MilfColors.TextPrimary)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, fontSize = 15.sp)
            Text(
                if (ready) "Ready" else "Missing",
                color = if (ready) MilfColors.Sage else MilfColors.NoRed,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun LanguageRow(
    selected: String,
    onLangChange: (String) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        selectableLanguages
            .forEach { option ->
                val code = option.code
                val isSelected = selected == code
                Button(
                    onClick = { onLangChange(code) },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSelected) MilfColors.SageDim else MilfColors.DarkSurface,
                        contentColor = if (isSelected) MilfColors.Sage else MilfColors.TextSecondary
                    ),
                    contentPadding = ButtonDefaults.TextButtonContentPadding
                ) {
                    Text(option.label, fontSize = 12.sp, textAlign = TextAlign.Center)
                }
            }
    }
}

@Composable
private fun SpeechInputModeRow(
    selected: SpeechInputMode,
    onSpeechInputModeChange: (SpeechInputMode) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(
            SpeechInputMode.Native to "Native",
            SpeechInputMode.BackendAudio to "Backend STT"
        ).forEach { (mode, label) ->
            val isSelected = selected == mode
            Button(
                onClick = { onSpeechInputModeChange(mode) },
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isSelected) MilfColors.SageDim else MilfColors.DarkSurface,
                    contentColor = if (isSelected) MilfColors.Sage else MilfColors.TextSecondary
                ),
                contentPadding = ButtonDefaults.TextButtonContentPadding
            ) {
                Text(label, fontSize = 12.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun ReadinessLine(row: ReadinessRow) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MilfColors.DarkSurface, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(row.label, color = MilfColors.TextPrimary, fontSize = 14.sp)
            Spacer(Modifier.width(12.dp))
            Text(
                if (row.ready) row.readyText else row.missingText,
                color = if (row.ready) MilfColors.Sage else MilfColors.NoRed,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun LogLine(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MilfColors.DarkSurface, RoundedCornerShape(10.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(label, color = MilfColors.TextSecondary, fontSize = 12.sp)
        Text(value, color = MilfColors.TextPrimary, fontSize = 14.sp, lineHeight = 19.sp)
    }
}

private data class ReadinessRow(
    val label: String,
    val ready: Boolean,
    val readyText: String = "Ready",
    val missingText: String = "Missing"
)

private fun SeniorUiState.readinessRows(): List<ReadinessRow> = listOf(
    ReadinessRow("Microphone", microphonePermissionGranted),
    ReadinessRow("Phone calls", callPhonePermissionGranted),
    ReadinessRow("Overlay", overlayPermissionGranted),
    ReadinessRow("Accessibility", accessibilityEnabled),
    ReadinessRow("Language", lang.isNotBlank(), readyText = lang.ifBlank { "Selected" }),
    ReadinessRow(
        "Backend",
        backendConnectionStatus == BackendConnectionStatus.Connected,
        readyText = "Connected",
        missingText = backendStatusText(backendConnectionStatus)
    )
)

private fun backendStatusText(status: BackendConnectionStatus): String =
    when (status) {
        BackendConnectionStatus.Unknown -> "Not checked"
        BackendConnectionStatus.Checking -> "Checking"
        BackendConnectionStatus.Connected -> "Connected"
        BackendConnectionStatus.Disconnected -> "Disconnected"
        BackendConnectionStatus.Failed -> "Failed"
    }
