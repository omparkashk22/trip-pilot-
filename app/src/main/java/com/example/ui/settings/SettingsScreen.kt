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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.components.AppCard
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
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateToSetupGuide: () -> Unit,
    onNavigateToDevTools: () -> Unit
) {
    val context = LocalContext.current
    val currentLang by viewModel.currentLanguage.collectAsState()
    val currentTheme by viewModel.currentTheme.collectAsState()
    val keepScreenOn by viewModel.keepScreenOn.collectAsState()
    val processTestRequests by viewModel.processTestRequests.collectAsState()
    val tapMethod by viewModel.tapMethod.collectAsState()
    val showDebugInfo by viewModel.showDebugInfo.collectAsState()

    var showLangDialog by remember { mutableStateOf(false) }
    var showTapMethodDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
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
            .background(DarkBackground)
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Setup Guide & Developer Tools Navigation Cards
        AppCard(
            backgroundColor = DarkSurfaceElevated,
            borderColor = PrimaryCyan.copy(alpha = 0.3f),
            onClick = onNavigateToSetupGuide,
            modifier = Modifier.testTag("nav_setup_guide")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(PrimaryCyan.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Rule, contentDescription = null, tint = PrimaryCyan, modifier = Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.setup_guide),
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Guided permission & device OEM configuration checklist",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
                Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, tint = PrimaryCyan, modifier = Modifier.size(16.dp))
            }
        }

        AppCard(
            backgroundColor = DarkSurfaceElevated,
            borderColor = BorderDivider,
            onClick = onNavigateToDevTools,
            modifier = Modifier.testTag("nav_dev_tools")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(DarkSurfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Build, contentDescription = null, tint = PrimaryCyan, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.developer_tools),
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Screen Inspector & Ride Offer Simulator",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
                Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
            }
        }

        // General Preferences Card
        AppCard(backgroundColor = DarkSurface) {
            Text(
                text = "Preferences",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = PrimaryCyan)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Language
            SettingsRow(
                icon = Icons.Default.Language,
                title = stringResource(R.string.language),
                subtitle = languages.firstOrNull { it.first == currentLang }?.second ?: "English",
                onClick = { showLangDialog = true }
            )

            // Theme
            SettingsRow(
                icon = Icons.Default.DarkMode,
                title = stringResource(R.string.theme),
                subtitle = currentTheme.replaceFirstChar { it.uppercase() },
                onClick = { showThemeDialog = true }
            )

            // Tap Method
            SettingsRow(
                icon = Icons.Default.TouchApp,
                title = stringResource(R.string.tap_method),
                subtitle = when (tapMethod) {
                    "node_click" -> "Node Click"
                    "parent_click" -> "Parent Click"
                    "gesture" -> "Gesture Tap"
                    else -> "Auto (Best calibrated)"
                },
                onClick = { showTapMethodDialog = true }
            )

            // Keep Screen On
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(R.string.keep_screen_on), color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(text = "Prevents device sleep while driver app is watching", color = TextSecondary, fontSize = 12.sp)
                }
                Switch(
                    checked = keepScreenOn,
                    onCheckedChange = viewModel::setKeepScreenOn,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = DarkBackground,
                        checkedTrackColor = PrimaryCyan,
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = DarkSurfaceElevated
                    )
                )
            }

            // Process Test Requests
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(R.string.process_test_requests), color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(text = "Accept cards tagged with 'TEST REQUEST' badge", color = TextSecondary, fontSize = 12.sp)
                }
                Switch(
                    checked = processTestRequests,
                    onCheckedChange = viewModel::setProcessTestRequests,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = DarkBackground,
                        checkedTrackColor = PrimaryCyan,
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = DarkSurfaceElevated
                    )
                )
            }

            // Show Debug Info
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(R.string.show_debug_info), color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(text = "Displays tap strategy and latency in Ride History", color = TextSecondary, fontSize = 12.sp)
                }
                Switch(
                    checked = showDebugInfo,
                    onCheckedChange = viewModel::setShowDebugInfo,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = DarkBackground,
                        checkedTrackColor = PrimaryCyan,
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = DarkSurfaceElevated
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Reset All Filters
            OutlinedButton(
                onClick = { showResetConfirmation = true },
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, AccentRedDanger),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentRedDanger),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.reset_filters), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        // About Card
        AppCard(backgroundColor = DarkSurface) {
            Text(
                text = stringResource(R.string.about_trippilot),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = stringResource(R.string.version_label),
                color = TextSecondary,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "Privacy Policy",
                    color = PrimaryCyan,
                    fontSize = 13.sp,
                    modifier = Modifier.clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://trippilot.in/privacy"))
                        try { context.startActivity(intent) } catch (_: Exception) {}
                    }
                )
                Text(
                    text = "Terms of Service",
                    color = PrimaryCyan,
                    fontSize = 13.sp,
                    modifier = Modifier.clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://trippilot.in/terms"))
                        try { context.startActivity(intent) } catch (_: Exception) {}
                    }
                )
                Text(
                    text = "Support",
                    color = PrimaryCyan,
                    fontSize = 13.sp,
                    modifier = Modifier.clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://trippilot.in/support"))
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
            title = { Text(stringResource(R.string.language), color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    languages.forEach { (code, name) ->
                        val isSelected = currentLang == code
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setLanguage(code)
                                    showLangDialog = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isSelected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (isSelected) PrimaryCyan else TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = name, color = if (isSelected) TextPrimary else TextSecondary, fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLangDialog = false }) {
                    Text("Close", color = PrimaryCyan)
                }
            },
            containerColor = DarkSurface
        )
    }

    // Theme Dialog
    if (showThemeDialog) {
        val themes = listOf("dark" to "Dark (Default)", "light" to "Light", "system" to "System Default")
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Choose Theme", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    themes.forEach { (key, label) ->
                        val isSelected = currentTheme == key
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setTheme(key)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isSelected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (isSelected) PrimaryCyan else TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = label, color = if (isSelected) TextPrimary else TextSecondary, fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) { Text("Cancel", color = PrimaryCyan) }
            },
            containerColor = DarkSurface
        )
    }

    // Tap Method Dialog
    if (showTapMethodDialog) {
        val methods = listOf(
            "auto" to "Auto (Best performing strategy)",
            "node_click" to "Node Click (ACTION_CLICK on node)",
            "parent_click" to "Parent Click (ACTION_CLICK on ancestor)",
            "gesture" to "Gesture Only (dispatchGesture tap)"
        )
        AlertDialog(
            onDismissRequest = { showTapMethodDialog = false },
            title = { Text(stringResource(R.string.tap_method), color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    methods.forEach { (key, label) ->
                        val isSelected = tapMethod == key
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setTapMethod(key)
                                    showTapMethodDialog = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isSelected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (isSelected) PrimaryCyan else TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = label, color = if (isSelected) TextPrimary else TextSecondary, fontSize = 13.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTapMethodDialog = false }) { Text("Cancel", color = PrimaryCyan) }
            },
            containerColor = DarkSurface
        )
    }

    // Reset filters confirmation
    if (showResetConfirmation) {
        AlertDialog(
            onDismissRequest = { showResetConfirmation = false },
            title = { Text("Reset Filters", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("Reset all distance, fare, and location rules back to defaults?", color = TextSecondary) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.resetAllFilters()
                        showResetConfirmation = false
                    }
                ) {
                    Text("Reset", color = AccentRedDanger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmation = false }) { Text("Cancel", color = TextSecondary) }
            },
            containerColor = DarkSurface
        )
    }
}

@Composable
fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(DarkSurfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = PrimaryCyan, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(text = title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(text = subtitle, color = TextSecondary, fontSize = 12.sp)
            }
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(14.dp))
    }
}
