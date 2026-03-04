package com.example.btkeyboard.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileProxyCircuitBreakerTest {

    private var nowMs = 0L

    @Test
    fun `cooldown activates after threshold failures`() {
        val breaker = ProfileProxyCircuitBreaker(
            cooldownMs = 30_000L,
            failureThreshold = 2,
            nowProvider = { nowMs },
        )

        assertNull(breaker.failFastMessage())
        assertEquals(false, breaker.recordFailure())
        assertNull(breaker.failFastMessage())

        assertEquals(true, breaker.recordFailure())
        assertNotNull(breaker.failFastMessage())
    }

    @Test
    fun `cooldown expires and reset clears state`() {
        val breaker = ProfileProxyCircuitBreaker(
            cooldownMs = 30_000L,
            failureThreshold = 2,
            nowProvider = { nowMs },
        )

        breaker.recordFailure()
        breaker.recordFailure()
        assertNotNull(breaker.failFastMessage())

        nowMs += 30_000L
        assertNull(breaker.failFastMessage())

        breaker.recordFailure()
        breaker.reset()
        assertNull(breaker.failFastMessage())
    }
}
