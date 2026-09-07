package com.example.selfdisciplinepoc01.ui.design.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.example.selfdisciplinepoc01.ui.design.theme.CultivationTheme

/**
 * Hộp thoại bảng thông cáo / ngọc giản phong cách Tiên Hiệp (CultivationDialog).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CultivationDialog(
    onDismissRequest: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    properties: DialogProperties = DialogProperties(usePlatformDefaultWidth = false),
    confirmButton: (@Composable () -> Unit)? = null,
    dismissButton: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    BasicAlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier.padding(24.dp),
        properties = properties
    ) {
        Surface(
            shape = CultivationTheme.shapes.dialog,
            color = CultivationTheme.colors.surface,
            border = BorderStroke(1.dp, CultivationTheme.colors.borderGlow),
            shadowElevation = CultivationTheme.elevation.dialog
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Tiêu đề ngọc giản
                Text(
                    text = title,
                    style = CultivationTheme.typography.titleCard,
                    color = CultivationTheme.colors.textPrimary
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Nội dung thân hộp thoại
                content()

                // Hàng nút điều khiển
                if (confirmButton != null || dismissButton != null) {
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (dismissButton != null) {
                            dismissButton()
                        }
                        if (dismissButton != null && confirmButton != null) {
                            Spacer(modifier = Modifier.width(10.dp))
                        }
                        if (confirmButton != null) {
                            confirmButton()
                        }
                    }
                }
            }
        }
    }
}
