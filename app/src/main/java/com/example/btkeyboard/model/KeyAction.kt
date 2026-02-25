package com.example.btkeyboard.model

sealed interface KeyAction {
    data class Text(
        val value: String,
    ) : KeyAction

    data class Special(
        val key: SpecialKey,
    ) : KeyAction

    data class ModifierToggle(
        val key: ModifierKey,
        val enabled: Boolean,
    ) : KeyAction
}
