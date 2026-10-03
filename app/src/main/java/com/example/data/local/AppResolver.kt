package com.example.data.local

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import com.example.data.model.TargetAppConfig
import com.example.util.AppLogger
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

                val variant1Map = mutableMapOf<String, String>()
                val v1Obj = obj.optJSONObject("variant1ViewIds")
                if (v1Obj != null) {
                    val keys = v1Obj.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        variant1Map[key] = v1Obj.getString(key)
                    }
                }

                val neverClickIds = mutableListOf<String>()
                val ncIdArr = obj.optJSONArray("neverClickIds")
                if (ncIdArr != null) {
                    for (n in 0 until ncIdArr.length()) neverClickIds.add(ncIdArr.getString(n))
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

                val hints = mutableListOf<String>()
                val hArr = obj.optJSONArray("detectionHints")
                if (hArr != null) {
                    for (h in 0 until hArr.length()) hints.add(hArr.getString(h))
                }

                val rideTypes = mutableListOf<String>()
                val rtArr = obj.optJSONArray("knownRideTypes")
                if (rtArr != null) {
                    for (r in 0 until rtArr.length()) rideTypes.add(rtArr.getString(r))
                }

                list.add(
                    TargetAppConfig(
                        appId = obj.getString("appId"),
                        displayName = obj.getString("displayName"),
                        candidatePackages = candidates,
                        labelKeywords = keywords,
                        excludePackages = excludes,
                        variant1ViewIds = variant1Map,
                        neverClickIds = neverClickIds,
                        fareRegex = obj.optString("fareRegex", ""),
                        distanceTimeRegex = obj.optString("distanceTimeRegex", ""),
                        distanceRegex = obj.optString("distanceRegex", ""),
                        acceptTexts = if (accepts.isNotEmpty()) accepts else listOf("Accept"),
                        neverClickTexts = neverClicks,
                        knownRideTypes = rideTypes,
                        detectionHints = hints
                    )
                )
            }
        } catch (e: Exception) {
            AppLogger.e("AppResolver", "Error loading target_apps.json", e)
        }
        list
    }

    suspend fun resolvePackageForTarget(target: TargetAppConfig, savedPackage: String?): String? = withContext(Dispatchers.IO) {
        // A manual choice saved in DataStore overrides the candidates,
        // but if the saved package is not installed or is in excludePackages, discard it and re-run resolution.
        if (!savedPackage.isNullOrEmpty() &&
            isPackageInstalled(savedPackage) &&
            !target.excludePackages.any { it.equals(savedPackage, ignoreCase = true) }
        ) {
            return@withContext savedPackage
        }

        // (a) First installed package from candidatePackages (excluding excludePackages)
        for (candidate in target.candidatePackages) {
            if (!target.excludePackages.any { it.equals(candidate, ignoreCase = true) } && isPackageInstalled(candidate)) {
                return@withContext candidate
            }
        }

        // (b) Find matching installed launchable apps by label keywords (excluding excludePackages)
        val matches = findMatchingInstalledApps(target)
        if (matches.size == 1) {
            return@withContext matches.first().packageName
        }

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
