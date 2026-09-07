package com.example.selfdisciplinepoc01.ui.missionhall

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.selfdisciplinepoc01.data.repository.CoreDataRepository
import com.example.selfdisciplinepoc01.domain.enforcement.TaskAppEnforcementAdapter
import com.example.selfdisciplinepoc01.domain.model.SequentialTaskChain
import com.example.selfdisciplinepoc01.domain.model.Task
import com.example.selfdisciplinepoc01.domain.model.VaultApp
import com.example.selfdisciplinepoc01.domain.usecase.ArchiveTaskUseCase
import com.example.selfdisciplinepoc01.domain.usecase.CompleteTaskUseCase
import com.example.selfdisciplinepoc01.domain.usecase.CreateTaskUseCase
import com.example.selfdisciplinepoc01.domain.usecase.GetMissionHallTasksUseCase
import com.example.selfdisciplinepoc01.domain.usecase.GetTaskLinkedAppsUseCase
import com.example.selfdisciplinepoc01.domain.usecase.GetVaultAppsUseCase
import com.example.selfdisciplinepoc01.domain.usecase.UpdateTaskLinkedAppsUseCase
import com.example.selfdisciplinepoc01.time.BusinessDayProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MissionHallUiState(
    val chain: SequentialTaskChain = SequentialTaskChain(null, emptyList(), emptyList(), 0, 0),
    val isLoading: Boolean = true,
    val isAddingTask: Boolean = false,
    val currentInputName: String = "",
    val inputError: String? = null,
    val bannerMessage: String? = null,
    // Phase 19: Task ↔ App Linkage
    val taskSelectedForLinkage: Task? = null,
    val availableVaultApps: List<VaultApp> = emptyList(),
    val selectedPackageNames: Set<String> = emptySet(),
    val taskLinkedAppsMap: Map<Long, List<VaultApp>> = emptyMap()
)

class MissionHallViewModel(
    private val getTasksUseCase: GetMissionHallTasksUseCase,
    private val createTaskUseCase: CreateTaskUseCase,
    private val completeTaskUseCase: CompleteTaskUseCase,
    private val archiveTaskUseCase: ArchiveTaskUseCase,
    private val getTaskLinkedAppsUseCase: GetTaskLinkedAppsUseCase,
    private val updateTaskLinkedAppsUseCase: UpdateTaskLinkedAppsUseCase,
    private val getVaultAppsUseCase: GetVaultAppsUseCase,
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
                val chain = getTasksUseCase.getSequentialChain()
                val allTasks = chain.incompleteTasks + chain.completedTasksToday
                val map = mutableMapOf<Long, List<VaultApp>>()
                for (task in allTasks) {
                    map[task.id] = getTaskLinkedAppsUseCase(task.id)
                }
                _uiState.update {
                    it.copy(
                        chain = chain,
                        taskLinkedAppsMap = map,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, bannerMessage = "Lỗi tải: ${e.message}")
                }
            }
        }
    }

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
        val name = _uiState.value.currentInputName
        viewModelScope.launch {
            val result = createTaskUseCase(name)
            result.onSuccess {
                refresh()
                _uiState.update {
                    it.copy(
                        isAddingTask = false,
                        currentInputName = "",
                        inputError = null,
                        bannerMessage = "Đã thêm nhiệm vụ thành công!"
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(inputError = error.message ?: "Tên không hợp lệ")
                }
            }
        }
    }

    fun onCompleteTask(taskId: Long) {
        viewModelScope.launch {
            val result = completeTaskUseCase(taskId)
            result.onSuccess {
                enforcementAdapter?.recomputeSnapshot()
                refresh()
                _uiState.update {
                    it.copy(bannerMessage = "Đã hoàn thành một nhiệm vụ!")
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(bannerMessage = "Lỗi hoàn thành nhiệm vụ: ${error.message}")
                }
            }
        }
    }

    fun onArchiveTask(taskId: Long) {
        viewModelScope.launch {
            val result = archiveTaskUseCase(taskId)
            result.onSuccess {
                enforcementAdapter?.recomputeSnapshot()
                refresh()
                _uiState.update {
                    it.copy(bannerMessage = "Đã xóa nhiệm vụ khỏi chuỗi.")
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(bannerMessage = "Lỗi lưu trữ: ${error.message}")
                }
            }
        }
    }

    // --- Phase 19: Task ↔ App Linkage Flow ---

    fun onOpenLinkageDialog(task: Task) {
        viewModelScope.launch {
            val available = getVaultAppsUseCase.getAllVaultApps()
            val linked = getTaskLinkedAppsUseCase(task.id)
            val selectedPkgs = linked.map { it.packageName }.toSet()
            _uiState.update {
                it.copy(
                    taskSelectedForLinkage = task,
                    availableVaultApps = available,
                    selectedPackageNames = selectedPkgs
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

    fun onSaveTaskLinkage() {
        val task = _uiState.value.taskSelectedForLinkage ?: return
        val selected = _uiState.value.selectedPackageNames.toList()
        viewModelScope.launch {
            val result = updateTaskLinkedAppsUseCase(task.id, selected)
            result.onSuccess {
                enforcementAdapter?.recomputeSnapshot()
                refresh()
                _uiState.update {
                    it.copy(
                        taskSelectedForLinkage = null,
                        selectedPackageNames = emptySet(),
                        bannerMessage = "Đã cập nhật ứng dụng liên kết cho [${task.name}]."
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(bannerMessage = "Lỗi liên kết ứng dụng: ${error.message}")
                }
            }
        }
    }

    fun onDismissLinkageDialog() {
        _uiState.update {
            it.copy(
                taskSelectedForLinkage = null,
                selectedPackageNames = emptySet()
            )
        }
    }

    fun onDismissBanner() {
        _uiState.update { it.copy(bannerMessage = null) }
    }

    companion object {
        fun provideFactory(
            repository: CoreDataRepository,
            businessDayProvider: BusinessDayProvider,
            enforcementAdapter: TaskAppEnforcementAdapter? = null
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MissionHallViewModel(
                    getTasksUseCase = GetMissionHallTasksUseCase(repository, businessDayProvider),
                    createTaskUseCase = CreateTaskUseCase(repository),
                    completeTaskUseCase = CompleteTaskUseCase(repository, businessDayProvider),
                    archiveTaskUseCase = ArchiveTaskUseCase(repository),
                    getTaskLinkedAppsUseCase = GetTaskLinkedAppsUseCase(repository),
                    updateTaskLinkedAppsUseCase = UpdateTaskLinkedAppsUseCase(repository),
                    getVaultAppsUseCase = GetVaultAppsUseCase(repository),
                    enforcementAdapter = enforcementAdapter
                ) as T
            }
        }
    }
}
