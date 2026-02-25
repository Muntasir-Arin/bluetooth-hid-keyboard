package com.example.btkeyboard.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class KeyMapUsQwertyTest {

    private val keyMap = KeyMapUsQwerty()

    @Test
    fun mapsLowercaseLetters() {
        val stroke = keyMap.mapChar('a')

        assertNotNull(stroke)
        assertEquals(0x04, stroke?.usage)
        assertEquals(false, stroke?.needsShift)
    }

    @Test
    fun mapsShiftedSymbols() {
        val stroke = keyMap.mapChar('!')

        assertNotNull(stroke)
        assertEquals(0x1E, stroke?.usage)
        assertEquals(true, stroke?.needsShift)
    }

    @Test
    fun mapsFunctionKey() {
        val stroke = keyMap.mapSpecial(com.example.btkeyboard.model.SpecialKey.F5)

        assertNotNull(stroke)
        assertEquals(0x3E, stroke?.usage)
    }
}
