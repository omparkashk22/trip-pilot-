package com.example.ui.settings

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.PreferencesManager
import com.example.data.model.StrategyStats
import com.example.data.repository.FilterRepository
import com.example.domain.tree.ScreenTreeDumper
import com.example.domain.tree.ScreenTreeSummary
import com.example.service.OfferWatcherService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class SettingsViewModel(
    private val preferencesManager: PreferencesManager,
    private val filterRepository: FilterRepository
) : ViewModel() {

    val currentLanguage: StateFlow<String> = preferencesManager.language
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "en")

    val currentTheme: StateFlow<String> = preferencesManager.theme
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "dark")

    val textScale: StateFlow<Float> = preferencesManager.textScale
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1.0f)

    val keepScreenOn: StateFlow<Boolean> = preferencesManager.keepScreenOn
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val processTestRequests: StateFlow<Boolean> = preferencesManager.processTestRequests
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val tapMethod: StateFlow<String> = preferencesManager.tapMethod
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "auto")

    val soundEnabled: StateFlow<Boolean> = preferencesManager.isSoundEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val vibrationEnabled: StateFlow<Boolean> = preferencesManager.isVibrationEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val turboMode: StateFlow<Boolean> = preferencesManager.turboMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val showDebugInfo: StateFlow<Boolean> = preferencesManager.showDebugInfo
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val captureOnNextOffer: StateFlow<Boolean> = preferencesManager.captureOnNextOffer
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val strategyStats: StateFlow<Map<String, StrategyStats>> = preferencesManager.strategyStats
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // Screen Inspector state
    val countdownSeconds = MutableStateFlow<Int?>(null)
    val latestDumpSummary = MutableStateFlow<ScreenTreeSummary?>(null)
    val existingDumps = MutableStateFlow<List<File>>(emptyList())

    // Simulator state
    val isSimulatorVisible = MutableStateFlow(false)
    val simulatorTapResult = MutableStateFlow<String?>(null)

    // Version update check state
    val isCheckingForUpdate = MutableStateFlow(false)
    val updateCheckResult = MutableStateFlow<String?>(null)
    val latestUpdateInfo = MutableStateFlow<com.example.data.remote.AppUpdateInfo?>(null)

    fun checkForUpdates() {
        if (isCheckingForUpdate.value) return
        viewModelScope.launch {
            isCheckingForUpdate.value = true
            updateCheckResult.value = null
            try {
                val checker = com.example.data.remote.VersionChecker()
                val info = checker.checkLatestRelease(com.example.BuildConfig.VERSION_NAME)
                if (info != null && info.isUpdateAvailable) {
                    latestUpdateInfo.value = info
                    com.example.TripPilotApp.updateInfoLive.value = info
                    updateCheckResult.value = "New version available: v${info.latestVersion}"
                } else if (info != null) {
                    latestUpdateInfo.value = null
                    updateCheckResult.value = "TripPilot is up to date (v${com.example.BuildConfig.VERSION_NAME})"
                } else {
                    latestUpdateInfo.value = null
                    updateCheckResult.value = "Unable to reach GitHub. Check internet connection."
                }
            } catch (e: Exception) {
                updateCheckResult.value = "Check failed: ${e.message}"
            } finally {
                isCheckingForUpdate.value = false
            }
        }
    }

    fun setLanguage(code: String) {
        viewModelScope.launch {
            preferencesManager.setLanguage(code)
        }
    }

    fun setTheme(theme: String) {
        viewModelScope.launch {
            preferencesManager.setTheme(theme)
        }
    }

    fun setTextScale(scale: Float) {
        viewModelScope.launch {
            preferencesManager.setTextScale(scale)
        }
    }

    fun setKeepScreenOn(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setKeepScreenOn(enabled)
        }
    }

    fun setProcessTestRequests(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setProcessTestRequests(enabled)
        }
    }

    fun setTapMethod(method: String) {
        viewModelScope.launch {
            preferencesManager.setTapMethod(method)
        }
    }

    fun setSoundEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setSoundEnabled(enabled)
        }
    }

    fun setVibrationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setVibrationEnabled(enabled)
        }
    }

    fun setTurboMode(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setTurboMode(enabled)
        }
    }

    fun setShowDebugInfo(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setShowDebugInfo(enabled)
        }
    }

    fun setCaptureOnNextOffer(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setCaptureOnNextOffer(enabled)
        }
    }

    fun resetAllFilters() {
        viewModelScope.launch {
            filterRepository.resetFilters()
        }
    }

    fun refreshExistingDumps(context: Context) {
        viewModelScope.launch {
            existingDumps.value = ScreenTreeDumper.getExistingDumps(context)
        }
    }

    fun startScreenCapture(context: Context, appPackage: String) {
        viewModelScope.launch {
            for (sec in 5 downTo 1) {
                countdownSeconds.value = sec
                delay(1000)
            }
            countdownSeconds.value = null

            val service = OfferWatcherService.instance
            val windows = service?.windows
            val root = service?.rootInActiveWindow

            val summary = ScreenTreeDumper.dumpScreenTree(
                context = context,
                windows = windows,
                rootNode = root,
                appPackage = appPackage
            )
            latestDumpSummary.value = summary
            refreshExistingDumps(context)
        }
    }

    fun shareDumpFile(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try { context.startActivity(Intent.createChooser(intent, "Share Screen Tree Dump")) } catch (_: Exception) {}
    }

    fun deleteAllDumps(context: Context) {
        viewModelScope.launch {
            ScreenTreeDumper.deleteAllDumps(context)
            refreshExistingDumps(context)
        }
    }

    fun openSimulator() {
        isSimulatorVisible.value = true
        simulatorTapResult.value = null
    }

    fun closeSimulator() {
        isSimulatorVisible.value = false
    }

    fun recordSimulatorTapResult(strategy: String, latencyMs: Long) {
        simulatorTapResult.value = "Tap succeeded via $strategy in ${latencyMs}ms ✓"
    }
}
