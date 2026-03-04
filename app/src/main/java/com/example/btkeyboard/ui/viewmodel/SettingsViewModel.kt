package com.example.btkeyboard.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.example.btkeyboard.bluetooth.BluetoothHidController
import com.example.btkeyboard.data.AppThemeMode
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

    fun setPointerSensitivity(value: Float) {
        controller.updatePointerSensitivity(value)
    }

    fun setThemeMode(mode: AppThemeMode) {
        controller.updateThemeMode(mode)
    }

    fun clearTrustedDevices() {
        controller.clearTrustedDevices()
    }

    fun clearDiagnostics() {
        logger.clear()
    }
}
