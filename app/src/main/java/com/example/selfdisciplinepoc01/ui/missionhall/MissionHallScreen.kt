package com.example.selfdisciplinepoc01.ui.missionhall

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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import com.example.selfdisciplinepoc01.domain.canonical.usecase.MissionHallTaskItem
import com.example.selfdisciplinepoc01.domain.canonical.vault.CanonicalVaultApp
import com.example.selfdisciplinepoc01.ui.design.components.AppItemSelectableRow
import com.example.selfdisciplinepoc01.ui.design.components.CultivationButton
import com.example.selfdisciplinepoc01.ui.design.components.CultivationButtonVariant
import com.example.selfdisciplinepoc01.ui.design.components.CultivationCard
import com.example.selfdisciplinepoc01.ui.design.components.CultivationCardVariant
import com.example.selfdisciplinepoc01.ui.design.components.CultivationDialog
import com.example.selfdisciplinepoc01.ui.design.components.ProgressCard
import com.example.selfdisciplinepoc01.ui.design.components.TaskCard
import com.example.selfdisciplinepoc01.ui.design.theme.CultivationTheme
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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
                    // Header
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

                    // Progress Card
                    item {
                        ProgressCard(
                            completedCount = uiState.completedCount,
                            totalCount = uiState.totalActiveCount
                        )
                    }

                    // Active Current Task Card (Sequential flow)
                    item {
                        CurrentTaskHighlight(
                            currentTask = uiState.currentTask,
                            isAllCompleted = uiState.isAllCompleted,
                            isEmpty = uiState.isEmpty,
                            onRename = { taskItem -> viewModel.onOpenRenameDialog(taskItem) },
                            onDelete = { taskItem -> viewModel.onPromptDeleteTask(taskItem) },
                            onOpenLinkage = { taskItem -> viewModel.onOpenLinkageDialog(taskItem) },
                            onCancelPending = { taskId -> viewModel.onCancelPendingRewards(taskId) }
                        )
                    }

                    // Incomplete Tasks Section (Các nhiệm vụ tiếp theo trong chuỗi)
                    val remainingIncomplete = uiState.incompleteTasks.filter {
                        it.task.id != uiState.currentTask?.task?.id
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
                            key = { it.task.id }
                        ) { taskItem ->
                            val formatter = remember {
                                DateTimeFormatter.ofPattern("HH:mm - dd/MM", Locale.getDefault())
                                    .withZone(ZoneId.systemDefault())
                            }
                            val formattedDate = remember(taskItem.task.createdAt) {
                                formatter.format(taskItem.task.createdAt)
                            }
                            TaskCard(
                                taskName = taskItem.task.title,
                                isCurrentActive = false,
                                isCompleted = false,
                                createdAtText = "Tạo lúc: $formattedDate",
                                linkedApps = taskItem.linkedApps,
                                pendingNextCycleApps = taskItem.pendingNextCycleRewardPackageNames,
                                onRenameClick = { viewModel.onOpenRenameDialog(taskItem) },
                                onDeleteClick = { viewModel.onPromptDeleteTask(taskItem) },
                                onLinkAppsClick = { viewModel.onOpenLinkageDialog(taskItem) },
                                onCancelPendingClick = { viewModel.onCancelPendingRewards(taskItem.task.id) }
                            )
                        }
                    }

                    // Completed Tasks Section
                    if (uiState.completedTasks.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(4.dp))
                            HorizontalDivider(color = CultivationTheme.colors.borderSubtle.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Đã hoàn thành hôm nay (${uiState.completedTasks.size})",
                                style = CultivationTheme.typography.titleCard,
                                color = CultivationTheme.colors.statusSuccess
                            )
                        }

                        items(
                            items = uiState.completedTasks,
                            key = { "completed_${it.task.id}" }
                        ) { taskItem ->
                            TaskCard(
                                taskName = taskItem.task.title,
                                isCurrentActive = false,
                                isCompleted = true,
                                linkedApps = taskItem.linkedApps,
                                pendingNextCycleApps = taskItem.pendingNextCycleRewardPackageNames,
                                onUndoClick = { viewModel.onPromptUndoTask(taskItem) },
                                onCancelPendingClick = { viewModel.onCancelPendingRewards(taskItem.task.id) }
                            )
                        }
                    }
                }
            }

            // Dialog: Thêm Nhiệm Vụ Mới
            if (uiState.isAddingTask) {
                AddTaskMinimalDialog(
                    name = uiState.currentInputName,
                    error = uiState.inputError,
                    onNameChange = { viewModel.onInputNameChanged(it) },
                    onConfirm = { viewModel.onConfirmAddTask() },
                    onDismiss = { viewModel.onDismissAddDialog() }
                )
            }

            // Dialog: Đổi Tên Nhiệm Vụ (Instant Update, Không Confirmation Dialog per SSOT)
            uiState.taskBeingRenamed?.let { taskItem ->
                RenameTaskDialog(
                    initialName = uiState.renameInputName,
                    onNameChange = { viewModel.onRenameInputChanged(it) },
                    onConfirm = { viewModel.onConfirmRename() },
                    onDismiss = { viewModel.onDismissRenameDialog() }
                )
            }

            // Dialog: Xác Nhận Hoàn Tác (Confirmation Dialog Required per SSOT)
            uiState.taskPendingUndo?.let { taskItem ->
                CultivationDialog(
                    onDismissRequest = { viewModel.onDismissUndoDialog() },
                    title = "Xác Nhận Hoàn Tác",
                    confirmButton = {
                        CultivationButton(
                            text = "Xác nhận",
                            onClick = { viewModel.onConfirmUndoTask() },
                            variant = CultivationButtonVariant.PRIMARY
                        )
                    },
                    dismissButton = {
                        CultivationButton(
                            text = "Hủy",
                            onClick = { viewModel.onDismissUndoDialog() },
                            variant = CultivationButtonVariant.GHOST
                        )
                    }
                ) {
                    Column {
                        Text(
                            text = "Ký chủ có chắc chắn muốn hoàn tác nhiệm vụ [${taskItem.task.title}]?",
                            style = CultivationTheme.typography.bodyText,
                            color = CultivationTheme.colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Lưu ý: Các pháp bảo liên quan sẽ được tái lập trạng thái phong ấn ngay tức thì.",
                            style = CultivationTheme.typography.caption,
                            color = CultivationTheme.colors.statusDanger
                        )
                    }
                }
            }

            // Dialog: Xác Nhận Xóa Nhiệm Vụ (Confirmation Dialog Required per SSOT)
            uiState.taskPendingDelete?.let { taskItem ->
                CultivationDialog(
                    onDismissRequest = { viewModel.onDismissDeleteDialog() },
                    title = "Xác Nhận Xóa Nhiệm Vụ",
                    confirmButton = {
                        CultivationButton(
                            text = "Xác nhận xóa",
                            onClick = { viewModel.onConfirmDeleteTask() },
                            variant = CultivationButtonVariant.PRIMARY
                        )
                    },
                    dismissButton = {
                        CultivationButton(
                            text = "Hủy",
                            onClick = { viewModel.onDismissDeleteDialog() },
                            variant = CultivationButtonVariant.GHOST
                        )
                    }
                ) {
                    Column {
                        Text(
                            text = "Ký chủ có chắc muốn xóa nhiệm vụ [${taskItem.task.title}] khỏi chuỗi tu luyện?",
                            style = CultivationTheme.typography.bodyText,
                            color = CultivationTheme.colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Lịch sử công đức hoàn thành trong các chu kỳ trước vẫn được lưu giữ trọn vẹn và không bị xóa.",
                            style = CultivationTheme.typography.caption,
                            color = CultivationTheme.colors.celestialGold
                        )
                    }
                }
            }

            // Dialog: Liên Kết Pháp Bảo & Cấu Hình Chờ Chu Kỳ Tiếp Theo
            uiState.taskSelectedForLinkage?.let { taskItem ->
                TaskLinkageDialog(
                    taskTitle = taskItem.task.title,
                    availableVaultApps = uiState.availableVaultApps,
                    selectedPackageNames = uiState.selectedPackageNames,
                    isPendingNextCycle = uiState.isPendingNextCycleSelected,
                    onToggleApp = { viewModel.onToggleAppSelection(it) },
                    onTogglePending = { viewModel.onTogglePendingNextCycle(it) },
                    onConfirm = { viewModel.onSaveTaskLinkage() },
                    onDismiss = { viewModel.onDismissLinkageDialog() }
                )
            }
        }
    }
}

@Composable
fun CurrentTaskHighlight(
    currentTask: MissionHallTaskItem?,
    isAllCompleted: Boolean,
    isEmpty: Boolean,
    onRename: (MissionHallTaskItem) -> Unit,
    onDelete: (MissionHallTaskItem) -> Unit,
    onOpenLinkage: (MissionHallTaskItem) -> Unit,
    onCancelPending: (String) -> Unit
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
                        text = "Ký chủ đã xuất sắc hoàn thành toàn bộ nhiệm vụ của chu kỳ này! Chu kỳ tiếp theo sẽ tái lập tại mốc 04:00 sáng mai.",
                        style = CultivationTheme.typography.bodyText,
                        color = CultivationTheme.colors.textPrimary
                    )
                }
            }
        }
        currentTask != null -> {
            TaskCard(
                taskName = currentTask.task.title,
                isCurrentActive = true,
                isCompleted = false,
                linkedApps = currentTask.linkedApps,
                pendingNextCycleApps = currentTask.pendingNextCycleRewardPackageNames,
                onRenameClick = { onRename(currentTask) },
                onDeleteClick = { onDelete(currentTask) },
                onLinkAppsClick = { onOpenLinkage(currentTask) },
                onCancelPendingClick = { onCancelPending(currentTask.task.id) }
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
                text = "Nhập danh hiệu nhiệm vụ tự kỷ luật của Ký chủ:",
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
 * Dialog Đổi tên nhiệm vụ tức thì: Không có bước confirmation thừa theo SSOT.
 */
@Composable
fun RenameTaskDialog(
    initialName: String,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    CultivationDialog(
        onDismissRequest = onDismiss,
        title = "Đổi Danh Hiệu Nhiệm Vụ",
        confirmButton = {
            CultivationButton(
                text = "Lưu thay đổi",
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
                text = "Nhập danh hiệu mới (hiệu lực tức thì):",
                style = CultivationTheme.typography.bodyText,
                color = CultivationTheme.colors.textSecondary
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = initialName,
                onValueChange = onNameChange,
                label = {
                    Text(
                        "Danh hiệu nhiệm vụ",
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
        }
    }
}

/**
 * Dialog chọn pháp bảo liên kết nhiều-nhiều và tùy chọn Chờ chu kỳ tiếp theo (04:00 AM).
 */
@Composable
fun TaskLinkageDialog(
    taskTitle: String,
    availableVaultApps: List<CanonicalVaultApp>,
    selectedPackageNames: Set<String>,
    isPendingNextCycle: Boolean,
    onToggleApp: (String) -> Unit,
    onTogglePending: (Boolean) -> Unit,
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
                .height(420.dp)
        ) {
            Text(
                text = "Nhiệm vụ: $taskTitle",
                style = CultivationTheme.typography.titleCard.copy(fontSize = 14.sp),
                color = CultivationTheme.colors.celestialGold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Chọn các pháp bảo từ Bảo Khố mà Ký chủ muốn liên kết:",
                style = CultivationTheme.typography.caption,
                color = CultivationTheme.colors.textSecondary
            )

            Spacer(modifier = Modifier.height(10.dp))

            if (availableVaultApps.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
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
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(availableVaultApps, key = { it.packageName }) { app ->
                        val isSelected = selectedPackageNames.contains(app.packageName)
                        AppItemSelectableRow(
                            packageName = app.packageName,
                            appName = app.displayName,
                            isSelected = isSelected,
                            onToggle = { onToggleApp(app.packageName) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = CultivationTheme.colors.borderSubtle.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(8.dp))

            // Tùy chọn Pending Next-Cycle (chờ 04:00 AM) theo SSOT Phần V.4
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onTogglePending(!isPendingNextCycle) }
                    .padding(vertical = 4.dp)
            ) {
                Checkbox(
                    checked = isPendingNextCycle,
                    onCheckedChange = { onTogglePending(it) },
                    colors = CheckboxDefaults.colors(
                        checkedColor = CultivationTheme.colors.celestialGold,
                        uncheckedColor = CultivationTheme.colors.borderSubtle,
                        checkmarkColor = CultivationTheme.colors.background
                    )
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text(
                        text = "Áp dụng từ chu kỳ tiếp theo (04:00 AM)",
                        style = CultivationTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
                        color = CultivationTheme.colors.textPrimary
                    )
                    Text(
                        text = "Không làm thay đổi trạng thái khóa của chu kỳ hiện tại",
                        style = CultivationTheme.typography.caption.copy(fontSize = 10.sp),
                        color = CultivationTheme.colors.textMuted
                    )
                }
            }
        }
    }
}
