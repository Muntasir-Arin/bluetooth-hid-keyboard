package com.example.btkeyboard.service

import com.example.btkeyboard.model.ConnectionState

internal object ServiceIdlePolicy {
    fun shouldArmIdleTimer(
        appInForeground: Boolean,
        state: ConnectionState,
    ): Boolean {
        return !appInForeground && state !is ConnectionState.Connected
    }
}
