package com.example.btkeyboard.data

import com.example.btkeyboard.model.HostDevice

interface DeviceStore {
    suspend fun saveTrusted(device: HostDevice)
    suspend fun removeTrusted(address: String)
    suspend fun listTrusted(): List<HostDevice>

    suspend fun saveLastConnected(address: String)
    suspend fun getLastConnected(): String?

    suspend fun clearAll()
}
