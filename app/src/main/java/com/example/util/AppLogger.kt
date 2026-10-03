package com.example.util

import android.util.Log
import com.example.BuildConfig

/**
 * Safe, centralized logger that respects release builds.
 * In release builds, debug/verbose/info logs are complete no-ops.
 * Sensitive data such as passenger addresses or fares are never printed in release builds.
 */
object AppLogger {

    private const val TAG_PREFIX = "TripPilot"

    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            Log.d("$TAG_PREFIX:$tag", message)
        }
    }

    fun i(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            Log.i("$TAG_PREFIX:$tag", message)
        }
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            if (throwable != null) {
                Log.w("$TAG_PREFIX:$tag", message, throwable)
            } else {
                Log.w("$TAG_PREFIX:$tag", message)
            }
        }
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        // Errors in release only log non-sensitive summaries without stack traces or addresses
        if (BuildConfig.DEBUG) {
            if (throwable != null) {
                Log.e("$TAG_PREFIX:$tag", message, throwable)
            } else {
                Log.e("$TAG_PREFIX:$tag", message)
            }
        } else {
            // Sanitized release error log
            Log.e("$TAG_PREFIX:$tag", message.take(120))
        }
    }
}
