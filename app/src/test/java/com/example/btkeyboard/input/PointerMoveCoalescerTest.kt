package com.example.btkeyboard.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PointerMoveCoalescerTest {

    @Test
    fun `coalesces multiple samples into one batch`() {
        val coalescer = PointerMoveCoalescer()
        coalescer.add(dx = 2f, dy = 3f, sensitivity = 1.0f)
        coalescer.add(dx = -1f, dy = 1f, sensitivity = 1.2f)
        coalescer.add(dx = 4f, dy = -2f, sensitivity = 1.4f)

        val batch = coalescer.drain()

        requireNotNull(batch)
        assertEquals(5f, batch.dx, 0.0001f)
        assertEquals(2f, batch.dy, 0.0001f)
        assertEquals(1.4f, batch.sensitivity, 0.0001f)
        assertEquals(3, batch.samples)
    }

    @Test
    fun `drain clears pending state`() {
        val coalescer = PointerMoveCoalescer()
        coalescer.add(dx = 1f, dy = 1f, sensitivity = 1.0f)

        val first = coalescer.drain()
        val second = coalescer.drain()

        requireNotNull(first)
        assertEquals(1, first.samples)
        assertNull(second)
    }
}
