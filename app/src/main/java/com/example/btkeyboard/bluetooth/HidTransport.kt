package com.example.btkeyboard.bluetooth

import com.example.btkeyboard.model.ConnectionState
import com.example.btkeyboard.model.HostDevice
import com.example.btkeyboard.model.KeyAction
import kotlinx.coroutines.flow.StateFlow

interface HidTransport {
    suspend fun registerApp(): Result<Unit>
    suspend fun unregisterApp(): Result<Unit>
    suspend fun connect(device: HostDevice): Result<Unit>
    suspend fun disconnect(): Result<Unit>
    suspend fun send(action: KeyAction): Result<Unit>

    val state: StateFlow<ConnectionState>
}
