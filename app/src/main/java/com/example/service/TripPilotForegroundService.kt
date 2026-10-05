package com.example.service

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.example.TripPilotApp
import com.example.util.TurboModeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class TripPilotForegroundService : Service() {

    companion object {
        const val ACTION_START = "com.example.service.ACTION_START"
        const val ACTION_STOP = "com.example.service.ACTION_STOP"
        private const val NOTIFICATION_ID = 1001

        fun startService(context: Context) {
            val intent = Intent(context, TripPilotForegroundService::class.java).apply {
                action = ACTION_START
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, TripPilotForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {
                // If service is not running or restricted
                TripPilotApp.engineState.value = false
                TurboModeManager.release()
                CoroutineScope(Dispatchers.IO).launch {
                    TripPilotApp.instance.preferencesManager.setEngineEnabled(false)
                }
            }
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var overlayNotificationManager: OverlayNotificationManager
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        overlayNotificationManager = OverlayNotificationManager(this)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Observe engine state and turbo mode to maintain wake lock and update notification
        serviceScope.launch {
            val app = TripPilotApp.instance
            combine(
                app.preferencesManager.engineEnabled,
                app.preferencesManager.turboMode
            ) { enabled, turbo ->
                enabled to turbo
            }.collect { (enabled, turbo) ->
                TurboModeManager.updateWakeLock(this@TripPilotForegroundService, enabled, turbo)
                if (enabled) {
                    val notif = overlayNotificationManager.createForegroundNotification(isTurbo = turbo)
                    notificationManager.notify(NOTIFICATION_ID, notif)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            TripPilotApp.engineState.value = false
            TurboModeManager.release()
            serviceScope.launch {
                TripPilotApp.instance.preferencesManager.setEngineEnabled(false)
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = overlayNotificationManager.createForegroundNotification(isTurbo = true)
        startForeground(NOTIFICATION_ID, notification)
        TripPilotApp.engineState.value = true
        TurboModeManager.updateWakeLock(this, engineRunning = true, turboEnabled = true)
        serviceScope.launch {
            TripPilotApp.instance.preferencesManager.setEngineEnabled(true)
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        TurboModeManager.release()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
