package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.example.data.local.AppDatabase
import com.example.data.local.AppResolver
import com.example.data.local.PreferencesManager
import com.example.data.remote.FirebaseManager
import com.example.data.repository.AuthRepository
import com.example.data.repository.FilterRepository
import com.example.data.repository.RideLogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TripPilotApp : Application() {

    lateinit var database: AppDatabase
        private set
    lateinit var preferencesManager: PreferencesManager
        private set
    lateinit var appResolver: AppResolver
        private set
    lateinit var firebaseManager: FirebaseManager
        private set
    lateinit var rideLogRepository: RideLogRepository
        private set
    lateinit var filterRepository: FilterRepository
        private set
    lateinit var authRepository: AuthRepository
        private set

    companion object {
        const val CHANNEL_SERVICE_ID = "trip_pilot_service_channel"
        const val CHANNEL_ALERT_ID = "trip_pilot_alert_channel"

        lateinit var instance: TripPilotApp
            private set

        // Global live engine status flows observed by UI & updated by OfferWatcherService
        val isServiceRunning = MutableStateFlow(false)
        val currentForegroundApp = MutableStateFlow<String?>(null) // e.g. "Bharat Taxi", "Rapido", null
        val detectionState = MutableStateFlow("Idle") // "Idle", "Watching", "Offer detected", "Offer screen not recognized"
        val offerRecognitionFailed = MutableStateFlow(false)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        createNotificationChannels()

        database = AppDatabase.getDatabase(this)
        preferencesManager = PreferencesManager(this)
        appResolver = AppResolver(this)
        firebaseManager = FirebaseManager(this)
        rideLogRepository = RideLogRepository(database.rideLogDao())
        filterRepository = FilterRepository(preferencesManager)
        authRepository = AuthRepository(firebaseManager)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val serviceChannel = NotificationChannel(
                CHANNEL_SERVICE_ID,
                "TripPilot Service Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows continuous background monitoring status for TripPilot"
            }

            val alertChannel = NotificationChannel(
                CHANNEL_ALERT_ID,
                "TripPilot Ride Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Heads-up alert notifications when matching ride offers are detected"
                enableVibration(true)
            }

            notificationManager.createNotificationChannel(serviceChannel)
            notificationManager.createNotificationChannel(alertChannel)
        }
    }
}
