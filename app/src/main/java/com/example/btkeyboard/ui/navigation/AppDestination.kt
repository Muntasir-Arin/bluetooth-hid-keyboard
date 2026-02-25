package com.example.btkeyboard.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.ui.graphics.vector.ImageVector

enum class AppDestination(
    val route: String,
    val title: String,
    val icon: ImageVector,
) {
    Devices(route = "devices", title = "Devices", icon = Icons.Default.Devices),
    Keyboard(route = "keyboard", title = "Keyboard", icon = Icons.Default.Keyboard),
    Trackpad(route = "trackpad", title = "Trackpad", icon = Icons.Default.TouchApp),
    Settings(route = "settings", title = "Settings", icon = Icons.Default.Settings),
}
