package com.example.btkeyboard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
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
    onVerticalScrollSteps: (Int) -> Unit,
    onHorizontalScrollSteps: (Int) -> Unit,
    onTap: () -> Unit,
    onDoubleTap: () -> Unit,
    onTwoFingerTap: () -> Unit,
    onPinchZoom: (Boolean) -> Unit,
    onThreeFingerSwipeUp: () -> Unit,
    onThreeFingerSwipeDown: () -> Unit,
    onThreeFingerSwipeLeft: () -> Unit,
    onThreeFingerSwipeRight: () -> Unit,
    onThreeFingerTap: () -> Unit,
    onButtonPressed: (MouseButton, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tapDistancePx = with(LocalDensity.current) { 8.dp.toPx() }
    var lastSingleTapUptime by remember { mutableLongStateOf(0L) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(UiTokens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(UiTokens.SectionSpacing),
    ) {
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(UiTokens.CardPadding),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SectionTitle("Touchpad")
                Text(
                    text = "Status: ${connectionState.shortLabel()} • Sensitivity: ${sensitivity}x",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "1 finger: move, tap, double-tap, hold+drag",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "2 fingers: right-click tap, vertical/horizontal scroll, pinch zoom, hold+drag",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "3 fingers: swipe up/down/left/right app shortcuts, tap lookup",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (MouseButton.LEFT in pressedButtons) {
                    Text(
                        text = "Drag active",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        AppCard(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(UiTokens.BorderWidth, MaterialTheme.colorScheme.outline)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            var maxPointers = 0
                            var gestureStartTime = 0L
                            var gestureEndTime = 0L
                            var totalMovement = 0f

                            var singleTapCandidate = true
                            var twoFingerTapCandidate = true
                            var oneFingerDragActive = false
                            var twoFingerDragActive = false

                            var twoFingerStartTime = 0L
                            var pinchReferenceDistance: Float? = null
                            var pinchTriggered = false
                            var scrollAccumulatorX = 0f
                            var scrollAccumulatorY = 0f

                            var threeFingerTotalDx = 0f
                            var threeFingerTotalDy = 0f

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
                                maxPointers = maxOf(maxPointers, pressed.size)

                                when {
                                    pressed.size >= 3 -> {
                                        singleTapCandidate = false
                                        twoFingerTapCandidate = false
                                        val avgDx = pressed.map { it.positionChange().x }.average().toFloat()
                                        val avgDy = pressed.map { it.positionChange().y }.average().toFloat()
                                        threeFingerTotalDx += avgDx
                                        threeFingerTotalDy += avgDy
                                        totalMovement += abs(avgDx) + abs(avgDy)
                                    }

                                    pressed.size == 2 -> {
                                        singleTapCandidate = false
                                        if (twoFingerStartTime == 0L) {
                                            twoFingerStartTime = gestureEndTime
                                        }

                                        val avgDx = pressed.map { it.positionChange().x }.average().toFloat()
                                        val avgDy = pressed.map { it.positionChange().y }.average().toFloat()
                                        totalMovement += abs(avgDx) + abs(avgDy)

                                        val distance = (pressed[0].position - pressed[1].position).getDistance()
                                        if (pinchReferenceDistance == null) {
                                            pinchReferenceDistance = distance
                                        } else {
                                            val reference = pinchReferenceDistance
                                            val scale = if (reference == 0f) 1f else distance / reference
                                            if (scale >= PINCH_OUT_SCALE_THRESHOLD) {
                                                onPinchZoom(true)
                                                pinchTriggered = true
                                                twoFingerTapCandidate = false
                                                pinchReferenceDistance = distance
                                            } else if (scale <= PINCH_IN_SCALE_THRESHOLD) {
                                                onPinchZoom(false)
                                                pinchTriggered = true
                                                twoFingerTapCandidate = false
                                                pinchReferenceDistance = distance
                                            }
                                        }

                                        if (!pinchTriggered) {
                                            val holdMs = gestureEndTime - twoFingerStartTime
                                            if (!twoFingerDragActive &&
                                                holdMs >= TWO_FINGER_DRAG_HOLD_MS &&
                                                totalMovement <= TWO_FINGER_DRAG_ACTIVATE_MOVE_PX
                                            ) {
                                                onButtonPressed(MouseButton.LEFT, true)
                                                twoFingerDragActive = true
                                                twoFingerTapCandidate = false
                                                scrollAccumulatorX = 0f
                                                scrollAccumulatorY = 0f
                                            }

                                            if (twoFingerDragActive) {
                                                if (avgDx != 0f || avgDy != 0f) {
                                                    onMove(avgDx, avgDy)
                                                }
                                            } else {
                                                if (abs(avgDx) > abs(avgDy)) {
                                                    scrollAccumulatorX += avgDx
                                                    while (scrollAccumulatorX >= SCROLL_STEP_PX) {
                                                        onHorizontalScrollSteps(-1)
                                                        scrollAccumulatorX -= SCROLL_STEP_PX
                                                    }
                                                    while (scrollAccumulatorX <= -SCROLL_STEP_PX) {
                                                        onHorizontalScrollSteps(1)
                                                        scrollAccumulatorX += SCROLL_STEP_PX
                                                    }
                                                } else {
                                                    scrollAccumulatorY += avgDy
                                                    while (scrollAccumulatorY >= SCROLL_STEP_PX) {
                                                        onVerticalScrollSteps(-1)
                                                        scrollAccumulatorY -= SCROLL_STEP_PX
                                                    }
                                                    while (scrollAccumulatorY <= -SCROLL_STEP_PX) {
                                                        onVerticalScrollSteps(1)
                                                        scrollAccumulatorY += SCROLL_STEP_PX
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    pressed.size == 1 -> {
                                        val change = pressed.first()
                                        val delta = change.positionChange()
                                        if (delta != Offset.Zero) {
                                            totalMovement += delta.getDistance()
                                            onMove(delta.x, delta.y)
                                        }

                                        val heldMs = gestureEndTime - gestureStartTime
                                        if (!oneFingerDragActive &&
                                            maxPointers == 1 &&
                                            heldMs >= LONG_PRESS_DRAG_MS &&
                                            totalMovement >= LONG_PRESS_DRAG_MOVE_THRESHOLD_PX
                                        ) {
                                            onButtonPressed(MouseButton.LEFT, true)
                                            oneFingerDragActive = true
                                            singleTapCandidate = false
                                        }

                                        if (maxPointers > 1) {
                                            singleTapCandidate = false
                                        }
                                    }
                                }
                            }

                            if (oneFingerDragActive) {
                                onButtonPressed(MouseButton.LEFT, false)
                            }
                            if (twoFingerDragActive) {
                                onButtonPressed(MouseButton.LEFT, false)
                            }

                            val durationMs = gestureEndTime - gestureStartTime
                            when {
                                maxPointers >= 3 -> {
                                    if (durationMs <= TAP_DURATION_MS && totalMovement <= THREE_FINGER_TAP_MAX_MOVE_PX) {
                                        onThreeFingerTap()
                                    } else {
                                        val absX = abs(threeFingerTotalDx)
                                        val absY = abs(threeFingerTotalDy)
                                        if (absY >= absX && absY >= THREE_FINGER_SWIPE_TRIGGER_PX) {
                                            if (threeFingerTotalDy < 0f) {
                                                onThreeFingerSwipeUp()
                                            } else {
                                                onThreeFingerSwipeDown()
                                            }
                                        } else if (absX > absY && absX >= THREE_FINGER_SWIPE_TRIGGER_PX) {
                                            if (threeFingerTotalDx < 0f) {
                                                onThreeFingerSwipeLeft()
                                            } else {
                                                onThreeFingerSwipeRight()
                                            }
                                        }
                                    }
                                    lastSingleTapUptime = 0L
                                }

                                maxPointers == 2 -> {
                                    if (twoFingerTapCandidate &&
                                        !pinchTriggered &&
                                        durationMs <= TAP_DURATION_MS &&
                                        totalMovement <= TWO_FINGER_TAP_MAX_MOVE_PX
                                    ) {
                                        onTwoFingerTap()
                                    }
                                    lastSingleTapUptime = 0L
                                }

                                maxPointers == 1 -> {
                                    if (singleTapCandidate &&
                                        durationMs <= TAP_DURATION_MS &&
                                        totalMovement <= tapDistancePx
                                    ) {
                                        val isDoubleTap =
                                            lastSingleTapUptime != 0L &&
                                                (gestureStartTime - lastSingleTapUptime) <= DOUBLE_TAP_WINDOW_MS
                                        if (isDoubleTap) {
                                            onDoubleTap()
                                            lastSingleTapUptime = 0L
                                        } else {
                                            onTap()
                                            lastSingleTapUptime = gestureEndTime
                                        }
                                    } else {
                                        lastSingleTapUptime = 0L
                                    }
                                }
                            }
                        }
                    },
            ) {
                Text(
                    text = "Touch surface",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
    }
}

private const val SCROLL_STEP_PX = 40f
private const val TAP_DURATION_MS = 190L
private const val DOUBLE_TAP_WINDOW_MS = 280L
private const val LONG_PRESS_DRAG_MS = 220L
private const val LONG_PRESS_DRAG_MOVE_THRESHOLD_PX = 10f
private const val TWO_FINGER_TAP_MAX_MOVE_PX = 20f
private const val TWO_FINGER_DRAG_HOLD_MS = 220L
private const val TWO_FINGER_DRAG_ACTIVATE_MOVE_PX = 18f
private const val THREE_FINGER_TAP_MAX_MOVE_PX = 22f
private const val THREE_FINGER_SWIPE_TRIGGER_PX = 52f
private const val PINCH_IN_SCALE_THRESHOLD = 0.88f
private const val PINCH_OUT_SCALE_THRESHOLD = 1.12f
