package com.example.btkeyboard.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.btkeyboard.bluetooth.BluetoothHidController
import com.example.btkeyboard.model.HostDevice
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class DevicesViewModel(
    private val controller: BluetoothHidController,
) : ViewModel() {

    val connectionState = controller.state
    val discoveredDevices = controller.discoveredDevices
    val bondedDevices = controller.bondedDevices
    val trustedDevices = controller.trustedDevices
    val requiresHostRepair = controller.requiresHostRepair
    val hidCapability = controller.hidCapability

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 32)
    val events: SharedFlow<String> = _events.asSharedFlow()

    fun bluetoothEnabled(): Boolean = controller.isBluetoothEnabled()

    fun refreshKnownDevices() = controller.refreshKnownDevices()

    fun startDiscovery() {
        viewModelScope.launch {
            controller.startDiscovery()
                .exceptionOrNull()
                ?.message
                ?.let { _events.emit(it) }
        }
    }

    fun stopDiscovery() {
        viewModelScope.launch {
            controller.stopDiscovery()
        }
    }

    fun pair(device: HostDevice) {
        viewModelScope.launch {
            controller.pair(device)
                .exceptionOrNull()
                ?.message
                ?.let { _events.emit(it) }
        }
    }

    fun connect(device: HostDevice) {
        viewModelScope.launch {
            controller.connect(device)
                .exceptionOrNull()
                ?.message
                ?.let { _events.emit(it) }
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            controller.disconnect()
                .exceptionOrNull()
                ?.message
                ?.let { _events.emit(it) }
        }
    }

    fun forgetTrusted(address: String) = controller.forgetTrustedDevice(address)

    fun acknowledgeHostRepair() = controller.acknowledgeHidDescriptorMigration()
}
