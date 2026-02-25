package com.example.btkeyboard.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.btkeyboard.model.HostDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.btKeyboardDataStore: DataStore<Preferences> by preferencesDataStore(name = "bt_keyboard")

class DataStoreDeviceStore(
    private val context: Context,
) : DeviceStore, AppSettingsStore {

    override val settings: Flow<AppSettings> = context.btKeyboardDataStore.data
        .catch {
            if (it is IOException) {
                emit(emptyPreferences())
            } else {
                throw it
            }
        }
        .map { prefs ->
            AppSettings(
                autoReconnect = prefs[Keys.AUTO_RECONNECT] ?: true,
                foregroundPersistence = prefs[Keys.FOREGROUND_PERSISTENCE] ?: true,
                pointerSensitivity = prefs[Keys.POINTER_SENSITIVITY] ?: 1.0f,
                acknowledgedHidDescriptorVersion = prefs[Keys.ACK_HID_DESCRIPTOR_VERSION]
                    ?: if (hasLegacyPairingData(prefs)) LEGACY_HID_DESCRIPTOR_VERSION else CURRENT_HID_DESCRIPTOR_VERSION,
            )
        }

    override suspend fun saveTrusted(device: HostDevice) {
        context.btKeyboardDataStore.edit { prefs ->
            val existing = prefs[Keys.TRUSTED] ?: emptySet()
            val updated = existing.filterNot { it.startsWith("${device.address}|") }
                .toMutableSet()
                .apply { add(encode(device)) }
            prefs[Keys.TRUSTED] = updated
        }
    }

    override suspend fun removeTrusted(address: String) {
        context.btKeyboardDataStore.edit { prefs ->
            val existing = prefs[Keys.TRUSTED] ?: emptySet()
            val updated = existing.filterNot { it.startsWith("$address|") }.toSet()
            prefs[Keys.TRUSTED] = updated
            if (prefs[Keys.LAST_CONNECTED] == address) {
                prefs.remove(Keys.LAST_CONNECTED)
            }
        }
    }

    override suspend fun listTrusted(): List<HostDevice> {
        val prefs = context.btKeyboardDataStore.data.first()
        return prefs[Keys.TRUSTED].orEmpty().mapNotNull(::decode)
    }

    override suspend fun saveLastConnected(address: String) {
        context.btKeyboardDataStore.edit { prefs ->
            prefs[Keys.LAST_CONNECTED] = address
        }
    }

    override suspend fun getLastConnected(): String? {
        val prefs = context.btKeyboardDataStore.data.first()
        return prefs[Keys.LAST_CONNECTED]
    }

    override suspend fun clearAll() {
        context.btKeyboardDataStore.edit { prefs ->
            prefs.remove(Keys.TRUSTED)
            prefs.remove(Keys.LAST_CONNECTED)
        }
    }

    override suspend fun updateAutoReconnect(enabled: Boolean) {
        context.btKeyboardDataStore.edit { prefs ->
            prefs[Keys.AUTO_RECONNECT] = enabled
        }
    }

    override suspend fun updateForegroundPersistence(enabled: Boolean) {
        context.btKeyboardDataStore.edit { prefs ->
            prefs[Keys.FOREGROUND_PERSISTENCE] = enabled
        }
    }

    override suspend fun updatePointerSensitivity(value: Float) {
        context.btKeyboardDataStore.edit { prefs ->
            prefs[Keys.POINTER_SENSITIVITY] = value
        }
    }

    override suspend fun updateAcknowledgedHidDescriptorVersion(version: Int) {
        context.btKeyboardDataStore.edit { prefs ->
            prefs[Keys.ACK_HID_DESCRIPTOR_VERSION] = version
        }
    }

    private fun encode(device: HostDevice): String {
        val safeName = device.name.replace("|", " ")
        return "${device.address}|$safeName"
    }

    private fun decode(raw: String): HostDevice? {
        val parts = raw.split("|", limit = 2)
        if (parts.size < 2) return null
        val address = parts[0]
        val name = parts[1].ifBlank { "Unknown" }
        return HostDevice(name = name, address = address, bonded = true, connected = false)
    }

    private object Keys {
        val TRUSTED = stringSetPreferencesKey("trusted_devices")
        val LAST_CONNECTED = stringPreferencesKey("last_connected")
        val AUTO_RECONNECT = booleanPreferencesKey("auto_reconnect")
        val FOREGROUND_PERSISTENCE = booleanPreferencesKey("foreground_persistence")
        val POINTER_SENSITIVITY = floatPreferencesKey("pointer_sensitivity")
        val ACK_HID_DESCRIPTOR_VERSION = intPreferencesKey("ack_hid_descriptor_version")
    }

    private fun hasLegacyPairingData(prefs: Preferences): Boolean {
        return !prefs[Keys.TRUSTED].isNullOrEmpty() || prefs[Keys.LAST_CONNECTED] != null
    }

    private companion object {
        const val CURRENT_HID_DESCRIPTOR_VERSION = 2
        const val LEGACY_HID_DESCRIPTOR_VERSION = 1
    }
}
