package com.example.btkeyboard.data

import com.example.btkeyboard.bluetooth.HidDescriptorVersion
import kotlinx.coroutines.flow.Flow

data class AppSettings(
    val autoReconnect: Boolean = true,
    val pointerSensitivity: Float = 1.0f,
    val themeMode: AppThemeMode = AppThemeMode.DARK,
    val acknowledgedHidDescriptorVersion: Int = HidDescriptorVersion.CURRENT,
    val notificationPermissionPrompted: Boolean = false,
)

interface AppSettingsStore {
    val settings: Flow<AppSettings>

    suspend fun updateAutoReconnect(enabled: Boolean)
    suspend fun updatePointerSensitivity(value: Float)
    suspend fun updateThemeMode(mode: AppThemeMode)
    suspend fun updateAcknowledgedHidDescriptorVersion(version: Int)
    suspend fun updateNotificationPermissionPrompted(prompted: Boolean)
}
