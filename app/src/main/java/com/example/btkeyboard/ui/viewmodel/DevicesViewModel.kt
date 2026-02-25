package com.example.btkeyboard.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.example.btkeyboard.bluetooth.BluetoothHidController
import com.example.btkeyboard.model.HostDevice

class DevicesViewModel(
    private val controller: BluetoothHidController,
) : ViewModel() {

    val connectionState = controller.state
    val discoveredDevices = controller.discoveredDevices
    val bondedDevices = controller.bondedDevices
    val trustedDevices = controller.trustedDevices
    val requiresHostRepair = controller.requiresHostRepair

    fun bluetoothEnabled(): Boolean = controller.isBluetoothEnabled()

    fun hidSupported(): Boolean = controller.isHidDeviceSupported()

    fun refreshKnownDevices() = controller.refreshKnownDevices()

    fun startDiscovery(): Result<Unit> = controller.startDiscovery()

    fun stopDiscovery() = controller.stopDiscovery()

    fun pair(device: HostDevice): Result<Unit> = controller.pair(device)

    fun connect(device: HostDevice): Result<Unit> = controller.connect(device)

    fun disconnect(): Result<Unit> = controller.disconnect()

    fun forgetTrusted(address: String) = controller.forgetTrustedDevice(address)

    fun acknowledgeHostRepair() = controller.acknowledgeHidDescriptorMigration()
}
