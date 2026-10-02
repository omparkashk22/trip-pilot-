package com.example.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.example.TripPilotApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
                CoroutineScope(Dispatchers.IO).launch {
                    TripPilotApp.instance.preferencesManager.setEngineEnabled(false)
                }
                TripPilotApp.engineState.value = false
            }
        }
    }

    private lateinit var overlayNotificationManager: OverlayNotificationManager

    override fun onCreate() {
        super.onCreate()
        overlayNotificationManager = OverlayNotificationManager(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            TripPilotApp.engineState.value = false
            CoroutineScope(Dispatchers.IO).launch {
                TripPilotApp.instance.preferencesManager.setEngineEnabled(false)
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = overlayNotificationManager.createForegroundNotification()
        startForeground(NOTIFICATION_ID, notification)
        TripPilotApp.engineState.value = true
        CoroutineScope(Dispatchers.IO).launch {
            TripPilotApp.instance.preferencesManager.setEngineEnabled(true)
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
