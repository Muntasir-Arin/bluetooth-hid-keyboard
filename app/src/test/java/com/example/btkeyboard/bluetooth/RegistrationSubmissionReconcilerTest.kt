package com.example.btkeyboard.bluetooth

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RegistrationSubmissionReconcilerTest {

    @Test
    fun callbackConfirmedWithinGraceWindowTreatsRegistrationAsSuccess() = runBlocking {
        var nowMs = 0L
        var callbackRegistered = false

        val reconciler = RegistrationSubmissionReconciler(
            waitStepMs = 25L,
            nowProvider = { nowMs },
            delayProvider = { delayMs ->
                nowMs += delayMs
                if (nowMs >= 100L) {
                    callbackRegistered = true
                }
            },
        )

        val result = reconciler.awaitCallbackConfirmation(graceMs = 750L) {
            callbackRegistered
        }

        assertTrue(result.callbackConfirmed)
        assertEquals(100L, result.waitMs)
    }

    @Test
    fun missingCallbackWithinGraceWindowKeepsRegistrationFailure() = runBlocking {
        var nowMs = 0L

        val reconciler = RegistrationSubmissionReconciler(
            waitStepMs = 25L,
            nowProvider = { nowMs },
            delayProvider = { delayMs ->
                nowMs += delayMs
            },
        )

        val result = reconciler.awaitCallbackConfirmation(graceMs = 750L) {
            false
        }

        assertFalse(result.callbackConfirmed)
        assertEquals(750L, result.waitMs)
    }
}
