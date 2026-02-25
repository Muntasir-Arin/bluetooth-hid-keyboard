package com.example.btkeyboard.bluetooth

import com.example.btkeyboard.model.ConnectionState
import com.example.btkeyboard.model.HostDevice
import com.example.btkeyboard.model.KeyAction
import kotlinx.coroutines.flow.StateFlow

interface HidTransport {
    fun registerApp(): Result<Unit>
    fun unregisterApp(): Result<Unit>
    fun connect(device: HostDevice): Result<Unit>
    fun disconnect(): Result<Unit>
    fun send(action: KeyAction): Result<Unit>

    val state: StateFlow<ConnectionState>
}
