package com.example.btkeyboard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.btkeyboard.model.ConnectionState
import com.example.btkeyboard.model.MouseButton
import com.example.btkeyboard.ui.components.AppCard
import com.example.btkeyboard.ui.components.SectionTitle
import com.example.btkeyboard.ui.components.shortLabel
import com.example.btkeyboard.ui.theme.UiTokens
import kotlin.math.abs

@Composable
fun TrackpadScreen(
    connectionState: ConnectionState,
    pressedButtons: Set<MouseButton>,
    sensitivity: Float,
    onMove: (Float, Float) -> Unit,
    onScrollSteps: (Int) -> Unit,
    onTap: () -> Unit,
    onButtonPressed: (MouseButton, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tapDistancePx = with(LocalDensity.current) { 8.dp.toPx() }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(UiTokens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(UiTokens.SectionSpacing),
    ) {
        item {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(UiTokens.CardPadding),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SectionTitle("Trackpad")
                        Text(
                            text = "${sensitivity}x",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = "Status: ${connectionState.shortLabel()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "1 finger: move • Tap: left click • 2 fingers: scroll",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            AppCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                var movedDistance = 0f
                                var tapCandidate = true
                                var hadMultiTouch = false
                                var scrollAccumulator = 0f
                                var gestureStartTime = 0L
                                var gestureEndTime = 0L

                                while (true) {
                                    val event = awaitPointerEvent()
                                    val pressed = event.changes.filter { it.pressed }
                                    if (pressed.isEmpty()) {
                                        break
                                    }

                                    if (gestureStartTime == 0L) {
                                        gestureStartTime = pressed.first().uptimeMillis
                                    }
                                    gestureEndTime = pressed.first().uptimeMillis

                                    if (pressed.size >= 2) {
                                        hadMultiTouch = true
                                    }

                                    if (!hadMultiTouch && pressed.size == 1) {
                                        val change = pressed.first()
                                        val delta = change.positionChange()
                                        movedDistance += delta.getDistance()
                                        if (delta != Offset.Zero) {
                                            onMove(delta.x, delta.y)
                                        }
                                    } else {
                                        tapCandidate = false
                                        val avgDy = pressed.map { it.positionChange().y }.average().toFloat()
                                        movedDistance += abs(avgDy)
                                        scrollAccumulator += avgDy
                                        while (scrollAccumulator >= SCROLL_STEP_PX) {
                                            onScrollSteps(-1)
                                            scrollAccumulator -= SCROLL_STEP_PX
                                        }
                                        while (scrollAccumulator <= -SCROLL_STEP_PX) {
                                            onScrollSteps(1)
                                            scrollAccumulator += SCROLL_STEP_PX
                                        }
                                    }
                                }

                                val durationMs = gestureEndTime - gestureStartTime
                                if (!hadMultiTouch &&
                                    tapCandidate &&
                                    movedDistance <= tapDistancePx &&
                                    durationMs in 0..TAP_DURATION_MS
                                ) {
                                    onTap()
                                }
                            }
                        },
                ) {
                    Text(
                        text = "Move cursor here",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(8.dp),
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TrackpadButton(
                    label = "Left",
                    pressed = MouseButton.LEFT in pressedButtons,
                    modifier = Modifier.weight(1f),
                    onPressed = { onButtonPressed(MouseButton.LEFT, it) },
                )
                TrackpadButton(
                    label = "Right",
                    pressed = MouseButton.RIGHT in pressedButtons,
                    modifier = Modifier.weight(1f),
                    onPressed = { onButtonPressed(MouseButton.RIGHT, it) },
                )
            }
        }
    }
}

@Composable
private fun TrackpadButton(
    label: String,
    pressed: Boolean,
    onPressed: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (pressed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val background = if (pressed) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = tween(durationMillis = 140),
        label = "trackpad-button-scale",
    )
    Box(
        modifier = modifier
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
            )
            .border(width = UiTokens.BorderWidth, color = borderColor, shape = MaterialTheme.shapes.small)
            .background(background, shape = MaterialTheme.shapes.small)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    onPressed(true)
                    waitForUpOrCancellation()
                    onPressed(false)
                }
            }
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = MaterialTheme.typography.labelLarge)
    }
}

private const val SCROLL_STEP_PX = 40f
private const val TAP_DURATION_MS = 180L
