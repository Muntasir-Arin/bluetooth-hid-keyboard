package com.example.btkeyboard.bluetooth

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoveryStartReconcilerTest {

    @Test
    fun callbackConfirmedWithinGraceWindowTreatsDiscoveryStartAsSuccess() = runBlocking {
        var nowMs = 0L
        var started = false

        val reconciler = DiscoveryStartReconciler(
            waitStepMs = 25L,
            nowProvider = { nowMs },
            delayProvider = { delayMs ->
                nowMs += delayMs
                if (nowMs >= 75L) {
                    started = true
                }
            },
        )

        val result = reconciler.awaitStartConfirmation(graceMs = 400L) { started }

        assertTrue(result.started)
        assertEquals(75L, result.waitMs)
    }

    @Test
    fun missingCallbackWithinGraceWindowKeepsDiscoveryStartFailure() = runBlocking {
        var nowMs = 0L

        val reconciler = DiscoveryStartReconciler(
            waitStepMs = 25L,
            nowProvider = { nowMs },
            delayProvider = { delayMs ->
                nowMs += delayMs
            },
        )

        val result = reconciler.awaitStartConfirmation(graceMs = 400L) { false }

        assertFalse(result.started)
        assertEquals(400L, result.waitMs)
    }
}
