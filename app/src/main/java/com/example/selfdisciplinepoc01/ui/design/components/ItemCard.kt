package com.example.selfdisciplinepoc01.ui.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.selfdisciplinepoc01.ui.design.theme.CultivationTheme

/**
 * Ô vật phẩm / Phần thưởng tu tiên (ItemCard / RewardCard).
 * Kế thừa pattern ô túi đồ (Inventory Slot) từ NeoMud và shop item từ IdleFantasy:
 * - Dạng ô vuông hoặc chữ nhật đứng nhỏ gọn.
 * - Hiển thị biểu tượng/chữ linh phù đại diện.
 * - Nhãn số lượng hoặc cấp bậc.
 */
@Composable
fun ItemCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    quantity: Int? = null,
    onClick: (() -> Unit)? = null,
    iconContent: (@Composable () -> Unit)? = null
) {
    CultivationCard(
        modifier = modifier,
        variant = CultivationCardVariant.STANDARD,
        shape = CultivationTheme.shapes.itemCell,
        onClick = onClick,
        contentPadding = 8.dp
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CultivationTheme.shapes.avatar)
                    .background(CultivationTheme.colors.cardBackgroundElevated),
                contentAlignment = Alignment.Center
            ) {
                if (iconContent != null) {
                    iconContent()
                } else {
                    Text(
                        text = title.firstOrNull()?.uppercase() ?: "宝",
                        style = CultivationTheme.typography.titleCard.copy(
                            fontWeight = FontWeight.Bold,
                            color = CultivationTheme.colors.spiritTeal
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = title,
                style = CultivationTheme.typography.caption.copy(fontWeight = FontWeight.Medium),
                color = CultivationTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )

            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = CultivationTheme.typography.caption.copy(fontSize = 10.sp),
                    color = CultivationTheme.colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (quantity != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "x$quantity",
                    style = CultivationTheme.typography.labelRune.copy(fontSize = 10.sp),
                    color = CultivationTheme.colors.celestialGold
                )
            }
        }
    }
}
