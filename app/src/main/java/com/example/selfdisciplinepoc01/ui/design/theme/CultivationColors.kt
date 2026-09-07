package com.example.selfdisciplinepoc01.ui.design.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Hệ thống màu sắc ngữ nghĩa (Semantic Color Tokens) phong cách Tiên Hiệp (Xianxia / Cultivation + RPG).
 * Phản ánh thẩm mỹ Huyền Mặc, Thanh Ngọc, Kim Tinh và Chu Sa.
 * Tuyệt đối không hard-code màu trong composable; mọi giao diện dùng qua [CultivationTheme.colors].
 */
@Immutable
data class CultivationColors(
    val background: Color,
    val surface: Color,
    val elevatedSurface: Color,
    val cardBackground: Color,
    val cardBackgroundElevated: Color,

    // Nhóm màu linh lực cốt lõi
    val celestialGold: Color,       // Kim Tinh / Hoàng Kim (Nhiệm vụ đang làm, điểm nhấn chính)
    val celestialGoldMuted: Color,  // Kim Tinh nhạt (Nền chip, viền phụ)
    val spiritTeal: Color,          // Thanh Ngọc / Linh Khí (Bảo Khố, hoàn thành)
    val spiritTealMuted: Color,     // Thanh Ngọc nhạt (Badge, glow phụ)
    val etherealIndigo: Color,      // Tử Tiêu / Phù Lục (Nhiệm Vụ Đường, liên kết)
    val etherealIndigoMuted: Color, // Tử Tiêu nhạt
    val crimsonSeal: Color,         // Chu Sa / Phong Ấn (Cảnh báo, xóa, gỡ bỏ)
    val crimsonSealMuted: Color,    // Chu Sa nhạt

    // Viền phù văn (Rune borders)
    val borderSubtle: Color,        // Viền thanh mảnh mặc định
    val borderGlow: Color,          // Viền phát quang linh lực
    val borderActive: Color,        // Viền trạng thái được chọn / đang kích hoạt

    // Chữ viết & Biểu tượng (Typography & Icons)
    val textPrimary: Color,         // Văn bản chính (độ tương phản cao)
    val textSecondary: Color,       // Văn bản phụ / giải thích
    val textMuted: Color,           // Văn bản mờ / ngày tháng / thông số
    val textOnPrimary: Color,       // Văn bản trên nền màu nổi

    // Trạng thái hiển thị (Presentation States Only - Không dùng làm business state)
    val statusSuccess: Color,       // Trạng thái hoàn thành (Ngọc bích)
    val statusWarning: Color,       // Trạng thái nhắc nhở
    val statusDanger: Color,        // Trạng thái nguy hiểm / hủy
    val statusDisabled: Color,      // Trạng thái vô hiệu hóa

    val isDark: Boolean
)

fun darkCultivationColors(): CultivationColors = CultivationColors(
    background = Color(0xFF090D16),               // Huyền Mặc (Đêm sâu thẳm)
    surface = Color(0xFF101626),                  // Bề mặt Mặc Ngọc
    elevatedSurface = Color(0xFF172036),          // Bề mặt nâng cao
    cardBackground = Color(0xFF131B2E),           // Nền thẻ linh ngọc
    cardBackgroundElevated = Color(0xFF1B2640),   // Nền thẻ nổi bật
    celestialGold = Color(0xFFF59E0B),            // Hoàng Kim Kim Tinh
    celestialGoldMuted = Color(0x26F59E0B),       // Hoàng Kim 15% alpha
    spiritTeal = Color(0xFF14B8A6),               // Thanh Ngọc Linh Khí
    spiritTealMuted = Color(0x2614B8A6),          // Thanh Ngọc 15% alpha
    etherealIndigo = Color(0xFF6366F1),           // Tử Tiêu Phù Lục
    etherealIndigoMuted = Color(0x266366F1),      // Tử Tiêu 15% alpha
    crimsonSeal = Color(0xFFF43F5E),              // Chu Sa Phong Ấn
    crimsonSealMuted = Color(0x26F43F5E),         // Chu Sa 15% alpha
    borderSubtle = Color(0xFF263554),             // Viền linh phù mờ
    borderGlow = Color(0x596366F1),               // Viền hào quang nhẹ
    borderActive = Color(0xFFF59E0B),             // Viền kích hoạt
    textPrimary = Color(0xFFF8FAFC),              // Bạch Ngọc (Chữ chính)
    textSecondary = Color(0xFFCBD5E1),            // Ngân Sương (Chữ phụ)
    textMuted = Color(0xFF718096),                // Huyền Khôi (Chữ mờ)
    textOnPrimary = Color(0xFF090D16),            // Chữ trên nền sáng
    statusSuccess = Color(0xFF10B981),            // Ngọc Bích
    statusWarning = Color(0xFFF59E0B),            // Hổ Phách
    statusDanger = Color(0xFFF43F5E),             // Đan Sa
    statusDisabled = Color(0xFF334155),           // Tàn Mặc
    isDark = true
)

fun lightCultivationColors(): CultivationColors = CultivationColors(
    background = Color(0xFFF4F6FB),               // Bạch Vân Thư Hương
    surface = Color(0xFFFFFFFF),                  // Bề mặt trắng tinh khiết
    elevatedSurface = Color(0xFFF8FAFC),          // Bề mặt nâng
    cardBackground = Color(0xFFFFFFFF),           // Nền thẻ ngọc giản
    cardBackgroundElevated = Color(0xFFF1F5F9),   // Nền thẻ nổi
    celestialGold = Color(0xFFD97706),            // Kim Tinh đậm
    celestialGoldMuted = Color(0x1AD97706),
    spiritTeal = Color(0xFF0D9488),               // Thanh Ngọc
    spiritTealMuted = Color(0x1A0D9488),
    etherealIndigo = Color(0xFF4F46E5),           // Tử Tiêu
    etherealIndigoMuted = Color(0x1A4F46E5),
    crimsonSeal = Color(0xFFE11D48),              // Chu Sa
    crimsonSealMuted = Color(0x1AE11D48),
    borderSubtle = Color(0xFFE2E8F0),             // Viền thanh nhạt
    borderGlow = Color(0x336366F1),
    borderActive = Color(0xFFD97706),
    textPrimary = Color(0xFF0F172A),              // Mực đen đậm
    textSecondary = Color(0xFF334155),            // Mực đen vừa
    textMuted = Color(0xFF64748B),                // Mực xám nhạt
    textOnPrimary = Color(0xFFFFFFFF),
    statusSuccess = Color(0xFF059669),
    statusWarning = Color(0xFFD97706),
    statusDanger = Color(0xFFE11D48),
    statusDisabled = Color(0xFFCBD5E1),
    isDark = false
)
