package com.example.selfdisciplinepoc01.ui.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.selfdisciplinepoc01.domain.model.VaultApp
import com.example.selfdisciplinepoc01.ui.design.theme.CultivationTheme

/**
 * Thẻ hiển thị Nhiệm Vụ Tu Luyện (TaskCard).
 * Kế thừa pattern trình bày nhiệm vụ (Quest presentation) từ IdleFantasy:
 * - Phân cấp trực quan giữa nhiệm vụ đang tiến hành (Active / Glowing) và nhiệm vụ chờ thực hiện.
 * - Hiển thị Icon thật của các ứng dụng Bảo Khố liên kết dưới dạng chip linh phù tinh gọn.
 * - Nút hoàn thành và nút điều chỉnh liên kết.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskCard(
    taskName: String,
    modifier: Modifier = Modifier,
    isCurrentActive: Boolean = false,
    isCompleted: Boolean = false,
    createdAtText: String? = null,
    linkedApps: List<VaultApp> = emptyList(),
    onCompleteClick: (() -> Unit)? = null,
    onDeleteClick: (() -> Unit)? = null,
    onLinkAppsClick: (() -> Unit)? = null
) {
    val cardVariant = when {
        isCurrentActive -> CultivationCardVariant.ACTIVE_GLOW
        isCompleted -> CultivationCardVariant.STANDARD
        else -> CultivationCardVariant.STANDARD
    }

    CultivationCard(
        modifier = modifier.fillMaxWidth(),
        variant = cardVariant,
        borderColor = when {
            isCurrentActive -> CultivationTheme.colors.celestialGold
            isCompleted -> CultivationTheme.colors.statusSuccess.copy(alpha = 0.5f)
            else -> null
        }
    ) {
        Column {
            // Phần đầu: Tiêu đề & Nút hành động nhanh
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isCompleted) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(CultivationTheme.colors.statusSuccess),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "✓",
                                style = CultivationTheme.typography.caption.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = CultivationTheme.colors.textOnPrimary
                                )
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    } else if (isCurrentActive) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(CultivationTheme.colors.celestialGold)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    Column {
                        if (isCurrentActive) {
                            Text(
                                text = "NHIỆM VỤ ĐANG THỰC HIỆN",
                                style = CultivationTheme.typography.labelRune,
                                color = CultivationTheme.colors.celestialGold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                        }
                        Text(
                            text = taskName,
                            style = CultivationTheme.typography.titleCard,
                            color = CultivationTheme.colors.textPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (createdAtText != null) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = createdAtText,
                                style = CultivationTheme.typography.caption,
                                color = CultivationTheme.colors.textMuted
                            )
                        }
                    }
                }

                // Nút hành động nhanh phía phải (nếu không phải task đang active)
                if (!isCurrentActive && !isCompleted) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (onCompleteClick != null) {
                            CultivationButton(
                                text = "Xong",
                                onClick = onCompleteClick,
                                variant = CultivationButtonVariant.SECONDARY,
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                        if (onDeleteClick != null) {
                            Spacer(modifier = Modifier.width(6.dp))
                            CultivationButton(
                                text = "Xóa",
                                onClick = onDeleteClick,
                                variant = CultivationButtonVariant.GHOST,
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            // Phần giữa: Ứng dụng Bảo Khố liên kết kèm Icon thật
            Spacer(modifier = Modifier.height(10.dp))
            if (linkedApps.isNotEmpty()) {
                Text(
                    text = "Ứng dụng liên kết:",
                    style = CultivationTheme.typography.caption.copy(fontWeight = FontWeight.Medium),
                    color = CultivationTheme.colors.textSecondary
                )
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    linkedApps.forEach { app ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(
                                    CultivationTheme.colors.etherealIndigoMuted,
                                    CultivationTheme.shapes.chip
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            AsyncAppIcon(
                                packageName = app.packageName,
                                fallbackAppName = app.appName,
                                size = 18.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = app.appName,
                                style = CultivationTheme.typography.caption.copy(fontWeight = FontWeight.Medium),
                                color = CultivationTheme.colors.textPrimary
                            )
                        }
                    }
                }
            } else if (!isCompleted) {
                Text(
                    text = "Chưa liên kết ứng dụng Bảo Khố",
                    style = CultivationTheme.typography.caption,
                    color = CultivationTheme.colors.textMuted
                )
            }

            // Phần cuối: Nút hành động chính (đối với task active hoặc nút sửa liên kết)
            if (isCurrentActive) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (onLinkAppsClick != null) {
                        CultivationButton(
                            text = if (linkedApps.isEmpty()) "Liên kết App" else "Sửa liên kết (${linkedApps.size})",
                            onClick = onLinkAppsClick,
                            variant = CultivationButtonVariant.SECONDARY,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (onCompleteClick != null) {
                        CultivationButton(
                            text = "Xác nhận hoàn thành",
                            onClick = onCompleteClick,
                            variant = CultivationButtonVariant.PRIMARY,
                            modifier = Modifier.weight(1.4f)
                        )
                    }
                }
            } else if (!isCompleted && onLinkAppsClick != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    CultivationButton(
                        text = if (linkedApps.isEmpty()) "+ Liên kết App" else "Sửa liên kết (${linkedApps.size})",
                        onClick = onLinkAppsClick,
                        variant = CultivationButtonVariant.GHOST,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}
