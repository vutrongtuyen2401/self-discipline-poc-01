package com.example.selfdisciplinepoc01.ui.design.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

val LocalCultivationColors = staticCompositionLocalOf { darkCultivationColors() }
val LocalCultivationTypography = staticCompositionLocalOf { defaultCultivationTypography() }
val LocalCultivationShapes = staticCompositionLocalOf { defaultCultivationShapes() }
val LocalCultivationSpacing = staticCompositionLocalOf { defaultCultivationSpacing() }
val LocalCultivationElevation = staticCompositionLocalOf { defaultCultivationElevation() }

/**
 * Điểm truy cập trung tâm cho hệ thống Cultivation Design System.
 * Ví dụ sử dụng:
 * [CultivationTheme.colors.background]
 * [CultivationTheme.typography.titleCard]
 * [CultivationTheme.shapes.card]
 */
object CultivationTheme {
    val colors: CultivationColors
        @Composable
        @ReadOnlyComposable
        get() = LocalCultivationColors.current

    val typography: CultivationTypography
        @Composable
        @ReadOnlyComposable
        get() = LocalCultivationTypography.current

    val shapes: CultivationShapes
        @Composable
        @ReadOnlyComposable
        get() = LocalCultivationShapes.current

    val spacing: CultivationSpacing
        @Composable
        @ReadOnlyComposable
        get() = LocalCultivationSpacing.current

    val elevation: CultivationElevation
        @Composable
        @ReadOnlyComposable
        get() = LocalCultivationElevation.current
}

/**
 * Composable Theme bọc toàn bộ giao diện, kết hợp chặt chẽ giữa Cultivation Semantic Tokens
 * và Android Material 3 Foundation.
 */
@Composable
fun CultivationTheme(
    darkTheme: Boolean = true, // Mặc định là Dark Mode chuẩn phong cách Tiên Hiệp / Huyền Mặc
    content: @Composable () -> Unit
) {
    val cultivationColors = if (darkTheme) darkCultivationColors() else lightCultivationColors()
    val cultivationTypography = defaultCultivationTypography()
    val cultivationShapes = defaultCultivationShapes()
    val cultivationSpacing = defaultCultivationSpacing()
    val cultivationElevation = defaultCultivationElevation()

    // Đồng bộ sang Material 3 ColorScheme để các component Material gốc thừa hưởng liền mạch
    val materialColorScheme = if (darkTheme) {
        darkColorScheme(
            primary = cultivationColors.celestialGold,
            onPrimary = cultivationColors.textOnPrimary,
            secondary = cultivationColors.spiritTeal,
            onSecondary = cultivationColors.textOnPrimary,
            tertiary = cultivationColors.etherealIndigo,
            background = cultivationColors.background,
            onBackground = cultivationColors.textPrimary,
            surface = cultivationColors.surface,
            onSurface = cultivationColors.textPrimary,
            surfaceVariant = cultivationColors.cardBackground,
            onSurfaceVariant = cultivationColors.textSecondary,
            error = cultivationColors.crimsonSeal,
            onError = cultivationColors.textPrimary,
            outline = cultivationColors.borderSubtle
        )
    } else {
        lightColorScheme(
            primary = cultivationColors.celestialGold,
            onPrimary = cultivationColors.textOnPrimary,
            secondary = cultivationColors.spiritTeal,
            onSecondary = cultivationColors.textOnPrimary,
            tertiary = cultivationColors.etherealIndigo,
            background = cultivationColors.background,
            onBackground = cultivationColors.textPrimary,
            surface = cultivationColors.surface,
            onSurface = cultivationColors.textPrimary,
            surfaceVariant = cultivationColors.cardBackground,
            onSurfaceVariant = cultivationColors.textSecondary,
            error = cultivationColors.crimsonSeal,
            onError = cultivationColors.textPrimary,
            outline = cultivationColors.borderSubtle
        )
    }

    CompositionLocalProvider(
        LocalCultivationColors provides cultivationColors,
        LocalCultivationTypography provides cultivationTypography,
        LocalCultivationShapes provides cultivationShapes,
        LocalCultivationSpacing provides cultivationSpacing,
        LocalCultivationElevation provides cultivationElevation
    ) {
        MaterialTheme(
            colorScheme = materialColorScheme,
            content = content
        )
    }
}
