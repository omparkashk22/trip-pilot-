package com.example.ui.history

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.RideLog
import com.example.ui.components.AppCard
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RideHistoryScreen(viewModel: HistoryViewModel) {
    val context = LocalContext.current
    val todayStats by viewModel.todayStats.collectAsState()
    val filteredLogs by viewModel.filteredLogs.collectAsState()
    val showDebugInfo by viewModel.showDebugInfo.collectAsState()

    val statusFilter by viewModel.selectedStatusFilter.collectAsState()
    val appFilter by viewModel.selectedAppFilter.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    var showClearConfirmation by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                // 1. Today's Stats Card
                AppCard(backgroundColor = DarkSurfaceElevated, borderColor = PrimaryCyan.copy(alpha = 0.3f)) {
                    Text(
                        text = stringResource(R.string.stats_today),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = PrimaryCyan)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StatTile(
                            label = stringResource(R.string.stat_accepted),
                            value = "${todayStats.acceptedCount}",
                            color = AccentGreenSuccess
                        )
                        StatTile(
                            label = stringResource(R.string.stat_skipped),
                            value = "${todayStats.skippedCount}",
                            color = AccentAmberWarning
                        )
                        StatTile(
                            label = stringResource(R.string.stat_total_fare),
                            value = "₹${todayStats.totalAcceptedFare.toInt()}",
                            color = PrimaryCyan
                        )
                    }
                }
            }

            // Search Bar & Filter Chips
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.searchQuery.value = it },
                    placeholder = { Text(stringResource(R.string.search_address_hint), color = TextSecondary, fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = PrimaryCyan, modifier = Modifier.size(20.dp))
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryCyan,
                        unfocusedBorderColor = BorderDivider,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = DarkSurface,
                        unfocusedContainerColor = DarkSurface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("history_search_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Status Filter Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val statusOptions = listOf(
                        "ALL" to stringResource(R.string.filter_all),
                        "ACCEPTED" to stringResource(R.string.stat_accepted),
                        "SKIPPED" to stringResource(R.string.stat_skipped),
                        "TAP_FAILED" to stringResource(R.string.filter_tap_failed)
                    )
                    statusOptions.forEach { (key, label) ->
                        val isSelected = statusFilter == key
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) PrimaryCyan.copy(alpha = 0.2f) else DarkSurface,
                            border = BorderStroke(1.dp, if (isSelected) PrimaryCyan else BorderDivider),
                            modifier = Modifier.clickable { viewModel.selectedStatusFilter.value = key }
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) PrimaryCyan else TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Action Bar (Clear All & Export CSV)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { showClearConfirmation = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = AccentRedDanger)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.clear_all), fontSize = 13.sp)
                    }

                    OutlinedButton(
                        onClick = { viewModel.exportCsv(context) },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, PrimaryCyan),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryCyan),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.export_csv), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Empty state
            if (filteredLogs.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.History, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(54.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(stringResource(R.string.no_rides_logged), color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                stringResource(R.string.no_rides_desc),
                                color = TextSecondary,
                                fontSize = 13.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                        }
                    }
                }
            }

            // Ride Cards List
            items(filteredLogs, key = { it.id }) { log ->
                RideLogCard(
                    log = log,
                    showDebugInfo = showDebugInfo,
                    onDelete = { viewModel.deleteLog(log.id) }
                )
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Confirmation dialog
    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text("Clear Ride History", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to permanently delete all logged ride offers?", color = TextSecondary) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllLogs()
                        showClearConfirmation = false
                    }
                ) {
                    Text("Delete All", color = AccentRedDanger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmation = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = DarkSurface
        )
    }
}

@Composable
fun StatTile(label: String, value: String, color: Color) {
    Column {
        Text(text = label, color = TextSecondary, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.Bold,
                color = color,
                fontSize = 24.sp
            )
        )
    }
}

@Composable
fun RideLogCard(
    log: RideLog,
    showDebugInfo: Boolean,
    onDelete: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    val dateFormat = SimpleDateFormat("h:mm a, d MMM", Locale.getDefault())
    val timeFormatted = dateFormat.format(Date(log.timestamp))

    AppCard(
        backgroundColor = DarkSurface,
        onClick = { isExpanded = !isExpanded },
        modifier = Modifier.testTag("ride_card_${log.id}")
    ) {
        // Top Header: App Name, Ride Type chip, Time, Status badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(PrimaryCyan.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = PrimaryCyan, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = if (log.appId == "bharat_taxi") "Bharat Taxi" else "Rapido",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = 14.sp
                    )
                    Text(text = timeFormatted, color = TextSecondary, fontSize = 11.sp)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = DarkSurfaceElevated
                ) {
                    Text(
                        text = log.rideType,
                        color = PrimaryCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                StatusBadge(status = log.status)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Fare Breakdown
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                if (log.extraFare > 0) {
                    Text(
                        text = "₹${log.baseFare.toInt()} + ₹${log.extraFare.toInt()}",
                        color = AccentGreenSuccess,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = "₹${log.totalFare.toInt()}",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = 24.sp
                    )
                )
            }

            IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = TextSecondary, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Pickup Block
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Default.NearMe, contentDescription = null, tint = AccentGreenSuccess, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${log.pickupDistanceKm} km",
                        fontWeight = FontWeight.Bold,
                        color = AccentGreenSuccess,
                        fontSize = 13.sp
                    )
                    if (log.pickupEtaMin != null) {
                        Text(
                            text = " · ${log.pickupEtaMin} min",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
                Text(
                    text = log.pickupAddress,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // DESTINATION (DROP) BLOCK - Mandatory
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Default.LocationOn, contentDescription = null, tint = PrimaryCyan, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${log.dropDistanceKm} km",
                        fontWeight = FontWeight.Bold,
                        color = PrimaryCyan,
                        fontSize = 13.sp
                    )
                    if (log.dropEtaMin != null) {
                        Text(
                            text = " · ${log.dropEtaMin} min",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    }
                    if (log.dropAddressTruncated) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = AccentAmberWarning.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = stringResource(R.string.address_truncated),
                                color = AccentAmberWarning,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Text(
                    text = log.dropAddress,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Skip Reason if skipped
        if (!log.skipReason.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                color = AccentAmberWarning.copy(alpha = 0.1f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, AccentAmberWarning.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.WarningAmber, contentDescription = null, tint = AccentAmberWarning, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${stringResource(R.string.skip_reason)}: ${log.skipReason}",
                        color = AccentAmberWarning,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Debug Line (Visible only when Show debug info is ON)
        if (showDebugInfo) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                color = DarkSurfaceVariant,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "DEBUG: Method = ${log.tapMethod ?: "N/A"} · Latency = ${log.tapLatencyMs} ms · Test = ${log.isTest}",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(6.dp)
                )
            }
        }
    }
}
