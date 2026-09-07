package com.example.selfdisciplinepoc01.ui.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.example.selfdisciplinepoc01.ui.design.animation.CultivationAnimation
import com.example.selfdisciplinepoc01.ui.design.theme.CultivationTheme

/**
 * Thẻ hiển thị Tiến Trình Tu Luyện Hôm Nay (ProgressCard).
 * Kế thừa pattern hiển thị tiến độ (Progression presentation) từ ASCENDANT & IdleFantasy,
 * thể hiện qua thanh linh lực phát quang mềm mại và tỷ lệ hoàn thành chuỗi ngày.
 */
@Composable
fun ProgressCard(
    completedCount: Int,
    totalCount: Int,
    modifier: Modifier = Modifier,
    title: String = "Tiến Trình Tu Luyện Hôm Nay",
    subtitle: String? = null
) {
    val progressRatio = if (totalCount > 0) completedCount.toFloat() / totalCount.toFloat() else 0f
    val animatedProgress by CultivationAnimation.animateProgress(progressRatio)
    val isFullyCompleted = totalCount > 0 && completedCount >= totalCount

    val resolvedSubtitle = subtitle ?: when {
        totalCount == 0 -> "Chưa có nhiệm vụ nào được thiết lập."
        isFullyCompleted -> "Ký chủ đã hoàn thành toàn bộ $completedCount/$totalCount nhiệm vụ hôm nay!"
        else -> "Ký chủ đã hoàn thành $completedCount/$totalCount nhiệm vụ."
    }

    CultivationCard(
        modifier = modifier.fillMaxWidth(),
        variant = if (isFullyCompleted) CultivationCardVariant.ELEVATED else CultivationCardVariant.STANDARD,
        borderColor = if (isFullyCompleted) CultivationTheme.colors.spiritTeal else null
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Biểu tượng tiến độ linh luân
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (isFullyCompleted) CultivationTheme.colors.spiritTealMuted
                            else CultivationTheme.colors.celestialGoldMuted
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$completedCount/$totalCount",
                        style = CultivationTheme.typography.labelRune,
                        color = if (isFullyCompleted) CultivationTheme.colors.spiritTeal
                        else CultivationTheme.colors.celestialGold
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Tiêu đề & phụ đề
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = CultivationTheme.typography.titleCard,
                        color = CultivationTheme.colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = resolvedSubtitle,
                        style = CultivationTheme.typography.caption,
                        color = CultivationTheme.colors.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Thanh Linh Lực (Spirit Energy Progress Bar)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(CultivationTheme.colors.surface)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    CultivationTheme.colors.spiritTeal,
                                    if (isFullyCompleted) CultivationTheme.colors.spiritTeal else CultivationTheme.colors.celestialGold
                                )
                            )
                        )
                )
            }
        }
    }
}
