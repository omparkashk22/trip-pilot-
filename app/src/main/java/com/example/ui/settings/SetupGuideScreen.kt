package com.example.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.service.OfferWatcherService
import com.example.ui.components.AppCard
import com.example.ui.theme.AccentGreenSuccess
import com.example.ui.theme.BorderDivider
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.PrimaryCyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupGuideScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isAccessibilityGranted by remember { mutableStateOf(false) }
    var isOverlayGranted by remember { mutableStateOf(false) }
    var isNotificationsGranted by remember { mutableStateOf(false) }
    var isBatteryIgnored by remember { mutableStateOf(false) }

    fun checkPermissions() {
        isAccessibilityGranted = OfferWatcherService.instance != null
        isOverlayGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(context) else true
        isNotificationsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else true

        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        isBatteryIgnored = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && pm != null) {
            pm.isIgnoringBatteryOptimizations(context.packageName)
        } else true
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                checkPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        checkPermissions()
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Setup Guide", fontWeight = FontWeight.Bold, color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = PrimaryCyan)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Complete these essential steps to enable automatic detection and background reliability.",
                style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary, lineHeight = 20.sp),
                modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp)
            )

            // Step 1: Android 13+ Sideload Restricted Settings
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                SetupStepCard(
                    stepNumber = "1",
                    icon = Icons.Default.LockOpen,
                    title = "Allow Restricted Settings (Android 13+)",
                    desc = "If Accessibility is greyed out: Go to App Info > tap 3 dots (top right) > tap 'Allow restricted settings'.",
                    isCompleted = isAccessibilityGranted,
                    actionText = "Open App Info",
                    onAction = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.parse("package:${context.packageName}")
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        try { context.startActivity(intent) } catch (_: Exception) {}
                    }
                )
            }

            // Step 2: Accessibility Service
            SetupStepCard(
                stepNumber = "2",
                icon = Icons.Default.AccessibilityNew,
                title = "Enable Accessibility Service",
                desc = "Required for TripPilot to read ride offer fares and addresses on screen and auto-accept.",
                isCompleted = isAccessibilityGranted,
                actionText = "Open Accessibility Settings",
                onAction = {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    try { context.startActivity(intent) } catch (_: Exception) {}
                }
            )

            // Step 3: Overlay (Display Over Other Apps)
            SetupStepCard(
                stepNumber = "3",
                icon = Icons.Default.Layers,
                title = "Display Over Other Apps",
                desc = "Allows heads-up notifications and offer alerts while driver apps are on screen.",
                isCompleted = isOverlayGranted,
                actionText = "Grant Overlay Permission",
                onAction = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                        try { context.startActivity(intent) } catch (_: Exception) {}
                    }
                }
            )

            // Step 4: Notifications
            SetupStepCard(
                stepNumber = "4",
                icon = Icons.Default.Notifications,
                title = "Allow Notifications",
                desc = "Required to keep the foreground service alive in background without system kills.",
                isCompleted = isNotificationsGranted,
                actionText = "Open Notification Settings",
                onAction = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        try { context.startActivity(intent) } catch (_: Exception) {}
                    }
                }
            )

            // Step 5: Battery Optimization & OEM Background Settings
            SetupStepCard(
                stepNumber = "5",
                icon = Icons.Default.BatteryAlert,
                title = "Disable Battery Restrictions",
                desc = "Set battery usage to 'Unrestricted' or 'No restrictions' so the service runs smoothly during driving.",
                isCompleted = isBatteryIgnored,
                actionText = "Ignore Battery Optimization",
                onAction = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        val intent = Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:${context.packageName}")
                        ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                        try { context.startActivity(intent) } catch (_: Exception) {}
                    }
                }
            )

            // OEM Helper Card
            AppCard(backgroundColor = DarkSurfaceElevated) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.Smartphone, contentDescription = null, tint = PrimaryCyan, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Device OEM Autostart Guide",
                            fontWeight = FontWeight.Bold,
                            color = PrimaryCyan,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "• Xiaomi / Poco / Redmi: Settings > Apps > Permissions > Autostart > Enable TripPilot.\n" +
                                    "• Oppo / Realme: Settings > Battery > App Battery Management > Allow Background Activity & Auto-launch.\n" +
                                    "• Vivo: Settings > Battery > Background power consumption > Set TripPilot to High Background Power.\n" +
                                    "• Samsung: Settings > Apps > TripPilot > Battery > Unrestricted.",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SetupStepCard(
    stepNumber: String,
    icon: ImageVector,
    title: String,
    desc: String,
    isCompleted: Boolean,
    actionText: String,
    onAction: () -> Unit
) {
    AppCard(
        backgroundColor = DarkSurface,
        borderColor = if (isCompleted) AccentGreenSuccess.copy(alpha = 0.4f) else BorderDivider
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (isCompleted) AccentGreenSuccess.copy(alpha = 0.15f) else PrimaryCyan.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                if (isCompleted) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "Done", tint = AccentGreenSuccess, modifier = Modifier.size(20.dp))
                } else {
                    Text(text = stepNumber, color = PrimaryCyan, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f)
                    )
                    if (isCompleted) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = AccentGreenSuccess.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "Done ✓",
                                color = AccentGreenSuccess,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = desc,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = DarkSurfaceElevated,
                    border = BorderStroke(1.dp, PrimaryCyan.copy(alpha = 0.5f)),
                    modifier = Modifier.clickable { onAction() }
                ) {
                    Text(
                        text = actionText,
                        color = PrimaryCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}
