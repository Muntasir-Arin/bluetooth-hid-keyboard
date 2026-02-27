package com.example.btkeyboard.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.btkeyboard.R
import com.example.btkeyboard.app.BtKeyboardApplication
import com.example.btkeyboard.model.ConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class BluetoothHidForegroundService : Service() {

    private val serviceScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var notificationJob: Job? = null

    private val app by lazy { application as BtKeyboardApplication }
    private val controller by lazy { app.hidController }

    override fun onCreate() {
        super.onCreate()
        val startupOk = runCatching {
            createNotificationChannel()
            startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.service_notification_disconnected)))
        }.onFailure {
            app.diagnosticsLogger.log("Service startup failed: ${it.message}")
        }.isSuccess
        if (!startupOk) {
            stopSelf()
            return
        }

        runCatching {
            controller.registerApp()
            controller.attemptAutoReconnect()
        }.onFailure {
            app.diagnosticsLogger.log("HID initialization failed: ${it.message}")
        }

        notificationJob = serviceScope.launch {
            controller.state.collectLatest { state ->
                val message = when (state) {
                    is ConnectionState.Connected -> getString(
                        R.string.service_notification_connected,
                        state.device.name,
                    )

                    else -> getString(R.string.service_notification_disconnected)
                }
                val manager = getSystemService(NotificationManager::class.java)
                manager.notify(NOTIFICATION_ID, buildNotification(message))
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = LocalBinder()

    override fun onDestroy() {
        notificationJob?.cancel()
        controller.unregisterApp()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.service_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(content: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle(getString(R.string.service_notification_title))
            .setContentText(content)
            .setOngoing(true)
            .build()
    }

    inner class LocalBinder : android.os.Binder() {
        fun service(): BluetoothHidForegroundService = this@BluetoothHidForegroundService
    }

    companion object {
        const val CHANNEL_ID = "bt_keyboard_service"
        const val NOTIFICATION_ID = 2001
        const val ACTION_STOP = "com.example.btkeyboard.action.STOP_SERVICE"
    }
}
