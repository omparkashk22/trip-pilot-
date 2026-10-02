package com.example.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.TripPilotApp
import com.example.ui.apps.AppsScreen
import com.example.ui.apps.AppsViewModel
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.AuthViewModel
import com.example.ui.components.AppTopBar
import com.example.ui.components.MainTab
import com.example.ui.dashboard.DashboardScreen
import com.example.ui.dashboard.DashboardViewModel
import com.example.ui.filters.FiltersScreen
import com.example.ui.filters.FiltersViewModel
import com.example.ui.history.HistoryViewModel
import com.example.ui.history.RideHistoryScreen
import com.example.ui.onboarding.OnboardingScreen
import com.example.ui.profile.ProfileScreen
import com.example.ui.profile.ProfileViewModel
import com.example.ui.settings.DeveloperToolsScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.settings.SettingsViewModel
import com.example.ui.settings.SetupGuideScreen
import com.example.ui.theme.DarkBackground
import kotlinx.coroutines.launch

enum class AppSubScreen {
    NONE,
    SETUP_GUIDE,
    DEV_TOOLS,
    PROFILE
}

@Composable
fun TripPilotNavGraph(
    app: TripPilotApp
) {
    val isDisclaimerAccepted by app.preferencesManager.isDisclaimerAccepted.collectAsState(initial = false)
    val currentUser by app.authRepository.currentUser.collectAsState()
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableStateOf(MainTab.DASHBOARD) }
    var currentSubScreen by remember { mutableStateOf(AppSubScreen.NONE) }

    val authViewModel = remember { AuthViewModel(app.authRepository) }
    val dashboardViewModel = remember { DashboardViewModel(app.preferencesManager, app.filterRepository) }
    val filtersViewModel = remember { FiltersViewModel(app.filterRepository) }
    val appsViewModel = remember { AppsViewModel(app.appResolver, app.preferencesManager) }
    val historyViewModel = remember { HistoryViewModel(app.rideLogRepository, app.preferencesManager) }
    val settingsViewModel = remember { SettingsViewModel(app.preferencesManager, app.filterRepository) }
    val profileViewModel = remember { ProfileViewModel(app.authRepository) }

    // 1. Onboarding & Disclaimer Check
    if (!isDisclaimerAccepted) {
        OnboardingScreen(
            onFinished = {
                scope.launch {
                    app.preferencesManager.setDisclaimerAccepted(true)
                }
            }
        )
        return
    }

    // 2. Authentication Check
    if (currentUser == null) {
        AuthScreen(
            viewModel = authViewModel,
            onAuthSuccess = {}
        )
        return
    }

    // 3. Sub-screens overlay/navigation with BackHandler
    when (currentSubScreen) {
        AppSubScreen.SETUP_GUIDE -> {
            BackHandler { currentSubScreen = AppSubScreen.NONE }
            SetupGuideScreen(onBack = { currentSubScreen = AppSubScreen.NONE })
            return
        }
        AppSubScreen.DEV_TOOLS -> {
            BackHandler { currentSubScreen = AppSubScreen.NONE }
            DeveloperToolsScreen(
                viewModel = settingsViewModel,
                onBack = { currentSubScreen = AppSubScreen.NONE }
            )
            return
        }
        AppSubScreen.PROFILE -> {
            BackHandler { currentSubScreen = AppSubScreen.NONE }
            ProfileScreen(
                viewModel = profileViewModel,
                onBack = { currentSubScreen = AppSubScreen.NONE },
                onSignOut = {
                    currentSubScreen = AppSubScreen.NONE
                }
            )
            return
        }
        AppSubScreen.NONE -> {
            // Main tab navigation
        }
    }

    // 4. Main Tab Interface
    Scaffold(
        topBar = {
            AppTopBar(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
                onProfileClick = { currentSubScreen = AppSubScreen.PROFILE }
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(DarkBackground)
        ) {
            when (selectedTab) {
                MainTab.DASHBOARD -> DashboardScreen(
                    viewModel = dashboardViewModel,
                    onNavigateToDevTools = { currentSubScreen = AppSubScreen.DEV_TOOLS }
                )
                MainTab.FILTERS -> FiltersScreen(viewModel = filtersViewModel)
                MainTab.APPS -> AppsScreen(viewModel = appsViewModel)
                MainTab.HISTORY -> RideHistoryScreen(viewModel = historyViewModel)
                MainTab.SETTINGS -> SettingsScreen(
                    viewModel = settingsViewModel,
                    onNavigateToSetupGuide = { currentSubScreen = AppSubScreen.SETUP_GUIDE },
                    onNavigateToDevTools = { currentSubScreen = AppSubScreen.DEV_TOOLS }
                )
            }
        }
    }
}
