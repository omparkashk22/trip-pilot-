package com.example

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.example.ui.navigation.TripPilotNavGraph
import com.example.ui.theme.TripPilotTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as TripPilotApp

        // Observe keepScreenOn preference
        lifecycleScope.launch {
            app.preferencesManager.keepScreenOn.collect { shouldKeepOn ->
                if (shouldKeepOn) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        }

        setContent {
            val themeSetting by app.preferencesManager.theme.collectAsState(initial = "dark")
            val systemInDark = isSystemInDarkTheme()

            val isDark = when (themeSetting.lowercase()) {
                "light" -> false
                "system" -> systemInDark
                else -> true
            }

            LaunchedEffect(themeSetting, isDark) {
                // Try to set AppCompat night mode if AppCompatDelegate is present
                try {
                    val delegateClass = Class.forName("androidx.appcompat.app.AppCompatDelegate")
                    val method = delegateClass.getMethod("setDefaultNightMode", Int::class.javaPrimitiveType)
                    val modeInt = when (themeSetting.lowercase()) {
                        "light" -> 1 // MODE_NIGHT_NO
                        "dark" -> 2 // MODE_NIGHT_YES
                        else -> -1 // MODE_NIGHT_FOLLOW_SYSTEM
                    }
                    method.invoke(null, modeInt)
                } catch (_: Throwable) {}

                // Update status and navigation bar icon colors instantly
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.isAppearanceLightStatusBars = !isDark
                insetsController.isAppearanceLightNavigationBars = !isDark
            }

            TripPilotTheme(themeSetting = themeSetting) {
                TripPilotNavGraph(app = app)
            }
        }
    }
}
