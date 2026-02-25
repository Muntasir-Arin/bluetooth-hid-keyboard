package com.example.btkeyboard.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.example.btkeyboard.bluetooth.BluetoothHidController
import com.example.btkeyboard.model.MouseButton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TrackpadViewModel(
    private val controller: BluetoothHidController,
) : ViewModel() {

    val connectionState = controller.state
    val pressedMouseButtons = controller.pressedMouseButtons

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    fun movePointer(
        dx: Float,
        dy: Float,
        sensitivity: Float,
    ) {
        controller.sendPointerMove(dxPx = dx, dyPx = dy, sensitivity = sensitivity)
            .exceptionOrNull()
            ?.message
            ?.let { _lastError.value = it }
    }

    fun scrollBySteps(steps: Int) {
        controller.sendVerticalScroll(steps)
            .exceptionOrNull()
            ?.message
            ?.let { _lastError.value = it }
    }

    fun setButtonPressed(
        button: MouseButton,
        pressed: Boolean,
    ) {
        controller.setMouseButton(button, pressed)
            .exceptionOrNull()
            ?.message
            ?.let { _lastError.value = it }
    }

    fun tapToClick() {
        controller.clickMouseButton(MouseButton.LEFT)
            .exceptionOrNull()
            ?.message
            ?.let { _lastError.value = it }
    }

    fun clearError() {
        _lastError.value = null
    }
}
