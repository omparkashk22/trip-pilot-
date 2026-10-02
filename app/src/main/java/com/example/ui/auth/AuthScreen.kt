package com.example.ui.auth

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.components.CustomTextField
import com.example.ui.components.PrimaryActionButton
import com.example.ui.theme.AccentGreenSuccess
import com.example.ui.theme.AccentRedDanger
import com.example.ui.theme.BorderDivider
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.PrimaryCyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun AuthScreen(
    viewModel: AuthViewModel,
    onAuthSuccess: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    if (currentUser != null) {
        onAuthSuccess()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp)
                .verticalScroll(scrollState)
                .padding(vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(PrimaryCyan.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Navigation,
                    contentDescription = null,
                    tint = PrimaryCyan,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "TripPilot",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = PrimaryCyan
                )
            )

            Text(
                text = when (uiState.mode) {
                    AuthMode.SIGN_IN -> "Welcome back, Driver Partner"
                    AuthMode.SIGN_UP -> "Create your Driver Account"
                    AuthMode.FORGOT_PASSWORD -> "Reset your Password"
                },
                style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary),
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
            )

            // Inline error
            AnimatedVisibility(visible = uiState.errorMessage != null) {
                Surface(
                    color = AccentRedDanger.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AccentRedDanger.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Text(
                        text = uiState.errorMessage ?: "",
                        color = AccentRedDanger,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Inline success
            AnimatedVisibility(visible = uiState.successMessage != null) {
                Surface(
                    color = AccentGreenSuccess.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AccentGreenSuccess.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Text(
                        text = uiState.successMessage ?: "",
                        color = AccentGreenSuccess,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Input Fields
            if (uiState.mode == AuthMode.SIGN_UP) {
                CustomTextField(
                    value = uiState.name,
                    onValueChange = viewModel::onNameChange,
                    label = stringResource(R.string.full_name_hint),
                    placeholder = "e.g. Ramesh Kumar",
                    testTag = "auth_name_input"
                )
                Spacer(modifier = Modifier.height(14.dp))
            }

            CustomTextField(
                value = uiState.email,
                onValueChange = viewModel::onEmailChange,
                label = stringResource(R.string.email_hint),
                placeholder = "driver@example.com",
                testTag = "auth_email_input"
            )

            if (uiState.mode != AuthMode.FORGOT_PASSWORD) {
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedTextField(
                    value = uiState.password,
                    onValueChange = viewModel::onPasswordChange,
                    label = { Text(stringResource(R.string.password_hint)) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    visualTransformation = if (uiState.isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = viewModel::togglePasswordVisibility) {
                            Icon(
                                imageVector = if (uiState.isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle password visibility",
                                tint = TextSecondary
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryCyan,
                        unfocusedBorderColor = BorderDivider,
                        focusedLabelColor = PrimaryCyan,
                        unfocusedLabelColor = TextSecondary,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = DarkSurface,
                        unfocusedContainerColor = DarkSurface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("auth_password_input")
                )
            }

            if (uiState.mode == AuthMode.SIGN_UP) {
                Spacer(modifier = Modifier.height(14.dp))
                OutlinedTextField(
                    value = uiState.confirmPassword,
                    onValueChange = viewModel::onConfirmPasswordChange,
                    label = { Text(stringResource(R.string.confirm_password_hint)) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    visualTransformation = PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryCyan,
                        unfocusedBorderColor = BorderDivider,
                        focusedLabelColor = PrimaryCyan,
                        unfocusedLabelColor = TextSecondary,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedContainerColor = DarkSurface,
                        unfocusedContainerColor = DarkSurface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("auth_confirm_password_input")
                )

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = uiState.agreeTerms,
                        onCheckedChange = viewModel::onAgreeTermsChange,
                        colors = CheckboxDefaults.colors(
                            checkedColor = PrimaryCyan,
                            checkmarkColor = DarkBackground,
                            uncheckedColor = TextSecondary
                        )
                    )
                    Text(
                        text = stringResource(R.string.agree_terms),
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                        fontSize = 12.sp
                    )
                }
            }

            // Forgot password button
            if (uiState.mode == AuthMode.SIGN_IN) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    TextButton(onClick = { viewModel.setMode(AuthMode.FORGOT_PASSWORD) }) {
                        Text(
                            text = stringResource(R.string.forgot_password),
                            color = PrimaryCyan,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Main Action Button
            PrimaryActionButton(
                text = if (uiState.isLoading) "Processing..." else when (uiState.mode) {
                    AuthMode.SIGN_IN -> stringResource(R.string.sign_in)
                    AuthMode.SIGN_UP -> stringResource(R.string.sign_up)
                    AuthMode.FORGOT_PASSWORD -> stringResource(R.string.send_reset_link)
                },
                onClick = viewModel::submit,
                enabled = !uiState.isLoading,
                testTag = "auth_submit_button"
            )

            // Mode switch
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 12.dp)
            ) {
                when (uiState.mode) {
                    AuthMode.SIGN_IN -> {
                        TextButton(onClick = { viewModel.setMode(AuthMode.SIGN_UP) }) {
                            Text(stringResource(R.string.dont_have_account), color = PrimaryCyan, fontSize = 14.sp)
                        }
                    }
                    AuthMode.SIGN_UP -> {
                        TextButton(onClick = { viewModel.setMode(AuthMode.SIGN_IN) }) {
                            Text(stringResource(R.string.already_have_account), color = PrimaryCyan, fontSize = 14.sp)
                        }
                    }
                    AuthMode.FORGOT_PASSWORD -> {
                        TextButton(onClick = { viewModel.setMode(AuthMode.SIGN_IN) }) {
                            Text("Back to Sign In", color = PrimaryCyan, fontSize = 14.sp)
                        }
                    }
                }
            }

            // Continue in Offline / Local Mode Button
            OutlinedButton(
                onClick = { viewModel.continueInOfflineMode() },
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderDivider),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .padding(top = 8.dp)
                    .testTag("offline_mode_button")
            ) {
                Text(
                    text = stringResource(R.string.continue_offline),
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Connect with us
            Text(
                text = stringResource(R.string.connect_with_us),
                style = MaterialTheme.typography.labelMedium.copy(color = TextSecondary)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SocialIconButton(icon = Icons.Default.Language, description = "Website") {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://trippilot.in"))
                    try { context.startActivity(intent) } catch (_: Exception) {}
                }
                SocialIconButton(icon = Icons.Default.Send, description = "Telegram") {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/trippilot_drivers"))
                    try { context.startActivity(intent) } catch (_: Exception) {}
                }
                SocialIconButton(icon = Icons.Default.Chat, description = "WhatsApp") {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/919999999999"))
                    try { context.startActivity(intent) } catch (_: Exception) {}
                }
            }
        }
    }
}

@Composable
fun SocialIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(DarkSurfaceElevated)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = PrimaryCyan,
            modifier = Modifier.size(22.dp)
        )
    }
}
