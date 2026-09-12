package com.example.selfdisciplinepoc01.ui.vault

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.selfdisciplinepoc01.domain.canonical.usecase.CanonicalVaultAppItem
import com.example.selfdisciplinepoc01.domain.model.DiscoveredApp
import com.example.selfdisciplinepoc01.ui.design.components.AppItemGridCard
import com.example.selfdisciplinepoc01.ui.design.components.AsyncAppIcon
import com.example.selfdisciplinepoc01.ui.design.components.CultivationButton
import com.example.selfdisciplinepoc01.ui.design.components.CultivationButtonVariant
import com.example.selfdisciplinepoc01.ui.design.components.CultivationCard
import com.example.selfdisciplinepoc01.ui.design.components.CultivationCardVariant
import com.example.selfdisciplinepoc01.ui.design.components.CultivationDialog
import com.example.selfdisciplinepoc01.ui.design.components.CultivationGrid
import com.example.selfdisciplinepoc01.ui.design.theme.CultivationTheme

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
        containerColor = CultivationTheme.colors.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            // Nút Thêm ứng dụng góc dưới phải phong cách Tiên Hiệp
            FloatingActionButton(
                onClick = { viewModel.onOpenAddDialog() },
                modifier = Modifier
                    .padding(end = 12.dp, bottom = 12.dp)
                    .size(CultivationTheme.spacing.touchTargetMin),
                shape = CircleShape,
                containerColor = CultivationTheme.colors.spiritTeal,
                contentColor = CultivationTheme.colors.textOnPrimary
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
                    modifier = Modifier.align(Alignment.Center),
                    color = CultivationTheme.colors.celestialGold
                )
            } else {
                CultivationGrid(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    minCellSize = 140.dp,
                    contentPadding = PaddingValues(top = 16.dp, bottom = 88.dp),
                    gap = CultivationTheme.spacing.gridGap
                ) {
                    // Header Bảo Khố (Chiếm toàn bộ chiều ngang grid)
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column {
                            Text(
                                text = "Bảo Khố",
                                style = CultivationTheme.typography.headingHero,
                                color = CultivationTheme.colors.celestialGold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Nơi thu nạp và phong ấn các ứng dụng xao nhãng của Ký chủ",
                                style = CultivationTheme.typography.subtitle,
                                color = CultivationTheme.colors.textSecondary
                            )
                        }
                    }

                    // Card Thống kê quy mô Bảo Khố (Full span)
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        val unlockedCount = uiState.appEnforcementMap.values.count {
                            it.finalAction == com.example.selfdisciplinepoc01.domain.enforcement.EnforcementAction.ALLOW
                        }
                        VaultSummaryCard(
                            totalApps = uiState.vaultApps.size,
                            sealedApps = uiState.vaultApps.count { it.linkedTasksCount > 0 },
                            unlockedApps = unlockedCount
                        )
                    }

                    // Trạng thái Bảo Khố trống hoặc Danh sách ứng dụng
                    if (uiState.vaultApps.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            CultivationCard(
                                modifier = Modifier.fillMaxWidth(),
                                variant = CultivationCardVariant.STANDARD,
                                contentPadding = 24.dp
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "Bảo Khố hiện đang trống rỗng",
                                        style = CultivationTheme.typography.titleCard,
                                        color = CultivationTheme.colors.celestialGold
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Ký chủ hãy bấm nút '+' ở góc dưới bên phải để thu nạp ứng dụng muốn đưa vào chuỗi tự kỷ luật.",
                                        style = CultivationTheme.typography.bodyText,
                                        color = CultivationTheme.colors.textSecondary,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    } else {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                text = "Danh sách ứng dụng trong Bảo Khố (${uiState.vaultApps.size})",
                                style = CultivationTheme.typography.titleCard,
                                color = CultivationTheme.colors.textPrimary
                            )
                        }

                        // Danh sách các ứng dụng trong grid túi đồ
                        items(
                            items = uiState.vaultApps,
                            key = { it.packageName }
                        ) { app ->
                            AppItemGridCard(
                                packageName = app.packageName,
                                appName = app.appName,
                                linkedTasksCount = app.linkedTasksCount,
                                enforcement = uiState.appEnforcementMap[app.packageName],
                                onRemoveClick = { viewModel.onPromptRemoveApp(app) }
                            )
                        }
                    }
                }
            }

            // Dialog Thu nạp ứng dụng đã cài đặt
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
                CultivationDialog(
                    onDismissRequest = { viewModel.onDismissRemoveDialog() },
                    title = "Gỡ khỏi Bảo Khố?",
                    confirmButton = {
                        CultivationButton(
                            text = "Xác nhận gỡ",
                            onClick = { viewModel.onConfirmRemoveApp() },
                            variant = CultivationButtonVariant.DANGER
                        )
                    },
                    dismissButton = {
                        CultivationButton(
                            text = "Hủy",
                            onClick = { viewModel.onDismissRemoveDialog() },
                            variant = CultivationButtonVariant.GHOST
                        )
                    }
                ) {
                    Text(
                        text = "Ký chủ có chắc muốn gỡ ứng dụng [${app.appName}] khỏi Bảo Khố?\n\n" +
                                "Thao tác này sẽ tự động hủy toàn bộ liên kết của ứng dụng này với các nhiệm vụ trong Nhiệm Vụ Đường. Bản thân các nhiệm vụ và lịch sử hoàn thành vẫn được giữ nguyên vẹn.",
                        style = CultivationTheme.typography.bodyText,
                        color = CultivationTheme.colors.textSecondary
                    )
                }
            }
        }
    }
}

@Composable
fun VaultSummaryCard(
    totalApps: Int,
    sealedApps: Int,
    unlockedApps: Int = 0
) {
    CultivationCard(
        modifier = Modifier.fillMaxWidth(),
        variant = CultivationCardVariant.STANDARD,
        contentPadding = 16.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(CultivationTheme.colors.spiritTealMuted, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$totalApps",
                    color = CultivationTheme.colors.spiritTeal,
                    style = CultivationTheme.typography.statNumber.copy(fontSize = 18.sp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = "Quy Mô Bảo Khố",
                    style = CultivationTheme.typography.titleCard,
                    color = CultivationTheme.colors.textPrimary
                )
                Text(
                    text = if (totalApps == 0) {
                        "Chưa thu nạp pháp bảo ứng dụng nào"
                    } else if (unlockedApps > 0) {
                        "Đã thu nạp $totalApps ứng dụng ($sealedApps có liên kết, $unlockedApps đã giải phong ấn)"
                    } else {
                        "Đã thu nạp $totalApps ứng dụng ($sealedApps ứng dụng có liên kết nhiệm vụ)"
                    },
                    style = CultivationTheme.typography.bodyText,
                    color = CultivationTheme.colors.textSecondary
                )
            }
        }
    }
}

/**
 * Hiển thị thẻ ứng dụng trong Bảo Khố dạng dòng đơn giản (dùng khi fallback).
 * Canonical Design V2 (Mục 6): TUYỆT ĐỐI KHÔNG HIỂN THỊ ICON Ổ KHÓA!
 */
@Composable
fun VaultAppItemCard(
    app: CanonicalVaultAppItem,
    onRemove: () -> Unit
) {
    AppItemGridCard(
        packageName = app.packageName,
        appName = app.appName,
        linkedTasksCount = app.linkedTasksCount,
        onRemoveClick = onRemove
    )
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
    CultivationDialog(
        onDismissRequest = onDismiss,
        title = "Thu Nạp Ứng Dụng Vào Bảo Khố",
        confirmButton = {
            CultivationButton(
                text = "Đóng",
                onClick = onDismiss,
                variant = CultivationButtonVariant.GHOST
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = {
                    Text(
                        "Tìm theo tên hoặc package...",
                        style = CultivationTheme.typography.caption,
                        color = CultivationTheme.colors.textMuted
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = CultivationTheme.shapes.button,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CultivationTheme.colors.celestialGold,
                    unfocusedBorderColor = CultivationTheme.colors.borderSubtle,
                    focusedTextColor = CultivationTheme.colors.textPrimary,
                    unfocusedTextColor = CultivationTheme.colors.textPrimary,
                    cursorColor = CultivationTheme.colors.celestialGold
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = CultivationTheme.colors.celestialGold)
                }
            } else if (discoveredApps.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Không tìm thấy ứng dụng phù hợp",
                        style = CultivationTheme.typography.bodyText,
                        color = CultivationTheme.colors.textMuted
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
                            // Icon thật của app Android
                            AsyncAppIcon(
                                packageName = app.packageName,
                                fallbackAppName = app.appName,
                                size = 38.dp
                            )

                            Spacer(modifier = Modifier.width(10.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = app.appName,
                                    style = CultivationTheme.typography.titleCard.copy(fontSize = 14.sp),
                                    color = CultivationTheme.colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = app.packageName,
                                    style = CultivationTheme.typography.caption,
                                    color = CultivationTheme.colors.textMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            if (app.isAlreadyInVault) {
                                Text(
                                    text = "Đã có",
                                    color = CultivationTheme.colors.spiritTeal,
                                    style = CultivationTheme.typography.caption.copy(fontWeight = FontWeight.Bold)
                                )
                            } else {
                                CultivationButton(
                                    text = "+ Thêm",
                                    onClick = { onAddApp(app) },
                                    variant = CultivationButtonVariant.SECONDARY,
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(34.dp)
                                )
                            }
                        }
                        HorizontalDivider(
                            color = CultivationTheme.colors.borderSubtle.copy(alpha = 0.5f),
                            thickness = 0.5.dp
                        )
                    }
                }
            }
        }
    }
}

