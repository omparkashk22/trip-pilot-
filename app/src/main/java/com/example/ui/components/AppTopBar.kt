package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabPosition
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.LocalAppColors

enum class MainTab(val titleRes: Int, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    DASHBOARD(R.string.tab_dashboard, Icons.Outlined.Dashboard),
    FILTERS(R.string.tab_filters, Icons.Outlined.FilterList),
    APPS(R.string.tab_apps, Icons.Outlined.Apps),
    HISTORY(R.string.tab_history, Icons.Outlined.History),
    SETTINGS(R.string.tab_settings, Icons.Outlined.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    selectedTab: MainTab,
    onTabSelected: (MainTab) -> Unit,
    onProfileClick: () -> Unit
) {
    val colors = LocalAppColors.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background)
    ) {
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(colors.accent.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Navigation,
                            contentDescription = "Logo",
                            tint = colors.accent,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "TripPilot",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 17.sp,
                            color = colors.text,
                            lineHeight = 22.sp
                        )
                    }
                }
            },
            actions = {
                IconButton(
                    onClick = onProfileClick,
                    modifier = Modifier.testTag("profile_icon_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AccountCircle,
                        contentDescription = "Profile",
                        tint = colors.accent,
                        modifier = Modifier.size(24.dp)
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = colors.background
            )
        )

        // Tab Row with row height 44dp, 18dp line icons above 10.5sp label, slim 2dp sliding indicator
        TabRow(
            selectedTabIndex = selectedTab.ordinal,
            containerColor = colors.background,
            contentColor = colors.text,
            divider = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(colors.hairline)
                )
            },
            indicator = { tabPositions ->
                SlidingTabIndicator(positions = tabPositions, selectedIndex = selectedTab.ordinal)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
        ) {
            MainTab.entries.forEach { tab ->
                val isSelected = selectedTab == tab
                Tab(
                    selected = isSelected,
                    onClick = { onTabSelected(tab) },
                    modifier = Modifier
                        .height(44.dp)
                        .testTag("tab_${tab.name.lowercase()}"),
                    selectedContentColor = colors.accent,
                    unselectedContentColor = colors.textSecondary
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = null,
                            tint = if (isSelected) colors.accent else colors.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = stringResource(tab.titleRes),
                            maxLines = 1,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) colors.accent else colors.textSecondary,
                            fontSize = 10.5.sp,
                            lineHeight = 13.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SlidingTabIndicator(
    positions: List<TabPosition>,
    selectedIndex: Int
) {
    val colors = LocalAppColors.current
    if (selectedIndex >= positions.size) return
    val currentPosition = positions[selectedIndex]

    val leftOffset by animateDpAsState(
        targetValue = currentPosition.left,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "tab_indicator_left"
    )
    val width by animateDpAsState(
        targetValue = currentPosition.width,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "tab_indicator_width"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentSize(Alignment.BottomStart)
            .offset(x = leftOffset)
            .width(width)
            .height(2.dp)
            .background(colors.accent)
    )
}
