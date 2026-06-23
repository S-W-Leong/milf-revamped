package ai.milf.client

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
    state: MainUiState,
    onBackendUrlChange: (String) -> Unit,
    onLangChange: (String) -> Unit,
    onMicPressed: () -> Unit,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
    onSpeakDecision: () -> Unit,
    onOpenAccessibility: () -> Unit
) {
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFFF8FAFC)
        ) {
            Box(Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(32.dp)
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(
                            text = "MILF",
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF111827)
                        )
                        Spacer(Modifier.height(16.dp))
                        LanguageRow(state.lang, onLangChange)
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = state.backendUrl,
                            onValueChange = onBackendUrlChange,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Backend") }
                        )
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = state.lastNarration ?: state.status,
                            fontSize = 26.sp,
                            lineHeight = 32.sp,
                            textAlign = TextAlign.Center,
                            color = Color(0xFF111827),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(28.dp))
                        Button(
                            onClick = onMicPressed,
                            enabled = !state.isRunning || state.isRecording,
                            modifier = Modifier.size(168.dp),
                            shape = CircleShape
                        ) {
                            Text(
                                text = if (state.isRecording) "Stop" else "Speak",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Column(Modifier.fillMaxWidth()) {
                        if (!state.accessibilityEnabled) {
                            OutlinedButton(
                                onClick = onOpenAccessibility,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 64.dp)
                            ) {
                                Text("Enable phone control", fontSize = 20.sp)
                            }
                        }
                    }
                }

                state.confirmation?.let { pending ->
                    ConfirmationOverlay(
                        pending = pending,
                        onApprove = onApprove,
                        onDeny = onDeny,
                        onSpeakDecision = onSpeakDecision
                    )
                }
            }
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
        listOf("en" to "English", "manglish" to "Manglish", "yue" to "Cantonese")
            .forEach { (code, label) ->
                val modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 52.dp)
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

@Composable
private fun ConfirmationOverlay(
    pending: PendingConfirmation,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
    onSpeakDecision: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC111827))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .background(Color.White, RoundedCornerShape(8.dp))
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = pending.summary,
                fontSize = 30.sp,
                lineHeight = 36.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = Color(0xFF111827)
            )
            Spacer(Modifier.height(28.dp))
            OutlinedButton(
                onClick = onSpeakDecision,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
            ) {
                Text("Speak yes/no", fontSize = 22.sp)
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDeny,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 72.dp)
                ) {
                    Text("No", fontSize = 24.sp)
                }
                Button(
                    onClick = onApprove,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 72.dp)
                ) {
                    Text("Yes", fontSize = 24.sp)
                }
            }
        }
    }
}
