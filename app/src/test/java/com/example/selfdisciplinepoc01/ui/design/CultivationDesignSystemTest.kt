package com.example.selfdisciplinepoc01.ui.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.selfdisciplinepoc01.ui.design.components.CultivationButtonVariant
import com.example.selfdisciplinepoc01.ui.design.components.CultivationCardVariant
import com.example.selfdisciplinepoc01.ui.design.theme.CultivationColors
import com.example.selfdisciplinepoc01.ui.design.theme.CultivationElevation
import com.example.selfdisciplinepoc01.ui.design.theme.CultivationShapes
import com.example.selfdisciplinepoc01.ui.design.theme.CultivationSpacing
import com.example.selfdisciplinepoc01.ui.design.theme.CultivationTypography
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit Test xác thực tính toàn vẹn của Cultivation UI Design System Foundation.
 * Đảm bảo:
 * - Semantic tokens (Colors, Typography, Shapes, Spacing, Elevation) được định nghĩa đầy đủ, hợp lệ.
 * - Phân cấp Dark Theme và Light Theme tuân thủ độ tương phản và không bị null.
 * - Các component variant (Button, Card) có enum đầy đủ.
 * - Touch target tối thiểu đáp ứng tiêu chuẩn Accessibility (>= 48dp).
 */
class CultivationDesignSystemTest {

    @Test
    fun testDarkColorTokensValidity() {
        val dark = com.example.selfdisciplinepoc01.ui.design.theme.darkCultivationColors()
        assertTrue(dark.isDark)

        // Nền tối Huyền Mặc không phải màu trắng
        assertNotEquals(Color.White, dark.background)
        assertEquals(Color(0xFF090D16), dark.background)

        // Màu chính Hoàng Kim (Celestial Gold) và Thanh Ngọc (Spirit Teal)
        assertEquals(Color(0xFFF59E0B), dark.celestialGold)
        assertEquals(Color(0xFF14B8A6), dark.spiritTeal)

        // Chữ chính có độ sáng cao trên nền tối
        assertEquals(Color(0xFFF8FAFC), dark.textPrimary)

        // Trạng thái thành công, cảnh báo, nguy hiểm
        assertEquals(Color(0xFF10B981), dark.statusSuccess)
        assertEquals(Color(0xFFF59E0B), dark.statusWarning)
        assertEquals(Color(0xFFF43F5E), dark.statusDanger)
    }

    @Test
    fun testLightColorTokensValidity() {
        val light = com.example.selfdisciplinepoc01.ui.design.theme.lightCultivationColors()
        assertFalse(light.isDark)

        // Nền sáng Bạch Ngọc
        assertEquals(Color(0xFFF4F6FB), light.background)

        // Màu chữ chính tối trên nền sáng
        assertEquals(Color(0xFF0F172A), light.textPrimary)

        // Hoàng Kim và Thanh Ngọc tương phản tốt
        assertEquals(Color(0xFFD97706), light.celestialGold)
        assertEquals(Color(0xFF0D9488), light.spiritTeal)
    }

    @Test
    fun testSpacingAndTouchTargetAccessibility() {
        val spacing = CultivationSpacing()

        // Tiêu chuẩn touch target của Android tối thiểu 48dp
        assertTrue(spacing.touchTargetMin >= 48.dp)

        // Thứ tự spacing tăng dần hợp lý
        assertTrue(spacing.xxs < spacing.xs)
        assertTrue(spacing.xs < spacing.sm)
        assertTrue(spacing.sm < spacing.md)
        assertTrue(spacing.md < spacing.lg)
        assertTrue(spacing.lg < spacing.xl)
        assertTrue(spacing.xl < spacing.xxl)

        // Grid gap và Card padding hợp lệ
        assertEquals(12.dp, spacing.gridGap)
        assertEquals(16.dp, spacing.cardPadding)
    }

    @Test
    fun testElevationTokens() {
        val elevation = CultivationElevation()
        assertEquals(0.dp, elevation.none)
        assertTrue(elevation.subtle > elevation.none)
        assertTrue(elevation.card >= elevation.subtle)
        assertTrue(elevation.cardElevated >= elevation.card)
        assertTrue(elevation.dialog >= elevation.cardElevated)
    }

    @Test
    fun testShapesTokens() {
        val shapes = CultivationShapes()
        assertNotNull(shapes.card)
        assertNotNull(shapes.cardElevated)
        assertNotNull(shapes.itemCell)
        assertNotNull(shapes.button)
        assertNotNull(shapes.chip)
        assertNotNull(shapes.dialog)
        assertNotNull(shapes.avatar)
        assertNotNull(shapes.circular)
    }

    @Test
    fun testTypographyTokens() {
        val typography = com.example.selfdisciplinepoc01.ui.design.theme.defaultCultivationTypography()

        // Hero heading phải lớn hơn Title card và Body text
        assertTrue(typography.headingHero.fontSize.value > typography.titleCard.fontSize.value)
        assertTrue(typography.titleCard.fontSize.value > typography.bodyText.fontSize.value)
        assertTrue(typography.bodyText.fontSize.value >= typography.caption.fontSize.value)

        // Stat number rõ nét
        assertTrue(typography.statNumber.fontSize.value >= 18f)
    }

    @Test
    fun testButtonVariants() {
        val variants = CultivationButtonVariant.values()
        assertTrue(variants.contains(CultivationButtonVariant.PRIMARY))
        assertTrue(variants.contains(CultivationButtonVariant.SECONDARY))
        assertTrue(variants.contains(CultivationButtonVariant.GHOST))
        assertTrue(variants.contains(CultivationButtonVariant.DANGER))
    }

    @Test
    fun testCardVariants() {
        val variants = CultivationCardVariant.values()
        assertTrue(variants.contains(CultivationCardVariant.STANDARD))
        assertTrue(variants.contains(CultivationCardVariant.ELEVATED))
        assertTrue(variants.contains(CultivationCardVariant.ACTIVE_GLOW))
        assertTrue(variants.contains(CultivationCardVariant.OUTLINED))
    }
}
