package com.example.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import com.example.TripPilotApp
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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.tree.ScreenTreeSummary
import com.example.ui.components.AppCard
import com.example.ui.components.PrimaryActionButton
import com.example.ui.simulator.SimulatorDialog
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
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperToolsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val countdown by viewModel.countdownSeconds.collectAsState()
    val dumpSummary by viewModel.latestDumpSummary.collectAsState()
    val existingDumps by viewModel.existingDumps.collectAsState()
    val captureOnNext by viewModel.captureOnNextOffer.collectAsState()
    val isSimulatorVisible by viewModel.isSimulatorVisible.collectAsState()

    val resolvedBharat by TripPilotApp.resolvedBharatPackageLive.collectAsState()
    val resolvedRapido by TripPilotApp.resolvedRapidoPackageLive.collectAsState()

    var selectedAppToInspect by remember(resolvedRapido, resolvedBharat) {
        mutableStateOf(resolvedRapido ?: resolvedBharat ?: "")
    }

    LaunchedEffect(Unit) {
        viewModel.refreshExistingDumps(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.developer_tools), fontWeight = FontWeight.Bold, color = TextPrimary) },
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Screen Inspector Section
            AppCard(backgroundColor = DarkSurface) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(PrimaryCyan.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, tint = PrimaryCyan, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.screen_inspector),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
                        )
                        Text(
                            text = "Extract and inspect the complete accessibility node tree without ADB",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Target Package Selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkSurfaceElevated)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val isRapido = selectedAppToInspect == (resolvedRapido ?: "")
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isRapido) PrimaryCyan else Color.Transparent)
                            .clickable { selectedAppToInspect = resolvedRapido ?: "" }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Rapido",
                            fontWeight = if (isRapido) FontWeight.Bold else FontWeight.Medium,
                            color = if (isRapido) Color(0xFF060B18) else TextSecondary,
                            fontSize = 13.sp
                        )
                    }

                    val isBharat = selectedAppToInspect == (resolvedBharat ?: "")
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isBharat) PrimaryCyan else Color.Transparent)
                            .clickable { selectedAppToInspect = resolvedBharat ?: "" }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Bharat Taxi",
                            fontWeight = if (isBharat) FontWeight.Bold else FontWeight.Medium,
                            color = if (isBharat) Color(0xFF060B18) else TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Capture on Next Offer Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Capture on next offer", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(text = "Automatically dumps tree when the next offer arrives", color = TextSecondary, fontSize = 11.sp)
                    }
                    Switch(
                        checked = captureOnNext,
                        onCheckedChange = viewModel::setCaptureOnNextOffer,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = DarkBackground,
                            checkedTrackColor = PrimaryCyan,
                            uncheckedThumbColor = TextSecondary,
                            uncheckedTrackColor = DarkSurfaceElevated
                        )
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Trigger 5-second countdown dump
                PrimaryActionButton(
                    text = if (countdown != null) "Capturing in $countdown s..." else "Capture Screen Tree (5s Delay)",
                    onClick = { viewModel.startScreenCapture(context, selectedAppToInspect) },
                    enabled = countdown == null,
                    icon = Icons.Default.CameraAlt,
                    testTag = "capture_tree_button"
                )

                if (countdown != null) {
                    Text(
                        text = "Open your driver app offer screen now!",
                        color = AccentAmberWarning,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            // 2. Simulator Section
            AppCard(backgroundColor = DarkSurface) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(PrimaryCyan.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = PrimaryCyan, modifier = Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.simulator),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
                        )
                        Text(
                            text = "Test offer card recognition, selection priority, and tap strategy locally",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                PrimaryActionButton(
                    text = "Launch Offer Simulator",
                    onClick = viewModel::openSimulator,
                    icon = Icons.Default.TouchApp,
                    testTag = "open_simulator_button"
                )
            }

            // 3. Saved Dumps History Card
            AppCard(backgroundColor = DarkSurface) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Screen Tree Dumps (${existingDumps.size}/10)",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = 14.sp
                    )
                    if (existingDumps.isNotEmpty()) {
                        TextButton(
                            onClick = { viewModel.deleteAllDumps(context) },
                            colors = ButtonDefaults.textButtonColors(contentColor = AccentRedDanger)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delete All", fontSize = 12.sp)
                        }
                    }
                }

                if (existingDumps.isEmpty()) {
                    Text(
                        text = "No screen dumps captured yet.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 10.dp)
                    )
                } else {
                    Spacer(modifier = Modifier.height(8.dp))
                    val dumpTimeFormat = SimpleDateFormat("h:mm a, d MMM", Locale.getDefault())
                    existingDumps.forEach { file ->
                        val timeStr = dumpTimeFormat.format(Date(file.lastModified()))
                        val offerFound = if (file.name.contains("offer_yes")) "yes" else if (file.name.contains("offer_no")) "no" else "yes"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Description, contentDescription = null, tint = PrimaryCyan, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(text = file.name, color = TextPrimary, fontSize = 12.sp, maxLines = 1)
                                    Text(text = "${file.length() / 1024} KB • $timeStr • offer found: $offerFound", color = TextSecondary, fontSize = 10.sp)
                                }
                            }
                            IconButton(onClick = { viewModel.shareDumpFile(context, file) }) {
                                Icon(Icons.Default.Share, contentDescription = "Share", tint = PrimaryCyan, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    // Dump Summary Dialog
    dumpSummary?.let { summary ->
        AlertDialog(
            onDismissRequest = { viewModel.latestDumpSummary.value = null },
            title = { Text("Screen Tree Dump Summary", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(text = summary.toSummaryText(), color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Screen Tree Summary", summary.toSummaryText()))
                    Toast.makeText(context, "Summary copied to clipboard", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Copy Summary", color = PrimaryCyan)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.shareDumpFile(context, File(summary.filePath))
                }) {
                    Text("Share File", color = PrimaryCyan)
                }
            },
            containerColor = DarkSurface
        )
    }

    // Simulator Dialog Overlay
    if (isSimulatorVisible) {
        SimulatorDialog(
            viewModel = viewModel,
            onDismiss = viewModel::closeSimulator
        )
    }
}
