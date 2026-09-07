package com.example.selfdisciplinepoc01.ui.design.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Hệ thống hình khối ngữ nghĩa (Semantic Shape Tokens) phong cách Tiên Hiệp.
 * Đường nét vát cong mềm mại như ngọc bội, linh phù, ngọc giản.
 */
@Immutable
data class CultivationShapes(
    val card: Shape = RoundedCornerShape(16.dp),          // Thẻ ngọc giản chính
    val cardElevated: Shape = RoundedCornerShape(18.dp),  // Thẻ nâng cao
    val itemCell: Shape = RoundedCornerShape(14.dp),      // Ô item túi đồ / app item
    val button: Shape = RoundedCornerShape(12.dp),        // Nút bấm tu tiên
    val chip: Shape = RoundedCornerShape(8.dp),           // Nhãn phù văn / chip liên kết
    val dialog: Shape = RoundedCornerShape(20.dp),        // Khung thông cáo / dialog
    val avatar: Shape = RoundedCornerShape(12.dp),        // Avatar app / biểu tượng
    val circular: Shape = CircleShape                     // Tròn hoàn hảo (FAB, badge)
)

fun defaultCultivationShapes(): CultivationShapes = CultivationShapes()
