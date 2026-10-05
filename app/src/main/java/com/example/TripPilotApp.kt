package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.example.data.local.AppDatabase
import com.example.data.local.AppResolver
import com.example.data.local.PreferencesManager
import com.example.data.model.AppRuntimeState
import com.example.data.model.AppRuntimeStatus
import com.example.data.remote.FirebaseManager
import com.example.data.repository.AuthRepository
import com.example.data.repository.FilterRepository
import com.example.data.repository.RideLogRepository
import com.example.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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

        // Single source of truth for engine state, backed by DataStore engineEnabled
        val engineState = MutableStateFlow(false)
        val isServiceRunning: MutableStateFlow<Boolean> get() = engineState

        val currentForegroundApp = MutableStateFlow<String?>(null) // e.g. "Bharat Taxi", "Rapido", null
        val detectionState = MutableStateFlow("Idle") // "Idle", "Watching", "Offer detected", "Offer screen not recognized"
        val offerRecognitionFailed = MutableStateFlow(false)

        val resolvedBharatPackageLive = MutableStateFlow<String?>(null)
        val resolvedRapidoPackageLive = MutableStateFlow<String?>(null)

        val defaultRuntimeStatuses = mapOf(
            "bharat_taxi" to AppRuntimeStatus(
                appId = "bharat_taxi",
                displayName = "Bharat Taxi",
                state = AppRuntimeState.NOT_INSTALLED
            ),
            "rapido" to AppRuntimeStatus(
                appId = "rapido",
                displayName = "Rapido",
                state = AppRuntimeState.NOT_INSTALLED
            )
        )
        val appRuntimeStatuses = MutableStateFlow<Map<String, AppRuntimeStatus>>(defaultRuntimeStatuses)

        val updateInfoLive = MutableStateFlow<com.example.data.remote.AppUpdateInfo?>(null)
    }

    private val appScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

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

        // One-time startup sanitization of allowed ride types
        appScope.launch(Dispatchers.IO) {
            try {
                preferencesManager.sanitizeAllowedRideTypes()
            } catch (_: Exception) {}
        }

        // Periodic GitHub version check (on startup and every 6 hours)
        appScope.launch(Dispatchers.IO) {
            val versionChecker = com.example.data.remote.VersionChecker()
            while (isActive) {
                try {
                    val info = versionChecker.checkLatestRelease(BuildConfig.VERSION_NAME)
                    if (info != null && info.isUpdateAvailable) {
                        updateInfoLive.value = info
                    }
                } catch (e: Exception) {
                    AppLogger.w("TripPilotApp", "Version check failed: ${e.message}")
                }
                kotlinx.coroutines.delay(6 * 3600 * 1000L)
            }
        }

        // Observe DataStore engineEnabled as the single source of truth for engineState
        appScope.launch {
            preferencesManager.engineEnabled.collect { enabled ->
                engineState.value = enabled
                if (!enabled) {
                    detectionState.value = "Idle"
                    currentForegroundApp.value = null
                    offerRecognitionFailed.value = false
                    val current = appRuntimeStatuses.value.toMutableMap()
                    current.forEach { (k, v) ->
                        if (v.state == AppRuntimeState.FOREGROUND || v.state == AppRuntimeState.OFFER_DETECTED || v.state == AppRuntimeState.NOT_RECOGNIZED) {
                            current[k] = v.copy(state = AppRuntimeState.IDLE)
                        }
                    }
                    appRuntimeStatuses.value = current
                }
            }
        }

        // Resolve and log target packages at startup
        appScope.launch(Dispatchers.IO) {
            val configs = appResolver.loadTargetConfigs()
            for (config in configs) {
                val savedPkg = if (config.appId == "bharat_taxi") {
                    preferencesManager.resolvedBharatTaxiPackage.first()
                } else {
                    preferencesManager.resolvedRapidoPackage.first()
                }
                val resolved = appResolver.resolvePackageForTarget(config, savedPkg)
                AppLogger.i("TripPilotApp", "Startup resolved package for ${config.appId}: $resolved")

                if (config.appId == "bharat_taxi") {
                    resolvedBharatPackageLive.value = resolved
                } else if (config.appId == "rapido") {
                    resolvedRapidoPackageLive.value = resolved
                }

                if (resolved != null && resolved != savedPkg) {
                    preferencesManager.setResolvedPackage(config.appId, resolved)
                }

                // Update initial runtime status with installation and package info
                val current = appRuntimeStatuses.value.toMutableMap()
                val existing = current[config.appId] ?: AppRuntimeStatus(config.appId, config.displayName)
                val isInstalled = resolved != null
                val isEnabled = if (config.appId == "bharat_taxi") {
                    preferencesManager.isBharatTaxiEnabled.first()
                } else {
                    preferencesManager.isRapidoEnabled.first()
                }
                val initialState = when {
                    !isInstalled -> AppRuntimeState.NOT_INSTALLED
                    !isEnabled -> AppRuntimeState.PAUSED
                    else -> AppRuntimeState.IDLE
                }
                current[config.appId] = existing.copy(
                    resolvedPackage = resolved,
                    isInstalled = isInstalled,
                    isEnabled = isEnabled,
                    state = initialState
                )
                appRuntimeStatuses.value = current
            }
        }
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
