package com.example.selfdisciplinepoc01.ui.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.selfdisciplinepoc01.ui.design.theme.CultivationTheme

/**
 * Ô ứng dụng Bảo Khố dạng Grid phong cách túi đồ tu tiên (AppItemCard).
 * Kế thừa pattern ô item (Item Cell) từ eOr và NeoMud:
 * - Hiển thị Icon thật của ứng dụng Android qua [AsyncAppIcon].
 * - Tên ứng dụng thanh thoát, cắt ngắn nếu dài.
 * - Nhãn số lượng nhiệm vụ liên kết (badge).
 * - Nút "Gỡ" phong cách Chu Sa (Crimson Seal).
 * - TUYỆT ĐỐI KHÔNG DÙNG ICON Ổ KHÓA (Tuân thủ nghiêm ngặt Canonical Design V2 Mục 6).
 */
@Composable
fun AppItemGridCard(
    packageName: String,
    appName: String,
    linkedTasksCount: Int,
    onRemoveClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasLinks = linkedTasksCount > 0

    CultivationCard(
        modifier = modifier,
        variant = if (hasLinks) CultivationCardVariant.ELEVATED else CultivationCardVariant.STANDARD,
        borderColor = if (hasLinks) CultivationTheme.colors.etherealIndigo.copy(alpha = 0.5f) else null,
        contentPadding = 12.dp
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Icon thật của ứng dụng Android
            AsyncAppIcon(
                packageName = packageName,
                fallbackAppName = appName,
                size = 52.dp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Tên ứng dụng
            Text(
                text = appName,
                style = CultivationTheme.typography.titleCard.copy(fontSize = 14.sp),
                color = CultivationTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Badge trạng thái liên kết nhiệm vụ (Dựa trên dữ liệu thực)
            val badgeText = if (hasLinks) "$linkedTasksCount nhiệm vụ" else "Chưa liên kết"
            val badgeBg = if (hasLinks) CultivationTheme.colors.etherealIndigoMuted else CultivationTheme.colors.surface
            val badgeColor = if (hasLinks) CultivationTheme.colors.etherealIndigo else CultivationTheme.colors.textMuted

            Box(
                modifier = Modifier
                    .background(badgeBg, CultivationTheme.shapes.chip)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = badgeText,
                    style = CultivationTheme.typography.caption.copy(fontWeight = FontWeight.Medium),
                    color = badgeColor
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Nút gỡ app khỏi Bảo Khố
            CultivationButton(
                text = "Gỡ",
                onClick = onRemoveClick,
                variant = CultivationButtonVariant.DANGER,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Hàng ứng dụng trong Dialog chọn app (AppItemSelectableRow).
 * Hỗ trợ checkbox và icon thật cho việc chọn liên kết với nhiệm vụ.
 */
@Composable
fun AppItemSelectableRow(
    packageName: String,
    appName: String,
    isSelected: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    CultivationCard(
        modifier = modifier.fillMaxWidth(),
        variant = if (isSelected) CultivationCardVariant.ELEVATED else CultivationCardVariant.STANDARD,
        borderColor = if (isSelected) CultivationTheme.colors.celestialGold else null,
        onClick = { onToggle(!isSelected) },
        contentPadding = 8.dp
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = onToggle,
                colors = CheckboxDefaults.colors(
                    checkedColor = CultivationTheme.colors.celestialGold,
                    checkmarkColor = CultivationTheme.colors.textOnPrimary,
                    uncheckedColor = CultivationTheme.colors.borderSubtle
                )
            )

            Spacer(modifier = Modifier.width(6.dp))

            AsyncAppIcon(
                packageName = packageName,
                fallbackAppName = appName,
                size = 36.dp
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = appName,
                    style = CultivationTheme.typography.titleCard.copy(fontSize = 14.sp),
                    color = CultivationTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = packageName,
                    style = CultivationTheme.typography.caption,
                    color = CultivationTheme.colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
