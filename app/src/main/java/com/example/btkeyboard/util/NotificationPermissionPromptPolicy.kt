package com.example.btkeyboard.util

internal object NotificationPermissionPromptPolicy {
    fun shouldPrompt(
        runtimePermissionRequired: Boolean,
        alreadyGranted: Boolean,
        alreadyPrompted: Boolean,
    ): Boolean {
        return runtimePermissionRequired && !alreadyGranted && !alreadyPrompted
    }
}
