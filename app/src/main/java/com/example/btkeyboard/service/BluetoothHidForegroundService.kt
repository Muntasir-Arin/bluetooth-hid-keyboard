package com.example.btkeyboard.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class BluetoothHidForegroundService : Service() {

    private val serviceScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var notificationJob: Job? = null
    private var hostConnectionJob: Job? = null
    private var startupJob: Job? = null
    private var idleStopJob: Job? = null
    private var appInForeground: Boolean = true
    private var latestConnectionState: ConnectionState = ConnectionState.Idle
    private var latestIsHostConnected: Boolean = false

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
            running = false
            stopSelf()
            return
        }
        running = true

        startupJob = serviceScope.launch {
            controller.registerApp()
                .exceptionOrNull()
                ?.let { app.diagnosticsLogger.log("HID initialization failed: ${it.message}") }
            runCatching {
                controller.attemptAutoReconnect()
            }.onFailure {
                app.diagnosticsLogger.log("Auto reconnect failed: ${it.message}")
            }
        }

        notificationJob = serviceScope.launch {
            controller.state.collectLatest { state ->
                latestConnectionState = state
                val message = when (state) {
                    is ConnectionState.Connected -> getString(
                        R.string.service_notification_connected,
                        state.device.name,
                    )

                    else -> getString(R.string.service_notification_disconnected)
                }
                val manager = getSystemService(NotificationManager::class.java)
                manager.notify(NOTIFICATION_ID, buildNotification(message))
                reevaluateIdleStopPolicy()
            }
        }
        hostConnectionJob = serviceScope.launch {
            controller.isHostConnected.collectLatest { connected ->
                latestIsHostConnected = connected
                reevaluateIdleStopPolicy()
            }
        }
        reevaluateIdleStopPolicy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_APP_FOREGROUND -> {
                appInForeground = true
            }

            ACTION_APP_BACKGROUND -> {
                appInForeground = false
            }
        }
        reevaluateIdleStopPolicy()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = LocalBinder()

    override fun onDestroy() {
        running = false
        notificationJob?.cancel()
        hostConnectionJob?.cancel()
        startupJob?.cancel()
        idleStopJob?.cancel()
        serviceScope.launch(Dispatchers.IO) {
            withTimeoutOrNull(SERVICE_SHUTDOWN_TIMEOUT_MS) {
                controller.unregisterApp()
            }
        }.invokeOnCompletion {
            serviceScope.cancel()
        }
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

    private fun reevaluateIdleStopPolicy() {
        if (ServiceIdlePolicy.shouldArmIdleTimer(
                appInForeground = appInForeground,
                isHostConnected = latestIsHostConnected,
                state = latestConnectionState,
            )
        ) {
            armIdleTimer()
        } else {
            cancelIdleTimer()
        }
    }

    private fun armIdleTimer() {
        idleStopJob?.cancel()
        idleStopJob = serviceScope.launch {
            delay(IDLE_TIMEOUT_MS)
            if (!ServiceIdlePolicy.shouldArmIdleTimer(
                    appInForeground = appInForeground,
                    isHostConnected = latestIsHostConnected,
                    state = latestConnectionState,
                )
            ) {
                return@launch
            }
            runCatching {
                controller.stopDiscovery()
            }.onFailure {
                app.diagnosticsLogger.log("Failed to stop discovery during idle shutdown: ${it.message}")
            }
            stopSelf()
        }
    }

    private fun cancelIdleTimer() {
        idleStopJob?.cancel()
        idleStopJob = null
    }

    inner class LocalBinder : android.os.Binder() {
        fun service(): BluetoothHidForegroundService = this@BluetoothHidForegroundService
    }

    companion object {
        const val CHANNEL_ID = "bt_keyboard_service"
        const val NOTIFICATION_ID = 2001
        const val ACTION_STOP = "com.example.btkeyboard.action.STOP_SERVICE"
        const val ACTION_APP_FOREGROUND = "com.example.btkeyboard.action.APP_FOREGROUND"
        const val ACTION_APP_BACKGROUND = "com.example.btkeyboard.action.APP_BACKGROUND"
        @Volatile
        private var running: Boolean = false
        private const val SERVICE_SHUTDOWN_TIMEOUT_MS = 2_000L
        private const val IDLE_TIMEOUT_MS = 120_000L

        fun isRunning(): Boolean = running
    }
}
