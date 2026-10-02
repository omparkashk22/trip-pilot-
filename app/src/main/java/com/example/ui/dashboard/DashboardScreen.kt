package com.example.ui.dashboard

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.data.model.AppRuntimeState
import com.example.data.model.AppRuntimeStatus
import com.example.ui.components.AppCard
import com.example.ui.components.AppTextField
import com.example.ui.components.Icon3D
import com.example.ui.components.Icon3DTint
import com.example.ui.components.PrimaryActionButton
import com.example.ui.components.StatusBadge
import com.example.ui.theme.LocalAppColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToDevTools: () -> Unit = {}
) {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val colors = LocalAppColors.current

    val isRunning by viewModel.isServiceRunning.collectAsState()
    val detectionState by viewModel.detectionState.collectAsState()
    val isOfferNotRecognized by viewModel.isOfferRecognitionFailed.collectAsState()
    val serviceMode by viewModel.serviceMode.collectAsState()
    val alertOnAccept by viewModel.alertOnAccept.collectAsState()
    val permissions by viewModel.permissions.collectAsState()
    val isFilterSavedRecently by viewModel.isFilterSavedRecently.collectAsState()
    val showDebugInfo by viewModel.showDebugInfo.collectAsState()
    val resolvedBharat by viewModel.resolvedBharatPackage.collectAsState()
    val resolvedRapido by viewModel.resolvedRapidoPackage.collectAsState()

    val appStatuses by viewModel.appRuntimeStatuses.collectAsState()
    val updateInfo by viewModel.updateInfo.collectAsState()

    val quickMinFare by viewModel.quickMinFare.collectAsState()
    val quickMaxFare by viewModel.quickMaxFare.collectAsState()
    val quickUnlimitedMax by viewModel.quickUnlimitedMax.collectAsState()

    // Breathing glow animation on Start button while engine runs
    val infiniteTransition = rememberInfiniteTransition(label = "engine_glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

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
            .background(colors.background)
            .verticalScroll(scrollState)
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 0. Update Available Banner (Non-intrusive)
        AnimatedVisibility(
            visible = updateInfo != null && updateInfo?.isUpdateAvailable == true,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            updateInfo?.let { info ->
                UpdateAvailableBanner(
                    updateInfo = info,
                    onDownloadClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(info.downloadUrl)).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        try { context.startActivity(intent) } catch (_: Exception) {}
                    },
                    onDismissClick = viewModel::dismissUpdateBanner
                )
            }
        }

        // 1. Service Control Card
        AppCard(
            modifier = Modifier.testTag("service_control_card"),
            backgroundColor = colors.surface,
            borderColor = colors.hairline
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon3D(
                            icon = Icons.Default.PowerSettingsNew,
                            tint = if (isRunning) Icon3DTint.GREEN else Icon3DTint.CYAN
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Service Control",
                                color = colors.textSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (isRunning) colors.success else colors.textSecondary.copy(alpha = 0.5f))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isRunning) stringResource(R.string.service_running) else stringResource(R.string.service_stopped),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = if (isRunning) colors.success else colors.text
                                )
                            }
                        }
                    }

                    StatusBadge(status = if (isRunning) "Running" else "Stopped")
                }

                if (!permissions.areRequiredGranted) {
                    Text(
                        text = stringResource(R.string.grant_permissions_first),
                        color = colors.danger,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Start and Stop buttons (44dp high, radius 14dp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Start Button (Primary)
                    val startModifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("service_start_button")
                        .then(
                            if (isRunning) {
                                Modifier.shadow(
                                    elevation = 6.dp,
                                    shape = RoundedCornerShape(14.dp),
                                    spotColor = colors.accent.copy(alpha = glowAlpha)
                                )
                            } else Modifier
                        )

                    Button(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                            viewModel.startEngine(context)
                        },
                        enabled = !isRunning && permissions.areRequiredGranted,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accent,
                            contentColor = Color(0xFF060B18),
                            disabledContainerColor = colors.surface2,
                            disabledContentColor = colors.textSecondary
                        ),
                        modifier = startModifier
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Start", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    // Stop Button (Red outline)
                    OutlinedButton(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                            viewModel.stopEngine(context)
                        },
                        enabled = isRunning,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, if (isRunning) colors.danger else colors.hairline),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = colors.danger,
                            disabledContentColor = colors.textSecondary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("service_stop_button")
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Stop", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Fully disable in Accessibility settings",
                    color = colors.accent,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .clickable {
                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            try { context.startActivity(intent) } catch (_: Exception) {}
                        }
                        .padding(4.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Mode Selector: Segmented Control
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surface2)
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val isAuto = serviceMode == "auto_accept"
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isAuto) colors.accent else Color.Transparent)
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                viewModel.setServiceMode("auto_accept")
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.mode_auto_accept),
                            fontWeight = if (isAuto) FontWeight.Bold else FontWeight.Medium,
                            color = if (isAuto) Color(0xFF060B18) else colors.textSecondary,
                            fontSize = 12.sp
                        )
                    }

                    val isNotify = serviceMode == "notify_only"
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isNotify) colors.accent else Color.Transparent)
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                viewModel.setServiceMode("notify_only")
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.mode_notify_only),
                            fontWeight = if (isNotify) FontWeight.Bold else FontWeight.Medium,
                            color = if (isNotify) Color(0xFF060B18) else colors.textSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // 2. Driver Apps Status Card (One compact row per target app, always both visible)
        AppCard(backgroundColor = colors.surface, borderColor = colors.hairline) {
            Text(
                text = stringResource(R.string.driver_apps_status),
                color = colors.textSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Row 1: Bharat Taxi
            val bharatStatus = appStatuses["bharat_taxi"] ?: AppRuntimeStatus(
                appId = "bharat_taxi",
                displayName = "Bharat Taxi",
                resolvedPackage = resolvedBharat
            )
            TargetAppStatusRow(
                status = bharatStatus,
                isRunning = isRunning,
                showDebugInfo = showDebugInfo,
                onRowClick = {
                    bharatStatus.resolvedPackage?.let { pkg -> viewModel.openApp(context, pkg) }
                }
            )

            Spacer(modifier = Modifier.height(6.dp))
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.hairline))
            Spacer(modifier = Modifier.height(6.dp))

            // Row 2: Rapido
            val rapidoStatus = appStatuses["rapido"] ?: AppRuntimeStatus(
                appId = "rapido",
                displayName = "Rapido",
                resolvedPackage = resolvedRapido
            )
            TargetAppStatusRow(
                status = rapidoStatus,
                isRunning = isRunning,
                showDebugInfo = showDebugInfo,
                onRowClick = {
                    rapidoStatus.resolvedPackage?.let { pkg -> viewModel.openApp(context, pkg) }
                }
            )

            if (isRunning && isOfferNotRecognized) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = colors.warning.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, colors.warning.copy(alpha = 0.3f)),
                    modifier = Modifier.clickable { onNavigateToDevTools() }
                ) {
                    Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = colors.warning, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.detection_not_recognized),
                            color = colors.warning,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }

        // 3. Info Banner
        Surface(
            color = colors.surface2,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, colors.hairline),
            modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp)
        ) {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Info, contentDescription = null, tint = colors.accent, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.lang_info_banner),
                    color = colors.textSecondary,
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }
        }

        // 4. Quick Fare Filter Card
        AppCard(backgroundColor = colors.surface, borderColor = colors.hairline) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon3D(icon = Icons.Default.CurrencyRupee, tint = Icon3DTint.AMBER)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.fare_filter_card),
                        color = colors.textSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AppTextField(
                    value = quickMinFare,
                    onValueChange = { viewModel.quickMinFare.value = it },
                    label = "Min Fare",
                    isNumeric = true,
                    unitText = "₹",
                    modifier = Modifier.weight(1f),
                    testTag = "dashboard_min_fare_input"
                )

                if (!quickUnlimitedMax) {
                    AppTextField(
                        value = quickMaxFare,
                        onValueChange = { viewModel.quickMaxFare.value = it },
                        label = "Max Fare",
                        isNumeric = true,
                        unitText = "₹",
                        modifier = Modifier.weight(1f),
                        testTag = "dashboard_max_fare_input"
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Checkbox(
                    checked = quickUnlimitedMax,
                    onCheckedChange = { viewModel.quickUnlimitedMax.value = it },
                    modifier = Modifier.scale(0.85f),
                    colors = CheckboxDefaults.colors(
                        checkedColor = colors.accent,
                        checkmarkColor = Color(0xFF060B18),
                        uncheckedColor = colors.textSecondary
                    )
                )
                Text(
                    text = "Unlimited Max Fare",
                    color = colors.textSecondary,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            PrimaryActionButton(
                text = if (isFilterSavedRecently) stringResource(R.string.filter_saved) else stringResource(R.string.save_filter),
                onClick = viewModel::saveQuickFare,
                isSuccessState = isFilterSavedRecently,
                testTag = "dashboard_save_filter_button"
            )
        }

        // 5. Alert on Accept Card
        AppCard(backgroundColor = colors.surface, borderColor = colors.hairline) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon3D(icon = Icons.Default.VolumeUp, tint = Icon3DTint.ROSE)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.alert_on_accept),
                            fontWeight = FontWeight.SemiBold,
                            color = colors.text,
                            fontSize = 13.sp
                        )
                        Text(
                            text = stringResource(R.string.play_sound_vibrate),
                            color = colors.textSecondary,
                            fontSize = 11.sp
                        )
                    }
                }

                Switch(
                    checked = alertOnAccept,
                    onCheckedChange = {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        viewModel.setAlertOnAccept(it)
                    },
                    modifier = Modifier.scale(0.82f),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colors.background,
                        checkedTrackColor = colors.accent,
                        uncheckedThumbColor = colors.textSecondary,
                        uncheckedTrackColor = colors.surface2
                    )
                )
            }
        }

        // 6. Permissions Card (Rows 48dp high with Icon3D and check or chevron)
        AppCard(backgroundColor = colors.surface, borderColor = colors.hairline) {
            Text(
                text = stringResource(R.string.permissions_card),
                color = colors.textSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(8.dp))

            PermissionCompactRow(
                icon = Icons.Default.AccessibilityNew,
                tint = Icon3DTint.CYAN,
                title = stringResource(R.string.perm_accessibility),
                isGranted = permissions.accessibilityGranted,
                onClick = {
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    try { context.startActivity(intent) } catch (_: Exception) {}
                }
            )

            PermissionCompactRow(
                icon = Icons.Default.Layers,
                tint = Icon3DTint.VIOLET,
                title = stringResource(R.string.perm_overlay),
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

            PermissionCompactRow(
                icon = Icons.Default.Notifications,
                tint = Icon3DTint.AMBER,
                title = stringResource(R.string.perm_notifications),
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

            PermissionCompactRow(
                icon = Icons.Default.BatteryAlert,
                tint = Icon3DTint.GREEN,
                title = stringResource(R.string.perm_battery),
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
fun TargetAppStatusRow(
    status: AppRuntimeStatus,
    isRunning: Boolean,
    showDebugInfo: Boolean,
    onRowClick: () -> Unit
) {
    val colors = LocalAppColors.current
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    val isBharat = status.appId == "bharat_taxi"
    val appMarkColor = if (isBharat) colors.accent else colors.warning

    val (chipLabel, chipColor) = when (status.state) {
        AppRuntimeState.FOREGROUND -> "Watching" to colors.accent
        AppRuntimeState.OFFER_DETECTED -> "Offer detected" to colors.success
        AppRuntimeState.NOT_RECOGNIZED -> "Not recognized" to colors.warning
        AppRuntimeState.PAUSED -> "Paused" to colors.textSecondary
        AppRuntimeState.NOT_INSTALLED -> "Not installed" to colors.textSecondary
        AppRuntimeState.IDLE -> "Idle" to colors.textSecondary
    }

    val caption = when {
        status.lastEventAt == null -> "no events yet"
        status.lastOfferParsedAt == null -> "last seen ${timeFormat.format(Date(status.lastEventAt))} · no offers yet"
        else -> "last seen ${timeFormat.format(Date(status.lastEventAt))} · last offer ${timeFormat.format(Date(status.lastOfferParsedAt))}"
    }

    val now = System.currentTimeMillis()
    val showZeroEventsHint = isRunning && status.isInstalled && status.isEnabled &&
            (status.lastEventAt == null || (now - status.lastEventAt > 5 * 60 * 1000L))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = status.isInstalled && status.resolvedPackage != null, onClick = onRowClick)
            .padding(vertical = 4.dp, horizontal = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App Icon
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(appMarkColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isBharat) "B" else "R",
                    color = appMarkColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = status.displayName,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.text,
                        fontSize = 13.sp
                    )
                }

                Text(
                    text = caption,
                    color = colors.textSecondary,
                    fontSize = 10.5.sp,
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // State chip
            Surface(
                color = chipColor.copy(alpha = 0.15f),
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, chipColor.copy(alpha = 0.35f)),
                modifier = Modifier.height(20.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 6.dp)) {
                    Text(
                        text = chipLabel,
                        color = chipColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        if (showDebugInfo) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${status.resolvedPackage ?: "Unresolved"} · events last 60 s: ${status.eventsLast60s}",
                color = colors.textSecondary,
                fontSize = 10.sp,
                modifier = Modifier.padding(start = 34.dp)
            )
        }

        if (showZeroEventsHint) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "No events from this app yet. Open it once while the service is running.",
                color = colors.accent,
                fontSize = 10.sp,
                lineHeight = 13.sp,
                modifier = Modifier.padding(start = 34.dp)
            )
        }
    }
}

@Composable
fun PermissionCompactRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Icon3DTint,
    title: String,
    isGranted: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalAppColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon3D(icon = icon, tint = tint)
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = title,
            fontWeight = FontWeight.Medium,
            color = colors.text,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )

        if (isGranted) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Granted",
                tint = colors.success,
                modifier = Modifier.size(18.dp)
            )
        } else {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = "Grant",
                tint = colors.textSecondary,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
fun UpdateAvailableBanner(
    updateInfo: com.example.data.remote.AppUpdateInfo,
    onDownloadClick: () -> Unit,
    onDismissClick: () -> Unit
) {
    val colors = LocalAppColors.current

    Surface(
        color = colors.accent.copy(alpha = 0.12f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, colors.accent.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp)
            .testTag("update_available_banner")
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.accent.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Update Available",
                                fontWeight = FontWeight.Bold,
                                color = colors.text,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = colors.accent.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "v${updateInfo.latestVersion}",
                                    color = colors.accent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }

                        Text(
                            text = if (updateInfo.releaseTitle.isNotEmpty() && updateInfo.releaseTitle != updateInfo.latestVersion) {
                                updateInfo.releaseTitle
                            } else {
                                "A newer version of TripPilot is ready to install"
                            },
                            color = colors.textSecondary,
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }
                }

                IconButton(
                    onClick = onDismissClick,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = onDownloadClick,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = Color(0xFF060B18)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .testTag("download_update_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Download APK (v${updateInfo.latestVersion})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
    }
}
