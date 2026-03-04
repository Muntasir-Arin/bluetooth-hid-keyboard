package com.example.btkeyboard.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.btkeyboard.bluetooth.BluetoothHidController
import com.example.btkeyboard.input.TextInputProcessor
import com.example.btkeyboard.model.KeyAction
import com.example.btkeyboard.model.ModifierKey
import com.example.btkeyboard.model.SpecialKey
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class KeyboardViewModel(
    private val controller: BluetoothHidController,
) : ViewModel() {

    var inputText by mutableStateOf("")
        private set

    val connectionState = controller.state
    val activeModifiers = controller.activeModifiers
    val unsupportedCharCount = controller.unsupportedCharCount

    private val processor = TextInputProcessor()
    private val actionsChannel = Channel<KeyAction>(capacity = Channel.UNLIMITED)

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val events: SharedFlow<String> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            for (action in actionsChannel) {
                controller.send(action)
                    .exceptionOrNull()
                    ?.message
                    ?.let { _events.emit(it) }
            }
        }
    }

    fun onInputTextChanged(newText: String) {
        inputText = newText
        val actions = processor.process(newText)
        actions.forEach { action ->
            val queued = actionsChannel.trySend(action)
            if (queued.isFailure) {
                _events.tryEmit("Keyboard input queue is busy. Please retry.")
            }
        }

        if (inputText.length > INPUT_BUFFER_MAX) {
            inputText = inputText.takeLast(INPUT_BUFFER_MAX)
            processor.prime(inputText)
        }
    }

    fun sendSpecial(key: SpecialKey) {
        val queued = actionsChannel.trySend(KeyAction.Special(key))
        if (queued.isFailure) {
            _events.tryEmit("Keyboard input queue is busy. Please retry.")
        }
    }

    fun toggleModifier(key: ModifierKey, enabled: Boolean) {
        val queued = actionsChannel.trySend(KeyAction.ModifierToggle(key, enabled))
        if (queued.isFailure) {
            _events.tryEmit("Keyboard input queue is busy. Please retry.")
        }
    }

    fun clearUnsupportedCount() {
        controller.clearUnsupportedCount()
    }

    fun clearInput() {
        inputText = ""
        processor.reset()
    }

    override fun onCleared() {
        actionsChannel.close()
        super.onCleared()
    }

    companion object {
        private const val INPUT_BUFFER_MAX = 160
    }
}
