package com.example.selfdisciplinepoc01.ui.design.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.example.selfdisciplinepoc01.ui.design.animation.pressScaleEffect
import com.example.selfdisciplinepoc01.ui.design.theme.CultivationTheme

enum class CultivationButtonVariant {
    PRIMARY,    // Hoàng Kim (Celestial Gold) - Thao tác chính
    SECONDARY,  // Thanh Ngọc (Spirit Teal) - Thao tác phụ
    GHOST,      // Viền mờ tối giản - Thao tác hủy, đóng
    DANGER      // Chu Sa (Crimson Seal) - Gỡ app, xóa nhiệm vụ
}

/**
 * Nút bấm phong cách Tiên Hiệp (CultivationButton).
 * Tích hợp hiệu ứng nảy nhẹ khi nhấn, phân cấp thị giác rõ ràng và tối ưu kích thước cảm ứng.
 */
@Composable
fun CultivationButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: CultivationButtonVariant = CultivationButtonVariant.PRIMARY,
    enabled: Boolean = true,
    loading: Boolean = false,
    shape: Shape = CultivationTheme.shapes.button,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    leadingIcon: (@Composable () -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }

    val containerColor = when {
        !enabled -> CultivationTheme.colors.surface
        variant == CultivationButtonVariant.PRIMARY -> CultivationTheme.colors.celestialGold
        variant == CultivationButtonVariant.SECONDARY -> CultivationTheme.colors.spiritTealMuted
        variant == CultivationButtonVariant.GHOST -> Color.Transparent
        variant == CultivationButtonVariant.DANGER -> CultivationTheme.colors.crimsonSealMuted
        else -> CultivationTheme.colors.surface
    }

    val contentColor = when {
        !enabled -> CultivationTheme.colors.statusDisabled
        variant == CultivationButtonVariant.PRIMARY -> CultivationTheme.colors.textOnPrimary
        variant == CultivationButtonVariant.SECONDARY -> CultivationTheme.colors.spiritTeal
        variant == CultivationButtonVariant.GHOST -> CultivationTheme.colors.textSecondary
        variant == CultivationButtonVariant.DANGER -> CultivationTheme.colors.crimsonSeal
        else -> CultivationTheme.colors.textPrimary
    }

    val border = when {
        !enabled -> BorderStroke(1.dp, CultivationTheme.colors.statusDisabled.copy(alpha = 0.3f))
        variant == CultivationButtonVariant.PRIMARY -> null
        variant == CultivationButtonVariant.SECONDARY -> BorderStroke(1.dp, CultivationTheme.colors.spiritTeal.copy(alpha = 0.5f))
        variant == CultivationButtonVariant.GHOST -> BorderStroke(1.dp, CultivationTheme.colors.borderSubtle)
        variant == CultivationButtonVariant.DANGER -> BorderStroke(1.dp, CultivationTheme.colors.crimsonSeal.copy(alpha = 0.5f))
        else -> null
    }

    Surface(
        modifier = modifier
            .defaultMinSize(minHeight = CultivationTheme.spacing.touchTargetMin)
            .pressScaleEffect(interactionSource, targetScale = if (enabled) 0.96f else 1.0f)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled && !loading,
                onClick = onClick
            ),
        shape = shape,
        color = containerColor,
        border = border,
        shadowElevation = if (enabled && variant == CultivationButtonVariant.PRIMARY) CultivationTheme.elevation.subtle else CultivationTheme.elevation.none
    ) {
        Row(
            modifier = Modifier.padding(contentPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = contentColor
                )
            } else {
                if (leadingIcon != null) {
                    Box(modifier = Modifier.padding(end = 6.dp)) {
                        leadingIcon()
                    }
                }
                Text(
                    text = text,
                    style = CultivationTheme.typography.button,
                    color = contentColor
                )
            }
        }
    }
}
