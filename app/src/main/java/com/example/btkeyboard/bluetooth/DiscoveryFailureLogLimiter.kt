package com.example.btkeyboard.bluetooth

internal class DiscoveryFailureLogLimiter(
    private val dedupeWindowMs: Long,
    private val nowProvider: () -> Long,
) {
    private var lastFingerprint: String? = null
    private var lastLoggedAtMs: Long = Long.MIN_VALUE

    fun shouldLog(fingerprint: String): Boolean {
        val nowMs = nowProvider()
        val isDuplicate = fingerprint == lastFingerprint
        val withinWindow = (nowMs - lastLoggedAtMs) < dedupeWindowMs
        if (isDuplicate && withinWindow) {
            return false
        }
        lastFingerprint = fingerprint
        lastLoggedAtMs = nowMs
        return true
    }
}
