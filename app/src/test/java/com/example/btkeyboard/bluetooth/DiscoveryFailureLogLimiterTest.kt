package com.example.btkeyboard.bluetooth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoveryFailureLogLimiterTest {

    @Test
    fun suppressesIdenticalFingerprintWithinWindow() {
        var nowMs = 0L
        val limiter = DiscoveryFailureLogLimiter(
            dedupeWindowMs = 10_000L,
            nowProvider = { nowMs },
        )

        assertTrue(limiter.shouldLog("fingerprint-a"))
        nowMs += 5_000L
        assertFalse(limiter.shouldLog("fingerprint-a"))
    }

    @Test
    fun allowsSameFingerprintAfterWindowAndDifferentFingerprintAnytime() {
        var nowMs = 0L
        val limiter = DiscoveryFailureLogLimiter(
            dedupeWindowMs = 10_000L,
            nowProvider = { nowMs },
        )

        assertTrue(limiter.shouldLog("fingerprint-a"))
        nowMs += 1_000L
        assertTrue(limiter.shouldLog("fingerprint-b"))
        nowMs += 10_000L
        assertTrue(limiter.shouldLog("fingerprint-b"))
    }
}
