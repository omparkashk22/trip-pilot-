package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LocalAppColors

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color? = null,
    borderColor: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val colors = LocalAppColors.current
    val bg = backgroundColor ?: colors.surface
    val border = borderColor ?: colors.hairline

    val cardModifier = modifier
        .fillMaxWidth()
        .widthIn(max = 600.dp)
        .clip(RoundedCornerShape(18.dp))
        .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)

    Card(
        modifier = cardModifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = bg),
        border = BorderStroke(1.dp, border)
    ) {
        Column(
            modifier = Modifier.padding(13.dp)
        ) {
            content()
        }
    }
}

@Composable
fun PrimaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isSuccessState: Boolean = false,
    icon: ImageVector? = null,
    testTag: String = "primary_button"
) {
    val colors = LocalAppColors.current

    val bgColor by animateColorAsState(
        targetValue = when {
            isSuccessState -> colors.success
            enabled -> colors.accent
            else -> colors.surface2
        },
        animationSpec = tween(220),
        label = "btn_bg"
    )

    val contentColor = if (isSuccessState || enabled) Color(0xFF060B18) else colors.textSecondary

    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 600.dp)
            .height(44.dp)
            .testTag(testTag),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = bgColor,
            contentColor = contentColor,
            disabledContainerColor = colors.surface2,
            disabledContentColor = colors.textSecondary
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = text,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = contentColor
            )
        }
    }
}

@Composable
fun StatusBadge(
    status: String,
    modifier: Modifier = Modifier
) {
    val colors = LocalAppColors.current

    val (bgColor, textColor, label) = when (status.uppercase()) {
        "ACCEPTED" -> Triple(colors.success.copy(alpha = 0.15f), colors.success, "Accepted")
        "SKIPPED" -> Triple(colors.warning.copy(alpha = 0.15f), colors.warning, "Skipped")
        "TAP_FAILED" -> Triple(colors.danger.copy(alpha = 0.15f), colors.danger, "Tap Failed")
        "RUNNING" -> Triple(colors.success.copy(alpha = 0.15f), colors.success, "Running")
        "STOPPED" -> Triple(colors.hairline, colors.textSecondary, "Stopped")
        else -> Triple(colors.accent.copy(alpha = 0.15f), colors.accent, status)
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, textColor.copy(alpha = 0.3f)),
        modifier = modifier.height(24.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Text(
                text = label,
                color = textColor,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun CustomTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    isNumeric: Boolean = false,
    unitText: String? = null,
    isError: Boolean = false,
    errorMessage: String? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    testTag: String = "text_input"
) {
    AppTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        modifier = modifier,
        placeholder = placeholder,
        isNumeric = isNumeric,
        unitText = unitText,
        isError = isError,
        errorMessage = errorMessage,
        trailingIcon = trailingIcon,
        testTag = testTag
    )
}
