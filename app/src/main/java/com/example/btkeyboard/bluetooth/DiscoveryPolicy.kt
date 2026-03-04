package com.example.btkeyboard.bluetooth

import com.example.btkeyboard.model.ConnectionState

internal object DiscoveryPolicy {
    const val BLOCKED_WHILE_CONNECTED_MESSAGE =
        "Disconnect from current host before discovery. Use Bonded devices if already paired."

    fun blockedReason(state: ConnectionState): String? {
        return when (state) {
            is ConnectionState.Connected -> BLOCKED_WHILE_CONNECTED_MESSAGE
            is ConnectionState.Connecting -> BLOCKED_WHILE_CONNECTED_MESSAGE
            else -> null
        }
    }
}
