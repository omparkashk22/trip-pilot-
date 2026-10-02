package com.example.ui.dashboard

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.ui.components.AppCard
import com.example.ui.components.CustomTextField
import com.example.ui.components.PrimaryActionButton
import com.example.ui.components.StatusBadge
import com.example.ui.theme.AccentAmberWarning
import com.example.ui.theme.AccentGreenSuccess
import com.example.ui.theme.AccentRedDanger
import com.example.ui.theme.BorderDivider
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.PrimaryCyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToDevTools: () -> Unit = {}
) {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val isRunning by viewModel.isServiceRunning.collectAsState()
    val foregroundApp by viewModel.currentForegroundApp.collectAsState()
    val detectionState by viewModel.detectionState.collectAsState()
    val isOfferNotRecognized by viewModel.isOfferRecognitionFailed.collectAsState()
    val serviceMode by viewModel.serviceMode.collectAsState()
    val alertOnAccept by viewModel.alertOnAccept.collectAsState()
    val permissions by viewModel.permissions.collectAsState()
    val isFilterSavedRecently by viewModel.isFilterSavedRecently.collectAsState()

    val quickMinFare by viewModel.quickMinFare.collectAsState()
    val quickMaxFare by viewModel.quickMaxFare.collectAsState()
    val quickUnlimitedMax by viewModel.quickUnlimitedMax.collectAsState()

    // Refresh permissions on resume
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissions(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Service Control Card
        AppCard(
            modifier = Modifier.testTag("service_control_card"),
            backgroundColor = DarkSurface
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Large circular Start/Stop button
                val buttonBg by animateColorAsState(
                    targetValue = if (isRunning) AccentRedDanger else PrimaryCyan,
                    animationSpec = tween(300),
                    label = "btn_color"
                )

                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .clip(CircleShape)
                        .background(buttonBg.copy(alpha = 0.15f))
                        .clickable(enabled = permissions.areRequiredGranted) {
                            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                            viewModel.toggleService(context)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(buttonBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = if (isRunning) "Stop Service" else "Start Service",
                            tint = Color(0xFF060B18),
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (isRunning) stringResource(R.string.service_running) else stringResource(R.string.service_stopped),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (isRunning) AccentGreenSuccess else TextPrimary
                    )
                )

                if (!permissions.areRequiredGranted) {
                    Text(
                        text = stringResource(R.string.grant_permissions_first),
                        color = AccentRedDanger,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                } else {
                    Text(
                        text = if (isRunning) stringResource(R.string.tap_to_stop) else stringResource(R.string.tap_to_start),
                        color = TextSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Mode Selector: Segmented Control (Auto-accept / Notify only)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkSurfaceElevated)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val isAuto = serviceMode == "auto_accept"
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isAuto) PrimaryCyan else Color.Transparent)
                            .clickable { viewModel.setServiceMode("auto_accept") }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.mode_auto_accept),
                            fontWeight = if (isAuto) FontWeight.Bold else FontWeight.Medium,
                            color = if (isAuto) Color(0xFF060B18) else TextSecondary,
                            fontSize = 13.sp
                        )
                    }

                    val isNotify = serviceMode == "notify_only"
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isNotify) PrimaryCyan else Color.Transparent)
                            .clickable { viewModel.setServiceMode("notify_only") }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.mode_notify_only),
                            fontWeight = if (isNotify) FontWeight.Bold else FontWeight.Medium,
                            color = if (isNotify) Color(0xFF060B18) else TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // 2. Driver Apps Status Card
        AppCard(backgroundColor = DarkSurface) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.driver_apps_status),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
                )
                StatusBadge(status = if (isRunning) detectionState else "Stopped")
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (!isRunning) {
                Text(
                    text = stringResource(R.string.service_stopped_hint),
                    style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary, fontSize = 13.sp)
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Foreground App: ",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                    Text(
                        text = foregroundApp ?: "None detected",
                        color = PrimaryCyan,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (isOfferNotRecognized) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = AccentAmberWarning.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, AccentAmberWarning.copy(alpha = 0.3f)),
                        modifier = Modifier.clickable { onNavigateToDevTools() }
                    ) {
                        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = AccentAmberWarning, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.detection_not_recognized),
                                color = AccentAmberWarning,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }

        // 3. Info Banner
        Surface(
            color = DarkSurfaceElevated,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, BorderDivider),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp)
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Info, contentDescription = null, tint = PrimaryCyan, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.lang_info_banner),
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        }

        // 4. Quick Fare Filter Card
        AppCard(backgroundColor = DarkSurface) {
            Text(
                text = stringResource(R.string.fare_filter_card),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = PrimaryCyan)
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CustomTextField(
                    value = quickMinFare,
                    onValueChange = { viewModel.quickMinFare.value = it },
                    label = stringResource(R.string.min_fare_label),
                    modifier = Modifier.weight(1f),
                    testTag = "dashboard_min_fare_input"
                )

                if (!quickUnlimitedMax) {
                    CustomTextField(
                        value = quickMaxFare,
                        onValueChange = { viewModel.quickMaxFare.value = it },
                        label = stringResource(R.string.max_fare_label),
                        modifier = Modifier.weight(1f),
                        testTag = "dashboard_max_fare_input"
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Checkbox(
                    checked = quickUnlimitedMax,
                    onCheckedChange = { viewModel.quickUnlimitedMax.value = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = PrimaryCyan,
                        checkmarkColor = DarkBackground,
                        uncheckedColor = TextSecondary
                    )
                )
                Text(
                    text = stringResource(R.string.unlimited) + " " + stringResource(R.string.max_fare_label),
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            PrimaryActionButton(
                text = if (isFilterSavedRecently) stringResource(R.string.filter_saved) else stringResource(R.string.save_filter),
                onClick = viewModel::saveQuickFare,
                isSuccessState = isFilterSavedRecently,
                testTag = "dashboard_save_filter_button"
            )
        }

        // 5. Alert on Accept Card
        AppCard(backgroundColor = DarkSurface) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(DarkSurfaceElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.VolumeUp, contentDescription = null, tint = PrimaryCyan, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.alert_on_accept),
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                            fontSize = 14.sp
                        )
                        Text(
                            text = stringResource(R.string.play_sound_vibrate),
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                Switch(
                    checked = alertOnAccept,
                    onCheckedChange = viewModel::setAlertOnAccept,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = DarkBackground,
                        checkedTrackColor = PrimaryCyan,
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = DarkSurfaceElevated
                    )
                )
            }
        }

        // 6. Permissions Card
        AppCard(backgroundColor = DarkSurface) {
            Text(
                text = stringResource(R.string.permissions_card),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Accessibility Row
            PermissionRow(
                icon = Icons.Default.AccessibilityNew,
                title = stringResource(R.string.perm_accessibility),
                description = stringResource(R.string.perm_accessibility_desc),
                isGranted = permissions.accessibilityGranted,
                onClick = {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    try { context.startActivity(intent) } catch (_: Exception) {}
                }
            )

            // Display Over Other Apps Row
            PermissionRow(
                icon = Icons.Default.Layers,
                title = stringResource(R.string.perm_overlay),
                description = stringResource(R.string.perm_overlay_desc),
                isGranted = permissions.overlayGranted,
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                        try { context.startActivity(intent) } catch (_: Exception) {}
                    }
                }
            )

            // Notifications Row
            PermissionRow(
                icon = Icons.Default.Notifications,
                title = stringResource(R.string.perm_notifications),
                description = stringResource(R.string.perm_notifications_desc),
                isGranted = permissions.notificationsGranted,
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        try { context.startActivity(intent) } catch (_: Exception) {}
                    }
                }
            )

            // Battery Optimization Row
            PermissionRow(
                icon = Icons.Default.BatteryAlert,
                title = stringResource(R.string.perm_battery),
                description = stringResource(R.string.perm_battery_desc),
                isGranted = permissions.batteryOptimizationIgnored,
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        val intent = Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:${context.packageName}")
                        ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                        try { context.startActivity(intent) } catch (_: Exception) {}
                    }
                }
            )
        }
    }
}

@Composable
fun PermissionRow(
    icon: ImageVector,
    title: String,
    description: String,
    isGranted: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (isGranted) AccentGreenSuccess.copy(alpha = 0.15f) else DarkSurfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isGranted) AccentGreenSuccess else PrimaryCyan,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                fontSize = 14.sp
            )
            Text(
                text = description,
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }

        if (isGranted) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Granted",
                tint = AccentGreenSuccess,
                modifier = Modifier.size(22.dp)
            )
        } else {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = "Grant",
                tint = TextSecondary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
