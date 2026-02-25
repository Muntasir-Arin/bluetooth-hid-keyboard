package com.example.btkeyboard.bluetooth

import com.example.btkeyboard.model.SpecialKey
import java.text.Normalizer

class KeyMapUsQwerty {

    data class KeyStroke(
        val usage: Int,
        val needsShift: Boolean,
    )

    fun mapChar(input: Char): KeyStroke? {
        if (input in 'a'..'z') {
            return KeyStroke(usage = 0x04 + (input - 'a'), needsShift = false)
        }
        if (input in 'A'..'Z') {
            return KeyStroke(usage = 0x04 + (input.lowercaseChar() - 'a'), needsShift = true)
        }

        return when (input) {
            '1' -> KeyStroke(0x1E, false)
            '2' -> KeyStroke(0x1F, false)
            '3' -> KeyStroke(0x20, false)
            '4' -> KeyStroke(0x21, false)
            '5' -> KeyStroke(0x22, false)
            '6' -> KeyStroke(0x23, false)
            '7' -> KeyStroke(0x24, false)
            '8' -> KeyStroke(0x25, false)
            '9' -> KeyStroke(0x26, false)
            '0' -> KeyStroke(0x27, false)
            '!' -> KeyStroke(0x1E, true)
            '@' -> KeyStroke(0x1F, true)
            '#' -> KeyStroke(0x20, true)
            '$' -> KeyStroke(0x21, true)
            '%' -> KeyStroke(0x22, true)
            '^' -> KeyStroke(0x23, true)
            '&' -> KeyStroke(0x24, true)
            '*' -> KeyStroke(0x25, true)
            '(' -> KeyStroke(0x26, true)
            ')' -> KeyStroke(0x27, true)
            '-' -> KeyStroke(0x2D, false)
            '_' -> KeyStroke(0x2D, true)
            '=' -> KeyStroke(0x2E, false)
            '+' -> KeyStroke(0x2E, true)
            '[' -> KeyStroke(0x2F, false)
            '{' -> KeyStroke(0x2F, true)
            ']' -> KeyStroke(0x30, false)
            '}' -> KeyStroke(0x30, true)
            '\\' -> KeyStroke(0x31, false)
            '|' -> KeyStroke(0x31, true)
            ';' -> KeyStroke(0x33, false)
            ':' -> KeyStroke(0x33, true)
            '\'' -> KeyStroke(0x34, false)
            '"' -> KeyStroke(0x34, true)
            '`' -> KeyStroke(0x35, false)
            '~' -> KeyStroke(0x35, true)
            ',' -> KeyStroke(0x36, false)
            '<' -> KeyStroke(0x36, true)
            '.' -> KeyStroke(0x37, false)
            '>' -> KeyStroke(0x37, true)
            '/' -> KeyStroke(0x38, false)
            '?' -> KeyStroke(0x38, true)
            ' ' -> KeyStroke(0x2C, false)
            '\n' -> KeyStroke(0x28, false)
            '\t' -> KeyStroke(0x2B, false)
            else -> null
        }
    }

    fun mapSpecial(key: SpecialKey): KeyStroke? {
        return when (key) {
            SpecialKey.ESC -> KeyStroke(0x29, false)
            SpecialKey.TAB -> KeyStroke(0x2B, false)
            SpecialKey.ENTER -> KeyStroke(0x28, false)
            SpecialKey.BACKSPACE -> KeyStroke(0x2A, false)
            SpecialKey.DELETE -> KeyStroke(0x4C, false)
            SpecialKey.INSERT -> KeyStroke(0x49, false)
            SpecialKey.HOME -> KeyStroke(0x4A, false)
            SpecialKey.END -> KeyStroke(0x4D, false)
            SpecialKey.PAGE_UP -> KeyStroke(0x4B, false)
            SpecialKey.PAGE_DOWN -> KeyStroke(0x4E, false)
            SpecialKey.ARROW_UP -> KeyStroke(0x52, false)
            SpecialKey.ARROW_DOWN -> KeyStroke(0x51, false)
            SpecialKey.ARROW_LEFT -> KeyStroke(0x50, false)
            SpecialKey.ARROW_RIGHT -> KeyStroke(0x4F, false)
            SpecialKey.F1 -> KeyStroke(0x3A, false)
            SpecialKey.F2 -> KeyStroke(0x3B, false)
            SpecialKey.F3 -> KeyStroke(0x3C, false)
            SpecialKey.F4 -> KeyStroke(0x3D, false)
            SpecialKey.F5 -> KeyStroke(0x3E, false)
            SpecialKey.F6 -> KeyStroke(0x3F, false)
            SpecialKey.F7 -> KeyStroke(0x40, false)
            SpecialKey.F8 -> KeyStroke(0x41, false)
            SpecialKey.F9 -> KeyStroke(0x42, false)
            SpecialKey.F10 -> KeyStroke(0x43, false)
            SpecialKey.F11 -> KeyStroke(0x44, false)
            SpecialKey.F12 -> KeyStroke(0x45, false)
            SpecialKey.VOL_UP,
            SpecialKey.VOL_DOWN,
            SpecialKey.MUTE,
            SpecialKey.PLAY_PAUSE,
            -> null
        }
    }

    fun mapConsumerUsage(key: SpecialKey): Int? {
        return when (key) {
            SpecialKey.VOL_UP -> 0x00E9
            SpecialKey.VOL_DOWN -> 0x00EA
            SpecialKey.MUTE -> 0x00E2
            SpecialKey.PLAY_PAUSE -> 0x00CD
            else -> null
        }
    }

    fun transliterateChar(input: Char): String {
        val normalized = Normalizer.normalize(input.toString(), Normalizer.Form.NFD)
        val ascii = normalized.replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
        return ascii.ifEmpty { "?" }
    }
}
