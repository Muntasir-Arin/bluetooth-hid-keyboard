package com.example.btkeyboard.input

data class CoalescedPointerMove(
    val dx: Float,
    val dy: Float,
    val sensitivity: Float,
    val samples: Int,
)

class PointerMoveCoalescer {
    private var totalDx = 0f
    private var totalDy = 0f
    private var lastSensitivity = 1f
    private var sampleCount = 0

    fun add(
        dx: Float,
        dy: Float,
        sensitivity: Float,
    ) {
        totalDx += dx
        totalDy += dy
        lastSensitivity = sensitivity
        sampleCount += 1
    }

    fun drain(): CoalescedPointerMove? {
        if (sampleCount == 0) {
            return null
        }
        val move = CoalescedPointerMove(
            dx = totalDx,
            dy = totalDy,
            sensitivity = lastSensitivity,
            samples = sampleCount,
        )
        totalDx = 0f
        totalDy = 0f
        lastSensitivity = 1f
        sampleCount = 0
        return move
    }
}
