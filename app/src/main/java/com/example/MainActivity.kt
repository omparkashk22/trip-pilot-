package com.example

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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

            TripPilotTheme(themeSetting = themeSetting) {
                TripPilotNavGraph(app = app)
            }
        }
    }
}
