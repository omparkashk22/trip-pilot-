package com.example.ui.dashboard

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.TripPilotApp
import com.example.data.local.PreferencesManager
import com.example.data.model.AppRuntimeStatus
import com.example.data.model.DriverFilter
import com.example.data.repository.FilterRepository
import com.example.service.OfferWatcherService
import com.example.service.TripPilotForegroundService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PermissionStatuses(
    val accessibilityGranted: Boolean = false,
    val overlayGranted: Boolean = false,
    val notificationsGranted: Boolean = false,
    val batteryOptimizationIgnored: Boolean = false
) {
    val areRequiredGranted: Boolean
        get() = accessibilityGranted && overlayGranted && notificationsGranted
}

class DashboardViewModel(
    private val preferencesManager: PreferencesManager,
    private val filterRepository: FilterRepository
) : ViewModel() {

    // Single source of truth for engine state
    val isServiceRunning: StateFlow<Boolean> = TripPilotApp.engineState
    val currentForegroundApp: StateFlow<String?> = TripPilotApp.currentForegroundApp
    val detectionState: StateFlow<String> = TripPilotApp.detectionState
    val isOfferRecognitionFailed: StateFlow<Boolean> = TripPilotApp.offerRecognitionFailed

    // Per-app runtime status map exposed to Dashboard
    val appRuntimeStatuses: StateFlow<Map<String, AppRuntimeStatus>> = TripPilotApp.appRuntimeStatuses

    // GitHub Release Update Info
    val updateInfo: StateFlow<com.example.data.remote.AppUpdateInfo?> = TripPilotApp.updateInfoLive

    val showDebugInfo: StateFlow<Boolean> = preferencesManager.showDebugInfo
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val resolvedBharatPackage: StateFlow<String?> = preferencesManager.resolvedBharatTaxiPackage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val resolvedRapidoPackage: StateFlow<String?> = preferencesManager.resolvedRapidoPackage
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val serviceMode: StateFlow<String> = preferencesManager.serviceMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "auto_accept")

    val alertOnAccept: StateFlow<Boolean> = preferencesManager.alertOnAccept
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val soundEnabled: StateFlow<Boolean> = preferencesManager.isSoundEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val vibrationEnabled: StateFlow<Boolean> = preferencesManager.isVibrationEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val driverFilter: StateFlow<DriverFilter> = filterRepository.filter
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DriverFilter())

    private val _permissions = MutableStateFlow(PermissionStatuses())
    val permissions: StateFlow<PermissionStatuses> = _permissions.asStateFlow()

    private val _isFilterSavedRecently = MutableStateFlow(false)
    val isFilterSavedRecently: StateFlow<Boolean> = _isFilterSavedRecently.asStateFlow()

    // Quick fare edit
    val quickMinFare = MutableStateFlow("80")
    val quickMaxFare = MutableStateFlow("")
    val quickUnlimitedMax = MutableStateFlow(true)

    init {
        viewModelScope.launch {
            driverFilter.collect { f ->
                quickMinFare.value = f.minFare.toInt().toString()
                quickMaxFare.value = f.maxFare?.toInt()?.toString() ?: ""
                quickUnlimitedMax.value = f.isUnlimitedMaxFare
            }
        }
    }

    fun refreshPermissions(context: Context) {
        val accessibility = OfferWatcherService.instance != null
        val overlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true
        val notifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else true

        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val battery = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && pm != null) {
            pm.isIgnoringBatteryOptimizations(context.packageName)
        } else true

        _permissions.value = PermissionStatuses(
            accessibilityGranted = accessibility,
            overlayGranted = overlay,
            notificationsGranted = notifications,
            batteryOptimizationIgnored = battery
        )
    }

    fun startEngine(context: Context) {
        if (!permissions.value.areRequiredGranted) return
        viewModelScope.launch {
            preferencesManager.setEngineEnabled(true)
            TripPilotForegroundService.startService(context)
        }
    }

    fun stopEngine(context: Context) {
        viewModelScope.launch {
            preferencesManager.setEngineEnabled(false)
            TripPilotForegroundService.stopService(context)
        }
    }

    fun setServiceMode(mode: String) {
        viewModelScope.launch {
            preferencesManager.setServiceMode(mode)
        }
    }

    fun setAlertOnAccept(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setAlertOnAccept(enabled)
        }
    }

    fun saveQuickFare() {
        viewModelScope.launch {
            val min = quickMinFare.value.toDoubleOrNull() ?: 80.0
            val max = if (quickUnlimitedMax.value) null else quickMaxFare.value.toDoubleOrNull()
            val current = driverFilter.value
            filterRepository.saveFilter(
                current.copy(
                    minFare = min,
                    maxFare = max,
                    isUnlimitedMaxFare = quickUnlimitedMax.value
                )
            )
            _isFilterSavedRecently.value = true
            delay(2000)
            _isFilterSavedRecently.value = false
        }
    }

    fun dismissUpdateBanner() {
        TripPilotApp.updateInfoLive.value = null
    }

    fun checkForUpdatesManual(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val checker = com.example.data.remote.VersionChecker()
                val info = checker.checkLatestRelease(com.example.BuildConfig.VERSION_NAME)
                if (info != null && info.isUpdateAvailable) {
                    TripPilotApp.updateInfoLive.value = info
                    onResult(true, "Update found: v${info.latestVersion}")
                } else if (info != null) {
                    onResult(false, "TripPilot is up to date (v${com.example.BuildConfig.VERSION_NAME})")
                } else {
                    onResult(false, "Unable to reach GitHub. Try again later.")
                }
            } catch (e: Exception) {
                onResult(false, "Check failed: ${e.message}")
            }
        }
    }
}
