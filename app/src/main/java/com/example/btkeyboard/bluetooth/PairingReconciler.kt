package com.example.btkeyboard.bluetooth

import android.bluetooth.BluetoothDevice

internal enum class PairingResolution {
    NONE,
    SUCCESS,
    FAILED_OR_CANCELLED,
}

internal object PairingReconciler {
    fun resolve(
        pendingAddress: String?,
        deviceAddress: String?,
        bondState: Int,
        previousBondState: Int,
    ): PairingResolution {
        if (pendingAddress.isNullOrBlank() || deviceAddress.isNullOrBlank()) {
            return PairingResolution.NONE
        }
        if (!pendingAddress.equals(deviceAddress, ignoreCase = true)) {
            return PairingResolution.NONE
        }

        return when (bondState) {
            BluetoothDevice.BOND_BONDED -> PairingResolution.SUCCESS
            BluetoothDevice.BOND_NONE -> {
                if (previousBondState == BluetoothDevice.BOND_BONDING || previousBondState == BluetoothDevice.BOND_BONDED) {
                    PairingResolution.FAILED_OR_CANCELLED
                } else {
                    PairingResolution.NONE
                }
            }

            else -> PairingResolution.NONE
        }
    }
}
