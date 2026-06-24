package ai.milf.client.ui

import ai.milf.client.R
import ai.milf.client.session.ACTING_PROMPT
import ai.milf.client.session.ActionTarget
import ai.milf.client.session.LISTENING_PROMPT
import ai.milf.client.session.READY_PROMPT
import ai.milf.client.session.SeniorUiState
import ai.milf.client.session.SeniorUxScreen
import ai.milf.client.session.THINKING_PROMPT
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.abs

@Composable
fun SeniorOverlayUi(
    state: SeniorUiState,
    onMicTap: () -> Unit,
    onCommandTextChange: (String) -> Unit,
    onSubmitText: () -> Unit,
    onRunStop: () -> Unit,
    onExitAgent: () -> Unit,
    onCollapseOverlay: () -> Unit,
    onExpandOverlay: () -> Unit,
    onBubbleDrag: (Float, Float) -> Unit,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
    onTransientMessageShown: () -> Unit
) {
    MaterialTheme {
        if (state.isCollapsed) {
            CollapsedBubble(
                onExpandOverlay = onExpandOverlay,
                onBubbleDrag = onBubbleDrag
            )
        } else {
            ExpandedOverlayShell(
                state = state,
                onCollapseOverlay = onCollapseOverlay,
                onMicTap = onMicTap,
                onCommandTextChange = onCommandTextChange,
                onSubmitText = onSubmitText,
                onRunStop = onRunStop,
                onExitAgent = onExitAgent,
                onApprove = onApprove,
                onDeny = onDeny,
                onTransientMessageShown = onTransientMessageShown
            )
        }
    }
}

@Composable
private fun CollapsedBubble(
    onExpandOverlay: () -> Unit,
    onBubbleDrag: (Float, Float) -> Unit
) {
    Button(
        onClick = onExpandOverlay,
        modifier = Modifier
            .size(MilfDimens.BubbleSize)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onBubbleDrag(dragAmount.x, dragAmount.y)
                }
            },
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = MilfColors.DarkSurface),
        border = BorderStroke(1.dp, MilfColors.Border)
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_helper),
            contentDescription = "Expand MILF",
            tint = Color.Unspecified,
            modifier = Modifier.size(34.dp)
        )
    }
}

@Composable
private fun ExpandedOverlayShell(
    state: SeniorUiState,
    onCollapseOverlay: () -> Unit,
    onMicTap: () -> Unit,
    onCommandTextChange: (String) -> Unit,
    onSubmitText: () -> Unit,
    onRunStop: () -> Unit,
    onExitAgent: () -> Unit,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
    onTransientMessageShown: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        ControlRail(
            state = state,
            onMicTap = onMicTap,
            onCommandTextChange = onCommandTextChange,
            onSubmitText = onSubmitText,
            onRunStop = onRunStop,
            onExitAgent = onExitAgent,
            onCollapseOverlay = onCollapseOverlay,
            onApprove = onApprove,
            onDeny = onDeny,
            onTransientMessageShown = onTransientMessageShown,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = {}
                )
        )
    }
}

@Composable
private fun ControlRail(
    state: SeniorUiState,
    onMicTap: () -> Unit,
    onCommandTextChange: (String) -> Unit,
    onSubmitText: () -> Unit,
    onRunStop: () -> Unit,
    onExitAgent: () -> Unit,
    onCollapseOverlay: () -> Unit,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
    onTransientMessageShown: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (state.screen == SeniorUxScreen.Failure) {
        LaunchedEffect(state.failure?.message) {
            delay(2400)
            onTransientMessageShown()
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        shape = RoundedCornerShape(MilfDimens.RailCorner),
        color = MilfColors.DarkSurface,
        border = BorderStroke(1.dp, MilfColors.Border),
        shadowElevation = 18.dp
    ) {
        Row(
            modifier = Modifier.padding(9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            if (state.screen == SeniorUxScreen.Confirming) {
                CompactConfirmationContent(state, Modifier.weight(1f))
                CompactConfirmationButton("No", onDeny, MilfColors.NoRed, Color.White)
                CompactConfirmationButton("Yes", onApprove, MilfColors.SageDim, MilfColors.Sage)
                CollapseOverlayButton(onCollapseOverlay)
            } else {
                RailCenterContent(
                    state = state,
                    onCommandTextChange = onCommandTextChange,
                    modifier = Modifier.weight(1f)
                )
                RailPrimaryAction(
                    state = state,
                    onMicTap = onMicTap,
                    onSubmitText = onSubmitText,
                    onRunStop = onRunStop
                )
                CollapseOverlayButton(onCollapseOverlay)
                ExitAgentButton(onExitAgent)
            }
        }
    }
}

@Composable
private fun CompactConfirmationContent(
    state: SeniorUiState,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxHeight(),
        shape = RoundedCornerShape(14.dp),
        color = MilfColors.CardSurface,
        border = BorderStroke(1.dp, MilfColors.Border)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                state.confirmation?.summary ?: "Confirm action?",
                color = MilfColors.TextPrimary,
                fontSize = 13.sp,
                lineHeight = 16.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RailCenterContent(
    state: SeniorUiState,
    onCommandTextChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxHeight(),
        shape = RoundedCornerShape(14.dp),
        color = MilfColors.CardSurface,
        border = BorderStroke(1.dp, MilfColors.Border)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            when (state.screen) {
                SeniorUxScreen.Idle -> BasicTextField(
                    value = state.commandText,
                    onValueChange = onCommandTextChange,
                    modifier = Modifier.fillMaxSize(),
                    singleLine = true,
                    textStyle = TextStyle(color = MilfColors.TextPrimary, fontSize = 14.sp),
                    decorationBox = { inner ->
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (state.commandText.isBlank()) {
                                Text(READY_PROMPT, color = MilfColors.TextSecondary, fontSize = 14.sp)
                            }
                            inner()
                        }
                    }
                )

                SeniorUxScreen.Listening -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MiniWaveform()
                    Text(LISTENING_PROMPT, color = MilfColors.TextPrimary, fontSize = 14.sp)
                }

                SeniorUxScreen.Thinking -> Text(THINKING_PROMPT, color = MilfColors.TextPrimary, fontSize = 14.sp)
                SeniorUxScreen.Acting -> Text(ACTING_PROMPT, color = MilfColors.TextPrimary, fontSize = 14.sp)
                SeniorUxScreen.Confirming -> Text("Waiting for confirmation", color = MilfColors.TextPrimary, fontSize = 14.sp)
                SeniorUxScreen.Failure -> Text(
                    state.failure?.message ?: state.captions,
                    color = MilfColors.TextPrimary,
                    fontSize = 13.sp,
                    lineHeight = 17.sp
                )
            }
        }
    }
}

@Composable
private fun RailPrimaryAction(
    state: SeniorUiState,
    onMicTap: () -> Unit,
    onSubmitText: () -> Unit,
    onRunStop: () -> Unit
) {
    when {
        state.screen == SeniorUxScreen.Thinking || state.screen == SeniorUxScreen.Acting -> RunStopButton(onRunStop)
        state.screen == SeniorUxScreen.Idle && state.commandText.isNotBlank() -> SendButton(onSubmitText)
        else -> MicButton(onMicTap)
    }
}

@Composable
private fun MicButton(onMicTap: () -> Unit) {
    IconButton(
        onClick = onMicTap,
        modifier = Modifier
            .size(42.dp)
            .background(MilfColors.SageDim, CircleShape)
    ) {
        Icon(Icons.Default.Mic, contentDescription = "Listen", tint = MilfColors.Sage)
    }
}

@Composable
private fun SendButton(onSubmitText: () -> Unit) {
    IconButton(
        onClick = onSubmitText,
        modifier = Modifier
            .size(42.dp)
            .background(MilfColors.SageDim, CircleShape)
    ) {
        Icon(Icons.Default.Send, contentDescription = "Send", tint = MilfColors.Sage)
    }
}

@Composable
private fun RunStopButton(onRunStop: () -> Unit) {
    IconButton(
        onClick = onRunStop,
        modifier = Modifier
            .size(42.dp)
            .background(MilfColors.RunStopWhite, CircleShape)
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .background(Color.Black, RoundedCornerShape(2.dp))
        )
    }
}

@Composable
private fun CompactConfirmationButton(
    label: String,
    onClick: () -> Unit,
    containerColor: Color,
    contentColor: Color
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .width(54.dp)
            .height(42.dp),
        shape = RoundedCornerShape(13.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        contentPadding = ButtonDefaults.ContentPadding
    ) {
        Text(label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}

@Composable
private fun CollapseOverlayButton(onCollapseOverlay: () -> Unit) {
    IconButton(
        onClick = onCollapseOverlay,
        modifier = Modifier
            .size(42.dp)
            .background(MilfColors.CardSurface, CircleShape)
            .border(1.dp, MilfColors.BorderStrong, CircleShape)
    ) {
        Icon(
            Icons.Default.KeyboardArrowDown,
            contentDescription = "Collapse MILF",
            tint = MilfColors.TextPrimary
        )
    }
}

@Composable
private fun ExitAgentButton(onExitAgent: () -> Unit) {
    IconButton(
        onClick = onExitAgent,
        modifier = Modifier
            .size(42.dp)
            .background(MilfColors.CardSurface, CircleShape)
            .border(1.dp, MilfColors.BorderStrong, CircleShape)
    ) {
        Icon(Icons.Default.Close, contentDescription = "Exit MILF", tint = MilfColors.TextPrimary)
    }
}

@Composable
private fun NarrationReplyPill(
    text: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = MilfDimens.RailMaxWidth)
            .padding(horizontal = MilfDimens.RailHorizontalMargin)
            .height(MilfDimens.ReplyPillHeight),
        shape = RoundedCornerShape(MilfDimens.ReplyPillCorner),
        color = MilfColors.DarkSurface,
        border = BorderStroke(1.dp, MilfColors.Border),
        shadowElevation = 12.dp
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text,
                color = MilfColors.TextPrimary,
                fontSize = 13.sp,
                lineHeight = 17.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ConfirmationCard(
    state: SeniorUiState,
    onApprove: () -> Unit,
    onDeny: () -> Unit,
    modifier: Modifier = Modifier
) {
    val confirmation = state.confirmation ?: return
    Surface(
        modifier = modifier
            .widthIn(max = 360.dp)
            .padding(horizontal = 24.dp),
        shape = RoundedCornerShape(MilfDimens.CardCorner),
        color = MilfColors.DarkSurface,
        border = BorderStroke(1.dp, MilfColors.Border),
        shadowElevation = 18.dp
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                confirmation.summary,
                color = MilfColors.TextPrimary,
                fontSize = 17.sp,
                lineHeight = 22.sp,
                textAlign = TextAlign.Center
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onDeny,
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                    shape = RoundedCornerShape(13.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MilfColors.NoRed)
                ) {
                    Text("No", fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = onApprove,
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                    shape = RoundedCornerShape(13.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MilfColors.SageDim,
                        contentColor = MilfColors.Sage
                    )
                ) {
                    Text("Yes", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun ActionTargetBox(target: ActionTarget) {
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .offset { IntOffset(target.x, target.y) }
            .width(with(density) { target.width.toDp() })
            .height(with(density) { target.height.toDp() })
            .border(3.dp, MilfColors.ElectricCyan, RoundedCornerShape(8.dp))
            .background(MilfColors.ElectricCyan.copy(alpha = 0.10f), RoundedCornerShape(8.dp))
    )
}

@Composable
private fun MiniWaveform() {
    val transition = rememberInfiniteTransition(label = "mini-wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(720), RepeatMode.Reverse),
        label = "phase"
    )

    Canvas(modifier = Modifier.size(width = 34.dp, height = 18.dp)) {
        val bars = 4
        val gap = size.width / (bars * 2)
        repeat(bars) { index ->
            val normalized = (index + 1) / bars.toFloat()
            val height = size.height * (0.35f + 0.55f * abs(phase - normalized))
            val x = gap + index * gap * 2
            drawLine(
                color = MilfColors.Sage,
                start = Offset(x, (size.height - height) / 2),
                end = Offset(x, (size.height + height) / 2),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

private val SeniorUiState.shouldShowNarrationReply: Boolean
    get() = !lastNarration.isNullOrBlank() &&
        screen != SeniorUxScreen.Idle &&
        screen != SeniorUxScreen.Failure &&
        screen != SeniorUxScreen.Confirming
