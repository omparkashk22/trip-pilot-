package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

enum class Icon3DTint(val topColor: Color, val bottomColor: Color, val glowColor: Color) {
    CYAN(Color(0xFF22D3EE), Color(0xFF0891B2), Color(0xFF06B6D4)),
    GREEN(Color(0xFF34D399), Color(0xFF059669), Color(0xFF10B981)),
    AMBER(Color(0xFFFBBF24), Color(0xFFD97706), Color(0xFFF59E0B)),
    VIOLET(Color(0xFFA78BFA), Color(0xFF7C3AED), Color(0xFF8B5CF6)),
    ROSE(Color(0xFFFB7185), Color(0xFFE11D48), Color(0xFFF43F5E))
}

@Composable
fun Icon3D(
    icon: ImageVector,
    tint: Icon3DTint = Icon3DTint.CYAN,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1.0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
        label = "icon3d_scale"
    )

    val shadowElevation = if (isPressed) 2.dp else 6.dp

    Box(
        modifier = modifier
            .size(34.dp)
            .scale(scale)
            .shadow(
                elevation = shadowElevation,
                shape = RoundedCornerShape(11.dp),
                spotColor = tint.glowColor.copy(alpha = 0.30f),
                ambientColor = tint.glowColor.copy(alpha = 0.30f)
            )
            .clip(RoundedCornerShape(11.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(tint.topColor, tint.bottomColor)
                )
            )
            .border(
                BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                shape = RoundedCornerShape(11.dp)
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick
                    )
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(18.dp)
        )
    }
}
