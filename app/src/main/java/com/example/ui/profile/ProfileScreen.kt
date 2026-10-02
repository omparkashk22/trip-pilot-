package com.example.ui.profile

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.components.AppCard
import com.example.ui.components.CustomTextField
import com.example.ui.components.PrimaryActionButton
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    onBack: () -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val user by viewModel.currentUser.collectAsState()
    val countdown by viewModel.countdown.collectAsState()
    val statusMsg by viewModel.statusMessage.collectAsState()

    var isHowToUseExpanded by remember { mutableStateOf(false) }
    var isChangeEmailExpanded by remember { mutableStateOf(false) }
    var newEmailInput by remember { mutableStateOf("") }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.updateAvatar(uri)
        }
    }

    val expiryDateStr = if (user != null) {
        SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault()).format(Date(user!!.expiresAt))
    } else ""

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profile_title), fontWeight = FontWeight.Bold, color = TextPrimary) },
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
            // Profile Card (Avatar, Name, Email, Status)
            AppCard(backgroundColor = DarkSurface) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Avatar with Camera Badge
                    Box(modifier = Modifier.size(72.dp)) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(DarkSurfaceElevated),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = PrimaryCyan, modifier = Modifier.size(42.dp))
                        }

                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(PrimaryCyan)
                                .align(Alignment.BottomEnd)
                                .clickable {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = "Pick Image", tint = DarkBackground, modifier = Modifier.size(14.dp))
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = user?.name ?: "Driver Partner",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
                        )
                        Text(
                            text = user?.email ?: "driver@trippilot.in",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            StatusBadge(status = if (countdown.isExpired) "EXPIRED" else "ACTIVE")
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = DarkSurfaceElevated
                            ) {
                                Text(
                                    text = user?.plan ?: "Trial Plan",
                                    color = PrimaryCyan,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Subscription Countdown Card
            val subscriptionBorderColor = when {
                countdown.isExpired -> AccentRedDanger
                countdown.isUnderSevenDays -> AccentAmberWarning
                else -> PrimaryCyan.copy(alpha = 0.4f)
            }

            AppCard(
                backgroundColor = DarkSurface,
                borderColor = subscriptionBorderColor
            ) {
                Text(
                    text = "Subscription Status",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (countdown.isExpired) AccentRedDanger else PrimaryCyan
                    )
                )

                Text(
                    text = if (countdown.isExpired) "Your subscription has expired. Automatic accepts are disabled."
                    else "Active until $expiryDateStr",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
                )

                // Countdown Tiles: Days / Hrs / Min / Sec
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val tileColor = when {
                        countdown.isExpired -> AccentRedDanger
                        countdown.isUnderSevenDays -> AccentAmberWarning
                        else -> PrimaryCyan
                    }
                    CountdownTile("DAYS", "%02d".format(countdown.days), tileColor)
                    CountdownTile("HRS", "%02d".format(countdown.hours), tileColor)
                    CountdownTile("MIN", "%02d".format(countdown.minutes), tileColor)
                    CountdownTile("SEC", "%02d".format(countdown.seconds), tileColor)
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Renew Button
                OutlinedButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://trippilot.in/plans"))
                        try { context.startActivity(intent) } catch (_: Exception) {}
                    },
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, PrimaryCyan),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryCyan),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.renew_plan), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }

            // Status message toast banner
            AnimatedVisibility(visible = statusMsg != null) {
                Surface(
                    color = AccentGreenSuccess.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, AccentGreenSuccess),
                    modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp)
                ) {
                    Text(text = statusMsg ?: "", color = AccentGreenSuccess, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
                }
            }

            // Expandable: How to Use
            AppCard(backgroundColor = DarkSurface) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isHowToUseExpanded = !isHowToUseExpanded },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.HelpOutline, contentDescription = null, tint = PrimaryCyan, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(stringResource(R.string.how_to_use), fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 14.sp)
                    }
                    Icon(
                        imageVector = if (isHowToUseExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = TextSecondary
                    )
                }

                AnimatedVisibility(visible = isHowToUseExpanded) {
                    Column(modifier = Modifier.padding(top = 10.dp)) {
                        Text(
                            text = "1. Enable Accessibility and Overlay permissions in Setup Guide.\n" +
                                    "2. Ensure your driver app's language is English.\n" +
                                    "3. Configure your desired minimum fare and pickup/drop distances in Filters.\n" +
                                    "4. Turn on the Service switch on Dashboard.\n" +
                                    "5. Keep the supported driver app in foreground. TripPilot will parse offers and accept matching rides automatically!",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            // Expandable: Change Email
            AppCard(backgroundColor = DarkSurface) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isChangeEmailExpanded = !isChangeEmailExpanded },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Mail, contentDescription = null, tint = PrimaryCyan, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(stringResource(R.string.change_email), fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 14.sp)
                    }
                    Icon(
                        imageVector = if (isChangeEmailExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = TextSecondary
                    )
                }

                AnimatedVisibility(visible = isChangeEmailExpanded) {
                    Column(modifier = Modifier.padding(top = 12.dp)) {
                        CustomTextField(
                            value = newEmailInput,
                            onValueChange = { newEmailInput = it },
                            label = "New Email Address",
                            placeholder = "newemail@example.com"
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        PrimaryActionButton(
                            text = "Submit Change Request",
                            onClick = {
                                viewModel.changeEmail(newEmailInput)
                                isChangeEmailExpanded = false
                            },
                            enabled = newEmailInput.contains("@")
                        )
                    }
                }
            }

            // Change Password Card
            AppCard(backgroundColor = DarkSurface) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LockReset, contentDescription = null, tint = PrimaryCyan, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(stringResource(R.string.change_password), fontWeight = FontWeight.SemiBold, color = TextPrimary, fontSize = 14.sp)
                            Text("Sends password reset link to your email", color = TextSecondary, fontSize = 11.sp)
                        }
                    }
                    TextButton(onClick = viewModel::changePassword) {
                        Text("Reset", color = PrimaryCyan, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Version Label & Sign Out Button
            Text(
                text = stringResource(R.string.version_label),
                color = TextSecondary,
                fontSize = 12.sp
            )

            Button(
                onClick = {
                    viewModel.signOut()
                    onSignOut()
                },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentRedDanger.copy(alpha = 0.2f)),
                border = BorderStroke(1.dp, AccentRedDanger),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
                    .height(52.dp)
                    .testTag("profile_sign_out_button")
            ) {
                Icon(Icons.Default.Logout, contentDescription = null, tint = AccentRedDanger, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.sign_out), color = AccentRedDanger, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun CountdownTile(label: String, value: String, accentColor: Color) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = DarkSurfaceElevated,
        border = BorderStroke(1.dp, BorderDivider),
        modifier = Modifier.width(70.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                    fontSize = 20.sp
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                color = TextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
