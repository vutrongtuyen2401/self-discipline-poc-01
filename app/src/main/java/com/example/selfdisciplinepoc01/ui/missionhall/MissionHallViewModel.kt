package com.example.selfdisciplinepoc01.ui.missionhall

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.selfdisciplinepoc01.domain.canonical.repository.CanonicalCycleRepository
import com.example.selfdisciplinepoc01.domain.canonical.repository.CanonicalLockEvaluator
import com.example.selfdisciplinepoc01.domain.canonical.repository.CanonicalRepositoryProvider
import com.example.selfdisciplinepoc01.domain.canonical.repository.CanonicalTaskRepository
import com.example.selfdisciplinepoc01.domain.canonical.repository.CanonicalVaultRepository
import com.example.selfdisciplinepoc01.domain.canonical.usecase.CreateTaskUseCase
import com.example.selfdisciplinepoc01.domain.canonical.usecase.DeleteTaskUseCase
import com.example.selfdisciplinepoc01.domain.canonical.usecase.GetMissionHallTasksCanonicalUseCase
import com.example.selfdisciplinepoc01.domain.canonical.usecase.MissionHallTaskItem
import com.example.selfdisciplinepoc01.domain.canonical.usecase.RenameTaskUseCase
import com.example.selfdisciplinepoc01.domain.canonical.usecase.UndoTaskUseCase
import com.example.selfdisciplinepoc01.domain.canonical.usecase.UpdateTaskRewardLinkageUseCase
import com.example.selfdisciplinepoc01.domain.canonical.vault.CanonicalVaultApp
import com.example.selfdisciplinepoc01.domain.enforcement.TaskAppEnforcementAdapter
import com.example.selfdisciplinepoc01.domain.enforcement.TaskAppEnforcementAdapterProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class MissionHallUiState(
    val tasks: List<MissionHallTaskItem> = emptyList(),
    val isLoading: Boolean = true,
    val isAddingTask: Boolean = false,
    val currentInputName: String = "",
    val inputError: String? = null,
    val bannerMessage: String? = null,

    // Rename Flow (Immediate update, no confirmation dialog per SSOT)
    val taskBeingRenamed: MissionHallTaskItem? = null,
    val renameInputName: String = "",

    // Undo Confirmation Dialog (SSOT: Bắt buộc có confirmation)
    val taskPendingUndo: MissionHallTaskItem? = null,

    // Delete Confirmation Dialog (SSOT: Bắt buộc có confirmation)
    val taskPendingDelete: MissionHallTaskItem? = null,

    // Task ↔ App Linkage & Pending Next-Cycle Flow
    val taskSelectedForLinkage: MissionHallTaskItem? = null,
    val availableVaultApps: List<CanonicalVaultApp> = emptyList(),
    val selectedPackageNames: Set<String> = emptySet(),
    val isPendingNextCycleSelected: Boolean = false
) {
    val completedCount: Int
        get() = tasks.count { it.isCompleted }

    val totalActiveCount: Int
        get() = tasks.size

    val incompleteTasks: List<MissionHallTaskItem>
        get() = tasks.filter { !it.isCompleted }

    val completedTasks: List<MissionHallTaskItem>
        get() = tasks.filter { it.isCompleted }

    val currentTask: MissionHallTaskItem?
        get() = incompleteTasks.firstOrNull()

    val isAllCompleted: Boolean
        get() = tasks.isNotEmpty() && incompleteTasks.isEmpty()

    val isEmpty: Boolean
        get() = tasks.isEmpty()
}

class MissionHallViewModel(
    private val getTasksUseCase: GetMissionHallTasksCanonicalUseCase,
    private val createTaskUseCase: CreateTaskUseCase,
    private val renameTaskUseCase: RenameTaskUseCase,
    private val undoTaskUseCase: UndoTaskUseCase,
    private val deleteTaskUseCase: DeleteTaskUseCase,
    private val updateRewardLinkageUseCase: UpdateTaskRewardLinkageUseCase,
    private val vaultRepository: CanonicalVaultRepository,
    private val cycleRepository: CanonicalCycleRepository,
    private val enforcementAdapter: TaskAppEnforcementAdapter? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(MissionHallUiState())
    val uiState: StateFlow<MissionHallUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            try {
                val taskItems = getTasksUseCase()
                _uiState.update {
                    it.copy(
                        tasks = taskItems,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, bannerMessage = "Lỗi tải nhiệm vụ: ${e.message}")
                }
            }
        }
    }

    // --- Add Task Flow ---

    fun onOpenAddDialog() {
        _uiState.update {
            it.copy(
                isAddingTask = true,
                currentInputName = "",
                inputError = null
            )
        }
    }

    fun onDismissAddDialog() {
        _uiState.update {
            it.copy(
                isAddingTask = false,
                currentInputName = "",
                inputError = null
            )
        }
    }

    fun onInputNameChanged(name: String) {
        _uiState.update {
            it.copy(
                currentInputName = name,
                inputError = null
            )
        }
    }

    fun onConfirmAddTask() {
        val title = _uiState.value.currentInputName.trim()
        if (title.isBlank()) {
            _uiState.update { it.copy(inputError = "Tên nhiệm vụ không được để trống") }
            return
        }

        viewModelScope.launch {
            try {
                val newId = UUID.randomUUID().toString()
                val orderIndex = _uiState.value.tasks.size
                createTaskUseCase(
                    id = newId,
                    title = title,
                    orderIndex = orderIndex,
                    hasReward = false,
                    linkedAppPackageNames = emptyList()
                )
                enforcementAdapter?.recomputeSnapshot()
                refresh()
                _uiState.update {
                    it.copy(
                        isAddingTask = false,
                        currentInputName = "",
                        inputError = null,
                        bannerMessage = "Đã khởi tạo nhiệm vụ tu luyện mới!"
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(inputError = e.message ?: "Lỗi tạo nhiệm vụ") }
            }
        }
    }

    // --- Rename Task Flow (Instant Update, No Confirmation Dialog per SSOT) ---

    fun onOpenRenameDialog(taskItem: MissionHallTaskItem) {
        _uiState.update {
            it.copy(
                taskBeingRenamed = taskItem,
                renameInputName = taskItem.task.title
            )
        }
    }

    fun onRenameInputChanged(newName: String) {
        _uiState.update { it.copy(renameInputName = newName) }
    }

    fun onConfirmRename() {
        val taskItem = _uiState.value.taskBeingRenamed ?: return
        val newTitle = _uiState.value.renameInputName.trim()
        if (newTitle.isBlank()) return

        viewModelScope.launch {
            try {
                renameTaskUseCase(taskItem.task.id, newTitle)
                refresh()
                _uiState.update {
                    it.copy(
                        taskBeingRenamed = null,
                        renameInputName = "",
                        bannerMessage = "Đã cập nhật danh hiệu nhiệm vụ thành công."
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(bannerMessage = "Lỗi đổi tên: ${e.message}")
                }
            }
        }
    }

    fun onDismissRenameDialog() {
        _uiState.update {
            it.copy(
                taskBeingRenamed = null,
                renameInputName = ""
            )
        }
    }

    // --- Undo Task Flow (Confirmation Dialog Required per SSOT) ---

    fun onPromptUndoTask(taskItem: MissionHallTaskItem) {
        _uiState.update { it.copy(taskPendingUndo = taskItem) }
    }

    fun onConfirmUndoTask() {
        val taskItem = _uiState.value.taskPendingUndo ?: return
        viewModelScope.launch {
            try {
                val currentCycle = cycleRepository.getCurrentCycle()
                undoTaskUseCase(taskItem.task.id, currentCycle.cycleId)
                enforcementAdapter?.recomputeSnapshot()
                refresh()
                _uiState.update {
                    it.copy(
                        taskPendingUndo = null,
                        bannerMessage = "Đã hoàn tác trạng thái nhiệm vụ. Các pháp bảo liên quan đã tái lập phong ấn."
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        taskPendingUndo = null,
                        bannerMessage = "Lỗi hoàn tác: ${e.message}"
                    )
                }
            }
        }
    }

    fun onDismissUndoDialog() {
        _uiState.update { it.copy(taskPendingUndo = null) }
    }

    // --- Delete Task Flow (Confirmation Dialog Required, Preserves History per SSOT) ---

    fun onPromptDeleteTask(taskItem: MissionHallTaskItem) {
        _uiState.update { it.copy(taskPendingDelete = taskItem) }
    }

    fun onConfirmDeleteTask() {
        val taskItem = _uiState.value.taskPendingDelete ?: return
        viewModelScope.launch {
            try {
                deleteTaskUseCase(taskItem.task.id)
                enforcementAdapter?.recomputeSnapshot()
                refresh()
                _uiState.update {
                    it.copy(
                        taskPendingDelete = null,
                        bannerMessage = "Đã đưa nhiệm vụ vào lưu trữ. Lịch sử công đức quá khứ vẫn bảo tồn nguyên vẹn."
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        taskPendingDelete = null,
                        bannerMessage = "Lỗi xóa nhiệm vụ: ${e.message}"
                    )
                }
            }
        }
    }

    fun onDismissDeleteDialog() {
        _uiState.update { it.copy(taskPendingDelete = null) }
    }

    // --- Task ↔ App Linkage & Pending Next-Cycle Flow ---

    fun onOpenLinkageDialog(taskItem: MissionHallTaskItem) {
        viewModelScope.launch {
            val available = vaultRepository.getAllVaultApps()
            val selected = taskItem.linkedApps.map { it.packageName }.toSet()
            _uiState.update {
                it.copy(
                    taskSelectedForLinkage = taskItem,
                    availableVaultApps = available,
                    selectedPackageNames = selected,
                    isPendingNextCycleSelected = false
                )
            }
        }
    }

    fun onToggleAppSelection(packageName: String) {
        _uiState.update { state ->
            val current = state.selectedPackageNames.toMutableSet()
            if (current.contains(packageName)) {
                current.remove(packageName)
            } else {
                current.add(packageName)
            }
            state.copy(selectedPackageNames = current)
        }
    }

    fun onTogglePendingNextCycle(isPending: Boolean) {
        _uiState.update { it.copy(isPendingNextCycleSelected = isPending) }
    }

    fun onSaveTaskLinkage() {
        val taskItem = _uiState.value.taskSelectedForLinkage ?: return
        val selected = _uiState.value.selectedPackageNames.toList()
        val isPending = _uiState.value.isPendingNextCycleSelected

        viewModelScope.launch {
            try {
                val timing = if (isPending) {
                    com.example.selfdisciplinepoc01.domain.canonical.task.RewardMutationTiming.PENDING_NEXT_CYCLE
                } else {
                    com.example.selfdisciplinepoc01.domain.canonical.task.RewardMutationTiming.IMMEDIATE_CURRENT_CYCLE
                }
                updateRewardLinkageUseCase.execute(
                    taskId = taskItem.task.id,
                    selectedPackageNames = selected,
                    timing = timing
                )
                enforcementAdapter?.recomputeSnapshot()
                refresh()
                val msg = if (isPending) {
                    "Cấu hình phần thưởng mới sẽ được áp dụng tại 04:00 AM sáng mai."
                } else {
                    "Đã cập nhật pháp bảo liên kết cho [${taskItem.task.title}]."
                }
                _uiState.update {
                    it.copy(
                        taskSelectedForLinkage = null,
                        selectedPackageNames = emptySet(),
                        isPendingNextCycleSelected = false,
                        bannerMessage = msg
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(bannerMessage = "Lỗi cập nhật liên kết: ${e.message}")
                }
            }
        }
    }

    fun onCancelPendingRewards(taskId: String) {
        viewModelScope.launch {
            try {
                updateRewardLinkageUseCase.cancelPending(taskId)
                refresh()
                _uiState.update {
                    it.copy(bannerMessage = "Đã hủy cấu hình phần thưởng đang chờ.")
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(bannerMessage = "Lỗi hủy cấu hình chờ: ${e.message}")
                }
            }
        }
    }

    fun onDismissLinkageDialog() {
        _uiState.update {
            it.copy(
                taskSelectedForLinkage = null,
                selectedPackageNames = emptySet(),
                isPendingNextCycleSelected = false
            )
        }
    }

    fun onDismissBanner() {
        _uiState.update { it.copy(bannerMessage = null) }
    }

    companion object {
        fun provideFactory(
            context: Context,
            enforcementAdapter: TaskAppEnforcementAdapter? = null
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val appContext = context.applicationContext
                val taskRepo = CanonicalRepositoryProvider.getTaskRepository(appContext)
                val vaultRepo = CanonicalRepositoryProvider.getVaultRepository(appContext)
                val cycleRepo = CanonicalRepositoryProvider.getCycleRepository(appContext)
                val lockEvaluator = CanonicalRepositoryProvider.getLockEvaluator(appContext)
                val effectiveAdapter = enforcementAdapter ?: TaskAppEnforcementAdapterProvider.getAdapter(appContext)

                return MissionHallViewModel(
                    getTasksUseCase = GetMissionHallTasksCanonicalUseCase(taskRepo, vaultRepo, cycleRepo),
                    createTaskUseCase = CreateTaskUseCase(taskRepo, lockEvaluator),
                    renameTaskUseCase = RenameTaskUseCase(taskRepo),
                    undoTaskUseCase = UndoTaskUseCase(taskRepo, lockEvaluator),
                    deleteTaskUseCase = DeleteTaskUseCase(taskRepo, lockEvaluator),
                    updateRewardLinkageUseCase = UpdateTaskRewardLinkageUseCase(taskRepo, lockEvaluator),
                    vaultRepository = vaultRepo,
                    cycleRepository = cycleRepo,
                    enforcementAdapter = effectiveAdapter
                ) as T
            }
        }
    }
}
