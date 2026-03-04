package com.example.btkeyboard.bluetooth

import android.bluetooth.BluetoothDevice
import org.junit.Assert.assertEquals
import org.junit.Test

class PairingReconcilerTest {

    @Test
    fun resolvesSuccessWhenPendingDeviceBecomesBonded() {
        val resolution = PairingReconciler.resolve(
            pendingAddress = "AA:BB:CC:DD:EE:FF",
            deviceAddress = "AA:BB:CC:DD:EE:FF",
            bondState = BluetoothDevice.BOND_BONDED,
            previousBondState = BluetoothDevice.BOND_BONDING,
        )

        assertEquals(PairingResolution.SUCCESS, resolution)
    }

    @Test
    fun resolvesFailureWhenPendingBondReturnsToNone() {
        val resolution = PairingReconciler.resolve(
            pendingAddress = "AA:BB:CC:DD:EE:FF",
            deviceAddress = "AA:BB:CC:DD:EE:FF",
            bondState = BluetoothDevice.BOND_NONE,
            previousBondState = BluetoothDevice.BOND_BONDING,
        )

        assertEquals(PairingResolution.FAILED_OR_CANCELLED, resolution)
    }

    @Test
    fun ignoresEventsForDifferentAddressOrNoPendingRequest() {
        val differentDevice = PairingReconciler.resolve(
            pendingAddress = "AA:BB:CC:DD:EE:FF",
            deviceAddress = "11:22:33:44:55:66",
            bondState = BluetoothDevice.BOND_BONDED,
            previousBondState = BluetoothDevice.BOND_BONDING,
        )
        val noPending = PairingReconciler.resolve(
            pendingAddress = null,
            deviceAddress = "AA:BB:CC:DD:EE:FF",
            bondState = BluetoothDevice.BOND_BONDED,
            previousBondState = BluetoothDevice.BOND_BONDING,
        )

        assertEquals(PairingResolution.NONE, differentDevice)
        assertEquals(PairingResolution.NONE, noPending)
    }
}
