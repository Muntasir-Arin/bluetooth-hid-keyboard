package com.example.btkeyboard.app

import android.app.Application
import com.example.btkeyboard.bluetooth.BluetoothHidController
import com.example.btkeyboard.data.DataStoreDeviceStore
import com.example.btkeyboard.util.DiagnosticsLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class BtKeyboardApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val diagnosticsLogger: DiagnosticsLogger by lazy {
        DiagnosticsLogger()
    }

    val deviceStore: DataStoreDeviceStore by lazy {
        DataStoreDeviceStore(this)
    }

    val hidController: BluetoothHidController by lazy {
        BluetoothHidController(
            context = this,
            deviceStore = deviceStore,
            settingsStore = deviceStore,
            logger = diagnosticsLogger,
            scope = appScope,
        )
    }
}
