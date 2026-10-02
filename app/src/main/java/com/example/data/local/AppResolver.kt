package com.example.data.local

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import com.example.data.model.TargetAppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

data class InstalledAppItem(
    val packageName: String,
    val appLabel: String,
    val icon: Drawable?
)

class AppResolver(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager

    suspend fun loadTargetConfigs(): List<TargetAppConfig> = withContext(Dispatchers.IO) {
        val list = mutableListOf<TargetAppConfig>()
        try {
            val jsonStr = context.assets.open("target_apps.json").bufferedReader().use { it.readText() }
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val candidates = mutableListOf<String>()
                val candArr = obj.optJSONArray("candidatePackages")
                if (candArr != null) {
                    for (c in 0 until candArr.length()) candidates.add(candArr.getString(c))
                }
                val keywords = mutableListOf<String>()
                val kwArr = obj.optJSONArray("labelKeywords")
                if (kwArr != null) {
                    for (k in 0 until kwArr.length()) keywords.add(kwArr.getString(k))
                }
                val excludes = mutableListOf<String>()
                val exArr = obj.optJSONArray("excludePackages")
                if (exArr != null) {
                    for (e in 0 until exArr.length()) excludes.add(exArr.getString(e))
                }

                val accepts = mutableListOf<String>()
                val accArr = obj.optJSONArray("acceptTexts")
                if (accArr != null) {
                    for (a in 0 until accArr.length()) accepts.add(accArr.getString(a))
                }
                val neverClicks = mutableListOf<String>()
                val ncArr = obj.optJSONArray("neverClickTexts")
                if (ncArr != null) {
                    for (n in 0 until ncArr.length()) neverClicks.add(ncArr.getString(n))
                }

                list.add(
                    TargetAppConfig(
                        appId = obj.getString("appId"),
                        displayName = obj.getString("displayName"),
                        candidatePackages = candidates,
                        labelKeywords = keywords,
                        excludePackages = excludes,
                        fareRegex = obj.optString("fareRegex", ""),
                        distanceTimeRegex = obj.optString("distanceTimeRegex", ""),
                        distanceRegex = obj.optString("distanceRegex", ""),
                        acceptTexts = if (accepts.isNotEmpty()) accepts else listOf("Accept"),
                        neverClickTexts = neverClicks
                    )
                )
            }
        } catch (_: Exception) {}
        list
    }

    suspend fun resolvePackageForTarget(target: TargetAppConfig, savedPackage: String?): String? = withContext(Dispatchers.IO) {
        // 0. If user previously chose a package and it is still installed, return it
        if (!savedPackage.isNullOrEmpty() && isPackageInstalled(savedPackage)) {
            return@withContext savedPackage
        }

        // (a) First installed package from candidatePackages
        for (candidate in target.candidatePackages) {
            if (isPackageInstalled(candidate)) {
                return@withContext candidate
            }
        }

        // (b) Find matching installed launchable apps by label keywords
        val matches = findMatchingInstalledApps(target)
        if (matches.size == 1) {
            return@withContext matches.first().packageName
        }

        // If multiple or none, caller can prompt user
        null
    }

    suspend fun findMatchingInstalledApps(target: TargetAppConfig): List<InstalledAppItem> = withContext(Dispatchers.IO) {
        val launchableApps = getAllLaunchableApps()
        launchableApps.filter { app ->
            val labelLower = app.appLabel.lowercase()
            val notExcluded = target.excludePackages.none { ex -> app.packageName.equals(ex, ignoreCase = true) }
            val matchesKeyword = target.labelKeywords.any { kw -> labelLower.contains(kw.lowercase()) }
            notExcluded && matchesKeyword
        }
    }

    suspend fun getAllLaunchableApps(): List<InstalledAppItem> = withContext(Dispatchers.IO) {
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = packageManager.queryIntentActivities(intent, 0)
        resolveInfos.mapNotNull { ri ->
            val ai = ri.activityInfo?.applicationInfo ?: return@mapNotNull null
            val label = packageManager.getApplicationLabel(ai).toString()
            val icon = try { packageManager.getApplicationIcon(ai) } catch (_: Exception) { null }
            InstalledAppItem(
                packageName = ai.packageName,
                appLabel = label,
                icon = icon
            )
        }.distinctBy { it.packageName }.sortedBy { it.appLabel.lowercase() }
    }

    fun isPackageInstalled(packageName: String): Boolean {
        return try {
            packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun getAppLabel(packageName: String): String {
        return try {
            val ai = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(ai).toString()
        } catch (_: Exception) {
            packageName
        }
    }

    fun getAppIcon(packageName: String): Drawable? {
        return try {
            packageManager.getApplicationIcon(packageName)
        } catch (_: Exception) {
            null
        }
    }
}
