package com.example.selfdisciplinepoc01.ui.vault

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.selfdisciplinepoc01.domain.model.DiscoveredApp
import com.example.selfdisciplinepoc01.domain.model.VaultApp

@Composable
fun VaultScreen(
    viewModel: VaultViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.bannerMessage) {
        uiState.bannerMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.onDismissBanner()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.onOpenAddDialog() },
                modifier = Modifier
                    .padding(end = 12.dp, bottom = 12.dp)
                    .size(48.dp),
                shape = CircleShape,
                containerColor = Color(0xFF00796B),
                contentColor = Color.White
            ) {
                Text(
                    text = "+",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Tiêu đề phân hệ tiên hiệp: Bảo Khố
                    item {
                        Column {
                            Text(
                                text = "Bảo Khố",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF004D40)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Nơi thu nạp và phong ấn các ứng dụng xao nhãng của Ký chủ",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Card Thống kê Bảo Khố
                    item {
                        VaultSummaryCard(
                            totalApps = uiState.vaultApps.size,
                            sealedApps = uiState.vaultApps.count { it.linkedTasksCount > 0 }
                        )
                    }

                    // Danh sách ứng dụng trong Bảo Khố
                    if (uiState.vaultApps.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFFE0F2F1)
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "Bảo Khố hiện đang trống rỗng",
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF004D40),
                                        fontSize = 15.sp
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Ký chủ hãy bấm nút '+' ở góc dưới bên phải để thu nạp ứng dụng muốn đưa vào chuỗi tự kỷ luật.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF00695C)
                                    )
                                }
                            }
                        }
                    } else {
                        item {
                            Text(
                                text = "Danh sách ứng dụng trong Bảo Khố (${uiState.vaultApps.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        items(
                            items = uiState.vaultApps,
                            key = { it.packageName }
                        ) { app ->
                            VaultAppItemCard(
                                app = app,
                                onRemove = { viewModel.onPromptRemoveApp(app) }
                            )
                        }
                    }
                }
            }

            // Dialog Thêm ứng dụng đã cài đặt
            if (uiState.isAddingApp) {
                AddDiscoveredAppDialog(
                    discoveredApps = uiState.filteredDiscoveredApps,
                    isLoading = uiState.isLoadingDiscovered,
                    searchQuery = uiState.searchQuery,
                    onSearchChange = { viewModel.onSearchQueryChanged(it) },
                    onAddApp = { viewModel.onAddApp(it) },
                    onDismiss = { viewModel.onDismissAddDialog() }
                )
            }

            // Dialog Xác nhận xóa ứng dụng khỏi Bảo Khố
            uiState.appPendingRemoval?.let { app ->
                AlertDialog(
                    onDismissRequest = { viewModel.onDismissRemoveDialog() },
                    title = {
                        Text(
                            text = "Gỡ khỏi Bảo Khố?",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Text(
                            text = "Ký chủ có chắc muốn gỡ ứng dụng [${app.appName}] khỏi Bảo Khố?\n\n" +
                                    "Thao tác này sẽ tự động hủy toàn bộ liên kết của ứng dụng này với các nhiệm vụ trong Nhiệm Vụ Đường. Bản thân các nhiệm vụ và lịch sử hoàn thành vẫn được giữ nguyên vẹn.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = { viewModel.onConfirmRemoveApp() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828))
                        ) {
                            Text("Xác nhận gỡ")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { viewModel.onDismissRemoveDialog() }) {
                            Text("Hủy")
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun VaultSummaryCard(
    totalApps: Int,
    sealedApps: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFE0F2F1)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Color(0xFF26A69A), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$totalApps",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = "Quy Mô Bảo Khố",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF004D40)
                )
                Text(
                    text = if (totalApps == 0) {
                        "Chưa thu nạp pháp bảo ứng dụng nào"
                    } else {
                        "Đã thu nạp $totalApps ứng dụng ($sealedApps ứng dụng có liên kết nhiệm vụ)"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF00695C)
                )
            }
        }
    }
}

/**
 * Hiển thị avatar và thông tin ứng dụng theo phong cách ô kho/túi đồ tiên hiệp.
 * Canonical Design V2 (Mục 6): TUYỆT ĐỐI KHÔNG HIỂN THỊ ICON Ổ KHÓA!
 * Trạng thái phong ấn biểu diễn bằng màu sắc.
 */
@Composable
fun VaultAppItemCard(
    app: VaultApp,
    onRemove: () -> Unit
) {
    val isLinked = app.linkedTasksCount > 0
    val containerColor = if (isLinked) Color(0xFFE8F5E9) else Color(0xFFFFFFFF)
    val avatarBgColor = if (isLinked) Color(0xFF2E7D32) else Color(0xFF00897B)
    val firstChar = app.appName.firstOrNull()?.uppercase() ?: "A"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar ô kho đồ
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(avatarBgColor, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = firstChar,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = app.appName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                // Badge trạng thái liên kết (màu sắc, không icon ổ khóa)
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isLinked) Color(0xFFC8E6C9) else Color(0xFFEEEEEE)
                ) {
                    Text(
                        text = if (isLinked) "Đang liên kết: ${app.linkedTasksCount} nhiệm vụ" else "Chưa liên kết nhiệm vụ",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isLinked) Color(0xFF1B5E20) else Color(0xFF616161),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            TextButton(
                onClick = onRemove,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(text = "Gỡ", color = Color(0xFFD32F2F), fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun AddDiscoveredAppDialog(
    discoveredApps: List<DiscoveredApp>,
    isLoading: Boolean,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onAddApp: (DiscoveredApp) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Thu Nạp Ứng Dụng Vào Bảo Khố",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    placeholder = { Text("Tìm theo tên hoặc package...", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (discoveredApps.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Không tìm thấy ứng dụng phù hợp",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(discoveredApps, key = { it.packageName }) { app ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = app.appName,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = app.packageName,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                if (app.isAlreadyInVault) {
                                    Text(
                                        text = "Đã có",
                                        color = Color(0xFF2E7D32),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                } else {
                                    OutlinedButton(
                                        onClick = { onAddApp(app) },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text(text = "+ Thêm", fontSize = 11.sp)
                                    }
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Đóng")
            }
        }
    )
}
