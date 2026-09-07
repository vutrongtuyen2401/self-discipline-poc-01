package com.example.selfdisciplinepoc01.ui.missionhall

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
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.selfdisciplinepoc01.domain.model.Task
import com.example.selfdisciplinepoc01.domain.model.VaultApp
import com.example.selfdisciplinepoc01.ui.design.components.AppItemSelectableRow
import com.example.selfdisciplinepoc01.ui.design.components.CultivationButton
import com.example.selfdisciplinepoc01.ui.design.components.CultivationButtonVariant
import com.example.selfdisciplinepoc01.ui.design.components.CultivationCard
import com.example.selfdisciplinepoc01.ui.design.components.CultivationCardVariant
import com.example.selfdisciplinepoc01.ui.design.components.CultivationDialog
import com.example.selfdisciplinepoc01.ui.design.components.ProgressCard
import com.example.selfdisciplinepoc01.ui.design.components.TaskCard
import com.example.selfdisciplinepoc01.ui.design.theme.CultivationTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MissionHallScreen(
    viewModel: MissionHallViewModel,
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
            // Canonical Design V2 (Section 5): "nút thêm nhiệm vụ là nút nhỏ ở góc dưới bên phải"
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
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Header: Tiên hiệp xưng hô "Ký chủ"
                    item {
                        Column {
                            Text(
                                text = "Nhiệm Vụ Đường",
                                style = CultivationTheme.typography.headingHero,
                                color = CultivationTheme.colors.celestialGold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Khu vực tiếp nhận và hoàn thành chuỗi tự kỷ luật của Ký chủ",
                                style = CultivationTheme.typography.subtitle,
                                color = CultivationTheme.colors.textSecondary
                            )
                        }
                    }

                    // Progress Card phong cách Tiên Hiệp
                    item {
                        ProgressCard(
                            completedCount = uiState.chain.completedCountToday,
                            totalCount = uiState.chain.totalActiveTasks
                        )
                    }

                    // Active Current Task Card (Sequential flow)
                    item {
                        CurrentTaskHighlight(
                            currentTask = uiState.chain.currentTask,
                            isAllCompleted = uiState.chain.isAllCompleted,
                            isEmpty = uiState.chain.isEmpty,
                            linkedApps = uiState.chain.currentTask?.let { uiState.taskLinkedAppsMap[it.id] } ?: emptyList(),
                            onComplete = { taskId -> viewModel.onCompleteTask(taskId) },
                            onOpenLinkage = { task -> viewModel.onOpenLinkageDialog(task) }
                        )
                    }

                    // Incomplete Tasks Section (Các nhiệm vụ còn lại trong chuỗi)
                    val remainingIncomplete = uiState.chain.incompleteTasks.filter {
                        it.id != uiState.chain.currentTask?.id
                    }
                    if (remainingIncomplete.isNotEmpty()) {
                        item {
                            Text(
                                text = "Nhiệm vụ tiếp theo trong chuỗi (${remainingIncomplete.size})",
                                style = CultivationTheme.typography.titleCard,
                                color = CultivationTheme.colors.textPrimary
                            )
                        }

                        items(
                            items = remainingIncomplete,
                            key = { it.id }
                        ) { task ->
                            val linkedApps = uiState.taskLinkedAppsMap[task.id] ?: emptyList()
                            val formattedDate = remember(task.createdAtWallMillis) {
                                SimpleDateFormat("HH:mm - dd/MM", Locale.getDefault()).format(Date(task.createdAtWallMillis))
                            }
                            TaskCard(
                                taskName = task.name,
                                isCurrentActive = false,
                                isCompleted = false,
                                createdAtText = "Tạo lúc: $formattedDate",
                                linkedApps = linkedApps,
                                onCompleteClick = { viewModel.onCompleteTask(task.id) },
                                onDeleteClick = { viewModel.onArchiveTask(task.id) },
                                onLinkAppsClick = { viewModel.onOpenLinkageDialog(task) }
                            )
                        }
                    }

                    // Completed Tasks Section
                    if (uiState.chain.completedTasksToday.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(4.dp))
                            HorizontalDivider(color = CultivationTheme.colors.borderSubtle.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Đã hoàn thành hôm nay (${uiState.chain.completedTasksToday.size})",
                                style = CultivationTheme.typography.titleCard,
                                color = CultivationTheme.colors.statusSuccess
                            )
                        }

                        items(
                            items = uiState.chain.completedTasksToday,
                            key = { "completed_${it.id}" }
                        ) { task ->
                            val linkedApps = uiState.taskLinkedAppsMap[task.id] ?: emptyList()
                            TaskCard(
                                taskName = task.name,
                                isCurrentActive = false,
                                isCompleted = true,
                                linkedApps = linkedApps
                            )
                        }
                    }
                }
            }

            // Minimal Task Creation Dialog (Canonical Design V2 Section 5)
            if (uiState.isAddingTask) {
                AddTaskMinimalDialog(
                    name = uiState.currentInputName,
                    error = uiState.inputError,
                    onNameChange = { viewModel.onInputNameChanged(it) },
                    onConfirm = { viewModel.onConfirmAddTask() },
                    onDismiss = { viewModel.onDismissAddDialog() }
                )
            }

            // Task ↔ App Linkage Dialog (Canonical Design V2 Section 5 & 6)
            uiState.taskSelectedForLinkage?.let { task ->
                TaskLinkageDialog(
                    task = task,
                    availableVaultApps = uiState.availableVaultApps,
                    selectedPackageNames = uiState.selectedPackageNames,
                    onToggleApp = { viewModel.onToggleAppSelection(it) },
                    onConfirm = { viewModel.onSaveTaskLinkage() },
                    onDismiss = { viewModel.onDismissLinkageDialog() }
                )
            }
        }
    }
}

@Composable
fun CurrentTaskHighlight(
    currentTask: Task?,
    isAllCompleted: Boolean,
    isEmpty: Boolean,
    linkedApps: List<VaultApp>,
    onComplete: (Long) -> Unit,
    onOpenLinkage: (Task) -> Unit
) {
    when {
        isEmpty -> {
            CultivationCard(
                modifier = Modifier.fillMaxWidth(),
                variant = CultivationCardVariant.STANDARD,
                contentPadding = 20.dp
            ) {
                Text(
                    text = "Hiện tại chuỗi đang rỗng. Bấm nút '+' ở góc dưới bên phải để bắt đầu tu luyện.",
                    style = CultivationTheme.typography.bodyText,
                    color = CultivationTheme.colors.textSecondary
                )
            }
        }
        isAllCompleted -> {
            CultivationCard(
                modifier = Modifier.fillMaxWidth(),
                variant = CultivationCardVariant.ELEVATED,
                borderColor = CultivationTheme.colors.statusSuccess,
                contentPadding = 20.dp
            ) {
                Column {
                    Text(
                        text = "VIÊN MÃN CÔNG ĐỨC",
                        style = CultivationTheme.typography.labelRune,
                        color = CultivationTheme.colors.statusSuccess
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Ký chủ đã xuất sắc hoàn thành toàn bộ nhiệm vụ của chu kỳ này! Chuỗi sẽ được khởi tạo lại tại 04:00 sáng mai.",
                        style = CultivationTheme.typography.bodyText,
                        color = CultivationTheme.colors.textPrimary
                    )
                }
            }
        }
        currentTask != null -> {
            TaskCard(
                taskName = currentTask.name,
                isCurrentActive = true,
                isCompleted = false,
                linkedApps = linkedApps,
                onCompleteClick = { onComplete(currentTask.id) },
                onLinkAppsClick = { onOpenLinkage(currentTask) }
            )
        }
    }
}

@Composable
fun AddTaskMinimalDialog(
    name: String,
    error: String?,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    CultivationDialog(
        onDismissRequest = onDismiss,
        title = "Thêm Nhiệm Vụ Mới",
        confirmButton = {
            CultivationButton(
                text = "Xác nhận",
                onClick = onConfirm,
                variant = CultivationButtonVariant.PRIMARY
            )
        },
        dismissButton = {
            CultivationButton(
                text = "Hủy",
                onClick = onDismiss,
                variant = CultivationButtonVariant.GHOST
            )
        }
    ) {
        Column {
            Text(
                text = "Nhập tên nhiệm vụ tự kỷ luật của Ký chủ:",
                style = CultivationTheme.typography.bodyText,
                color = CultivationTheme.colors.textSecondary
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                label = {
                    Text(
                        "Tên nhiệm vụ",
                        style = CultivationTheme.typography.caption,
                        color = CultivationTheme.colors.textMuted
                    )
                },
                isError = error != null,
                supportingText = {
                    if (error != null) {
                        Text(
                            text = error,
                            style = CultivationTheme.typography.caption,
                            color = CultivationTheme.colors.statusDanger
                        )
                    }
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
        }
    }
}

/**
 * Dialog chọn ứng dụng từ Bảo Khố để liên kết với Task.
 * Canonical Design V2 (Mục 5 & 6):
 * - Chỉ cho phép chọn từ các app hiện có trong Bảo Khố.
 * - Cho phép chọn nhiều app.
 * - Thuật ngữ trung tính: "Ứng dụng liên kết" (không hiển thị 2/3 hay tỷ lệ mở khóa).
 */
@Composable
fun TaskLinkageDialog(
    task: Task,
    availableVaultApps: List<VaultApp>,
    selectedPackageNames: Set<String>,
    onToggleApp: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    CultivationDialog(
        onDismissRequest = onDismiss,
        title = "Ứng Dụng Liên Kết",
        confirmButton = {
            CultivationButton(
                text = "Lưu liên kết",
                onClick = onConfirm,
                variant = CultivationButtonVariant.PRIMARY
            )
        },
        dismissButton = {
            CultivationButton(
                text = "Hủy",
                onClick = onDismiss,
                variant = CultivationButtonVariant.GHOST
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(380.dp)
        ) {
            Text(
                text = "Nhiệm vụ: ${task.name}",
                style = CultivationTheme.typography.titleCard.copy(fontSize = 14.sp),
                color = CultivationTheme.colors.celestialGold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Chọn các pháp bảo ứng dụng từ Bảo Khố mà Ký chủ muốn liên kết:",
                style = CultivationTheme.typography.caption,
                color = CultivationTheme.colors.textSecondary
            )

            Spacer(modifier = Modifier.height(10.dp))

            if (availableVaultApps.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Bảo Khố hiện đang trống rỗng.\nKý chủ hãy thêm ứng dụng vào Bảo Khố trước!",
                        style = CultivationTheme.typography.bodyText,
                        color = CultivationTheme.colors.statusDanger,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(availableVaultApps, key = { it.packageName }) { app ->
                        val isSelected = selectedPackageNames.contains(app.packageName)
                        AppItemSelectableRow(
                            packageName = app.packageName,
                            appName = app.appName,
                            isSelected = isSelected,
                            onToggle = { onToggleApp(app.packageName) }
                        )
                    }
                }
            }
        }
    }
}

