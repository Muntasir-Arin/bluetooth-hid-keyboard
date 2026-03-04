package com.example.btkeyboard.bluetooth

import android.os.SystemClock
import kotlin.math.min
import kotlinx.coroutines.delay

internal data class DiscoveryStartReconcileResult(
    val started: Boolean,
    val waitMs: Long,
)

internal class DiscoveryStartReconciler(
    private val waitStepMs: Long = 25L,
    private val nowProvider: () -> Long = { SystemClock.elapsedRealtime() },
    private val delayProvider: suspend (Long) -> Unit = { delay(it) },
) {
    suspend fun awaitStartConfirmation(
        graceMs: Long,
        isStarted: () -> Boolean,
    ): DiscoveryStartReconcileResult {
        if (isStarted()) {
            return DiscoveryStartReconcileResult(started = true, waitMs = 0L)
        }

        val startedAt = nowProvider()
        val deadline = startedAt + graceMs
        while (nowProvider() < deadline) {
            val remainingMs = deadline - nowProvider()
            delayProvider(min(waitStepMs, remainingMs))
            if (isStarted()) {
                return DiscoveryStartReconcileResult(
                    started = true,
                    waitMs = (nowProvider() - startedAt).coerceAtLeast(0L),
                )
            }
        }

        return DiscoveryStartReconcileResult(
            started = isStarted(),
            waitMs = (nowProvider() - startedAt).coerceAtLeast(0L),
        )
    }
}
