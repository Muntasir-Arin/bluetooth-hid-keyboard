package com.example.btkeyboard.service

import com.example.btkeyboard.model.ConnectionState

internal object ServiceIdlePolicy {
    fun shouldArmIdleTimer(
        appInForeground: Boolean,
        isHostConnected: Boolean,
        state: ConnectionState,
    ): Boolean {
        return !appInForeground && !isHostConnected && state !is ConnectionState.Connected
    }
}
