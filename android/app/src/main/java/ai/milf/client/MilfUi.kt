package ai.milf.client

import ai.milf.client.session.SeniorUiState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MilfUi(
    state: SeniorUiState,
    onBackendUrlChange: (String) -> Unit,
    onLangChange: (String) -> Unit,
    onDemoModeChange: (Boolean) -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenOverlayPermission: () -> Unit,
    onOpenAssistSettings: () -> Unit,
    onRequestAudioPermission: () -> Unit,
    onRequestCallPermission: () -> Unit,
    onStartOverlay: () -> Unit,
    onStopOverlay: () -> Unit
) {
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFFF8FAFC)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Text(
                    text = "MILF buyer setup",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF111827)
                )

                OutlinedTextField(
                    value = state.backendUrl,
                    onValueChange = onBackendUrlChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Backend websocket") }
                )

                LanguageRow(state.lang, onLangChange)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Demo watch mode",
                        fontSize = 18.sp,
                        color = Color(0xFF111827)
                    )
                    Switch(
                        checked = state.demoMode,
                        onCheckedChange = onDemoModeChange
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SetupButton("Microphone permission", onRequestAudioPermission)
                    SetupButton("Phone call permission", onRequestCallPermission)
                    SetupButton("Accessibility Service", onOpenAccessibility)
                    SetupButton("Display over other apps", onOpenOverlayPermission)
                    SetupButton("Default assistant app", onOpenAssistSettings)
                }

                Spacer(Modifier.height(4.dp))

                Button(
                    onClick = onStartOverlay,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                ) {
                    Text("Start helper", fontSize = 20.sp)
                }

                OutlinedButton(
                    onClick = onStopOverlay,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                ) {
                    Text("Stop helper", fontSize = 18.sp)
                }
            }
        }
    }
}

@Composable
private fun SetupButton(
    label: String,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
    ) {
        Text(label, fontSize = 18.sp)
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
        listOf("en" to "English", "manglish" to "Manglish", "yue" to "Cantonese")
            .forEach { (code, label) ->
                val modifier = Modifier
                    .weight(1f)
                    .height(60.dp)
                if (selected == code) {
                    Button(
                        onClick = { onLangChange(code) },
                        modifier = modifier
                    ) {
                        Text(label, textAlign = TextAlign.Center)
                    }
                } else {
                    OutlinedButton(
                        onClick = { onLangChange(code) },
                        modifier = modifier
                    ) {
                        Text(label, textAlign = TextAlign.Center)
                    }
                }
            }
    }
}
