package com.example.btkeyboard.bluetooth

import com.example.btkeyboard.model.ErrorCode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BluetoothDiagnosticsTagsTest {

    @Test
    fun registrationReconciledWarningIsNotClassifiedAsRegistrationFailed() {
        val warning = BluetoothDiagnosticsTags.registrationReconciledWarning()

        assertTrue(warning.contains(BluetoothDiagnosticsTags.REGISTRATION_RECONCILED))
        assertFalse(warning.contains(ErrorCode.REGISTRATION_FAILED.name))
    }
}
