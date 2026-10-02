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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import com.example.ui.components.AppCard
import com.example.ui.components.AppTextField
import com.example.ui.components.PrimaryActionButton
import com.example.ui.theme.LocalAppColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FiltersScreen(viewModel: FiltersViewModel) {
    val colors = LocalAppColors.current

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

    val matchStrategy by viewModel.multipleMatchStrategy.collectAsState()

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
        // Error banner
        AnimatedVisibility(visible = errorMessage != null) {
            Surface(
                color = colors.danger.copy(alpha = 0.15f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, colors.danger.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp)
            ) {
                Text(
                    text = errorMessage ?: "",
                    color = colors.danger,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        // Section 1: Distance Limits Card
        AppCard(backgroundColor = colors.surface, borderColor = colors.hairline) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.distance_filter),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = colors.text)
                )
                Switch(
                    checked = isDistEnabled,
                    onCheckedChange = { viewModel.isDistanceFilterEnabled.value = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colors.background,
                        checkedTrackColor = colors.accent,
                        uncheckedThumbColor = colors.textSecondary,
                        uncheckedTrackColor = colors.surface2
                    )
                )
            }

            AnimatedVisibility(visible = isDistEnabled) {
                Column(modifier = Modifier.padding(top = 14.dp)) {
                    Text(
                        text = "Pickup Distance Range",
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AppTextField(
                            value = pMin,
                            onValueChange = { viewModel.pickupDistMin.value = it },
                            label = "Min Pickup",
                            isNumeric = true,
                            unitText = "km",
                            modifier = Modifier.weight(1f),
                            testTag = "filter_pickup_min"
                        )
                        AppTextField(
                            value = pMax,
                            onValueChange = { viewModel.pickupDistMax.value = it },
                            label = "Max Pickup",
                            isNumeric = true,
                            unitText = "km",
                            modifier = Modifier.weight(1f),
                            testTag = "filter_pickup_max"
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Drop Distance Range",
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AppTextField(
                            value = dMin,
                            onValueChange = { viewModel.dropDistMin.value = it },
                            label = "Min Drop",
                            isNumeric = true,
                            unitText = "km",
                            modifier = Modifier.weight(1f),
                            testTag = "filter_drop_min"
                        )
                        AppTextField(
                            value = dMax,
                            onValueChange = { viewModel.dropDistMax.value = it },
                            label = "Max Drop",
                            isNumeric = true,
                            unitText = "km",
                            modifier = Modifier.weight(1f),
                            testTag = "filter_drop_max"
                        )
                    }
                }
            }
        }

        // Section 2: Fare per km & Basis Card
        AppCard(backgroundColor = colors.surface, borderColor = colors.hairline) {
            Text(
                text = stringResource(R.string.fare_settings),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = colors.text)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Segmented Control for Fare Basis
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surface2)
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val isBaseOnly = fareBasis == "base_only"
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isBaseOnly) colors.accent else Color.Transparent)
                        .clickable { viewModel.fareBasis.value = "base_only" }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.fare_base_only),
                        fontWeight = if (isBaseOnly) FontWeight.Bold else FontWeight.Medium,
                        color = if (isBaseOnly) Color(0xFF060B18) else colors.textSecondary,
                        fontSize = 12.sp
                    )
                }

                val isBaseExtra = fareBasis == "base_extra"
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isBaseExtra) colors.accent else Color.Transparent)
                        .clickable { viewModel.fareBasis.value = "base_extra" }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.fare_base_extra),
                        fontWeight = if (isBaseExtra) FontWeight.Bold else FontWeight.Medium,
                        color = if (isBaseExtra) Color(0xFF060B18) else colors.textSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            AppTextField(
                value = minFarePerKm,
                onValueChange = { viewModel.minFarePerKm.value = it },
                label = stringResource(R.string.min_fare_per_km),
                placeholder = "Optional e.g. 15",
                isNumeric = true,
                unitText = "₹/km",
                testTag = "filter_min_fare_per_km"
            )
        }

        // Section 3: Location Filter Card
        AppCard(backgroundColor = colors.surface, borderColor = colors.hairline) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.location_filter),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = colors.text)
                )
                Switch(
                    checked = isLocEnabled,
                    onCheckedChange = { viewModel.isLocationFilterEnabled.value = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colors.background,
                        checkedTrackColor = colors.accent,
                        uncheckedThumbColor = colors.textSecondary,
                        uncheckedTrackColor = colors.surface2
                    )
                )
            }

            AnimatedVisibility(visible = isLocEnabled) {
                Column(modifier = Modifier.padding(top = 14.dp)) {
                    AppTextField(
                        value = newKeyword,
                        onValueChange = { viewModel.newLocationKeyword.value = it },
                        label = "Location Keyword",
                        placeholder = stringResource(R.string.location_keyword_hint),
                        testTag = "filter_loc_input"
                    )

                    Spacer(modifier = Modifier.height(8.dp))

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
                                .background(colors.surface2)
                                .padding(2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isAcceptKw) colors.success else Color.Transparent)
                                    .clickable { viewModel.isNewKeywordAccept.value = true }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.filter_accept),
                                    fontWeight = FontWeight.Bold,
                                    color = if (isAcceptKw) Color(0xFF060B18) else colors.textSecondary,
                                    fontSize = 12.sp
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (!isAcceptKw) colors.danger else Color.Transparent)
                                    .clickable { viewModel.isNewKeywordAccept.value = false }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.filter_reject),
                                    fontWeight = FontWeight.Bold,
                                    color = if (!isAcceptKw) Color.White else colors.textSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = viewModel::addLocationKeyword,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, colors.accent),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.accent),
                            modifier = Modifier.height(44.dp)
                        ) {
                            Text(stringResource(R.string.add_filter), fontSize = 13.sp)
                        }
                    }

                    if (locKeywords.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            locKeywords.forEach { item ->
                                val chipBorder = if (item.isAccept) colors.success else colors.danger
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = chipBorder.copy(alpha = 0.15f),
                                    border = BorderStroke(1.dp, chipBorder),
                                    modifier = Modifier.clickable { viewModel.removeLocationKeyword(item) }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    ) {
                                        Text(
                                            text = "${if (item.isAccept) "✓" else "✗"} ${item.keyword}",
                                            color = chipBorder,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(Icons.Default.Close, contentDescription = "Delete", tint = chipBorder, modifier = Modifier.size(13.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section 4: Multiple Match Selection Strategy Card
        AppCard(backgroundColor = colors.surface, borderColor = colors.hairline) {
            Text(
                text = stringResource(R.string.when_several_rides_match),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = colors.text)
            )

            Spacer(modifier = Modifier.height(10.dp))

            StrategyOption(
                title = stringResource(R.string.strategy_first_top),
                description = "Accepts the topmost eligible offer card immediately",
                isSelected = matchStrategy == "first_match",
                onClick = { viewModel.multipleMatchStrategy.value = "first_match" }
            )
            StrategyOption(
                title = stringResource(R.string.strategy_highest_fare),
                description = "Compares visible cards and picks the highest total payout",
                isSelected = matchStrategy == "highest_fare",
                onClick = { viewModel.multipleMatchStrategy.value = "highest_fare" }
            )
            StrategyOption(
                title = stringResource(R.string.strategy_highest_per_km),
                description = "Picks the trip offering maximum earnings per kilometer",
                isSelected = matchStrategy == "highest_per_km",
                onClick = { viewModel.multipleMatchStrategy.value = "highest_per_km" }
            )
            StrategyOption(
                title = stringResource(R.string.strategy_nearest_pickup),
                description = "Picks the offer with shortest driver-to-pickup distance",
                isSelected = matchStrategy == "nearest_pickup",
                onClick = { viewModel.multipleMatchStrategy.value = "nearest_pickup" }
            )
        }

        // Section 6: Action Buttons
        PrimaryActionButton(
            text = if (isSavedRecently) stringResource(R.string.filter_saved) else stringResource(R.string.save_filter),
            onClick = viewModel::saveFilters,
            isSuccessState = isSavedRecently,
            testTag = "filters_save_button"
        )

        OutlinedButton(
            onClick = viewModel::resetFilters,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, colors.hairline),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textSecondary),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp)
                .height(44.dp)
        ) {
            Text(stringResource(R.string.reset_filters), fontSize = 13.sp)
        }
    }
}

@Composable
fun StrategyOption(
    title: String,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalAppColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isSelected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (isSelected) colors.accent else colors.textSecondary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) colors.accent else colors.text,
                fontSize = 13.sp
            )
            Text(
                text = description,
                color = colors.textSecondary,
                fontSize = 11.sp
            )
        }
    }
}
