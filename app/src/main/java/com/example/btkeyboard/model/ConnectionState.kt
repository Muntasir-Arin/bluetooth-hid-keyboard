package com.example.btkeyboard.model

sealed interface ConnectionState {
    data object Idle : ConnectionState
    data object Discovering : ConnectionState

    data class Pairing(
        val device: HostDevice,
    ) : ConnectionState

    data class Connecting(
        val device: HostDevice,
    ) : ConnectionState

    data class Connected(
        val device: HostDevice,
        val latencyMs: Long = 0L,
    ) : ConnectionState

    data class Error(
        val code: ErrorCode,
        val message: String,
    ) : ConnectionState
}
