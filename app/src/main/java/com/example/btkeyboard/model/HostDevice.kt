package com.example.btkeyboard.model

data class HostDevice(
    val name: String,
    val address: String,
    val bonded: Boolean,
    val connected: Boolean,
)
