package com.example.btkeyboard.data

import kotlinx.coroutines.flow.Flow

data class AppSettings(
    val autoReconnect: Boolean = true,
    val pointerSensitivity: Float = 1.0f,
    val acknowledgedHidDescriptorVersion: Int = 2,
)

interface AppSettingsStore {
    val settings: Flow<AppSettings>

    suspend fun updateAutoReconnect(enabled: Boolean)
    suspend fun updatePointerSensitivity(value: Float)
    suspend fun updateAcknowledgedHidDescriptorVersion(version: Int)
}
