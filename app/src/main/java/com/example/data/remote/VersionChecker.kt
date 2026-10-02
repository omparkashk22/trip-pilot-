package com.example.data.remote

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class AppUpdateInfo(
    val currentVersion: String,
    val latestVersion: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val releasePageUrl: String,
    val isUpdateAvailable: Boolean
)

class VersionChecker(
    private val owner: String = "omparkashk22",
    private val repo: String = "trip-pilot-",
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
) {

    companion object {
        private const val TAG = "VersionChecker"

        /**
         * Compares two semantic version strings (e.g. "1.0.1" vs "1.0", "v1.2.0" vs "1.1").
         * Returns true if [latest] is strictly greater than [current].
         */
        fun isNewerVersion(current: String, latest: String): Boolean {
            val cleanCurrent = cleanVersion(current)
            val cleanLatest = cleanVersion(latest)

            if (cleanCurrent.isEmpty() || cleanLatest.isEmpty()) return false
            if (cleanCurrent == cleanLatest) return false

            val currentParts = cleanCurrent.split(".").mapNotNull { it.toIntOrNull() }
            val latestParts = cleanLatest.split(".").mapNotNull { it.toIntOrNull() }

            val maxLen = maxOf(currentParts.size, latestParts.size)
            for (i in 0 until maxLen) {
                val c = currentParts.getOrElse(i) { 0 }
                val l = latestParts.getOrElse(i) { 0 }
                if (l > c) return true
                if (l < c) return false
            }

            return false
        }

        private fun cleanVersion(ver: String): String {
            return ver.trim()
                .removePrefix("v")
                .removePrefix("V")
                .split("-")[0] // ignore build metadata / prerelease suffix for basic comparison
                .trim()
        }
    }

    suspend fun checkLatestRelease(currentVersion: String = BuildConfig.VERSION_NAME): AppUpdateInfo? = withContext(Dispatchers.IO) {
        val url = "https://api.github.com/repos/$owner/$repo/releases/latest"
        try {
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "TripPilot-Android-App")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "GitHub releases request returned code: ${response.code}")
                    return@withContext null
                }

                val bodyStr = response.body?.string() ?: return@withContext null
                val json = JSONObject(bodyStr)

                val tagName = json.optString("tag_name", "").trim()
                val releaseTitle = json.optString("name", tagName)
                val releaseNotes = json.optString("body", "")
                val htmlUrl = json.optString("html_url", "https://github.com/$owner/$repo/releases")

                // Find direct APK download URL from assets if present
                var apkUrl = htmlUrl
                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val assetName = asset.optString("name", "")
                        val downloadUrl = asset.optString("browser_download_url", "")
                        if (assetName.endsWith(".apk", ignoreCase = true) && downloadUrl.isNotEmpty()) {
                            apkUrl = downloadUrl
                            break
                        }
                    }
                }

                val cleanLatest = tagName.removePrefix("v").removePrefix("V")
                val isUpdateAvailable = isNewerVersion(currentVersion, tagName)

                AppUpdateInfo(
                    currentVersion = currentVersion,
                    latestVersion = cleanLatest.ifEmpty { tagName },
                    releaseTitle = releaseTitle,
                    releaseNotes = releaseNotes,
                    downloadUrl = apkUrl,
                    releasePageUrl = htmlUrl,
                    isUpdateAvailable = isUpdateAvailable
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for updates: ${e.message}")
            null
        }
    }
}
