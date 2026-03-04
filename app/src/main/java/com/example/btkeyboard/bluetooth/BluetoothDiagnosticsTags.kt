package com.example.btkeyboard.bluetooth

internal object BluetoothDiagnosticsTags {
    const val REGISTRATION_RECONCILED = "REGISTRATION_RECONCILED"

    fun registrationReconciledWarning(): String {
        return "Warn($REGISTRATION_RECONCILED): registerApp returned false but callback confirmed registration."
    }
}
