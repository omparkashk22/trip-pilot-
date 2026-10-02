package com.example.ui.apps

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.components.AppCard
import com.example.ui.theme.BorderDivider
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.PrimaryCyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun AppsScreen(viewModel: AppsViewModel) {
    val context = LocalContext.current
    val appItems by viewModel.appItems.collectAsState()
    val dialogTarget by viewModel.candidateDialogTarget.collectAsState()
    val allApps by viewModel.allLaunchableApps.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadApps()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.installed_target_apps),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
            )

            Text(
                text = stringResource(R.string.target_apps_helper),
                style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary, lineHeight = 20.sp)
            )

            // Rows for each target app
            appItems.forEach { item ->
                AppCard(
                    backgroundColor = if (item.isInstalled) DarkSurface else DarkSurfaceElevated.copy(alpha = 0.6f),
                    borderColor = if (item.isEnabled && item.isInstalled) PrimaryCyan.copy(alpha = 0.3f) else BorderDivider
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // App Icon
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(DarkSurfaceElevated),
                            contentAlignment = Alignment.Center
                        ) {
                            if (item.appIcon != null) {
                                val bitmap = drawableToBitmap(item.appIcon)
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = item.config.displayName,
                                    modifier = Modifier.size(36.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.DirectionsCar,
                                    contentDescription = null,
                                    tint = if (item.isInstalled) PrimaryCyan else TextSecondary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.config.displayName,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (item.isInstalled) TextPrimary else TextSecondary
                                )
                            )

                            Text(
                                text = item.resolvedPackage ?: stringResource(R.string.status_not_installed),
                                color = TextSecondary,
                                fontSize = 11.sp,
                                maxLines = 1
                            )

                            val statusText = when {
                                !item.isInstalled -> stringResource(R.string.status_not_installed)
                                item.isEnabled -> stringResource(R.string.status_active)
                                else -> stringResource(R.string.status_paused)
                            }
                            val statusColor = when {
                                !item.isInstalled -> TextSecondary
                                item.isEnabled -> PrimaryCyan
                                else -> TextSecondary
                            }

                            Text(
                                text = statusText,
                                color = statusColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }

                        // Open app icon button
                        if (item.isInstalled && item.resolvedPackage != null) {
                            IconButton(
                                onClick = { viewModel.openApp(context, item.resolvedPackage) },
                                modifier = Modifier.testTag("open_${item.config.appId}")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = stringResource(R.string.open_app),
                                    tint = PrimaryCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Active Toggle
                        Switch(
                            checked = item.isEnabled && item.isInstalled,
                            onCheckedChange = { viewModel.toggleApp(item.config.appId, it) },
                            enabled = item.isInstalled,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = DarkBackground,
                                checkedTrackColor = PrimaryCyan,
                                uncheckedThumbColor = TextSecondary,
                                uncheckedTrackColor = DarkSurfaceElevated
                            )
                        )
                    }

                    // Change app button
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        TextButton(
                            onClick = { viewModel.openChangeAppDialog(item.config) },
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.change_app),
                                color = PrimaryCyan,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }

    // App Selection Dialog
    dialogTarget?.let { target ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDialog,
            title = {
                Text(
                    text = "${stringResource(R.string.select_driver_app)}: ${target.displayName}",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 350.dp)
                ) {
                    items(allApps) { app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.selectPackageForTarget(target.appId, app.packageName) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (app.icon != null) {
                                val bmp = drawableToBitmap(app.icon)
                                Image(bitmap = bmp.asImageBitmap(), contentDescription = null, modifier = Modifier.size(32.dp))
                            } else {
                                Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = PrimaryCyan, modifier = Modifier.size(24.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(text = app.appLabel, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text(text = app.packageName, color = TextSecondary, fontSize = 11.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissDialog) {
                    Text("Cancel", color = PrimaryCyan)
                }
            },
            containerColor = DarkSurface
        )
    }
}

fun drawableToBitmap(drawable: Drawable): Bitmap {
    if (drawable is BitmapDrawable && drawable.bitmap != null) {
        return drawable.bitmap
    }
    val bitmap = Bitmap.createBitmap(
        if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 48,
        if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 48,
        Bitmap.Config.ARGB_8888
    )
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
}
