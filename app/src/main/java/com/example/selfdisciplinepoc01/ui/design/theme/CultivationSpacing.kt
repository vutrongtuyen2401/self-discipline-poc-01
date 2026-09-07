package com.example.selfdisciplinepoc01.ui.design.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Hệ thống khoảng cách & bố cục ngữ nghĩa (Semantic Spacing Tokens) phong cách Tiên Hiệp.
 */
@Immutable
data class CultivationSpacing(
    val xxs: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,

    // Bố cục thành phần
    val screenHorizontalPadding: Dp = 16.dp,
    val screenVerticalPadding: Dp = 16.dp,
    val cardPadding: Dp = 16.dp,
    val cardGap: Dp = 12.dp,
    val gridGap: Dp = 12.dp,

    // Kích thước chuẩn các biểu tượng
    val iconSm: Dp = 20.dp,
    val iconMd: Dp = 36.dp,
    val iconLg: Dp = 48.dp,
    val touchTargetMin: Dp = 48.dp
)

fun defaultCultivationSpacing(): CultivationSpacing = CultivationSpacing()
