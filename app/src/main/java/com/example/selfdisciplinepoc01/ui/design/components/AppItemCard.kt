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
import androidx.compose.ui.graphics.Color
import com.example.selfdisciplinepoc01.domain.enforcement.AppEnforcementDetails
import com.example.selfdisciplinepoc01.domain.enforcement.EnforcementReason
import com.example.selfdisciplinepoc01.ui.design.theme.CultivationTheme

private data class EnforcementBadgeConfig(
    val text: String,
    val backgroundColor: Color,
    val textColor: Color,
    val borderColor: Color?
)

/**
 * Ô ứng dụng Bảo Khố dạng Grid phong cách túi đồ tu tiên (AppItemCard).
 * Kế thừa pattern ô item (Item Cell) từ eOr và NeoMud:
 * - Hiển thị Icon thật của ứng dụng Android qua [AsyncAppIcon].
 * - Tên ứng dụng thanh thoát, cắt ngắn nếu dài.
 * - Nhãn trạng thái thực thi nhận trực tiếp từ business layer qua [enforcement].
 * - Nút "Gỡ" phong cách Chu Sa (Crimson Seal).
 * - TUYỆT ĐỐI KHÔNG DÙNG ICON Ổ KHÓA (Tuân thủ nghiêm ngặt Canonical Design V2 Mục 6).
 */
@Composable
fun AppItemGridCard(
    packageName: String,
    appName: String,
    linkedTasksCount: Int,
    onRemoveClick: () -> Unit,
    modifier: Modifier = Modifier,
    enforcement: AppEnforcementDetails? = null
) {
    val hasLinks = (enforcement?.totalLinkedTasksCount ?: linkedTasksCount) > 0

    // Phân giải hiển thị trạng thái hoàn toàn từ Business Layer (Zero Business Logic in UI)
    val badgeConfig: EnforcementBadgeConfig = when {
        enforcement != null -> {
            when (enforcement.reason) {
                EnforcementReason.LOCKED_BY_POLICY -> EnforcementBadgeConfig(
                    text = "Khóa kỹ thuật",
                    backgroundColor = CultivationTheme.colors.crimsonSealMuted,
                    textColor = CultivationTheme.colors.crimsonSeal,
                    borderColor = CultivationTheme.colors.crimsonSeal.copy(alpha = 0.6f)
                )
                EnforcementReason.ALLOWED_UNLOCKED_BY_TASKS -> EnforcementBadgeConfig(
                    text = "Đã mở (${enforcement.completedLinkedTasksCount}/${enforcement.requiredTasksCount})",
                    backgroundColor = CultivationTheme.colors.spiritTealMuted,
                    textColor = CultivationTheme.colors.spiritTeal,
                    borderColor = CultivationTheme.colors.spiritTeal.copy(alpha = 0.6f)
                )
                EnforcementReason.LOCKED_INSUFFICIENT_TASKS -> EnforcementBadgeConfig(
                    text = "Phong ấn (${enforcement.completedLinkedTasksCount}/${enforcement.totalLinkedTasksCount})",
                    backgroundColor = CultivationTheme.colors.celestialGoldMuted,
                    textColor = CultivationTheme.colors.celestialGold,
                    borderColor = CultivationTheme.colors.etherealIndigo.copy(alpha = 0.5f)
                )
                EnforcementReason.LOCKED_BY_VAULT_NO_TASK -> EnforcementBadgeConfig(
                    text = "Chưa liên kết (Khóa)",
                    backgroundColor = CultivationTheme.colors.surface,
                    textColor = CultivationTheme.colors.textMuted,
                    borderColor = null
                )
                else -> EnforcementBadgeConfig(
                    text = if (hasLinks) "$linkedTasksCount nhiệm vụ" else "Chưa liên kết",
                    backgroundColor = if (hasLinks) CultivationTheme.colors.etherealIndigoMuted else CultivationTheme.colors.surface,
                    textColor = if (hasLinks) CultivationTheme.colors.etherealIndigo else CultivationTheme.colors.textMuted,
                    borderColor = if (hasLinks) CultivationTheme.colors.etherealIndigo.copy(alpha = 0.5f) else null
                )
            }
        }
        else -> {
            EnforcementBadgeConfig(
                text = if (hasLinks) "$linkedTasksCount nhiệm vụ" else "Chưa liên kết",
                backgroundColor = if (hasLinks) CultivationTheme.colors.etherealIndigoMuted else CultivationTheme.colors.surface,
                textColor = if (hasLinks) CultivationTheme.colors.etherealIndigo else CultivationTheme.colors.textMuted,
                borderColor = if (hasLinks) CultivationTheme.colors.etherealIndigo.copy(alpha = 0.5f) else null
            )
        }
    }

    CultivationCard(
        modifier = modifier,
        variant = if (hasLinks) CultivationCardVariant.ELEVATED else CultivationCardVariant.STANDARD,
        borderColor = badgeConfig.borderColor,
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

            // Badge trạng thái thực thi phong ấn (Dựa trên dữ liệu thực từ Business Layer)
            Box(
                modifier = Modifier
                    .background(badgeConfig.backgroundColor, CultivationTheme.shapes.chip)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = badgeConfig.text,
                    style = CultivationTheme.typography.caption.copy(fontWeight = FontWeight.Medium),
                    color = badgeConfig.textColor
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
