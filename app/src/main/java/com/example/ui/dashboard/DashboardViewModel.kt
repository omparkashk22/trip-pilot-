package com.example.ui.dashboard

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.TripPilotApp
import com.example.data.local.PreferencesManager
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

    val isServiceRunning: StateFlow<Boolean> = TripPilotApp.isServiceRunning
    val currentForegroundApp: StateFlow<String?> = TripPilotApp.currentForegroundApp
    val detectionState: StateFlow<String> = TripPilotApp.detectionState
    val isOfferRecognitionFailed: StateFlow<Boolean> = TripPilotApp.offerRecognitionFailed

    val serviceMode: StateFlow<String> = preferencesManager.serviceMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "auto_accept")

    val alertOnAccept: StateFlow<Boolean> = preferencesManager.alertOnAccept
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val driverFilter: StateFlow<DriverFilter> = filterRepository.filter
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DriverFilter())

    private val _permissions = MutableStateFlow(PermissionStatuses())
    val permissions: StateFlow<PermissionStatuses> = _permissions.asStateFlow()

    private val _isFilterSavedRecently = MutableStateFlow(false)
    val isFilterSavedRecently: StateFlow<Boolean> = _isFilterSavedRecently.asStateFlow()

    // Temporary quick edit state
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

    fun toggleService(context: Context) {
        if (!permissions.value.areRequiredGranted) return

        if (isServiceRunning.value) {
            TripPilotForegroundService.stopService(context)
        } else {
            TripPilotForegroundService.startService(context)
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
}
