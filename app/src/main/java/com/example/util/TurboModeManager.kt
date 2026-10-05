package com.example.util

import android.content.Context
import android.os.PowerManager

object TurboModeManager {
    @Volatile
    private var wakeLock: PowerManager.WakeLock? = null

    @Synchronized
    fun updateWakeLock(context: Context, engineRunning: Boolean, turboEnabled: Boolean) {
        if (engineRunning && turboEnabled) {
            if (wakeLock == null) {
                val powerManager = context.applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
                wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "trippilot:engine")
            }
            if (wakeLock?.isHeld == false) {
                try {
                    wakeLock?.acquire(12 * 60 * 60 * 1000L) // 12-hour safety timeout
                } catch (_: Exception) {}
            }
        } else {
            release()
        }
    }

    @Synchronized
    fun release() {
        if (wakeLock?.isHeld == true) {
            try {
                wakeLock?.release()
            } catch (_: Exception) {}
        }
    }

    @Synchronized
    fun isHeld(): Boolean = wakeLock?.isHeld == true
}
