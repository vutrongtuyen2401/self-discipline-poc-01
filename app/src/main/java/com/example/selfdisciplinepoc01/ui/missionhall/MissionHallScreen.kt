package com.example.selfdisciplinepoc01.ui.missionhall

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.Checkbox
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
import com.example.selfdisciplinepoc01.domain.model.Task
import com.example.selfdisciplinepoc01.domain.model.VaultApp
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            // Canonical Design V2 (Section 5): "nút thêm nhiệm vụ là nút nhỏ ở góc dưới bên phải"
            FloatingActionButton(
                onClick = { viewModel.onOpenAddDialog() },
                modifier = Modifier
                    .padding(end = 12.dp, bottom = 12.dp)
                    .size(48.dp),
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
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
                    // Header: Tiên hiệp xưng hô "Ký chủ"
                    item {
                        Column {
                            Text(
                                text = "Nhiệm Vụ Đường",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Khu vực tiếp nhận và hoàn thành chuỗi tự kỷ luật của Ký chủ",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Progress Card
                    item {
                        MissionProgressCard(
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

                    // Incomplete Tasks Section
                    if (uiState.chain.incompleteTasks.isNotEmpty()) {
                        item {
                            Text(
                                text = "Danh sách nhiệm vụ cần làm (${uiState.chain.incompleteTasks.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        items(
                            items = uiState.chain.incompleteTasks,
                            key = { it.id }
                        ) { task ->
                            val isCurrent = task.id == uiState.chain.currentTask?.id
                            val linkedApps = uiState.taskLinkedAppsMap[task.id] ?: emptyList()
                            TaskItemCard(
                                task = task,
                                isCurrent = isCurrent,
                                linkedApps = linkedApps,
                                onComplete = { viewModel.onCompleteTask(task.id) },
                                onArchive = { viewModel.onArchiveTask(task.id) },
                                onOpenLinkage = { viewModel.onOpenLinkageDialog(task) }
                            )
                        }
                    }

                    // Completed Tasks Section
                    if (uiState.chain.completedTasksToday.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Đã hoàn thành hôm nay (${uiState.chain.completedTasksToday.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF2E7D32)
                            )
                        }

                        items(
                            items = uiState.chain.completedTasksToday,
                            key = { "completed_${it.id}" }
                        ) { task ->
                            val linkedApps = uiState.taskLinkedAppsMap[task.id] ?: emptyList()
                            CompletedTaskItemCard(
                                task = task,
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
fun MissionProgressCard(
    completedCount: Int,
    totalCount: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF1F8E9)
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
                    .size(44.dp)
                    .background(Color(0xFF81C784), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$completedCount/$totalCount",
                    color = Color(0xFF1B5E20),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = "Tiến Trình Tu Luyện Hôm Nay",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1B5E20)
                )
                Text(
                    text = if (totalCount == 0) {
                        "Chưa có nhiệm vụ nào. Ký chủ hãy thêm nhiệm vụ mới!"
                    } else if (completedCount >= totalCount) {
                        "Ký chủ đã hoàn thành toàn bộ $totalCount/$totalCount nhiệm vụ hôm nay!"
                    } else {
                        "Ký chủ đã hoàn thành $completedCount/$totalCount nhiệm vụ"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF33691E)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CurrentTaskHighlight(
    currentTask: Task?,
    isAllCompleted: Boolean,
    isEmpty: Boolean,
    linkedApps: List<VaultApp>,
    onComplete: (Long) -> Unit,
    onOpenLinkage: (Task) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isAllCompleted) Color(0xFFE8F5E9) else Color(0xFFEDE7F6)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = if (isAllCompleted) "Trạng Thái Chuỗi Nhiệm Vụ" else "Nhiệm Vụ Đang Thực Hiện",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (isAllCompleted) Color(0xFF2E7D32) else Color(0xFF512DA8)
            )
            Spacer(modifier = Modifier.height(6.dp))

            when {
                isEmpty -> {
                    Text(
                        text = "Hiện tại chuỗi đang rỗng. Bấm nút '+' ở góc dưới bên phải để bắt đầu.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF424242)
                    )
                }
                isAllCompleted -> {
                    Text(
                        text = "Ký chủ đã xuất sắc hoàn thành toàn bộ nhiệm vụ của chu kỳ này! Nhiệm vụ sẽ reset tại 04:00 sáng mai.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF1B5E20)
                    )
                }
                currentTask != null -> {
                    Text(
                        text = currentTask.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF311B92)
                    )

                    // Hiển thị các ứng dụng liên kết
                    Spacer(modifier = Modifier.height(6.dp))
                    if (linkedApps.isNotEmpty()) {
                        Text(
                            text = "Ứng dụng liên kết:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF4527A0)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            linkedApps.forEach { app ->
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFFD1C4E9)
                                ) {
                                    Text(
                                        text = app.appName,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF311B92),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "Chưa liên kết ứng dụng Bảo Khố",
                            fontSize = 11.sp,
                            color = Color(0xFF757575)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onOpenLinkage(currentTask) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = "Liên kết App", fontSize = 12.sp)
                        }

                        Button(
                            onClick = { onComplete(currentTask.id) },
                            modifier = Modifier.weight(2f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF673AB7)
                            )
                        ) {
                            Text(
                                text = "Xác nhận hoàn thành",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskItemCard(
    task: Task,
    isCurrent: Boolean,
    linkedApps: List<VaultApp>,
    onComplete: () -> Unit,
    onArchive: () -> Unit,
    onOpenLinkage: () -> Unit
) {
    val formattedDate = remember(task.createdAtWallMillis) {
        SimpleDateFormat("HH:mm - dd/MM", Locale.getDefault()).format(Date(task.createdAtWallMillis))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) Color(0xFFFAFAFA) else Color(0xFFFFFFFF)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = task.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Tạo lúc: $formattedDate",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onComplete,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(text = "Xong", fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    TextButton(
                        onClick = onArchive,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(text = "Xóa", color = Color(0xFFD32F2F), fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(6.dp))

            // Hiển thị danh sách ứng dụng liên kết và nút chọn liên kết
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (linkedApps.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        linkedApps.forEach { app ->
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFFE0F2F1)
                            ) {
                                Text(
                                    text = app.appName,
                                    fontSize = 10.sp,
                                    color = Color(0xFF004D40),
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = "Chưa liên kết ứng dụng",
                        fontSize = 11.sp,
                        color = Color(0xFF9E9E9E),
                        modifier = Modifier.weight(1f)
                    )
                }

                TextButton(
                    onClick = onOpenLinkage,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (linkedApps.isEmpty()) "+ Liên kết App" else "Sửa liên kết (${linkedApps.size})",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CompletedTaskItemCard(
    task: Task,
    linkedApps: List<VaultApp>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF5F5F5)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(Color(0xFF4CAF50), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "✓", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = task.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF757575)
                )
            }

            if (linkedApps.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(
                    modifier = Modifier.padding(start = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    linkedApps.forEach { app ->
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFEEEEEE)
                        ) {
                            Text(
                                text = app.appName,
                                fontSize = 9.sp,
                                color = Color(0xFF757575),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Thêm Nhiệm Vụ Mới",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = "Nhập tên nhiệm vụ tự kỷ luật của Ký chủ:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = { Text("Tên nhiệm vụ") },
                    isError = error != null,
                    supportingText = {
                        if (error != null) {
                            Text(text = error, color = MaterialTheme.colorScheme.error)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Xác nhận")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy")
            }
        }
    )
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "Ứng Dụng Liên Kết",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Text(
                    text = "Nhiệm vụ: ${task.name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp)
            ) {
                Text(
                    text = "Chọn các ứng dụng từ Bảo Khố mà Ký chủ muốn liên kết với nhiệm vụ này:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))

                if (availableVaultApps.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Bảo Khố hiện đang trống rỗng.\nKý chủ hãy thêm ứng dụng vào Bảo Khố trước!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFC62828),
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(availableVaultApps, key = { it.packageName }) { app ->
                            val isSelected = selectedPackageNames.contains(app.packageName)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onToggleApp(app.packageName) }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { onToggleApp(app.packageName) }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = app.appName,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = app.packageName,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Lưu liên kết")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy")
            }
        }
    )
}
