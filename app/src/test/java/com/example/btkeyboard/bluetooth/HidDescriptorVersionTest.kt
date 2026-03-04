package com.example.btkeyboard.bluetooth

import org.junit.Assert.assertEquals
import org.junit.Test

class HidDescriptorVersionTest {

    @Test
    fun storedVersionTakesPriority() {
        val resolved = HidDescriptorVersion.resolveAcknowledgedVersion(
            storedVersion = 7,
            hasLegacyPairingData = true,
        )

        assertEquals(7, resolved)
    }

    @Test
    fun legacyPairingDataDefaultsToLegacyVersion() {
        val resolved = HidDescriptorVersion.resolveAcknowledgedVersion(
            storedVersion = null,
            hasLegacyPairingData = true,
        )

        assertEquals(HidDescriptorVersion.LEGACY, resolved)
    }

    @Test
    fun freshInstallDefaultsToCurrentVersion() {
        val resolved = HidDescriptorVersion.resolveAcknowledgedVersion(
            storedVersion = null,
            hasLegacyPairingData = false,
        )

        assertEquals(HidDescriptorVersion.CURRENT, resolved)
    }
}
