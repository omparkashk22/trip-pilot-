package com.example.ui.filters

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.model.LocationKeyword
import com.example.ui.components.AppCard
import com.example.ui.components.CustomTextField
import com.example.ui.components.PrimaryActionButton
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FiltersScreen(viewModel: FiltersViewModel) {
    val filter by viewModel.filter.collectAsState()
    val isSavedRecently by viewModel.isSavedRecently.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    val isDistEnabled by viewModel.isDistanceFilterEnabled.collectAsState()
    val pMin by viewModel.pickupDistMin.collectAsState()
    val pMax by viewModel.pickupDistMax.collectAsState()
    val dMin by viewModel.dropDistMin.collectAsState()
    val dMax by viewModel.dropDistMax.collectAsState()

    val fareBasis by viewModel.fareBasis.collectAsState()
    val minFarePerKm by viewModel.minFarePerKm.collectAsState()

    val isLocEnabled by viewModel.isLocationFilterEnabled.collectAsState()
    val newKeyword by viewModel.newLocationKeyword.collectAsState()
    val isAcceptKw by viewModel.isNewKeywordAccept.collectAsState()
    val locKeywords by viewModel.locationKeywords.collectAsState()

    val allowedTypes by viewModel.allowedRideTypes.collectAsState()
    val matchStrategy by viewModel.multipleMatchStrategy.collectAsState()

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
        // Plain-Language Rule Summary Preview Card
        AppCard(backgroundColor = DarkSurfaceElevated, borderColor = PrimaryCyan.copy(alpha = 0.3f)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Default.Info, contentDescription = null, tint = PrimaryCyan, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = stringResource(R.string.rule_summary_title),
                        fontWeight = FontWeight.Bold,
                        color = PrimaryCyan,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = filter.generateRuleSummary(),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = TextPrimary,
                            lineHeight = 20.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }
        }

        // Error message if any
        AnimatedVisibility(visible = errorMessage != null) {
            Surface(
                color = AccentRedDanger.copy(alpha = 0.15f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, AccentRedDanger.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp)
            ) {
                Text(
                    text = errorMessage ?: "",
                    color = AccentRedDanger,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        // 1. Distance Filter Card
        AppCard(backgroundColor = DarkSurface) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.distance_filter),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
                )
                Switch(
                    checked = isDistEnabled,
                    onCheckedChange = { viewModel.isDistanceFilterEnabled.value = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = DarkBackground,
                        checkedTrackColor = PrimaryCyan,
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = DarkSurfaceElevated
                    )
                )
            }

            AnimatedVisibility(visible = isDistEnabled) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    Text(text = "Pickup Distance (km)", color = PrimaryCyan, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CustomTextField(
                            value = pMin,
                            onValueChange = { viewModel.pickupDistMin.value = it },
                            label = "Min (km)",
                            modifier = Modifier.weight(1f),
                            testTag = "filter_pickup_min"
                        )
                        CustomTextField(
                            value = pMax,
                            onValueChange = { viewModel.pickupDistMax.value = it },
                            label = "Max (km)",
                            modifier = Modifier.weight(1f),
                            testTag = "filter_pickup_max"
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(text = "Drop Distance (km)", color = PrimaryCyan, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CustomTextField(
                            value = dMin,
                            onValueChange = { viewModel.dropDistMin.value = it },
                            label = "Min (km)",
                            modifier = Modifier.weight(1f),
                            testTag = "filter_drop_min"
                        )
                        CustomTextField(
                            value = dMax,
                            onValueChange = { viewModel.dropDistMax.value = it },
                            label = "Max (km)",
                            modifier = Modifier.weight(1f),
                            testTag = "filter_drop_max"
                        )
                    }
                }
            }
        }

        // 2. Fare Settings Card
        AppCard(backgroundColor = DarkSurface) {
            Text(
                text = stringResource(R.string.fare_settings),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.fare_used_for_filters),
                color = TextSecondary,
                fontSize = 13.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSurfaceElevated)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val isBaseOnly = fareBasis == "base_only"
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isBaseOnly) PrimaryCyan else Color.Transparent)
                        .clickable { viewModel.fareBasis.value = "base_only" }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.fare_base_only),
                        fontWeight = if (isBaseOnly) FontWeight.Bold else FontWeight.Medium,
                        color = if (isBaseOnly) Color(0xFF060B18) else TextSecondary,
                        fontSize = 13.sp
                    )
                }

                val isBaseExtra = fareBasis == "base_extra"
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isBaseExtra) PrimaryCyan else Color.Transparent)
                        .clickable { viewModel.fareBasis.value = "base_extra" }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.fare_base_extra),
                        fontWeight = if (isBaseExtra) FontWeight.Bold else FontWeight.Medium,
                        color = if (isBaseExtra) Color(0xFF060B18) else TextSecondary,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            CustomTextField(
                value = minFarePerKm,
                onValueChange = { viewModel.minFarePerKm.value = it },
                label = stringResource(R.string.min_fare_per_km),
                placeholder = "Optional e.g. 15",
                testTag = "filter_min_fare_per_km"
            )
        }

        // 3. Location Filter Card
        AppCard(backgroundColor = DarkSurface) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.location_filter),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
                )
                Switch(
                    checked = isLocEnabled,
                    onCheckedChange = { viewModel.isLocationFilterEnabled.value = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = DarkBackground,
                        checkedTrackColor = PrimaryCyan,
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = DarkSurfaceElevated
                    )
                )
            }

            AnimatedVisibility(visible = isLocEnabled) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    CustomTextField(
                        value = newKeyword,
                        onValueChange = { viewModel.newLocationKeyword.value = it },
                        label = "Location Keyword",
                        placeholder = stringResource(R.string.location_keyword_hint),
                        testTag = "filter_loc_input"
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Segmented Accept / Reject
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(DarkSurfaceElevated)
                                .padding(2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isAcceptKw) AccentGreenSuccess else Color.Transparent)
                                    .clickable { viewModel.isNewKeywordAccept.value = true }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.filter_accept),
                                    fontWeight = FontWeight.Bold,
                                    color = if (isAcceptKw) Color(0xFF060B18) else TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (!isAcceptKw) AccentRedDanger else Color.Transparent)
                                    .clickable { viewModel.isNewKeywordAccept.value = false }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.filter_reject),
                                    fontWeight = FontWeight.Bold,
                                    color = if (!isAcceptKw) Color.White else TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = viewModel::addLocationKeyword,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, PrimaryCyan),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryCyan),
                            modifier = Modifier.height(44.dp)
                        ) {
                            Text(stringResource(R.string.add_filter), fontSize = 13.sp)
                        }
                    }

                    if (locKeywords.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            locKeywords.forEach { item ->
                                val chipBg = if (item.isAccept) AccentGreenSuccess.copy(alpha = 0.2f) else AccentRedDanger.copy(alpha = 0.2f)
                                val chipBorder = if (item.isAccept) AccentGreenSuccess else AccentRedDanger
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = chipBg,
                                    border = BorderStroke(1.dp, chipBorder),
                                    modifier = Modifier.clickable { viewModel.removeLocationKeyword(item) }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = "${if (item.isAccept) "✓" else "✗"} ${item.keyword}",
                                            color = chipBorder,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Icon(Icons.Default.Close, contentDescription = "Delete", tint = chipBorder, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Allowed Ride Types Card
        AppCard(backgroundColor = DarkSurface) {
            Text(
                text = stringResource(R.string.allowed_ride_types),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
            )

            Spacer(modifier = Modifier.height(12.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                viewModel.availableRideTypes.forEach { type ->
                    val isSelected = allowedTypes.isEmpty() || allowedTypes.contains(type)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) PrimaryCyan.copy(alpha = 0.15f) else DarkSurfaceElevated,
                        border = BorderStroke(1.dp, if (isSelected) PrimaryCyan else BorderDivider),
                        modifier = Modifier.clickable { viewModel.toggleRideType(type) }
                    ) {
                        Text(
                            text = type,
                            color = if (isSelected) PrimaryCyan else TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }

        // 5. Match Strategy Card
        AppCard(backgroundColor = DarkSurface) {
            Text(
                text = stringResource(R.string.when_several_rides_match),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
            )

            Spacer(modifier = Modifier.height(12.dp))

            val strategies = listOf(
                "first_match" to R.string.strategy_first_top,
                "highest_fare" to R.string.strategy_highest_fare,
                "highest_per_km" to R.string.strategy_highest_per_km,
                "nearest_pickup" to R.string.strategy_nearest_pickup
            )

            strategies.forEach { (key, labelRes) ->
                val isSelected = matchStrategy == key
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.multipleMatchStrategy.value = key }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isSelected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (isSelected) PrimaryCyan else TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(labelRes),
                        color = if (isSelected) TextPrimary else TextSecondary,
                        fontSize = 14.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }

        // Save & Reset Buttons
        PrimaryActionButton(
            text = if (isSavedRecently) stringResource(R.string.filter_saved) else stringResource(R.string.save_filter),
            onClick = viewModel::saveFilters,
            isSuccessState = isSavedRecently,
            testTag = "filter_save_all_button"
        )
    }
}
