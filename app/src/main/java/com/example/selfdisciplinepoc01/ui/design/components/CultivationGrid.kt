package com.example.selfdisciplinepoc01.ui.design.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.selfdisciplinepoc01.ui.design.theme.CultivationTheme

/**
 * Lưới tự co giãn phong cách Tiên Hiệp (CultivationGrid).
 * Kế thừa pattern lưới kho đồ / túi trữ vật (Inventory / Item Grid) từ NeoMud và eOr:
 * - Tự động tính toán số cột dựa trên kích thước màn hình thiết bị (adaptive columns).
 * - Đảm bảo khoảng cách đều đặn (consistent spacing) và lướt mượt mà.
 */
@Composable
fun CultivationGrid(
    modifier: Modifier = Modifier,
    minCellSize: Dp = 150.dp,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    gap: Dp = CultivationTheme.spacing.gridGap,
    content: LazyGridScope.() -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = minCellSize),
        modifier = modifier,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalArrangement = Arrangement.spacedBy(gap),
        content = content
    )
}
