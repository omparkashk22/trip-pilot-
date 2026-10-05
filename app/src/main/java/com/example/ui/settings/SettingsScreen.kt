package com.example.ui.settings

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.components.AppCard
import com.example.ui.components.Icon3D
import com.example.ui.components.Icon3DTint
import com.example.ui.theme.LocalAppColors

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateToSetupGuide: () -> Unit,
    onNavigateToDevTools: () -> Unit
) {
    val context = LocalContext.current
    val colors = LocalAppColors.current

    val currentLang by viewModel.currentLanguage.collectAsState()
    val currentTheme by viewModel.currentTheme.collectAsState()
    val textScale by viewModel.textScale.collectAsState()
    val keepScreenOn by viewModel.keepScreenOn.collectAsState()
    val processTestRequests by viewModel.processTestRequests.collectAsState()
    val tapMethod by viewModel.tapMethod.collectAsState()
    val showDebugInfo by viewModel.showDebugInfo.collectAsState()
    val soundEnabled by viewModel.soundEnabled.collectAsState()
    val vibrationEnabled by viewModel.vibrationEnabled.collectAsState()
    val turboMode by viewModel.turboMode.collectAsState()

    val isCheckingForUpdate by viewModel.isCheckingForUpdate.collectAsState()
    val updateCheckResult by viewModel.updateCheckResult.collectAsState()
    val latestUpdateInfo by viewModel.latestUpdateInfo.collectAsState()
    val lastCheckedTime by viewModel.lastCheckedTime.collectAsState()
    val isDiagnosticsExpanded by viewModel.isDiagnosticsExpanded.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val downloadStatusMessage by viewModel.downloadStatusMessage.collectAsState()
    val installErrorMessage by viewModel.installErrorMessage.collectAsState()
    val clipboardMessage by viewModel.clipboardMessage.collectAsState()
    val includePreReleases by viewModel.includePreReleases.collectAsState()
    val updateSourceType by viewModel.updateSourceType.collectAsState()

    val installedVersionCode = remember { com.example.data.remote.VersionChecker.getInstalledVersionCode(context) }
    val installedVersionName = remember { com.example.data.remote.VersionChecker.getInstalledVersionName(context) }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.resumeInstallAfterPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var showLangDialog by remember { mutableStateOf(false) }
    var showTapMethodDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showTextScaleDialog by remember { mutableStateOf(false) }
    var showSoundVibDialog by remember { mutableStateOf(false) }
    var showResetConfirmation by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    val languages = listOf(
        "en" to "English",
        "hi" to "हिन्दी (Hindi)",
        "ta" to "தமிழ் (Tamil)",
        "kn" to "ಕನ್ನಡ (Kannada)",
        "te" to "తెలుగు (Telugu)",
        "ml" to "മലയാളം (Malayalam)",
        "mr" to "मराठी (Marathi)",
        "pa" to "ਪੰਜਾਬੀ (Punjabi)",
        "bn" to "বাংলা (Bengali)",
        "gu" to "ગુજરાતી (Gujarati)"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(scrollState)
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Setup Guide Navigation Card
        AppCard(
            backgroundColor = colors.surface,
            borderColor = colors.hairline,
            onClick = onNavigateToSetupGuide,
            modifier = Modifier.testTag("nav_setup_guide")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon3D(icon = Icons.Default.Rule, tint = Icon3DTint.CYAN)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.setup_guide),
                            fontWeight = FontWeight.SemiBold,
                            color = colors.text,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Guided permission & device OEM configuration",
                            color = colors.textSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
                Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, tint = colors.accent, modifier = Modifier.size(14.dp))
            }
        }

        // Developer Tools Navigation Card
        AppCard(
            backgroundColor = colors.surface,
            borderColor = colors.hairline,
            onClick = onNavigateToDevTools,
            modifier = Modifier.testTag("nav_dev_tools")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon3D(icon = Icons.Default.Build, tint = Icon3DTint.VIOLET)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.developer_tools),
                            fontWeight = FontWeight.SemiBold,
                            color = colors.text,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Screen Inspector & Ride Offer Simulator",
                            color = colors.textSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
                Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(14.dp))
            }
        }

        // App Preferences Card
        AppCard(backgroundColor = colors.surface, borderColor = colors.hairline) {
            Text(
                text = "Preferences",
                color = colors.textSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Language Row
            SettingsClickableRow(
                icon = Icons.Default.Language,
                tint = Icon3DTint.CYAN,
                title = stringResource(R.string.language),
                value = languages.find { it.first == currentLang }?.second ?: "English",
                onClick = { showLangDialog = true }
            )

            // Theme Row
            SettingsClickableRow(
                icon = Icons.Default.DarkMode,
                tint = Icon3DTint.AMBER,
                title = stringResource(R.string.theme),
                value = when (currentTheme.lowercase()) {
                    "light" -> "Light"
                    "system" -> "System"
                    else -> "Dark"
                },
                onClick = { showThemeDialog = true }
            )

            // Tap Method Row
            SettingsClickableRow(
                icon = Icons.Default.TouchApp,
                tint = Icon3DTint.GREEN,
                title = stringResource(R.string.tap_method),
                value = when (tapMethod) {
                    "node_click" -> "Node Click"
                    "parent_click" -> "Parent Click"
                    "gesture" -> "Gesture Tap"
                    else -> "Auto"
                },
                onClick = { showTapMethodDialog = true }
            )

            // Sound & Vibration Row
            val soundVibSummary = when {
                soundEnabled && vibrationEnabled -> "Sound + Vibrate"
                soundEnabled -> "Sound only"
                vibrationEnabled -> "Vibrate only"
                else -> "Silent"
            }
            SettingsClickableRow(
                icon = Icons.Default.VolumeUp,
                tint = Icon3DTint.ROSE,
                title = stringResource(R.string.sound_vibration),
                value = soundVibSummary,
                onClick = { showSoundVibDialog = true }
            )

            // Text Size Row
            val textScaleLabel = when ((textScale * 100).toInt()) {
                115 -> "115% (Medium)"
                130 -> "130% (Large)"
                140 -> "140% (Extra Large)"
                else -> "100% (Default)"
            }
            SettingsClickableRow(
                icon = Icons.Default.FormatSize,
                tint = Icon3DTint.CYAN,
                title = "Text Size",
                value = textScaleLabel,
                onClick = { showTextScaleDialog = true }
            )
        }

        // Monitoring & Automation Controls Card
        AppCard(backgroundColor = colors.surface, borderColor = colors.hairline) {
            Text(
                text = "Monitoring & Behavior",
                color = colors.textSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Keep Screen On
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(R.string.keep_screen_on), color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text(text = "Prevents screen from sleeping while monitoring", color = colors.textSecondary, fontSize = 11.sp)
                }
                Switch(
                    checked = keepScreenOn,
                    onCheckedChange = viewModel::setKeepScreenOn,
                    modifier = Modifier.scale(0.82f),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colors.background,
                        checkedTrackColor = colors.accent,
                        uncheckedThumbColor = colors.textSecondary,
                        uncheckedTrackColor = colors.surface2
                    )
                )
            }

            // Turbo Mode (default ON)
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Turbo mode", color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text(text = "Keeps CPU awake for instant tap; uses a bit more battery", color = colors.textSecondary, fontSize = 11.sp)
                }
                Switch(
                    checked = turboMode,
                    onCheckedChange = viewModel::setTurboMode,
                    modifier = Modifier.scale(0.82f),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colors.background,
                        checkedTrackColor = colors.accent,
                        uncheckedThumbColor = colors.textSecondary,
                        uncheckedTrackColor = colors.surface2
                    )
                )
            }

            // Process Test Requests
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(R.string.process_test_requests), color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text(text = "Parses and evaluates mock/test offer cards", color = colors.textSecondary, fontSize = 11.sp)
                }
                Switch(
                    checked = processTestRequests,
                    onCheckedChange = viewModel::setProcessTestRequests,
                    modifier = Modifier.scale(0.82f),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colors.background,
                        checkedTrackColor = colors.accent,
                        uncheckedThumbColor = colors.textSecondary,
                        uncheckedTrackColor = colors.surface2
                    )
                )
            }

            // Show Debug Info
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(R.string.show_debug_info), color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text(text = "Displays package and resolution details", color = colors.textSecondary, fontSize = 11.sp)
                }
                Switch(
                    checked = showDebugInfo,
                    onCheckedChange = viewModel::setShowDebugInfo,
                    modifier = Modifier.scale(0.82f),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colors.background,
                        checkedTrackColor = colors.accent,
                        uncheckedThumbColor = colors.textSecondary,
                        uncheckedTrackColor = colors.surface2
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Reset All Filters
            OutlinedButton(
                onClick = { showResetConfirmation = true },
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, colors.danger.copy(alpha = 0.5f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.danger),
                modifier = Modifier.fillMaxWidth().height(42.dp)
            ) {
                Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.reset_filters), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        // About Card
        AppCard(backgroundColor = colors.surface, borderColor = colors.hairline) {
            Text(
                text = stringResource(R.string.about_trippilot),
                color = colors.textSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Installed version
            Text(
                text = "Installed: $installedVersionName (build $installedVersionCode)",
                color = colors.text,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )

            // Latest version (after a check)
            if (latestUpdateInfo != null && latestUpdateInfo?.status != com.example.data.remote.UpdateResultState.ERROR) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Latest: build ${latestUpdateInfo?.remoteBuild}",
                    color = colors.accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Last checked time
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Last checked: ${lastCheckedTime ?: "Never"}",
                color = colors.textSecondary,
                fontSize = 11.sp
            )

            // Source name
            Spacer(modifier = Modifier.height(2.dp))
            val sourceDisplayName = if (updateSourceType == "HOSTED_JSON") {
                "Hosted version.json"
            } else {
                "GitHub Releases (${com.example.BuildConfig.GITHUB_OWNER}/${com.example.BuildConfig.GITHUB_REPO})"
            }
            Text(
                text = "Source: $sourceDisplayName",
                color = colors.textSecondary,
                fontSize = 11.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Options: Pre-releases toggle and Source toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = includePreReleases,
                        onCheckedChange = { viewModel.setIncludePreReleases(it) },
                        colors = CheckboxDefaults.colors(checkedColor = colors.accent)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Include pre-releases", fontSize = 11.sp, color = colors.text)
                }

                Text(
                    text = if (updateSourceType == "GITHUB") "Use JSON" else "Use GitHub",
                    fontSize = 11.sp,
                    color = colors.accent,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable {
                        viewModel.setUpdateSourceType(if (updateSourceType == "GITHUB") "HOSTED_JSON" else "GITHUB")
                    }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Check for Updates button
            OutlinedButton(
                onClick = { viewModel.checkForUpdates(context) },
                enabled = !isCheckingForUpdate,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, colors.accent),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = colors.accent,
                    disabledContentColor = colors.textSecondary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .testTag("check_for_updates_button")
            ) {
                if (isCheckingForUpdate) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = colors.accent,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Checking for updates...", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                } else {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Check for Updates", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            if (updateCheckResult != null) {
                Spacer(modifier = Modifier.height(8.dp))
                val isError = latestUpdateInfo?.status == com.example.data.remote.UpdateResultState.ERROR
                val isUpdateAvailable = latestUpdateInfo?.status == com.example.data.remote.UpdateResultState.UPDATE_AVAILABLE

                Surface(
                    color = when {
                        isError -> colors.danger.copy(alpha = 0.12f)
                        isUpdateAvailable -> colors.accent.copy(alpha = 0.12f)
                        else -> colors.surface2
                    },
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(
                        1.dp,
                        when {
                            isError -> colors.danger.copy(alpha = 0.4f)
                            isUpdateAvailable -> colors.accent.copy(alpha = 0.4f)
                            else -> colors.hairline
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = updateCheckResult.orEmpty(),
                            color = when {
                                isError -> colors.danger
                                isUpdateAvailable -> colors.accent
                                else -> colors.text
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )

                        if (isUpdateAvailable && latestUpdateInfo != null) {
                            val info = latestUpdateInfo!!
                            if (info.releaseNotes.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = info.releaseNotes,
                                    color = colors.textSecondary,
                                    fontSize = 11.sp,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (info.apkSizeBytes > 0) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Size: ${com.example.data.remote.VersionChecker.formatFileSize(info.apkSizeBytes)}",
                                    color = colors.textSecondary,
                                    fontSize = 11.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { viewModel.downloadAndInstallUpdate(context) },
                                enabled = downloadProgress == null,
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.accent,
                                    contentColor = Color(0xFF060B18)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(38.dp)
                                    .testTag("download_update_button_settings")
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Download & Install (v${info.latestVersion})", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            if (downloadProgress != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { downloadProgress ?: 0f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = colors.accent,
                                    trackColor = colors.surface
                                )
                                if (downloadStatusMessage != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = downloadStatusMessage.orEmpty(),
                                        color = colors.accent,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }

                        if (installErrorMessage != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = installErrorMessage.orEmpty(),
                                color = colors.danger,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            // Diagnostics Section
            val diag = latestUpdateInfo?.diagnostics
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                color = colors.surface2,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, colors.hairline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.toggleDiagnostics() },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Diagnostics",
                            color = colors.text,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Icon(
                            imageVector = if (isDiagnosticsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isDiagnosticsExpanded) "Collapse" else "Expand",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    if (isDiagnosticsExpanded) {
                        Spacer(modifier = Modifier.height(8.dp))
                        if (diag != null) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Source URL: ${diag.sourceUrl}", fontSize = 10.sp, color = colors.textSecondary)
                                Text("HTTP Status: ${diag.httpStatus}", fontSize = 10.sp, color = colors.textSecondary)
                                Text("Releases Found: ${diag.releasesFound}", fontSize = 10.sp, color = colors.textSecondary)
                                Text("Tags Found: ${if (diag.tagsFound.isEmpty()) "None" else diag.tagsFound.joinToString(", ")}", fontSize = 10.sp, color = colors.textSecondary)
                                val parsedStr = diag.parsedBuilds.joinToString(", ") { "${it.first}: ${it.second ?: "unparsed"}" }
                                Text("Parsed Builds: [${if (parsedStr.isEmpty()) "None" else parsedStr}]", fontSize = 10.sp, color = colors.textSecondary)
                                Text("Chosen Release: ${diag.chosenRelease ?: "None"}", fontSize = 10.sp, color = colors.textSecondary)
                                Text("Chosen Asset: ${diag.chosenAsset ?: "None"}", fontSize = 10.sp, color = colors.textSecondary)
                                Text("Comparison: ${diag.comparison}", fontSize = 10.sp, color = if (diag.isUpdateAvailable) colors.accent else colors.textSecondary, fontWeight = FontWeight.Bold)

                                Spacer(modifier = Modifier.height(6.dp))

                                OutlinedButton(
                                    onClick = { viewModel.copyDiagnostics(context) },
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, colors.accent),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.accent),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(32.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Copy diagnostics", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }

                                if (clipboardMessage != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = clipboardMessage.orEmpty(),
                                        color = colors.accent,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = "Run 'Check for Updates' to view network diagnostics.",
                                color = colors.textSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "Privacy Policy",
                    color = colors.accent,
                    fontSize = 12.sp,
                    modifier = Modifier.clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://trippilot.in/privacy"))
                        try { context.startActivity(intent) } catch (_: Exception) {}
                    }
                )
                Text(
                    text = "Terms of Service",
                    color = colors.accent,
                    fontSize = 12.sp,
                    modifier = Modifier.clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://trippilot.in/terms"))
                        try { context.startActivity(intent) } catch (_: Exception) {}
                    }
                )
            }
        }
    }

    // Language Dialog
    if (showLangDialog) {
        AlertDialog(
            onDismissRequest = { showLangDialog = false },
            title = { Text("Select Language", color = colors.text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp) },
            text = {
                Column {
                    languages.forEach { (code, name) ->
                        val isSelected = currentLang == code
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setLanguage(code)
                                    showLangDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isSelected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (isSelected) colors.accent else colors.textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(text = name, color = if (isSelected) colors.text else colors.textSecondary, fontSize = 13.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLangDialog = false }) {
                    Text("Close", color = colors.accent, fontSize = 12.sp)
                }
            },
            containerColor = colors.surface
        )
    }

    // Theme Dialog
    if (showThemeDialog) {
        val themes = listOf("dark" to "Dark", "light" to "Light", "system" to "System Default")
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Choose Theme", color = colors.text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp) },
            text = {
                Column {
                    themes.forEach { (key, label) ->
                        val isSelected = currentTheme.equals(key, ignoreCase = true)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setTheme(key)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isSelected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (isSelected) colors.accent else colors.textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(text = label, color = if (isSelected) colors.text else colors.textSecondary, fontSize = 13.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) { Text("Cancel", color = colors.accent, fontSize = 12.sp) }
            },
            containerColor = colors.surface
        )
    }

    // Tap Method Dialog
    if (showTapMethodDialog) {
        val methods = listOf(
            "auto" to "Auto (Best performing strategy)",
            "node_click" to "Node Click (ACTION_CLICK on node)",
            "parent_click" to "Parent Click (ACTION_CLICK on parent)",
            "gesture" to "Gesture Tap (Simulated touch at bounds center)"
        )
        AlertDialog(
            onDismissRequest = { showTapMethodDialog = false },
            title = { Text("Select Tap Strategy", color = colors.text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp) },
            text = {
                Column {
                    methods.forEach { (key, desc) ->
                        val isSelected = tapMethod == key
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setTapMethod(key)
                                    showTapMethodDialog = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isSelected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (isSelected) colors.accent else colors.textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(text = desc, color = if (isSelected) colors.text else colors.textSecondary, fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTapMethodDialog = false }) { Text("Close", color = colors.accent, fontSize = 12.sp) }
            },
            containerColor = colors.surface
        )
    }

    // Text Size Dialog
    if (showTextScaleDialog) {
        val scaleOptions = listOf(
            1.0f to "100% (Default)",
            1.15f to "115% (Medium)",
            1.30f to "130% (Large)",
            1.40f to "140% (Extra Large)"
        )
        AlertDialog(
            onDismissRequest = { showTextScaleDialog = false },
            title = {
                Text("Text Size", color = colors.text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            },
            text = {
                Column {
                    Text(
                        text = "Adjust text scale for comfortable viewing on vehicle mounts. Layouts dynamically adapt up to 140% without clipping.",
                        color = colors.textSecondary,
                        fontSize = 11.5.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    scaleOptions.forEach { (scale, label) ->
                        val isSelected = kotlin.math.abs(textScale - scale) < 0.01f
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setTextScale(scale)
                                    showTextScaleDialog = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) colors.accent else colors.text,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                            Icon(
                                imageVector = if (isSelected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (isSelected) colors.accent else colors.textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showTextScaleDialog = false }) {
                    Text("Close", color = colors.textSecondary, fontSize = 12.sp)
                }
            },
            containerColor = colors.surface
        )
    }

    // Sound & Vibration Dialog
    if (showSoundVibDialog) {
        val overlayNotificationManager = remember { com.example.service.OverlayNotificationManager(context) }
        AlertDialog(
            onDismissRequest = { showSoundVibDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.sound_vibration),
                    color = colors.text,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Customize alert feedback when incoming ride offers match or are auto-accepted.",
                        color = colors.textSecondary,
                        fontSize = 12.sp
                    )

                    // Sound Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.VolumeUp, contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Play Sound", color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                Text("Notification audio chime", color = colors.textSecondary, fontSize = 11.sp)
                            }
                        }
                        Switch(
                            checked = soundEnabled,
                            onCheckedChange = { viewModel.setSoundEnabled(it) },
                            modifier = Modifier.scale(0.82f),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = colors.background,
                                checkedTrackColor = colors.accent,
                                uncheckedThumbColor = colors.textSecondary,
                                uncheckedTrackColor = colors.surface2
                            )
                        )
                    }

                    // Vibration Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Vibration, contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Vibrate", color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                Text("Double-pulse haptic pattern", color = colors.textSecondary, fontSize = 11.sp)
                            }
                        }
                        Switch(
                            checked = vibrationEnabled,
                            onCheckedChange = { viewModel.setVibrationEnabled(it) },
                            modifier = Modifier.scale(0.82f),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = colors.background,
                                checkedTrackColor = colors.accent,
                                uncheckedThumbColor = colors.textSecondary,
                                uncheckedTrackColor = colors.surface2
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Test Alert Button
                    OutlinedButton(
                        onClick = {
                            overlayNotificationManager.playAlertFeedback(sound = soundEnabled, vibrate = vibrationEnabled)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, colors.accent)
                    ) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = colors.accent, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Test Alert Feedback", color = colors.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSoundVibDialog = false }) {
                    Text("Done", color = colors.accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            containerColor = colors.surface
        )
    }

    // Confirmation dialog for Reset
    if (showResetConfirmation) {
        AlertDialog(
            onDismissRequest = { showResetConfirmation = false },
            title = { Text("Reset Filters", color = colors.text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp) },
            text = { Text("Are you sure you want to reset all driver filters to factory defaults?", color = colors.textSecondary, fontSize = 12.sp) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.resetAllFilters()
                        showResetConfirmation = false
                    }
                ) {
                    Text("Reset", color = colors.danger, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmation = false }) {
                    Text("Cancel", color = colors.textSecondary, fontSize = 12.sp)
                }
            },
            containerColor = colors.surface
        )
    }
}

@Composable
fun SettingsClickableRow(
    icon: ImageVector,
    tint: Icon3DTint,
    title: String,
    value: String,
    onClick: () -> Unit
) {
    val colors = LocalAppColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon3D(icon = icon, tint = tint)
            Spacer(modifier = Modifier.width(10.dp))
            Text(text = title, fontWeight = FontWeight.Medium, color = colors.text, fontSize = 13.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = value, color = colors.accent, fontSize = 12.sp)
            Spacer(modifier = Modifier.width(4.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(13.dp))
        }
    }
}
