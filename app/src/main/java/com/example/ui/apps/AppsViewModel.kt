package com.example.ui.apps

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppResolver
import com.example.data.local.InstalledAppItem
import com.example.data.local.PreferencesManager
import com.example.data.model.TargetAppConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TargetAppUiItem(
    val config: TargetAppConfig,
    val resolvedPackage: String?,
    val isInstalled: Boolean,
    val isEnabled: Boolean,
    val appIcon: Drawable? = null
)

class AppsViewModel(
    private val appResolver: AppResolver,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _appItems = MutableStateFlow<List<TargetAppUiItem>>(emptyList())
    val appItems: StateFlow<List<TargetAppUiItem>> = _appItems.asStateFlow()

    private val _candidateDialogTarget = MutableStateFlow<TargetAppConfig?>(null)
    val candidateDialogTarget: StateFlow<TargetAppConfig?> = _candidateDialogTarget.asStateFlow()

    private val _allLaunchableApps = MutableStateFlow<List<InstalledAppItem>>(emptyList())
    val allLaunchableApps: StateFlow<List<InstalledAppItem>> = _allLaunchableApps.asStateFlow()

    fun loadApps() {
        viewModelScope.launch {
            val configs = appResolver.loadTargetConfigs()
            val items = mutableListOf<TargetAppUiItem>()

            for (target in configs) {
                val savedPkg = if (target.appId == "bharat_taxi") {
                    preferencesManager.resolvedBharatTaxiPackage.first()
                } else {
                    preferencesManager.resolvedRapidoPackage.first()
                }

                val resolved = appResolver.resolvePackageForTarget(target, savedPkg)
                val isInstalled = resolved != null && appResolver.isPackageInstalled(resolved)
                val isEnabled = if (target.appId == "bharat_taxi") {
                    preferencesManager.isBharatTaxiEnabled.first()
                } else {
                    preferencesManager.isRapidoEnabled.first()
                }
                val icon = if (isInstalled && resolved != null) appResolver.getAppIcon(resolved) else null

                items.add(
                    TargetAppUiItem(
                        config = target,
                        resolvedPackage = resolved,
                        isInstalled = isInstalled,
                        isEnabled = isEnabled,
                        appIcon = icon
                    )
                )

                if (resolved != null && resolved != savedPkg) {
                    preferencesManager.setResolvedPackage(target.appId, resolved)
                }
            }
            _appItems.value = items
        }
    }

    fun toggleApp(targetId: String, enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setAppEnabled(targetId, enabled)
            loadApps()
        }
    }

    fun openApp(context: Context, packageName: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try { context.startActivity(intent) } catch (_: Exception) {}
        }
    }

    fun openChangeAppDialog(target: TargetAppConfig) {
        viewModelScope.launch {
            val apps = appResolver.getAllLaunchableApps()
            _allLaunchableApps.value = apps
            _candidateDialogTarget.value = target
        }
    }

    fun dismissDialog() {
        _candidateDialogTarget.value = null
    }

    fun selectPackageForTarget(targetId: String, packageName: String) {
        viewModelScope.launch {
            preferencesManager.setResolvedPackage(targetId, packageName)
            _candidateDialogTarget.value = null
            loadApps()
        }
    }
}
