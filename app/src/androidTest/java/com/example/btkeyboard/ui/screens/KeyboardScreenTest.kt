package com.example.btkeyboard.ui.screens

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.example.btkeyboard.model.ConnectionState
import com.example.btkeyboard.model.SpecialKey
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class KeyboardScreenTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun backspaceControlUsesIconAndSendsBackspace() {
        val sentSpecial = mutableListOf<SpecialKey>()

        rule.setContent {
            KeyboardScreen(
                connectionState = ConnectionState.Idle,
                inputText = "",
                unsupportedCharCount = 0,
                activeModifiers = emptySet(),
                onInputChanged = {},
                onSendSpecial = { sentSpecial.add(it) },
                onModifierToggle = { _, _ -> },
                onClearUnsupportedCount = {},
                onClearInput = {},
            )
        }

        rule.onNodeWithContentDescription("Backspace").assertIsDisplayed().performClick()
        assertEquals(listOf(SpecialKey.BACKSPACE), sentSpecial)
        rule.onAllNodesWithText("Backspace").assertCountEquals(0)
    }
}
