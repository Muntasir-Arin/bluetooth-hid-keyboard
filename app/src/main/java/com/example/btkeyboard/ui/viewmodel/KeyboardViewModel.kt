package com.example.btkeyboard.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.example.btkeyboard.bluetooth.BluetoothHidController
import com.example.btkeyboard.input.TextInputProcessor
import com.example.btkeyboard.model.KeyAction
import com.example.btkeyboard.model.ModifierKey
import com.example.btkeyboard.model.SpecialKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class KeyboardViewModel(
    private val controller: BluetoothHidController,
) : ViewModel() {

    var inputText by mutableStateOf("")
        private set

    val connectionState = controller.state
    val activeModifiers = controller.activeModifiers
    val unsupportedCharCount = controller.unsupportedCharCount

    private val processor = TextInputProcessor()
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    fun onInputTextChanged(newText: String) {
        inputText = newText
        val actions = processor.process(newText)
        actions.forEach { action ->
            val result = controller.send(action)
            result.exceptionOrNull()?.let { err ->
                _lastError.value = err.message
            }
        }

        if (inputText.length > INPUT_BUFFER_MAX) {
            inputText = inputText.takeLast(INPUT_BUFFER_MAX)
            processor.prime(inputText)
        }
    }

    fun sendSpecial(key: SpecialKey) {
        val result = controller.send(KeyAction.Special(key))
        result.exceptionOrNull()?.let { err ->
            _lastError.value = err.message
        }
    }

    fun toggleModifier(key: ModifierKey, enabled: Boolean) {
        val result = controller.send(KeyAction.ModifierToggle(key, enabled))
        result.exceptionOrNull()?.let { err ->
            _lastError.value = err.message
        }
    }

    fun clearError() {
        _lastError.value = null
    }

    fun clearUnsupportedCount() {
        controller.clearUnsupportedCount()
    }

    fun clearInput() {
        inputText = ""
        processor.reset()
    }

    companion object {
        private const val INPUT_BUFFER_MAX = 160
    }
}
