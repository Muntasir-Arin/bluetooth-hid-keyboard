package com.example.btkeyboard.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.example.btkeyboard.bluetooth.BluetoothHidController
import com.example.btkeyboard.util.DiagnosticsLogger

class SettingsViewModel(
    private val controller: BluetoothHidController,
    private val logger: DiagnosticsLogger,
) : ViewModel() {

    val settings = controller.settings
    val diagnostics = logger.entries

    fun setAutoReconnect(enabled: Boolean) {
        controller.updateAutoReconnect(enabled)
    }

    fun setForegroundPersistence(enabled: Boolean) {
        controller.updateForegroundPersistence(enabled)
    }

    fun setPointerSensitivity(value: Float) {
        controller.updatePointerSensitivity(value)
    }

    fun clearTrustedDevices() {
        controller.clearTrustedDevices()
    }

    fun clearDiagnostics() {
        logger.clear()
    }
}
