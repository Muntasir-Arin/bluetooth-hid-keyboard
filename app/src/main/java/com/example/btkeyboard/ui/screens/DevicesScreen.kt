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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.btkeyboard.model.ConnectionState
import com.example.btkeyboard.model.HidCapability
import com.example.btkeyboard.model.HostDevice
import com.example.btkeyboard.ui.components.AppButton
import com.example.btkeyboard.ui.components.AppButtonVariant
import com.example.btkeyboard.ui.components.AppCard
import com.example.btkeyboard.ui.components.AppCardVariant
import com.example.btkeyboard.ui.components.ConnectionStatusPill
import com.example.btkeyboard.ui.components.EmptyState
import com.example.btkeyboard.ui.components.InfoChip
import com.example.btkeyboard.ui.components.SectionHeader
import com.example.btkeyboard.ui.theme.UiTokens

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DevicesScreen(
    bluetoothEnabled: Boolean,
    hidCapability: HidCapability,
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
    val discoveryBlockedByConnection =
        connectionState is ConnectionState.Connected || connectionState is ConnectionState.Connecting

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(UiTokens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(UiTokens.SectionSpacing),
    ) {
        item {
            AppCard(
                modifier = Modifier.fillMaxWidth(),
                variant = AppCardVariant.Emphasis,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(UiTokens.CardPadding),
                    verticalArrangement = Arrangement.spacedBy(UiTokens.CardSpacing),
                ) {
                    SectionHeader(
                        title = "Connection",
                        subtitle = "Manage Bluetooth discovery and host pairing",
                    )
                    ConnectionStatusPill(connectionState = connectionState)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(UiTokens.Space2),
                        verticalArrangement = Arrangement.spacedBy(UiTokens.Space2),
                    ) {
                        InfoChip(label = "Bluetooth", value = if (bluetoothEnabled) "ON" else "OFF")
                        InfoChip(label = "HID", value = hidCapability.label())
                    }

                    if (isDiscovering) {
                        AppButton(
                            onClick = onStopDiscovery,
                            variant = AppButtonVariant.Primary,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Stop Discovery")
                        }
                    } else {
                        AppButton(
                            onClick = onStartDiscovery,
                            enabled = !discoveryBlockedByConnection,
                            variant = AppButtonVariant.Primary,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Start Discovery")
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(UiTokens.Space2),
                        verticalArrangement = Arrangement.spacedBy(UiTokens.Space2),
                    ) {
                        AppButton(
                            onClick = onOpenBluetoothSettings,
                            variant = AppButtonVariant.Secondary,
                        ) {
                            Text("BT Settings")
                        }
                        AppButton(
                            onClick = onRefresh,
                            variant = AppButtonVariant.Secondary,
                        ) {
                            Text("Refresh")
                        }
                        if (connectionState is ConnectionState.Connected) {
                            AppButton(
                                onClick = onDisconnect,
                                variant = AppButtonVariant.Secondary,
                            ) {
                                Text("Disconnect")
                            }
                        }
                    }

                    if (discoveryBlockedByConnection) {
                        Text(
                            text = "Discovery is unavailable while connected. Disconnect first or use bonded devices.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (hidCapability == HidCapability.UNAVAILABLE) {
                        Text(
                            text = "This phone or ROM may not expose Bluetooth HID Device mode reliably.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (requiresHostRepair) {
            item {
                AppCard(
                    modifier = Modifier.fillMaxWidth(),
                    variant = AppCardVariant.Emphasis,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(UiTokens.CardPadding),
                        verticalArrangement = Arrangement.spacedBy(UiTokens.CardSpacing),
                    ) {
                        SectionHeader(
                            title = "Re-pair required",
                            subtitle = "Touchpad report support changed the HID descriptor.",
                        )
                        Text(
                            text = "Forget this phone from your host and pair again to restore full input support.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(UiTokens.Space2),
                            verticalArrangement = Arrangement.spacedBy(UiTokens.Space2),
                        ) {
                            AppButton(
                                onClick = onOpenBluetoothSettings,
                                variant = AppButtonVariant.Primary,
                            ) {
                                Text("Open BT Settings")
                            }
                            AppButton(
                                onClick = onAcknowledgeRepair,
                                variant = AppButtonVariant.Secondary,
                            ) {
                                Text("I Re-paired")
                            }
                        }
                    }
                }
            }
        }

        item { SectionHeader(title = "Trusted devices") }
        if (trustedDevices.isEmpty()) {
            item {
                EmptyState("No trusted devices yet.")
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

        item { SectionHeader(title = "Bonded devices") }
        if (bondedDevices.isEmpty()) {
            item {
                EmptyState("No bonded devices.")
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

        item { SectionHeader(title = "Discovered devices") }
        if (discoveredDevices.isEmpty()) {
            item {
                val emptyMessage = if (isDiscovering) {
                    "Scanning nearby hosts..."
                } else {
                    "Start discovery to find nearby hosts."
                }
                EmptyState(
                    message = emptyMessage,
                    actionLabel = if (!isDiscovering && !discoveryBlockedByConnection) "Start discovery" else null,
                    onAction = if (!isDiscovering && !discoveryBlockedByConnection) onStartDiscovery else null,
                )
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

private fun HidCapability.label(): String {
    return when (this) {
        HidCapability.UNKNOWN -> "Checking"
        HidCapability.AVAILABLE -> "Available"
        HidCapability.UNAVAILABLE -> "Unavailable"
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
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        variant = AppCardVariant.Interactive,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(UiTokens.CardPadding),
            verticalArrangement = Arrangement.spacedBy(UiTokens.Space2),
        ) {
            SectionHeader(
                title = device.name,
                subtitle = device.connectionLabel(),
            )
            Text(
                text = device.address,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(UiTokens.Space2),
                verticalArrangement = Arrangement.spacedBy(UiTokens.Space2),
            ) {
                if (!device.bonded) {
                    AppButton(
                        onClick = { onPair(device) },
                        variant = AppButtonVariant.Primary,
                    ) {
                        Text("Pair")
                    }
                } else {
                    AppButton(
                        onClick = { onConnect(device) },
                        variant = AppButtonVariant.Primary,
                    ) {
                        Text(if (device.connected) "Reconnect" else "Connect")
                    }
                }
                onForget?.let {
                    AppButton(
                        onClick = it,
                        variant = AppButtonVariant.Secondary,
                    ) {
                        Text("Forget")
                    }
                }
            }
        }
    }
}

private fun HostDevice.connectionLabel(): String {
    return when {
        connected -> "Connected"
        bonded -> "Bonded"
        else -> "Available"
    }
}
