package com.example.btkeyboard.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.btkeyboard.bluetooth.BluetoothHidController
import com.example.btkeyboard.input.PointerMoveCoalescer
import com.example.btkeyboard.model.MouseButton
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TrackpadViewModel(
    private val controller: BluetoothHidController,
) : ViewModel() {

    val connectionState = controller.state
    val pressedMouseButtons = controller.pressedMouseButtons

    private val pointerMoves = Channel<PointerMoveCommand>(capacity = Channel.UNLIMITED)

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val events: SharedFlow<String> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            processPointerMoves()
        }
    }

    fun movePointer(
        dx: Float,
        dy: Float,
        sensitivity: Float,
    ) {
        val queued = pointerMoves.trySend(PointerMoveCommand(dx = dx, dy = dy, sensitivity = sensitivity))
        if (queued.isFailure) {
            _events.tryEmit("Trackpad input queue is busy. Please retry.")
        }
    }

    fun scrollBySteps(steps: Int) {
        launchCommand {
            controller.sendVerticalScroll(steps)
        }
    }

    fun scrollHorizontallyBySteps(steps: Int) {
        launchCommand {
            controller.sendHorizontalScroll(steps)
        }
    }

    fun setButtonPressed(
        button: MouseButton,
        pressed: Boolean,
    ) {
        launchCommand {
            controller.setMouseButton(button, pressed)
        }
    }

    fun tapToClick() {
        launchCommand {
            controller.clickMouseButton(MouseButton.LEFT)
        }
    }

    fun doubleTapToDoubleClick() {
        launchCommand {
            controller.doubleClickMouseButton(MouseButton.LEFT)
        }
    }

    fun twoFingerTapRightClick() {
        launchCommand {
            controller.clickMouseButton(MouseButton.RIGHT)
        }
    }

    fun pinchZoom(zoomIn: Boolean) {
        launchCommand {
            controller.sendZoom(zoomIn)
        }
    }

    fun threeFingerSwipeUp() {
        launchCommand {
            controller.shortcutTaskView()
        }
    }

    fun threeFingerSwipeDown() {
        launchCommand {
            controller.shortcutShowDesktop()
        }
    }

    fun threeFingerSwipeLeft() {
        launchCommand {
            controller.shortcutSwitchApp(next = false)
        }
    }

    fun threeFingerSwipeRight() {
        launchCommand {
            controller.shortcutSwitchApp(next = true)
        }
    }

    fun threeFingerTapLookup() {
        launchCommand {
            controller.shortcutLookup()
        }
    }

    override fun onCleared() {
        pointerMoves.close()
        super.onCleared()
    }

    private suspend fun processPointerMoves() {
        val coalescer = PointerMoveCoalescer()
        while (viewModelScope.isActive) {
            val first = pointerMoves.receiveCatching().getOrNull() ?: break
            coalescer.add(
                dx = first.dx,
                dy = first.dy,
                sensitivity = first.sensitivity,
            )

            delay(POINTER_FLUSH_INTERVAL_MS)

            while (true) {
                val next = pointerMoves.tryReceive().getOrNull() ?: break
                coalescer.add(
                    dx = next.dx,
                    dy = next.dy,
                    sensitivity = next.sensitivity,
                )
            }

            val batch = coalescer.drain() ?: continue
            controller.sendPointerMove(
                dxPx = batch.dx,
                dyPx = batch.dy,
                sensitivity = batch.sensitivity,
                sourceSamples = batch.samples,
            ).exceptionOrNull()
                ?.message
                ?.let { _events.emit(it) }
        }
    }

    private fun launchCommand(block: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            block()
                .exceptionOrNull()
                ?.message
                ?.let { _events.emit(it) }
        }
    }

    private data class PointerMoveCommand(
        val dx: Float,
        val dy: Float,
        val sensitivity: Float,
    )

    companion object {
        private const val POINTER_FLUSH_INTERVAL_MS = 8L
    }
}
