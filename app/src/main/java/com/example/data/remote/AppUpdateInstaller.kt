package com.example.data.remote

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

sealed class InstallResult {
    object Success : InstallResult()
    object PermissionRequired : InstallResult()
    data class Error(val message: String) : InstallResult()
}

object AppUpdateInstaller {
    private const val TAG = "AppUpdateInstaller"

    fun canRequestInstalls(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun openInstallPermissionSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to open ACTION_MANAGE_UNKNOWN_APP_SOURCES: ${e.message}")
            }
        }
    }

    suspend fun downloadApk(
        context: Context,
        downloadUrl: String,
        expectedSizeBytes: Long? = null,
        expectedSha256: String? = null,
        onProgress: (downloadedBytes: Long, totalBytes: Long, percentage: Int) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val targetFile = File(updatesDir, "trip-pilot-update.apk")
        val tmpFile = File(updatesDir, "trip-pilot-update.apk.tmp")

        if (tmpFile.exists()) tmpFile.delete()
        if (targetFile.exists()) targetFile.delete()

        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()

        val request = Request.Builder()
            .url(downloadUrl)
            .header("User-Agent", "TripPilot-Android-App")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Download failed with HTTP ${response.code}: ${response.message}")
                }
                val body = response.body ?: throw IllegalStateException("Download response body is empty")
                val totalLength = if (body.contentLength() > 0) body.contentLength() else (expectedSizeBytes ?: -1L)

                body.byteStream().use { input ->
                    FileOutputStream(tmpFile).use { output ->
                        val buffer = ByteArray(8 * 1024)
                        var bytesCopied: Long = 0
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            bytesCopied += read
                            val pct = if (totalLength > 0) {
                                ((bytesCopied * 100) / totalLength).toInt().coerceIn(0, 100)
                            } else {
                                0
                            }
                            onProgress(bytesCopied, totalLength, pct)
                        }
                        output.flush()
                    }
                }

                // Verify file size if expected
                if (expectedSizeBytes != null && expectedSizeBytes > 0 && tmpFile.length() != expectedSizeBytes) {
                    throw IllegalStateException("Downloaded APK size (${tmpFile.length()} bytes) does not match expected size ($expectedSizeBytes bytes)")
                }

                // Verify SHA-256 if provided
                if (!expectedSha256.isNullOrBlank()) {
                    val digest = MessageDigest.getInstance("SHA-256")
                    tmpFile.inputStream().use { stream ->
                        val buf = ByteArray(8192)
                        var r: Int
                        while (stream.read(buf).also { r = it } != -1) {
                            digest.update(buf, 0, r)
                        }
                    }
                    val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
                    if (!actualHash.equals(expectedSha256.trim(), ignoreCase = true)) {
                        throw IllegalStateException("SHA-256 checksum mismatch (expected $expectedSha256, got $actualHash)")
                    }
                }

                if (!tmpFile.renameTo(targetFile)) {
                    tmpFile.copyTo(targetFile, overwrite = true)
                    tmpFile.delete()
                }

                targetFile
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Download failed: ${e.message}")
            if (tmpFile.exists()) tmpFile.delete()
            if (targetFile.exists()) targetFile.delete()
            throw e
        }
    }

    fun triggerInstall(context: Context, apkFile: File): InstallResult {
        if (!apkFile.exists()) {
            return InstallResult.Error("APK file not found at ${apkFile.absolutePath}")
        }

        if (!canRequestInstalls(context)) {
            return InstallResult.PermissionRequired
        }

        return try {
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            InstallResult.Success
        } catch (e: Exception) {
            AppLogger.e(TAG, "Install trigger failed: ${e.message}")
            InstallResult.Error("Installation failed: ${e.message}. The new APK is signed with a different key. Build all versions with the same keystore, or uninstall once and reinstall.")
        }
    }
}
