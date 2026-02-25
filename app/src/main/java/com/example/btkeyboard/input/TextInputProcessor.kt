package com.example.btkeyboard.input

import com.example.btkeyboard.model.KeyAction
import com.example.btkeyboard.model.SpecialKey

class TextInputProcessor {

    private var previous = ""

    fun process(newText: String): List<KeyAction> {
        if (newText == previous) {
            return emptyList()
        }

        if (newText.startsWith(previous)) {
            val inserted = newText.removePrefix(previous)
            previous = newText
            return if (inserted.isEmpty()) emptyList() else listOf(KeyAction.Text(inserted))
        }

        if (previous.startsWith(newText)) {
            val removed = previous.length - newText.length
            previous = newText
            return List(removed) { KeyAction.Special(SpecialKey.BACKSPACE) }
        }

        val prefix = commonPrefixLength(previous, newText)
        val oldTail = previous.substring(prefix)
        val newTail = newText.substring(prefix)

        val suffix = commonSuffixLength(oldTail, newTail)
        val removedCount = oldTail.length - suffix
        val addedText = newTail.substring(0, newTail.length - suffix)

        val actions = mutableListOf<KeyAction>()
        repeat(removedCount) {
            actions += KeyAction.Special(SpecialKey.BACKSPACE)
        }
        if (addedText.isNotEmpty()) {
            actions += KeyAction.Text(addedText)
        }

        previous = newText
        return actions
    }

    fun reset() {
        previous = ""
    }

    fun prime(current: String) {
        previous = current
    }

    private fun commonPrefixLength(a: String, b: String): Int {
        val max = minOf(a.length, b.length)
        var index = 0
        while (index < max && a[index] == b[index]) {
            index++
        }
        return index
    }

    private fun commonSuffixLength(a: String, b: String): Int {
        val max = minOf(a.length, b.length)
        var index = 0
        while (index < max && a[a.length - 1 - index] == b[b.length - 1 - index]) {
            index++
        }
        return index
    }
}
