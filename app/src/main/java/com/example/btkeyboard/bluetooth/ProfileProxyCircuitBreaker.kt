package com.example.btkeyboard.bluetooth

class ProfileProxyCircuitBreaker(
    private val cooldownMs: Long,
    private val failureThreshold: Int,
    private val nowProvider: () -> Long,
) {
    private var failureCount = 0
    private var unavailableUntilMs = 0L

    fun failFastMessage(): String? {
        val now = nowProvider()
        if (now >= unavailableUntilMs) {
            return null
        }
        val remainingSeconds = ((unavailableUntilMs - now) + 999L) / 1000L
        return "HID profile temporarily unavailable after repeated failures. Retry in ${remainingSeconds}s."
    }

    fun recordFailure(): Boolean {
        failureCount += 1
        if (failureCount < failureThreshold) {
            return false
        }
        unavailableUntilMs = nowProvider() + cooldownMs
        return true
    }

    fun reset() {
        failureCount = 0
        unavailableUntilMs = 0L
    }
}
