package com.example.selfdisciplinepoc01.ui.missionhall

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.selfdisciplinepoc01.data.repository.CoreDataRepository
import com.example.selfdisciplinepoc01.domain.model.SequentialTaskChain
import com.example.selfdisciplinepoc01.domain.usecase.ArchiveTaskUseCase
import com.example.selfdisciplinepoc01.domain.usecase.CompleteTaskUseCase
import com.example.selfdisciplinepoc01.domain.usecase.CreateTaskUseCase
import com.example.selfdisciplinepoc01.domain.usecase.GetMissionHallTasksUseCase
import com.example.selfdisciplinepoc01.time.BusinessDayProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MissionHallUiState(
    val chain: SequentialTaskChain = SequentialTaskChain(null, emptyList(), emptyList(), 0, 0),
    val isLoading: Boolean = true,
    val isAddingTask: Boolean = false,
    val currentInputName: String = "",
    val inputError: String? = null,
    val bannerMessage: String? = null
)

class MissionHallViewModel(
    private val getTasksUseCase: GetMissionHallTasksUseCase,
    private val createTaskUseCase: CreateTaskUseCase,
    private val completeTaskUseCase: CompleteTaskUseCase,
    private val archiveTaskUseCase: ArchiveTaskUseCase
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
                _uiState.update {
                    it.copy(chain = chain, isLoading = false)
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
                val updatedChain = getTasksUseCase.getSequentialChain()
                _uiState.update {
                    it.copy(
                        chain = updatedChain,
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
                val updatedChain = getTasksUseCase.getSequentialChain()
                _uiState.update {
                    it.copy(
                        chain = updatedChain,
                        bannerMessage = "Đã hoàn thành một nhiệm vụ!"
                    )
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
                val updatedChain = getTasksUseCase.getSequentialChain()
                _uiState.update {
                    it.copy(
                        chain = updatedChain,
                        bannerMessage = "Đã xóa nhiệm vụ khỏi chuỗi."
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(bannerMessage = "Lỗi lưu trữ: ${error.message}")
                }
            }
        }
    }

    fun onDismissBanner() {
        _uiState.update { it.copy(bannerMessage = null) }
    }

    companion object {
        fun provideFactory(
            repository: CoreDataRepository,
            businessDayProvider: BusinessDayProvider
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MissionHallViewModel(
                    getTasksUseCase = GetMissionHallTasksUseCase(repository, businessDayProvider),
                    createTaskUseCase = CreateTaskUseCase(repository),
                    completeTaskUseCase = CompleteTaskUseCase(repository, businessDayProvider),
                    archiveTaskUseCase = ArchiveTaskUseCase(repository)
                ) as T
            }
        }
    }
}
