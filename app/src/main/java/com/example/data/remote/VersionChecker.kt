package com.example.data.remote

import android.content.Context
import android.os.Build
import com.example.BuildConfig
import com.example.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

enum class UpdateSourceType(val displayName: String) {
    GITHUB("GitHub Releases"),
    HOSTED_JSON("Hosted version.json")
}

enum class UpdateResultState {
    UP_TO_DATE,
    UPDATE_AVAILABLE,
    ERROR
}

data class UpdateDiagnostics(
    val sourceUrl: String,
    val httpStatus: String,
    val releasesFound: Int,
    val tagsFound: List<String>,
    val parsedBuilds: List<Pair<String, Long?>>,
    val chosenRelease: String?,
    val chosenAsset: String?,
    val comparison: String,
    val isUpdateAvailable: Boolean,
    val rawText: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class AppUpdateInfo(
    val currentVersion: String,
    val latestVersion: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val releasePageUrl: String,
    val isUpdateAvailable: Boolean,
    val remoteBuild: Long = 0,
    val installedBuild: Long = 0,
    val apkSizeBytes: Long = 0,
    val sha256: String? = null,
    val minSupportedVersionCode: Long? = null,
    val status: UpdateResultState = if (isUpdateAvailable) UpdateResultState.UPDATE_AVAILABLE else UpdateResultState.UP_TO_DATE,
    val errorMessage: String? = null,
    val diagnostics: UpdateDiagnostics? = null
)

class VersionChecker(
    val owner: String = BuildConfig.GITHUB_OWNER,
    val repo: String = BuildConfig.GITHUB_REPO,
    val hostedJsonUrl: String = BuildConfig.HOSTED_VERSION_JSON_URL,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .cache(null)
        .build()
) {

    companion object {
        private const val TAG = "VersionChecker"

        /**
         * Derives the remote build integer from release metadata following Section B:
         * 1) versionCode line in release body
         * 2) regex (?i)build[\s_-]*(\d+) on tag_name, then on name
         * 3) regex (?i)release[\s_-]*(\d+) on tag_name, then on name (e.g. "release 12 (final)" -> 12)
         * 4) trailing integer of tag_name or name ((\d+)\s*$)
         * 5) an integer in the APK asset name such as app-release-6.apk
         */
        fun parseBuildNumber(
            tag: String,
            name: String = "",
            body: String = "",
            assetNames: List<String> = emptyList()
        ): Long? {
            // (1) "versionCode" field in release body
            if (body.isNotBlank()) {
                val bodyMatcher = Pattern.compile("(?im)^\\s*versionCode\\s*[:=]\\s*(\\d+)").matcher(body)
                if (bodyMatcher.find()) {
                    bodyMatcher.group(1)?.toLongOrNull()?.let { return it }
                }
            }

            // (2) regex (?i)build[\s_-]*(\d+) on tag_name, then on name
            val buildRegex = Pattern.compile("(?i)build[\\s_-]*(\\d+)")
            val tagBuildMatcher = buildRegex.matcher(tag)
            if (tagBuildMatcher.find()) {
                tagBuildMatcher.group(1)?.toLongOrNull()?.let { return it }
            }
            if (name.isNotBlank()) {
                val nameBuildMatcher = buildRegex.matcher(name)
                if (nameBuildMatcher.find()) {
                    nameBuildMatcher.group(1)?.toLongOrNull()?.let { return it }
                }
            }

            // Support "release 12 (final)" and release variations
            val releaseRegex = Pattern.compile("(?i)release[\\s_-]*(\\d+)")
            val tagReleaseMatcher = releaseRegex.matcher(tag)
            if (tagReleaseMatcher.find()) {
                tagReleaseMatcher.group(1)?.toLongOrNull()?.let { return it }
            }
            if (name.isNotBlank()) {
                val nameReleaseMatcher = releaseRegex.matcher(name)
                if (nameReleaseMatcher.find()) {
                    nameReleaseMatcher.group(1)?.toLongOrNull()?.let { return it }
                }
            }

            // (3) trailing integer of tag_name or name ((\d+)\s*$)
            val trailingRegex = Pattern.compile("(\\d+)\\s*$")
            val tagTrailingMatcher = trailingRegex.matcher(tag.trim())
            if (tagTrailingMatcher.find()) {
                tagTrailingMatcher.group(1)?.toLongOrNull()?.let { return it }
            }
            if (name.isNotBlank()) {
                val nameTrailingMatcher = trailingRegex.matcher(name.trim())
                if (nameTrailingMatcher.find()) {
                    nameTrailingMatcher.group(1)?.toLongOrNull()?.let { return it }
                }
            }

            // (4) an integer in the APK asset name such as app-release-6.apk
            val assetRegex = Pattern.compile("(?i)(?:app-release-|release-|build-|[-_])(\\d+)\\.apk")
            val generalAssetRegex = Pattern.compile("(?i)(\\d+)\\.apk")
            for (assetName in assetNames) {
                val m1 = assetRegex.matcher(assetName)
                if (m1.find()) {
                    m1.group(1)?.toLongOrNull()?.let { return it }
                }
                val m2 = generalAssetRegex.matcher(assetName)
                if (m2.find()) {
                    m2.group(1)?.toLongOrNull()?.let { return it }
                }
            }

            return null
        }

        fun compareVersions(installedCode: Long, remoteCode: Long): UpdateResultState {
            return if (remoteCode > installedCode) {
                UpdateResultState.UPDATE_AVAILABLE
            } else {
                UpdateResultState.UP_TO_DATE
            }
        }

        /**
         * Legacy semantic comparison maintained for existing unit tests.
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
                .split("-")[0]
                .trim()
        }

        fun getInstalledVersionCode(context: Context?): Long {
            if (context == null) return BuildConfig.VERSION_CODE.toLong()
            return try {
                val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    pInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    pInfo.versionCode.toLong()
                }
            } catch (_: Exception) {
                BuildConfig.VERSION_CODE.toLong()
            }
        }

        fun getInstalledVersionName(context: Context?): String {
            if (context == null) return BuildConfig.VERSION_NAME
            return try {
                val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                pInfo.versionName ?: BuildConfig.VERSION_NAME
            } catch (_: Exception) {
                BuildConfig.VERSION_NAME
            }
        }

        fun formatFileSize(bytes: Long): String {
            if (bytes <= 0) return "0 MB"
            val mb = bytes / (1024.0 * 1024.0)
            return String.format(Locale.US, "%.1f MB", mb)
        }
    }

    /**
     * Preserved signature for backward compatibility with TripPilotApp & DashboardViewModel.
     */
    suspend fun checkLatestRelease(currentVersion: String = BuildConfig.VERSION_NAME): AppUpdateInfo? {
        val result = checkUpdate(context = null, sourceType = UpdateSourceType.GITHUB, includePreReleases = true)
        return if (result.status == UpdateResultState.ERROR) {
            // Null on error for existing consumers expecting null on network/server failure
            null
        } else {
            result
        }
    }

    suspend fun checkUpdate(
        context: Context? = null,
        sourceType: UpdateSourceType = UpdateSourceType.GITHUB,
        includePreReleases: Boolean = true,
        customInstalledBuild: Long? = null,
        customInstalledVersion: String? = null
    ): AppUpdateInfo = withContext(Dispatchers.IO) {
        val installedBuild = customInstalledBuild ?: getInstalledVersionCode(context)
        val installedVersion = customInstalledVersion ?: getInstalledVersionName(context)

        when (sourceType) {
            UpdateSourceType.GITHUB -> checkGitHub(installedBuild, installedVersion, includePreReleases)
            UpdateSourceType.HOSTED_JSON -> checkHostedJson(installedBuild, installedVersion)
        }
    }

    private suspend fun checkGitHub(
        installedBuild: Long,
        installedVersion: String,
        includePreReleases: Boolean
    ): AppUpdateInfo {
        val url = "https://api.github.com/repos/$owner/$repo/releases"
        val request = Request.Builder()
            .url(url)
            .cacheControl(CacheControl.Builder().noCache().noStore().build())
            .header("Cache-Control", "no-cache")
            .header("Pragma", "no-cache")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "TripPilot-Android-App")
            .build()

        var response: Response? = null
        var lastException: Exception? = null

        // Retry once on failure
        for (attempt in 1..2) {
            try {
                response = client.newCall(request).execute()
                break
            } catch (e: Exception) {
                lastException = e
                if (attempt == 1) {
                    delay(300)
                }
            }
        }

        if (response == null) {
            val errorMsg = "Network error: ${lastException?.localizedMessage ?: "Connection timed out"}"
            AppLogger.e(TAG, errorMsg)
            return AppUpdateInfo(
                currentVersion = installedVersion,
                latestVersion = installedVersion,
                releaseTitle = "Check Failed",
                releaseNotes = "",
                downloadUrl = "",
                releasePageUrl = "https://github.com/$owner/$repo/releases",
                isUpdateAvailable = false,
                installedBuild = installedBuild,
                status = UpdateResultState.ERROR,
                errorMessage = errorMsg,
                diagnostics = UpdateDiagnostics(
                    sourceUrl = url,
                    httpStatus = "Network Timeout / Error",
                    releasesFound = 0,
                    tagsFound = emptyList(),
                    parsedBuilds = emptyList(),
                    chosenRelease = null,
                    chosenAsset = null,
                    comparison = "remote ? > installed $installedBuild = false",
                    isUpdateAvailable = false,
                    rawText = buildDiagnosticsString(
                        sourceUrl = url,
                        httpStatus = "Network Timeout / Error",
                        releasesFound = 0,
                        tagsFound = emptyList(),
                        parsedBuilds = emptyList(),
                        chosenRelease = null,
                        chosenAsset = null,
                        comparison = "remote ? > installed $installedBuild = false",
                        result = "ERROR: $errorMsg"
                    )
                )
            )
        }

        return response.use { resp ->
            if (resp.code == 404) {
                val errorMsg = "Repository is private or not found — use a public repo or a hosted version.json"
                return@use createErrorResult(
                    installedBuild = installedBuild,
                    installedVersion = installedVersion,
                    url = url,
                    httpStatus = "404 Not Found",
                    errorMsg = errorMsg
                )
            }

            if (resp.code == 403) {
                val resetEpochSec = resp.header("x-ratelimit-reset")?.toLongOrNull()
                val resetMsg = if (resetEpochSec != null && resetEpochSec > 0) {
                    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                    "GitHub API rate limit exceeded. Reset at ${sdf.format(Date(resetEpochSec * 1000L))}."
                } else {
                    "GitHub API rate limit exceeded (HTTP 403)."
                }
                return@use createErrorResult(
                    installedBuild = installedBuild,
                    installedVersion = installedVersion,
                    url = url,
                    httpStatus = "403 Forbidden",
                    errorMsg = resetMsg
                )
            }

            if (!resp.isSuccessful) {
                val errorMsg = "HTTP error ${resp.code}: ${resp.message}"
                return@use createErrorResult(
                    installedBuild = installedBuild,
                    installedVersion = installedVersion,
                    url = url,
                    httpStatus = "${resp.code} ${resp.message}",
                    errorMsg = errorMsg
                )
            }

            val bodyStr = resp.body?.string().orEmpty()
            val jsonArray = try {
                JSONArray(bodyStr)
            } catch (e: Exception) {
                return@use createErrorResult(
                    installedBuild = installedBuild,
                    installedVersion = installedVersion,
                    url = url,
                    httpStatus = "${resp.code} ${resp.message}",
                    errorMsg = "Invalid JSON response from GitHub API"
                )
            }

            if (jsonArray.length() == 0) {
                return@use createErrorResult(
                    installedBuild = installedBuild,
                    installedVersion = installedVersion,
                    url = url,
                    httpStatus = "${resp.code} OK",
                    errorMsg = "No releases found in repository"
                )
            }

            data class ReleaseCandidate(
                val tagName: String,
                val releaseName: String,
                val body: String,
                val htmlUrl: String,
                val isDraft: Boolean,
                val isPrerelease: Boolean,
                val assetName: String?,
                val assetDownloadUrl: String,
                val assetSize: Long,
                val parsedBuild: Long?
            )

            val tagsFound = mutableListOf<String>()
            val parsedBuilds = mutableListOf<Pair<String, Long?>>()
            val candidates = mutableListOf<ReleaseCandidate>()

            for (i in 0 until jsonArray.length()) {
                val releaseObj = jsonArray.optJSONObject(i) ?: continue
                val isDraft = releaseObj.optBoolean("draft", false)
                if (isDraft) continue // Draft releases are strictly ignored

                val isPrerelease = releaseObj.optBoolean("prerelease", false)
                if (!includePreReleases && isPrerelease) continue // Follow pre-releases setting

                val tagName = releaseObj.optString("tag_name", "").trim()
                val releaseName = releaseObj.optString("name", "").trim()
                val body = releaseObj.optString("body", "").trim()
                val htmlUrl = releaseObj.optString("html_url", "https://github.com/$owner/$repo/releases")

                tagsFound.add(tagName.ifEmpty { "unnamed-release-$i" })

                // Find candidate APK assets: prefer asset with "release" in filename, otherwise first .apk
                val assetsArray = releaseObj.optJSONArray("assets")
                val apkAssets = mutableListOf<Pair<JSONObject, String>>()
                if (assetsArray != null) {
                    for (j in 0 until assetsArray.length()) {
                        val asset = assetsArray.optJSONObject(j) ?: continue
                        val name = asset.optString("name", "")
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            apkAssets.add(Pair(asset, name))
                        }
                    }
                }

                // Sort APK assets so names containing "release" come first
                val sortedApkAssets = apkAssets.sortedByDescending { it.second.contains("release", ignoreCase = true) }
                val chosenAssetObj = sortedApkAssets.firstOrNull()?.first
                val chosenAssetName = sortedApkAssets.firstOrNull()?.second
                val chosenDownloadUrl = chosenAssetObj?.optString("browser_download_url", "").orEmpty()
                val chosenAssetSize = chosenAssetObj?.optLong("size", 0L) ?: 0L

                val allAssetNames = apkAssets.map { it.second }
                val parsedBuild = parseBuildNumber(tagName, releaseName, body, allAssetNames)

                parsedBuilds.add(Pair(tagName, parsedBuild))

                candidates.add(
                    ReleaseCandidate(
                        tagName = tagName,
                        releaseName = releaseName,
                        body = body,
                        htmlUrl = htmlUrl,
                        isDraft = isDraft,
                        isPrerelease = isPrerelease,
                        assetName = chosenAssetName,
                        assetDownloadUrl = chosenDownloadUrl,
                        assetSize = chosenAssetSize,
                        parsedBuild = parsedBuild
                    )
                )
            }

            if (candidates.isEmpty()) {
                val errorMsg = if (!includePreReleases) {
                    "No non-prerelease releases found. Enable 'Include pre-releases' to check all releases."
                } else {
                    "No valid releases found in repository"
                }
                return@use createErrorResult(
                    installedBuild = installedBuild,
                    installedVersion = installedVersion,
                    url = url,
                    httpStatus = "${resp.code} OK",
                    errorMsg = errorMsg,
                    tagsFound = tagsFound,
                    parsedBuilds = parsedBuilds
                )
            }

            // Check if any candidate has a valid parsed build number
            val validCandidates = candidates.filter { it.parsedBuild != null }
            if (validCandidates.isEmpty()) {
                val firstTag = candidates.first().tagName
                val errorMsg = "Cannot read the build number from tag '$firstTag'"
                return@use createErrorResult(
                    installedBuild = installedBuild,
                    installedVersion = installedVersion,
                    url = url,
                    httpStatus = "${resp.code} OK",
                    errorMsg = errorMsg,
                    tagsFound = tagsFound,
                    parsedBuilds = parsedBuilds
                )
            }

            // Pick the release with the HIGHEST remoteBuild, not the newest by date
            val chosen = validCandidates.maxByOrNull { it.parsedBuild ?: 0L }!!
            val remoteBuild = chosen.parsedBuild!!

            if (chosen.assetName == null || chosen.assetDownloadUrl.isEmpty()) {
                val errorMsg = "Release '${chosen.tagName}' has no APK assets"
                return@use createErrorResult(
                    installedBuild = installedBuild,
                    installedVersion = installedVersion,
                    url = url,
                    httpStatus = "${resp.code} OK",
                    errorMsg = errorMsg,
                    tagsFound = tagsFound,
                    parsedBuilds = parsedBuilds,
                    chosenRelease = "${chosen.tagName} (build $remoteBuild)"
                )
            }

            val isUpdateAvailable = remoteBuild > installedBuild
            val comparisonStr = "remote $remoteBuild > installed $installedBuild = $isUpdateAvailable"
            val chosenReleaseStr = "${chosen.tagName} (build $remoteBuild)"
            val chosenAssetStr = "${chosen.assetName} (${formatFileSize(chosen.assetSize)})"

            val diagnostics = UpdateDiagnostics(
                sourceUrl = url,
                httpStatus = "${resp.code} OK",
                releasesFound = jsonArray.length(),
                tagsFound = tagsFound,
                parsedBuilds = parsedBuilds,
                chosenRelease = chosenReleaseStr,
                chosenAsset = chosenAssetStr,
                comparison = comparisonStr,
                isUpdateAvailable = isUpdateAvailable,
                rawText = buildDiagnosticsString(
                    sourceUrl = url,
                    httpStatus = "${resp.code} OK",
                    releasesFound = jsonArray.length(),
                    tagsFound = tagsFound,
                    parsedBuilds = parsedBuilds,
                    chosenRelease = chosenReleaseStr,
                    chosenAsset = chosenAssetStr,
                    comparison = comparisonStr,
                    result = if (isUpdateAvailable) "UPDATE_AVAILABLE" else "UP_TO_DATE"
                )
            )

            val displayVersion = chosen.tagName.removePrefix("v").removePrefix("V").ifEmpty { "1.0.$remoteBuild" }

            AppUpdateInfo(
                currentVersion = installedVersion,
                latestVersion = displayVersion,
                releaseTitle = chosen.releaseName.ifEmpty { chosen.tagName },
                releaseNotes = chosen.body,
                downloadUrl = chosen.assetDownloadUrl,
                releasePageUrl = chosen.htmlUrl,
                isUpdateAvailable = isUpdateAvailable,
                remoteBuild = remoteBuild,
                installedBuild = installedBuild,
                apkSizeBytes = chosen.assetSize,
                sha256 = null,
                minSupportedVersionCode = null,
                status = if (isUpdateAvailable) UpdateResultState.UPDATE_AVAILABLE else UpdateResultState.UP_TO_DATE,
                errorMessage = null,
                diagnostics = diagnostics
            )
        }
    }

    private suspend fun checkHostedJson(
        installedBuild: Long,
        installedVersion: String
    ): AppUpdateInfo {
        val bustingParam = if (hostedJsonUrl.contains("?")) "&t=${System.currentTimeMillis()}" else "?t=${System.currentTimeMillis()}"
        val finalUrl = "$hostedJsonUrl$bustingParam"

        val request = Request.Builder()
            .url(finalUrl)
            .cacheControl(CacheControl.Builder().noCache().noStore().build())
            .header("Cache-Control", "no-cache")
            .header("Pragma", "no-cache")
            .header("Accept", "application/json")
            .header("User-Agent", "TripPilot-Android-App")
            .build()

        var response: Response? = null
        var lastException: Exception? = null

        for (attempt in 1..2) {
            try {
                response = client.newCall(request).execute()
                break
            } catch (e: Exception) {
                lastException = e
                if (attempt == 1) {
                    delay(300)
                }
            }
        }

        if (response == null) {
            val errorMsg = "Network error: ${lastException?.localizedMessage ?: "Connection timed out"}"
            return createErrorResult(
                installedBuild = installedBuild,
                installedVersion = installedVersion,
                url = finalUrl,
                httpStatus = "Network Timeout / Error",
                errorMsg = errorMsg
            )
        }

        return response.use { resp ->
            if (resp.code == 404) {
                val errorMsg = "hosted version.json not found (HTTP 404)"
                return@use createErrorResult(
                    installedBuild = installedBuild,
                    installedVersion = installedVersion,
                    url = finalUrl,
                    httpStatus = "404 Not Found",
                    errorMsg = errorMsg
                )
            }

            if (!resp.isSuccessful) {
                val errorMsg = "HTTP error ${resp.code}: ${resp.message}"
                return@use createErrorResult(
                    installedBuild = installedBuild,
                    installedVersion = installedVersion,
                    url = finalUrl,
                    httpStatus = "${resp.code} ${resp.message}",
                    errorMsg = errorMsg
                )
            }

            val bodyStr = resp.body?.string().orEmpty()
            val json = try {
                JSONObject(bodyStr)
            } catch (e: Exception) {
                return@use createErrorResult(
                    installedBuild = installedBuild,
                    installedVersion = installedVersion,
                    url = finalUrl,
                    httpStatus = "${resp.code} ${resp.message}",
                    errorMsg = "Invalid JSON response from hosted version.json"
                )
            }

            val remoteBuild = json.optLong("versionCode", -1L)
            val versionName = json.optString("versionName", "1.0.$remoteBuild")
            val apkUrl = json.optString("apkUrl", "")
            val sha256 = if (json.has("sha256")) json.optString("sha256") else null
            val notes = json.optString("notes", "")
            val minSupported = if (json.has("minSupportedVersionCode")) json.optLong("minSupportedVersionCode") else null

            if (remoteBuild <= 0 || apkUrl.isBlank()) {
                val errorMsg = "version.json missing required 'versionCode' or 'apkUrl' fields"
                return@use createErrorResult(
                    installedBuild = installedBuild,
                    installedVersion = installedVersion,
                    url = finalUrl,
                    httpStatus = "${resp.code} OK",
                    errorMsg = errorMsg
                )
            }

            val isUpdateAvailable = remoteBuild > installedBuild
            val comparisonStr = "remote $remoteBuild > installed $installedBuild = $isUpdateAvailable"
            val chosenReleaseStr = "build $remoteBuild ($versionName)"
            val chosenAssetStr = apkUrl.substringAfterLast('/')

            val diagnostics = UpdateDiagnostics(
                sourceUrl = finalUrl,
                httpStatus = "${resp.code} OK",
                releasesFound = 1,
                tagsFound = listOf("build $remoteBuild"),
                parsedBuilds = listOf(Pair("build $remoteBuild", remoteBuild)),
                chosenRelease = chosenReleaseStr,
                chosenAsset = chosenAssetStr,
                comparison = comparisonStr,
                isUpdateAvailable = isUpdateAvailable,
                rawText = buildDiagnosticsString(
                    sourceUrl = finalUrl,
                    httpStatus = "${resp.code} OK",
                    releasesFound = 1,
                    tagsFound = listOf("build $remoteBuild"),
                    parsedBuilds = listOf(Pair("build $remoteBuild", remoteBuild)),
                    chosenRelease = chosenReleaseStr,
                    chosenAsset = chosenAssetStr,
                    comparison = comparisonStr,
                    result = if (isUpdateAvailable) "UPDATE_AVAILABLE" else "UP_TO_DATE"
                )
            )

            AppUpdateInfo(
                currentVersion = installedVersion,
                latestVersion = versionName,
                releaseTitle = "TripPilot v$versionName",
                releaseNotes = notes,
                downloadUrl = apkUrl,
                releasePageUrl = hostedJsonUrl,
                isUpdateAvailable = isUpdateAvailable,
                remoteBuild = remoteBuild,
                installedBuild = installedBuild,
                apkSizeBytes = 0L,
                sha256 = sha256,
                minSupportedVersionCode = minSupported,
                status = if (isUpdateAvailable) UpdateResultState.UPDATE_AVAILABLE else UpdateResultState.UP_TO_DATE,
                errorMessage = null,
                diagnostics = diagnostics
            )
        }
    }

    private fun createErrorResult(
        installedBuild: Long,
        installedVersion: String,
        url: String,
        httpStatus: String,
        errorMsg: String,
        tagsFound: List<String> = emptyList(),
        parsedBuilds: List<Pair<String, Long?>> = emptyList(),
        chosenRelease: String? = null
    ): AppUpdateInfo {
        val diagnostics = UpdateDiagnostics(
            sourceUrl = url,
            httpStatus = httpStatus,
            releasesFound = tagsFound.size,
            tagsFound = tagsFound,
            parsedBuilds = parsedBuilds,
            chosenRelease = chosenRelease,
            chosenAsset = null,
            comparison = "remote ? > installed $installedBuild = false",
            isUpdateAvailable = false,
            rawText = buildDiagnosticsString(
                sourceUrl = url,
                httpStatus = httpStatus,
                releasesFound = tagsFound.size,
                tagsFound = tagsFound,
                parsedBuilds = parsedBuilds,
                chosenRelease = chosenRelease,
                chosenAsset = null,
                comparison = "remote ? > installed $installedBuild = false",
                result = "ERROR: $errorMsg"
            )
        )

        return AppUpdateInfo(
            currentVersion = installedVersion,
            latestVersion = installedVersion,
            releaseTitle = "Check Failed",
            releaseNotes = "",
            downloadUrl = "",
            releasePageUrl = url,
            isUpdateAvailable = false,
            installedBuild = installedBuild,
            status = UpdateResultState.ERROR,
            errorMessage = errorMsg,
            diagnostics = diagnostics
        )
    }

    private fun buildDiagnosticsString(
        sourceUrl: String,
        httpStatus: String,
        releasesFound: Int,
        tagsFound: List<String>,
        parsedBuilds: List<Pair<String, Long?>>,
        chosenRelease: String?,
        chosenAsset: String?,
        comparison: String,
        result: String
    ): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val formattedDate = sdf.format(Date())
        val parsedListStr = parsedBuilds.joinToString(separator = ", ") { "${it.first}: ${it.second ?: "unparsed"}" }

        return """
            TripPilot In-App Update Diagnostics
            ------------------------------------
            Timestamp: $formattedDate
            Source URL: $sourceUrl
            HTTP Status: $httpStatus
            Releases Found: $releasesFound
            Tags Found: ${if (tagsFound.isEmpty()) "None" else tagsFound.joinToString(", ")}
            Parsed Builds: [${if (parsedListStr.isEmpty()) "None" else parsedListStr}]
            Chosen Release: ${chosenRelease ?: "None"}
            Chosen Asset: ${chosenAsset ?: "None"}
            Comparison: $comparison
            Result State: $result
        """.trimIndent()
    }
}
