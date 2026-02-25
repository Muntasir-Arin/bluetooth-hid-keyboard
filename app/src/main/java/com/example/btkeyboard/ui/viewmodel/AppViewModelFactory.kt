package com.example.btkeyboard.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.btkeyboard.app.BtKeyboardApplication

class AppViewModelFactory(
    private val app: BtKeyboardApplication,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(DevicesViewModel::class.java) -> {
                DevicesViewModel(app.hidController) as T
            }

            modelClass.isAssignableFrom(KeyboardViewModel::class.java) -> {
                KeyboardViewModel(app.hidController) as T
            }

            modelClass.isAssignableFrom(SettingsViewModel::class.java) -> {
                SettingsViewModel(app.hidController, app.diagnosticsLogger) as T
            }

            modelClass.isAssignableFrom(TrackpadViewModel::class.java) -> {
                TrackpadViewModel(app.hidController) as T
            }

            else -> error("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
