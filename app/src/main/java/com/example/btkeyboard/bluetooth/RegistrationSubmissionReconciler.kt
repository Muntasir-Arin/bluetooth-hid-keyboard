package com.example.btkeyboard.bluetooth

import android.os.SystemClock
import kotlin.math.min
import kotlinx.coroutines.delay

internal data class RegistrationReconcileResult(
    val callbackConfirmed: Boolean,
    val waitMs: Long,
)

internal class RegistrationSubmissionReconciler(
    private val waitStepMs: Long = 25L,
    private val nowProvider: () -> Long = { SystemClock.elapsedRealtime() },
    private val delayProvider: suspend (Long) -> Unit = { delay(it) },
) {
    suspend fun awaitCallbackConfirmation(
        graceMs: Long,
        isRegistered: () -> Boolean,
    ): RegistrationReconcileResult {
        if (isRegistered()) {
            return RegistrationReconcileResult(callbackConfirmed = true, waitMs = 0L)
        }

        val startedAt = nowProvider()
        val deadline = startedAt + graceMs
        while (nowProvider() < deadline) {
            val remainingMs = deadline - nowProvider()
            delayProvider(min(waitStepMs, remainingMs))
            if (isRegistered()) {
                return RegistrationReconcileResult(
                    callbackConfirmed = true,
                    waitMs = (nowProvider() - startedAt).coerceAtLeast(0L),
                )
            }
        }

        return RegistrationReconcileResult(
            callbackConfirmed = isRegistered(),
            waitMs = (nowProvider() - startedAt).coerceAtLeast(0L),
        )
    }
}
