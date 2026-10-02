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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.asImageBitmap
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
fun AppsScreen(viewModel: AppsViewModel) {
    val context = LocalContext.current
    val colors = LocalAppColors.current
    val appItems by viewModel.appItems.collectAsState()
    val dialogTarget by viewModel.candidateDialogTarget.collectAsState()
    val allApps by viewModel.allLaunchableApps.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadApps()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(14.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.installed_target_apps),
                color = colors.textSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = stringResource(R.string.target_apps_helper),
                color = colors.textSecondary,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )

            // Rows for each target app (exact layout and row order preserved)
            appItems.forEach { item ->
                AppCard(
                    backgroundColor = if (item.isInstalled) colors.surface else colors.surface2.copy(alpha = 0.5f),
                    borderColor = if (item.isEnabled && item.isInstalled) colors.accent.copy(alpha = 0.35f) else colors.hairline
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. App Icon (icon)
                        if (item.appIcon != null) {
                            val bitmap = drawableToBitmap(item.appIcon)
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(colors.surface2),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = item.config.displayName,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        } else {
                            Icon3D(
                                icon = Icons.Default.DirectionsCar,
                                tint = if (item.config.appId == "bharat_taxi") Icon3DTint.CYAN else Icon3DTint.AMBER
                            )
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // 2. Name & Status Text (name, status text)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.config.displayName,
                                fontWeight = FontWeight.SemiBold,
                                color = if (item.isInstalled) colors.text else colors.textSecondary,
                                fontSize = 13.sp
                            )

                            Text(
                                text = item.resolvedPackage ?: stringResource(R.string.status_not_installed),
                                color = colors.textSecondary,
                                fontSize = 10.5.sp,
                                maxLines = 1
                            )

                            val statusText = when {
                                !item.isInstalled -> stringResource(R.string.status_not_installed)
                                item.isEnabled -> stringResource(R.string.status_active)
                                else -> stringResource(R.string.status_paused)
                            }
                            val statusColor = when {
                                !item.isInstalled -> colors.textSecondary
                                item.isEnabled -> colors.success
                                else -> colors.textSecondary
                            }

                            Text(
                                text = statusText,
                                color = statusColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 1.dp)
                            )
                        }

                        // 3. Open icon
                        if (item.isInstalled && item.resolvedPackage != null) {
                            IconButton(
                                onClick = { viewModel.openApp(context, item.resolvedPackage) },
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("open_${item.config.appId}")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = stringResource(R.string.open_app),
                                    tint = colors.accent,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                        }

                        // 4. Toggle
                        Switch(
                            checked = item.isEnabled && item.isInstalled,
                            onCheckedChange = { viewModel.toggleApp(item.config.appId, it) },
                            enabled = item.isInstalled,
                            modifier = Modifier.scale(0.82f),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = colors.background,
                                checkedTrackColor = colors.accent,
                                uncheckedThumbColor = colors.textSecondary,
                                uncheckedTrackColor = colors.surface2
                            )
                        )
                    }

                    // Change app button
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        TextButton(
                            onClick = { viewModel.openChangeAppDialog(item.config) },
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.change_app),
                                color = colors.accent,
                                fontSize = 11.sp
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
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = colors.text
                )
            },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                ) {
                    items(allApps) { app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.selectPackageForTarget(target.appId, app.packageName) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (app.icon != null) {
                                val bmp = drawableToBitmap(app.icon)
                                Image(bitmap = bmp.asImageBitmap(), contentDescription = null, modifier = Modifier.size(28.dp))
                            } else {
                                Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(text = app.appLabel, color = colors.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text(text = app.packageName, color = colors.textSecondary, fontSize = 10.5.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::dismissDialog) {
                    Text("Cancel", color = colors.accent, fontSize = 12.sp)
                }
            },
            containerColor = colors.surface
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
