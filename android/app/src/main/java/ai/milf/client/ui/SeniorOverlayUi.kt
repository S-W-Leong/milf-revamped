package ai.milf.client.ui

import ai.milf.client.R
import ai.milf.client.session.SeniorUiState
import ai.milf.client.session.SeniorUxScreen
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

@Composable
fun SeniorOverlayUi(
    state: SeniorUiState,
    onBubbleTap: () -> Unit,
    onStopListening: () -> Unit,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
    onSpeakDecision: () -> Unit,
    onWatchModeChange: (Boolean) -> Unit,
    onRetry: () -> Unit,
    onCallBuyer: () -> Unit
) {
    MaterialTheme {
        when (state.screen) {
            SeniorUxScreen.Idle -> HelperBubble(
                contentDescription = "Start helper",
                onClick = onBubbleTap
            )
            SeniorUxScreen.Listening -> ListeningOverlay(
                captions = state.captions,
                onStopListening = onStopListening
            )
            SeniorUxScreen.Confirming -> ConfirmationGate(
                state = state,
                onApprove = onApprove,
                onDeny = onDeny,
                onSpeakDecision = onSpeakDecision
            )
            SeniorUxScreen.Working -> WorkingOverlay(
                state = state,
                onWatchModeChange = onWatchModeChange
            )
            SeniorUxScreen.Success -> SuccessOverlay(state = state)
            SeniorUxScreen.Failure -> FailureOverlay(
                state = state,
                onRetry = onRetry,
                onCallBuyer = onCallBuyer
            )
        }
    }
}

@Composable
private fun HelperBubble(contentDescription: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(MilfDimens.BubbleSize),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = MilfColors.HelperYellow)
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_helper),
            contentDescription = contentDescription,
            tint = Color.Unspecified,
            modifier = Modifier.size(44.dp)
        )
    }
}

@Composable
private fun ListeningOverlay(captions: String, onStopListening: () -> Unit) {
    FullOverlay {
        Text(
            text = "Listening",
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            color = MilfColors.Ink
        )
        Spacer(Modifier.height(24.dp))
        Waveform()
        Spacer(Modifier.height(28.dp))
        CaptionText(captions)
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onStopListening,
            modifier = Modifier
                .fillMaxWidth()
                .height(MilfDimens.PrimaryTarget),
            colors = ButtonDefaults.buttonColors(containerColor = MilfColors.NoRed)
        ) {
            Icon(Icons.Default.Close, contentDescription = null)
            Text("Stop", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ConfirmationGate(
    state: SeniorUiState,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
    onSpeakDecision: () -> Unit
) {
    val pending = state.confirmation ?: return
    FullOverlay(scrim = true) {
        Surface(
            shape = RoundedCornerShape(MilfDimens.Corner),
            color = MilfColors.Paper,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                pending.contact?.let { contact ->
                    Image(
                        painter = painterResource(contact.photoResId),
                        contentDescription = contact.displayName,
                        modifier = Modifier
                            .size(144.dp)
                            .clip(CircleShape)
                    )
                    Spacer(Modifier.height(18.dp))
                }
                Text(
                    text = pending.summary,
                    fontSize = 34.sp,
                    lineHeight = 40.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MilfColors.Ink
                )
                Spacer(Modifier.height(24.dp))
                OutlinedButton(
                    onClick = onSpeakDecision,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(MilfDimens.PrimaryTarget)
                ) {
                    Icon(Icons.Default.Mic, contentDescription = null)
                    Text("Speak yes or no", fontSize = 24.sp)
                }
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onDeny,
                        modifier = Modifier
                            .weight(1f)
                            .height(88.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MilfColors.NoRed)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null)
                        Text("NO", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = onApprove,
                        modifier = Modifier
                            .weight(1f)
                            .height(88.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MilfColors.YesGreen)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Text("YES", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkingOverlay(state: SeniorUiState, onWatchModeChange: (Boolean) -> Unit) {
    if (!state.watchMode && !state.demoMode) {
        Box(
            modifier = Modifier.size(MilfDimens.BubbleSize),
            contentAlignment = Alignment.Center
        ) {
            HelperBubble(
                contentDescription = "Show progress",
                onClick = { onWatchModeChange(true) }
            )
        }
        return
    }

    FullOverlay {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Working",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MilfColors.Ink
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Visibility,
                    contentDescription = null,
                    tint = MilfColors.Ink
                )
                Switch(
                    checked = state.watchMode || state.demoMode,
                    onCheckedChange = onWatchModeChange
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Waveform()
        Spacer(Modifier.height(24.dp))
        CaptionText(state.captions)
    }
}

@Composable
private fun SuccessOverlay(state: SeniorUiState) {
    FullOverlay {
        Icon(
            Icons.Default.Check,
            contentDescription = null,
            tint = MilfColors.YesGreen,
            modifier = Modifier.size(104.dp)
        )
        Spacer(Modifier.height(24.dp))
        CaptionText(state.success?.summary ?: "Done.")
    }
}

@Composable
private fun FailureOverlay(state: SeniorUiState, onRetry: () -> Unit, onCallBuyer: () -> Unit) {
    FullOverlay {
        CaptionText(
            state.failure?.message
                ?: "I'm having a little trouble doing that. Want me to call your daughter to help?"
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onCallBuyer,
            modifier = Modifier
                .fillMaxWidth()
                .height(MilfDimens.PrimaryTarget),
            colors = ButtonDefaults.buttonColors(containerColor = MilfColors.YesGreen)
        ) {
            Icon(Icons.Default.Call, contentDescription = null)
            Text("YES, call daughter", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onRetry,
            modifier = Modifier
                .fillMaxWidth()
                .height(MilfDimens.PrimaryTarget)
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Text("Try again", fontSize = 24.sp)
        }
    }
}

@Composable
private fun FullOverlay(scrim: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (scrim) MilfColors.Scrim else MilfColors.Paper)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            content = content
        )
    }
}

@Composable
private fun CaptionText(text: String) {
    Text(
        text = text,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        textAlign = TextAlign.Center,
        color = MilfColors.Ink,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun Waveform() {
    val transition = rememberInfiniteTransition(label = "wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "phase"
    )

    Canvas(modifier = Modifier.size(width = 240.dp, height = 96.dp)) {
        val bars = 7
        val gap = size.width / (bars * 2)
        repeat(bars) { index ->
            val normalized = (index + 1) / bars.toFloat()
            val height = size.height * (0.25f + 0.65f * abs(phase - normalized))
            val x = gap + index * gap * 2
            drawLine(
                color = MilfColors.QuietBlue,
                start = Offset(x, (size.height - height) / 2),
                end = Offset(x, (size.height + height) / 2),
                strokeWidth = 10.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}
