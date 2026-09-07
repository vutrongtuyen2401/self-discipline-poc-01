package com.example.selfdisciplinepoc01.ui.design.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Hệ thống độ sâu & bóng đổ ngữ nghĩa (Semantic Elevation Tokens) phong cách Tiên Hiệp.
 */
@Immutable
data class CultivationElevation(
    val none: Dp = 0.dp,
    val subtle: Dp = 2.dp,
    val card: Dp = 4.dp,
    val cardElevated: Dp = 8.dp,
    val dialog: Dp = 16.dp
)

fun defaultCultivationElevation(): CultivationElevation = CultivationElevation()
