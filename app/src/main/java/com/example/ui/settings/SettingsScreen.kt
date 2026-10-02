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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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

            Text(
                text = stringResource(R.string.version_label),
                color = colors.textSecondary,
                fontSize = 11.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

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
