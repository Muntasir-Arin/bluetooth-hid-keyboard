package com.example.btkeyboard.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.btkeyboard.model.ConnectionState
import com.example.btkeyboard.model.HostDevice
import com.example.btkeyboard.ui.components.AppCard
import com.example.btkeyboard.ui.components.SectionTitle
import com.example.btkeyboard.ui.components.shortLabel
import com.example.btkeyboard.ui.theme.UiTokens

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DevicesScreen(
    bluetoothEnabled: Boolean,
    hidSupported: Boolean,
    connectionState: ConnectionState,
    trustedDevices: List<HostDevice>,
    bondedDevices: List<HostDevice>,
    discoveredDevices: List<HostDevice>,
    requiresHostRepair: Boolean,
    onStartDiscovery: () -> Unit,
    onStopDiscovery: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    onRefresh: () -> Unit,
    onPair: (HostDevice) -> Unit,
    onConnect: (HostDevice) -> Unit,
    onDisconnect: () -> Unit,
    onForgetTrusted: (String) -> Unit,
    onAcknowledgeRepair: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDiscovering = connectionState is ConnectionState.Discovering

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(UiTokens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(UiTokens.SectionSpacing),
    ) {
        item {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(UiTokens.CardPadding),
                    verticalArrangement = Arrangement.spacedBy(UiTokens.CardSpacing),
                ) {
                    Text("Connection", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Bluetooth: ${if (bluetoothEnabled) "ON" else "OFF"}")
                    Text("HID support: ${if (hidSupported) "Available" else "Unavailable"}")
                    Text("State: ${connectionState.shortLabel()}")
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (isDiscovering) {
                            OutlinedButton(onClick = onStopDiscovery) {
                                Text("Stop Discovery")
                            }
                        } else {
                            Button(onClick = onStartDiscovery) {
                                Text("Start Discovery")
                            }
                        }
                        OutlinedButton(onClick = onOpenBluetoothSettings) {
                            Text("BT Settings")
                        }
                        OutlinedButton(onClick = onRefresh) {
                            Text("Refresh")
                        }
                        if (connectionState is ConnectionState.Connected) {
                            OutlinedButton(onClick = onDisconnect) {
                                Text("Disconnect")
                            }
                        }
                    }
                    if (!hidSupported) {
                        Text(
                            text = "This phone may not expose Bluetooth HID Device mode.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (requiresHostRepair) {
            item {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(UiTokens.CardPadding),
                        verticalArrangement = Arrangement.spacedBy(UiTokens.CardSpacing),
                    ) {
                        Text(
                            "Re-pair required",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            "The HID descriptor now includes touchpad reports. Forget this phone from your host and pair again.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(onClick = onOpenBluetoothSettings) {
                                Text("Open BT Settings")
                            }
                            OutlinedButton(onClick = onAcknowledgeRepair) {
                                Text("I Re-paired")
                            }
                        }
                    }
                }
            }
        }

        item { SectionTitle("Trusted devices") }
        if (trustedDevices.isEmpty()) {
            item {
                Text(
                    "No trusted devices yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(trustedDevices, key = { it.address }) { device ->
                DeviceRow(
                    device = device,
                    onPair = onPair,
                    onConnect = onConnect,
                    onForget = { onForgetTrusted(device.address) },
                )
            }
        }

        item { SectionTitle("Bonded devices") }
        if (bondedDevices.isEmpty()) {
            item {
                Text("No bonded devices.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            items(bondedDevices, key = { "bonded-${it.address}" }) { device ->
                DeviceRow(
                    device = device,
                    onPair = onPair,
                    onConnect = onConnect,
                )
            }
        }

        item { SectionTitle("Discovered devices") }
        if (discoveredDevices.isEmpty()) {
            item {
                Text("Start discovery to find nearby hosts.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            items(discoveredDevices, key = { "discovered-${it.address}" }) { device ->
                DeviceRow(
                    device = device,
                    onPair = onPair,
                    onConnect = onConnect,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeviceRow(
    device: HostDevice,
    onPair: (HostDevice) -> Unit,
    onConnect: (HostDevice) -> Unit,
    onForget: (() -> Unit)? = null,
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(UiTokens.CardPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(device.name, style = MaterialTheme.typography.titleMedium)
            Text(device.address, style = MaterialTheme.typography.labelSmall)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!device.bonded) {
                    Button(onClick = { onPair(device) }) {
                        Text("Pair")
                    }
                } else {
                    Button(onClick = { onConnect(device) }) {
                        Text(if (device.connected) "Reconnect" else "Connect")
                    }
                }
                onForget?.let {
                    OutlinedButton(onClick = it) {
                        Text("Forget")
                    }
                }
            }
        }
    }
}
