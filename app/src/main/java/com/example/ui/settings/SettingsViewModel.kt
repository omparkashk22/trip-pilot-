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
    val lastCheckedTime = MutableStateFlow<String?>(null)
    val isDiagnosticsExpanded = MutableStateFlow(false)
    val downloadProgress = MutableStateFlow<Float?>(null)
    val downloadStatusMessage = MutableStateFlow<String?>(null)
    val downloadedApkFile = MutableStateFlow<File?>(null)
    val installErrorMessage = MutableStateFlow<String?>(null)
    val clipboardMessage = MutableStateFlow<String?>(null)

    val includePreReleases: StateFlow<Boolean> = preferencesManager.includePreReleases
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val updateSourceType: StateFlow<String> = preferencesManager.updateSourceType
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "GITHUB")

    fun setIncludePreReleases(include: Boolean) {
        viewModelScope.launch {
            preferencesManager.setIncludePreReleases(include)
        }
    }

    fun setUpdateSourceType(type: String) {
        viewModelScope.launch {
            preferencesManager.setUpdateSourceType(type)
        }
    }

    fun toggleDiagnostics() {
        isDiagnosticsExpanded.value = !isDiagnosticsExpanded.value
    }

    fun checkForUpdates(context: Context? = null) {
        if (isCheckingForUpdate.value) return
        viewModelScope.launch {
            isCheckingForUpdate.value = true
            updateCheckResult.value = null
            installErrorMessage.value = null
            try {
                val checker = com.example.data.remote.VersionChecker()
                val isPre = includePreReleases.first()
                val src = if (updateSourceType.first() == "HOSTED_JSON") {
                    com.example.data.remote.UpdateSourceType.HOSTED_JSON
                } else {
                    com.example.data.remote.UpdateSourceType.GITHUB
                }

                val info = checker.checkUpdate(
                    context = context,
                    sourceType = src,
                    includePreReleases = isPre
                )
                latestUpdateInfo.value = info

                val timeFmt = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
                lastCheckedTime.value = timeFmt.format(java.util.Date())

                when (info.status) {
                    com.example.data.remote.UpdateResultState.UPDATE_AVAILABLE -> {
                        com.example.TripPilotApp.updateInfoLive.value = info
                        updateCheckResult.value = "New version available: v${info.latestVersion} (build ${info.remoteBuild})"
                    }
                    com.example.data.remote.UpdateResultState.UP_TO_DATE -> {
                        com.example.TripPilotApp.updateInfoLive.value = null
                        updateCheckResult.value = "You have the latest version (build ${info.installedBuild})"
                    }
                    com.example.data.remote.UpdateResultState.ERROR -> {
                        com.example.TripPilotApp.updateInfoLive.value = null
                        updateCheckResult.value = info.errorMessage ?: "Update check encountered an error"
                    }
                }
            } catch (e: Exception) {
                updateCheckResult.value = "Check failed: ${e.message}"
            } finally {
                isCheckingForUpdate.value = false
            }
        }
    }

    fun downloadAndInstallUpdate(context: Context) {
        val info = latestUpdateInfo.value ?: return
        if (info.downloadUrl.isBlank()) return

        val cached = downloadedApkFile.value
        if (cached != null && cached.exists() && cached.length() > 0) {
            triggerInstall(context, cached)
            return
        }

        viewModelScope.launch {
            downloadProgress.value = 0f
            downloadStatusMessage.value = "Starting download..."
            installErrorMessage.value = null

            try {
                val apkFile = com.example.data.remote.AppUpdateInstaller.downloadApk(
                    context = context,
                    downloadUrl = info.downloadUrl,
                    expectedSizeBytes = if (info.apkSizeBytes > 0) info.apkSizeBytes else null,
                    expectedSha256 = info.sha256
                ) { downloaded, total, pct ->
                    downloadProgress.value = pct / 100f
                    val dlMb = downloaded / (1024f * 1024f)
                    if (total > 0) {
                        val totMb = total / (1024f * 1024f)
                        downloadStatusMessage.value = String.format(java.util.Locale.US, "Downloading: %.1f MB / %.1f MB (%d%%)", dlMb, totMb, pct)
                    } else {
                        downloadStatusMessage.value = String.format(java.util.Locale.US, "Downloading: %.1f MB", dlMb)
                    }
                }

                downloadedApkFile.value = apkFile
                downloadProgress.value = null
                downloadStatusMessage.value = "Download complete"

                triggerInstall(context, apkFile)
            } catch (e: Exception) {
                downloadProgress.value = null
                downloadStatusMessage.value = null
                installErrorMessage.value = "Download failed: ${e.message}"
            }
        }
    }

    private fun triggerInstall(context: Context, apkFile: File) {
        val res = com.example.data.remote.AppUpdateInstaller.triggerInstall(context, apkFile)
        when (res) {
            is com.example.data.remote.InstallResult.Success -> {
                installErrorMessage.value = null
            }
            is com.example.data.remote.InstallResult.PermissionRequired -> {
                installErrorMessage.value = "Permission required: Please enable 'Allow from this source' to install updates."
                com.example.data.remote.AppUpdateInstaller.openInstallPermissionSettings(context)
            }
            is com.example.data.remote.InstallResult.Error -> {
                installErrorMessage.value = res.message
            }
        }
    }

    fun resumeInstallAfterPermission(context: Context) {
        val file = downloadedApkFile.value
        if (file != null && file.exists() && com.example.data.remote.AppUpdateInstaller.canRequestInstalls(context)) {
            triggerInstall(context, file)
        }
    }

    fun copyDiagnostics(context: Context) {
        val raw = latestUpdateInfo.value?.diagnostics?.rawText ?: "No diagnostics available. Please run an update check first."
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("TripPilot Diagnostics", raw)
        clipboard.setPrimaryClip(clip)
        clipboardMessage.value = "Diagnostics copied to clipboard"
    }

    fun clearClipboardMessage() {
        clipboardMessage.value = null
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
