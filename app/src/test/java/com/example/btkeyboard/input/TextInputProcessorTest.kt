package com.example.btkeyboard.input

import com.example.btkeyboard.model.KeyAction
import com.example.btkeyboard.model.SpecialKey
import org.junit.Assert.assertEquals
import org.junit.Test

class TextInputProcessorTest {

    @Test
    fun emitsInsertedText() {
        val processor = TextInputProcessor()
        val actions = processor.process("hello")

        assertEquals(listOf(KeyAction.Text("hello")), actions)
    }

    @Test
    fun emitsBackspaceOnDelete() {
        val processor = TextInputProcessor()
        processor.process("hello")

        val actions = processor.process("hell")

        assertEquals(listOf(KeyAction.Special(SpecialKey.BACKSPACE)), actions)
    }

    @Test
    fun emitsReplaceSequence() {
        val processor = TextInputProcessor()
        processor.process("abc")

        val actions = processor.process("adc")

        assertEquals(
            listOf(
                KeyAction.Special(SpecialKey.BACKSPACE),
                KeyAction.Text("d"),
            ),
            actions,
        )
    }
}
