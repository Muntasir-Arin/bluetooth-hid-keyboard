package com.example.btkeyboard.bluetooth

object HidDescriptorVersion {
    const val CURRENT = 2
    const val LEGACY = 1

    fun resolveAcknowledgedVersion(
        storedVersion: Int?,
        hasLegacyPairingData: Boolean,
    ): Int {
        return storedVersion ?: if (hasLegacyPairingData) LEGACY else CURRENT
    }
}
