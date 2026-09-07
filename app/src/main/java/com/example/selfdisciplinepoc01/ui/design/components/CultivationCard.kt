package com.example.selfdisciplinepoc01.ui.design.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.selfdisciplinepoc01.ui.design.animation.pressScaleEffect
import com.example.selfdisciplinepoc01.ui.design.animation.subtleBreathingBorder
import com.example.selfdisciplinepoc01.ui.design.theme.CultivationTheme

enum class CultivationCardVariant {
    STANDARD,      // Thẻ chuẩn mặc định
    ELEVATED,      // Thẻ nâng cao nổi bật
    ACTIVE_GLOW,   // Thẻ đang kích hoạt (có viền thở hào quang)
    OUTLINED       // Thẻ viền thanh mảnh
}

/**
 * Thẻ ngọc giản / linh phù phong cách Tiên Hiệp (CultivationCard).
 * Cung cấp bố cục nhiều tầng (layered surfaces), độ sâu mềm mại và viền phù văn trang nhã.
 */
@Composable
fun CultivationCard(
    modifier: Modifier = Modifier,
    variant: CultivationCardVariant = CultivationCardVariant.STANDARD,
    shape: Shape = CultivationTheme.shapes.card,
    onClick: (() -> Unit)? = null,
    backgroundColor: Color? = null,
    borderColor: Color? = null,
    contentPadding: Dp = CultivationTheme.spacing.cardPadding,
    content: @Composable BoxScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    val resolvedBgColor = backgroundColor ?: when (variant) {
        CultivationCardVariant.STANDARD -> CultivationTheme.colors.cardBackground
        CultivationCardVariant.ELEVATED -> CultivationTheme.colors.cardBackgroundElevated
        CultivationCardVariant.ACTIVE_GLOW -> CultivationTheme.colors.cardBackground
        CultivationCardVariant.OUTLINED -> CultivationTheme.colors.surface
    }

    val resolvedBorder = when (variant) {
        CultivationCardVariant.STANDARD -> BorderStroke(1.dp, borderColor ?: CultivationTheme.colors.borderSubtle)
        CultivationCardVariant.ELEVATED -> BorderStroke(1.dp, borderColor ?: CultivationTheme.colors.borderGlow)
        CultivationCardVariant.ACTIVE_GLOW -> null // Được vẽ bằng subtleBreathingBorder
        CultivationCardVariant.OUTLINED -> BorderStroke(1.dp, borderColor ?: CultivationTheme.colors.borderSubtle)
    }

    val baseModifier = if (variant == CultivationCardVariant.ACTIVE_GLOW) {
        modifier.subtleBreathingBorder(
            baseColor = borderColor ?: CultivationTheme.colors.celestialGold,
            shape = shape,
            strokeWidth = 1.5.dp
        )
    } else {
        modifier
    }

    val clickableModifier = if (onClick != null) {
        baseModifier
            .pressScaleEffect(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    } else {
        baseModifier
    }

    Surface(
        modifier = clickableModifier,
        shape = shape,
        color = resolvedBgColor,
        border = resolvedBorder,
        shadowElevation = when (variant) {
            CultivationCardVariant.ELEVATED -> CultivationTheme.elevation.cardElevated
            CultivationCardVariant.STANDARD -> CultivationTheme.elevation.card
            CultivationCardVariant.ACTIVE_GLOW -> CultivationTheme.elevation.card
            CultivationCardVariant.OUTLINED -> CultivationTheme.elevation.none
        }
    ) {
        Box(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}
