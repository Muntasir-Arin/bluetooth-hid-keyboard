package com.example.btkeyboard.ui.screens

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.btkeyboard.model.ConnectionState
import com.example.btkeyboard.model.HidCapability
import org.junit.Rule
import org.junit.Test

class DevicesScreenTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun startDiscoveryDisabledWhenConnected() {
        rule.setContent {
            DevicesScreen(
                bluetoothEnabled = true,
                hidCapability = HidCapability.AVAILABLE,
                connectionState = ConnectionState.Connected(
                    device = host("connected-host", connected = true),
                    latencyMs = 0L,
                ),
                trustedDevices = emptyList(),
                bondedDevices = emptyList(),
                discoveredDevices = emptyList(),
                requiresHostRepair = false,
                onStartDiscovery = {},
                onStopDiscovery = {},
                onOpenBluetoothSettings = {},
                onRefresh = {},
                onPair = {},
                onConnect = {},
                onDisconnect = {},
                onForgetTrusted = {},
                onAcknowledgeRepair = {},
            )
        }

        rule.onNodeWithText("Start Discovery").assertIsNotEnabled()
        rule.onNodeWithText("Disconnect").assertIsEnabled()
    }

    @Test
    fun startDiscoveryEnabledWhenIdle() {
        rule.setContent {
            DevicesScreen(
                bluetoothEnabled = true,
                hidCapability = HidCapability.AVAILABLE,
                connectionState = ConnectionState.Idle,
                trustedDevices = emptyList(),
                bondedDevices = emptyList(),
                discoveredDevices = emptyList(),
                requiresHostRepair = false,
                onStartDiscovery = {},
                onStopDiscovery = {},
                onOpenBluetoothSettings = {},
                onRefresh = {},
                onPair = {},
                onConnect = {},
                onDisconnect = {},
                onForgetTrusted = {},
                onAcknowledgeRepair = {},
            )
        }

        rule.onNodeWithText("Start Discovery").assertIsEnabled()
    }

    private fun host(
        address: String,
        connected: Boolean,
    ) = com.example.btkeyboard.model.HostDevice(
        name = "Host",
        address = address,
        bonded = true,
        connected = connected,
    )
}
