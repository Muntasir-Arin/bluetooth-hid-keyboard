package com.example.btkeyboard.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class DiagnosticsLogger {
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault())
    private val _entries = MutableStateFlow<List<String>>(emptyList())

    val entries: StateFlow<List<String>> = _entries.asStateFlow()

    fun log(message: String) {
        val stamp = formatter.format(Instant.now())
        val line = "[$stamp] $message"
        val updated = (_entries.value + line).takeLast(MAX_LINES)
        _entries.value = updated
    }

    fun clear() {
        _entries.value = emptyList()
    }

    companion object {
        private const val MAX_LINES = 500
    }
}
