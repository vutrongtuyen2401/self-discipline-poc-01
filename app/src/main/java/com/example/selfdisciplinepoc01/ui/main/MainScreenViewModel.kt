package com.example.selfdisciplinepoc01.ui.main

import androidx.lifecycle.ViewModel
import com.example.selfdisciplinepoc01.data.DataRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface MainScreenUiState {
    data object Loading : MainScreenUiState
    data class Success(val data: List<String>) : MainScreenUiState
    data class Error(val throwable: Throwable) : MainScreenUiState
}

class MainScreenViewModel(
    @Suppress("unused") private val dataRepository: DataRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow<MainScreenUiState>(MainScreenUiState.Loading)
    val uiState: StateFlow<MainScreenUiState> = _uiState.asStateFlow()
}
