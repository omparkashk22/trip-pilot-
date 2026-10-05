package com.example.ui.history

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.example.ui.components.AppTextField
import com.example.ui.theme.LocalAppColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RideHistoryScreen(viewModel: HistoryViewModel) {
    val context = LocalContext.current
    val colors = LocalAppColors.current

    val todayStats by viewModel.todayStats.collectAsState()
    val filteredLogs by viewModel.filteredLogs.collectAsState()
    val showDebugInfo by viewModel.showDebugInfo.collectAsState()

    val statusFilter by viewModel.selectedStatusFilter.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    var showClearConfirmation by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    // Animated number count-up for stats
    val animAccepted by animateIntAsState(
        targetValue = todayStats.acceptedCount,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "anim_acc"
    )
    val animSkipped by animateIntAsState(
        targetValue = todayStats.skippedCount,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "anim_skip"
    )
    val animMissed by animateIntAsState(
        targetValue = todayStats.missedCount,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "anim_missed"
    )
    val animFare by animateIntAsState(
        targetValue = todayStats.totalAcceptedFare.toInt(),
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "anim_fare"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header with title and overflow menu
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.history_title),
                        color = colors.textSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Menu",
                                tint = colors.textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier.background(colors.surface)
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Download, contentDescription = null, tint = colors.accent, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(stringResource(R.string.export_csv), color = colors.text, fontSize = 12.sp)
                                    }
                                },
                                onClick = {
                                    showMenu = false
                                    viewModel.exportCsv(context)
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Delete, contentDescription = null, tint = colors.danger, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(stringResource(R.string.clear_all), color = colors.danger, fontSize = 12.sp)
                                    }
                                },
                                onClick = {
                                    showMenu = false
                                    showClearConfirmation = true
                                }
                            )
                        }
                    }
                }
            }

            // 1. Stats Row: One compact row with 3 mini stats, about 52dp high
            item {
                Surface(
                    color = colors.surface,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, colors.hairline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MiniStat(
                            label = stringResource(R.string.stat_accepted),
                            value = "$animAccepted",
                            color = colors.success
                        )
                        Box(modifier = Modifier.height(24.dp).width(1.dp).background(colors.hairline))
                        MiniStat(
                            label = stringResource(R.string.stat_skipped),
                            value = "$animSkipped",
                            color = colors.warning
                        )
                        Box(modifier = Modifier.height(24.dp).width(1.dp).background(colors.hairline))
                        MiniStat(
                            label = "Missed",
                            value = "$animMissed",
                            color = colors.danger
                        )
                        Box(modifier = Modifier.height(24.dp).width(1.dp).background(colors.hairline))
                        MiniStat(
                            label = stringResource(R.string.stat_total_fare),
                            value = "₹$animFare",
                            color = colors.accent
                        )
                    }
                }
            }

            // 2. Search & Filter Chips (Filter chips row 28dp high, horizontally scrollable)
            item {
                AppTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.searchQuery.value = it },
                    label = "Search Destination / Address",
                    placeholder = "e.g. Airport, Whitefield",
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp))
                    },
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "history_search_input"
                )

                Spacer(modifier = Modifier.height(6.dp))

                val filterScrollState = rememberScrollState()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(filterScrollState),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val statusOptions = listOf(
                        "ALL" to "All",
                        "ACCEPTED" to "Accepted",
                        "SKIPPED" to "Skipped",
                        "TAP_FAILED" to "Tap Failed"
                    )
                    statusOptions.forEach { (key, label) ->
                        val isSelected = statusFilter == key
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) colors.accent.copy(alpha = 0.2f) else colors.surface,
                            border = BorderStroke(1.dp, if (isSelected) colors.accent else colors.hairline),
                            modifier = Modifier
                                .height(28.dp)
                                .clickable { viewModel.selectedStatusFilter.value = key }
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 10.dp)
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) colors.accent else colors.textSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            // 3. Empty state
            if (filteredLogs.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 36.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.History, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(stringResource(R.string.no_rides_logged), color = colors.text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Offers parsed from Bharat Taxi & Rapido will appear here.",
                                color = colors.textSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            // 4. Ride Cards List (Collapsed height ~60-64dp)
            items(filteredLogs, key = { it.id }) { log ->
                CompactRideLogCard(
                    log = log,
                    showDebugInfo = showDebugInfo,
                    onDelete = { viewModel.deleteLog(log.id) }
                )
            }

            item {
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    // Confirmation dialog
    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text("Clear Ride History", color = colors.text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp) },
            text = { Text("Are you sure you want to permanently delete all logged ride offers?", color = colors.textSecondary, fontSize = 12.sp) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllLogs()
                        showClearConfirmation = false
                    }
                ) {
                    Text("Delete All", color = colors.danger, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmation = false }) {
                    Text("Cancel", color = colors.textSecondary, fontSize = 12.sp)
                }
            },
            containerColor = colors.surface
        )
    }
}

@Composable
fun MiniStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, color = LocalAppColors.current.textSecondary, fontSize = 10.5.sp)
        Text(
            text = value,
            fontWeight = FontWeight.Bold,
            color = color,
            fontSize = 15.sp,
            lineHeight = 18.sp
        )
    }
}

@Composable
fun CompactRideLogCard(
    log: RideLog,
    showDebugInfo: Boolean,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val colors = LocalAppColors.current
    var isExpanded by remember { mutableStateOf(false) }

    val dateFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    val timeFormatted = dateFormat.format(Date(log.timestamp))

    val isBharat = log.appId == "bharat_taxi"
    val appDisplayName = if (isBharat) "Bharat Taxi" else "Rapido"
    val appMarkColor = if (isBharat) colors.accent else colors.warning

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        border = BorderStroke(1.dp, colors.hairline),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(220, easing = FastOutSlowInEasing))
            .clickable { isExpanded = !isExpanded }
            .testTag("ride_card_${log.id}")
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            // LINE 1: 22dp app mark, app name, time, status pill (10sp), fare on the right (14sp SemiBold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 22dp app mark
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(appMarkColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isBharat) "B" else "R",
                        color = appMarkColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = appDisplayName,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.text,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = timeFormatted,
                    color = colors.textSecondary,
                    fontSize = 10.sp
                )

                Spacer(modifier = Modifier.width(6.dp))

                // Status pill (10sp)
                val statusColor = when (log.status.uppercase()) {
                    "ACCEPTED" -> colors.success
                    "SKIPPED" -> colors.warning
                    else -> colors.danger
                }
                Surface(
                    color = statusColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.3f)),
                    modifier = Modifier.heightIn(min = 18.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)) {
                        Text(
                            text = log.status,
                            color = statusColor,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Missed pill when outcome == MISSED
                if (log.outcome.equals("MISSED", ignoreCase = true)) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Surface(
                        color = colors.danger.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, colors.danger.copy(alpha = 0.5f)),
                        modifier = Modifier.heightIn(min = 18.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
                            Text(
                                text = "Missed",
                                color = colors.danger,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Small "×N" pill next to the status when seenCount > 1
                if (log.seenCount > 1) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Surface(
                        color = colors.warning.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, colors.warning.copy(alpha = 0.5f)),
                        modifier = Modifier.heightIn(min = 18.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
                            Text(
                                text = "×${log.seenCount}",
                                color = colors.warning,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Amber "?" pill for LOW/MEDIUM confidence
                if (log.parseConfidence.equals("LOW", ignoreCase = true) || log.parseConfidence.equals("MEDIUM", ignoreCase = true)) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Surface(
                        color = colors.warning.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, colors.warning.copy(alpha = 0.5f)),
                        modifier = Modifier.heightIn(min = 18.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
                            Text(
                                text = "?",
                                color = colors.warning,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Fare on the right (14sp SemiBold): "₹90 + ₹26 = ₹116" or "₹95"
                val fareDisplay = if (log.extraFare > 0.0) {
                    "₹${log.baseFare.toInt()} + ₹${log.extraFare.toInt()} = ₹${log.totalFare.toInt()}"
                } else {
                    "₹${log.totalFare.toInt()}"
                }
                Text(
                    text = fareDisplay,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.text,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // LINE 2: pin icon + DESTINATION address on ONE line with ellipsis (11sp) and tiny "pickup km → drop km" (10sp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))

                Text(
                    text = log.dropAddress.ifEmpty { "Destination" },
                    color = colors.text,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(6.dp))

                // tiny "pickup km → drop km" text (10sp)
                Text(
                    text = "${log.pickupDistanceKm} km → ${log.dropDistanceKm} km",
                    color = colors.textSecondary,
                    fontSize = 10.sp,
                    maxLines = 1
                )
            }

            // EXPANDED VIEW: Smooth animation
            if (isExpanded) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(colors.hairline))
                Spacer(modifier = Modifier.height(8.dp))

                // Full Pickup Address
                Row(verticalAlignment = Alignment.Top) {
                    Text("Pickup: ", color = colors.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Text(log.pickupAddress, color = colors.text, fontSize = 11.sp, lineHeight = 15.sp)
                }
                Spacer(modifier = Modifier.height(4.dp))

                // Full Destination Address (Always store and show the destination address)
                Row(verticalAlignment = Alignment.Top) {
                    Text("Drop: ", color = colors.textSecondary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Text(log.dropAddress, color = colors.text, fontSize = 11.sp, lineHeight = 15.sp)
                }
                Spacer(modifier = Modifier.height(6.dp))

                // Sighting info: "First seen 9:10 PM · last seen 9:11 PM · seen 4×"
                val firstSeenTime = if (log.firstSeenAt > 0) log.firstSeenAt else log.timestamp
                val lastSeenTime = if (log.lastSeenAt > 0) log.lastSeenAt else log.timestamp
                val firstSeenStr = dateFormat.format(Date(firstSeenTime))
                val lastSeenStr = dateFormat.format(Date(lastSeenTime))
                Text(
                    text = "First seen $firstSeenStr · last seen $lastSeenStr · seen ${log.seenCount}×",
                    color = colors.textSecondary,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(6.dp))

                // Ride Type & Breakdown
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Ride Type: ${log.rideType}", color = colors.accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (log.seenCount > 1) {
                            Text("seen ${log.seenCount}×", color = colors.warning, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        if (log.extraFare > 0) {
                            Text("Base ₹${log.baseFare.toInt()} + Extra ₹${log.extraFare.toInt()}", color = colors.textSecondary, fontSize = 11.sp)
                        } else {
                            Text("Fare: ₹${log.baseFare.toInt()}", color = colors.textSecondary, fontSize = 11.sp)
                        }
                    }
                }

                // Fare per km (calculated and app-shown)
                val calcRate = if (log.dropDistanceKm > 0) log.totalFare / log.dropDistanceKm else 0.0
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Calculated: ₹${"%.1f".format(calcRate)}/km", color = colors.textSecondary, fontSize = 10.5.sp)
                    if (log.appFarePerKm != null) {
                        Text("App fare/km: ₹${log.appFarePerKm.toInt()}/km", color = colors.accent, fontSize = 10.5.sp)
                    }
                }

                // Skip reason
                if (!log.skipReason.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Reason: ${log.skipReason}",
                        color = colors.warning,
                        fontSize = 11.sp
                    )
                }

                // Debug line
                if (showDebugInfo) {
                    Spacer(modifier = Modifier.height(4.dp))
                    val totalMs = log.eventLagMs + log.parseMs + log.decideMs + log.tapLatencyMs
                    Text(
                        text = "lag ${log.eventLagMs} · parse ${log.parseMs} · decide ${log.decideMs} · tap ${log.tapLatencyMs} = ${totalMs} ms",
                        color = colors.textSecondary,
                        fontSize = 10.sp
                    )

                    // Copy raw card button
                    if (!log.rawCard.isNullOrEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, colors.accent),
                            color = colors.accent.copy(alpha = 0.1f),
                            modifier = Modifier
                                .height(28.dp)
                                .clickable {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Raw Card", log.rawCard))
                                    android.widget.Toast.makeText(context, "Raw card copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                                }
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
                                Text("Copy raw card", color = colors.accent, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                // Delete button
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    TextButton(onClick = onDelete) {
                        Text("Delete Log", color = colors.danger, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
