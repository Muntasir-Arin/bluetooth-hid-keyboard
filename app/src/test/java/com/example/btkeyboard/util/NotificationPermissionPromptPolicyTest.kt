package com.example.btkeyboard.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPermissionPromptPolicyTest {

    @Test
    fun promptsOnlyWhenRuntimePermissionIsRequiredAndNotGrantedAndNotPrompted() {
        assertTrue(
            NotificationPermissionPromptPolicy.shouldPrompt(
                runtimePermissionRequired = true,
                alreadyGranted = false,
                alreadyPrompted = false,
            ),
        )
    }

    @Test
    fun doesNotPromptWhenAlreadyGranted() {
        assertFalse(
            NotificationPermissionPromptPolicy.shouldPrompt(
                runtimePermissionRequired = true,
                alreadyGranted = true,
                alreadyPrompted = false,
            ),
        )
    }

    @Test
    fun doesNotPromptWhenAlreadyPrompted() {
        assertFalse(
            NotificationPermissionPromptPolicy.shouldPrompt(
                runtimePermissionRequired = true,
                alreadyGranted = false,
                alreadyPrompted = true,
            ),
        )
    }

    @Test
    fun doesNotPromptWhenPermissionNotRequiredOnThisApi() {
        assertFalse(
            NotificationPermissionPromptPolicy.shouldPrompt(
                runtimePermissionRequired = false,
                alreadyGranted = false,
                alreadyPrompted = false,
            ),
        )
    }
}
