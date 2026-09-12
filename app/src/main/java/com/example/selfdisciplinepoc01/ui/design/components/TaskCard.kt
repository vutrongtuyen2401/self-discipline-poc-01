package com.example.selfdisciplinepoc01.ui.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.example.selfdisciplinepoc01.domain.canonical.vault.CanonicalVaultApp
import com.example.selfdisciplinepoc01.ui.design.theme.CultivationTheme

/**
 * Thẻ hiển thị Nhiệm Vụ Tu Luyện (TaskCard) chuẩn Canonical.
 * Hỗ trợ đầy đủ các thao tác SSOT:
 * - Rename tức thì (không confirmation dialog)
 * - Complete (không confirmation dialog trong Mission Hall)
 * - Undo (kèm confirmation dialog của ViewModel)
 * - Delete (kèm confirmation dialog của ViewModel)
 * - Hiển thị liên kết N:M và trạng thái Pending Next-Cycle (chờ 04:00 AM)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskCard(
    taskName: String,
    modifier: Modifier = Modifier,
    isCurrentActive: Boolean = false,
    isCompleted: Boolean = false,
    createdAtText: String? = null,
    linkedApps: List<CanonicalVaultApp> = emptyList(),
    pendingNextCycleApps: List<String>? = null,
    onRenameClick: (() -> Unit)? = null,
    onUndoClick: (() -> Unit)? = null,
    onDeleteClick: (() -> Unit)? = null,
    onLinkAppsClick: (() -> Unit)? = null,
    onCancelPendingClick: (() -> Unit)? = null
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

                    Column(modifier = Modifier.weight(1f)) {
                        if (isCurrentActive) {
                            Text(
                                text = "NHIỆM VỤ ĐANG THỰC HIỆN",
                                style = CultivationTheme.typography.labelRune,
                                color = CultivationTheme.colors.celestialGold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = taskName,
                                style = CultivationTheme.typography.titleCard,
                                color = CultivationTheme.colors.textPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (onRenameClick != null && !isCompleted) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "✎",
                                    fontSize = 14.sp,
                                    color = CultivationTheme.colors.celestialGold,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable { onRenameClick() }
                                        .padding(4.dp)
                                )
                            }
                        }
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

                // Nút hành động nhanh phía phải
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isCompleted && onUndoClick != null) {
                        CultivationButton(
                            text = "Hoàn tác",
                            onClick = onUndoClick,
                            variant = CultivationButtonVariant.GHOST,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        )
                    } else if (!isCurrentActive && !isCompleted) {
                        if (onDeleteClick != null) {
                            CultivationButton(
                                text = "Xóa",
                                onClick = onDeleteClick,
                                variant = CultivationButtonVariant.GHOST,
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    } else if (isCurrentActive && onDeleteClick != null) {
                        CultivationButton(
                            text = "Xóa",
                            onClick = onDeleteClick,
                            variant = CultivationButtonVariant.GHOST,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        )
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
                                fallbackAppName = app.displayName,
                                size = 18.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = app.displayName,
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

            // Hiển thị Pending Next-Cycle Rewards nếu có
            if (pendingNextCycleApps != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            CultivationTheme.colors.celestialGold.copy(alpha = 0.12f),
                            CultivationTheme.shapes.card
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (pendingNextCycleApps.isEmpty()) {
                            "⏳ Chờ 04:00 AM: Gỡ toàn bộ liên kết"
                        } else {
                            "⏳ Chờ 04:00 AM: Cập nhật ${pendingNextCycleApps.size} app"
                        },
                        style = CultivationTheme.typography.caption.copy(
                            color = CultivationTheme.colors.celestialGold,
                            fontWeight = FontWeight.Medium
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    if (onCancelPendingClick != null) {
                        Text(
                            text = "Hủy chờ",
                            style = CultivationTheme.typography.caption.copy(
                                color = CultivationTheme.colors.statusDanger,
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier
                                .clip(CultivationTheme.shapes.chip)
                                .clickable { onCancelPendingClick() }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Phần cuối: Nút hành động chính (đối với task active hoặc nút sửa liên kết)
            if (isCurrentActive) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onLinkAppsClick != null) {
                        CultivationButton(
                            text = if (linkedApps.isEmpty()) "Liên kết App" else "Sửa liên kết (${linkedApps.size})",
                            onClick = onLinkAppsClick,
                            variant = CultivationButtonVariant.SECONDARY,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    // Trạng thái chỉ đọc trong Mission Hall theo SSOT:
                    // Thao tác hoàn thành chỉ xuất hiện bên trong System Panel khi app bị khóa
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(
                                CultivationTheme.colors.background,
                                CultivationTheme.shapes.chip
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "⏳ Đang thực hiện",
                            style = CultivationTheme.typography.caption.copy(fontWeight = FontWeight.Medium),
                            color = CultivationTheme.colors.textMuted
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
