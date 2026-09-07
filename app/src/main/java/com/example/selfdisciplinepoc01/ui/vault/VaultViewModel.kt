package com.example.selfdisciplinepoc01.ui.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.selfdisciplinepoc01.data.repository.CoreDataRepository
import com.example.selfdisciplinepoc01.domain.discovery.InstalledAppDiscoveryService
import com.example.selfdisciplinepoc01.domain.enforcement.AppEnforcementClassification
import com.example.selfdisciplinepoc01.domain.enforcement.AppEnforcementDetails
import com.example.selfdisciplinepoc01.domain.enforcement.BusinessUnlockDecision
import com.example.selfdisciplinepoc01.domain.enforcement.EnforcementAction
import com.example.selfdisciplinepoc01.domain.enforcement.EnforcementReason
import com.example.selfdisciplinepoc01.domain.enforcement.TaskAppEnforcementAdapter
import com.example.selfdisciplinepoc01.domain.model.DiscoveredApp
import com.example.selfdisciplinepoc01.domain.model.VaultApp
import com.example.selfdisciplinepoc01.domain.usecase.AddVaultAppUseCase
import com.example.selfdisciplinepoc01.domain.usecase.GetVaultAppsUseCase
import com.example.selfdisciplinepoc01.domain.usecase.RemoveVaultAppUseCase
import com.example.selfdisciplinepoc01.policy.PolicyDecision
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VaultUiState(
    val vaultApps: List<VaultApp> = emptyList(),
    val appEnforcementMap: Map<String, AppEnforcementDetails> = emptyMap(),
    val isLoading: Boolean = true,
    val isAddingApp: Boolean = false,
    val discoveredApps: List<DiscoveredApp> = emptyList(),
    val isLoadingDiscovered: Boolean = false,
    val searchQuery: String = "",
    val appPendingRemoval: VaultApp? = null,
    val bannerMessage: String? = null
) {
    val filteredDiscoveredApps: List<DiscoveredApp>
        get() = if (searchQuery.isBlank()) {
            discoveredApps
        } else {
            discoveredApps.filter {
                it.appName.contains(searchQuery, ignoreCase = true) ||
                it.packageName.contains(searchQuery, ignoreCase = true)
            }
        }
}

class VaultViewModel(
    private val getVaultAppsUseCase: GetVaultAppsUseCase,
    private val addVaultAppUseCase: AddVaultAppUseCase,
    private val removeVaultAppUseCase: RemoveVaultAppUseCase,
    private val discoveryService: InstalledAppDiscoveryService,
    private val enforcementAdapter: TaskAppEnforcementAdapter? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(VaultUiState())
    val uiState: StateFlow<VaultUiState> = _uiState.asStateFlow()

    init {
        observeVaultApps()
    }

    private fun observeVaultApps() {
        viewModelScope.launch {
            getVaultAppsUseCase.observeVaultApps()
                .catch { e ->
                    _uiState.update { it.copy(isLoading = false, bannerMessage = "Lỗi tải Bảo Khố: ${e.message}") }
                }
                .collect { apps ->
                    val enforcements = apps.associate { app ->
                        app.packageName to (enforcementAdapter?.evaluateSync(app.packageName) ?: createDefaultEnforcement(app))
                    }
                    _uiState.update {
                        it.copy(
                            vaultApps = apps,
                            appEnforcementMap = enforcements,
                            isLoading = false
                        )
                    }
                }
        }
    }

    fun refreshEnforcement() {
        val currentApps = _uiState.value.vaultApps
        if (currentApps.isNotEmpty()) {
            val enforcements = currentApps.associate { app ->
                app.packageName to (enforcementAdapter?.evaluateSync(app.packageName) ?: createDefaultEnforcement(app))
            }
            _uiState.update { it.copy(appEnforcementMap = enforcements) }
        }
    }

    private fun createDefaultEnforcement(app: VaultApp): AppEnforcementDetails {
        val hasTasks = app.linkedTasksCount > 0
        return AppEnforcementDetails(
            packageName = app.packageName,
            isTechnicalLockActive = false,
            technicalPolicyDecision = PolicyDecision.ALLOW,
            isVaultApp = true,
            classification = if (hasTasks) AppEnforcementClassification.VAULT_APP_WITH_TASKS else AppEnforcementClassification.VAULT_APP_UNLINKED,
            totalLinkedTasksCount = app.linkedTasksCount,
            activeLinkedTasksCount = app.linkedTasksCount,
            completedLinkedTasksCount = 0,
            incompleteLinkedTasksCount = app.linkedTasksCount,
            archivedLinkedTasksCount = 0,
            requiredTasksCount = if (hasTasks) (2 * app.linkedTasksCount + 2) / 3 else 0,
            businessUnlockDecision = if (hasTasks) BusinessUnlockDecision.INSUFFICIENT_COMPLETION else BusinessUnlockDecision.NO_LINKED_TASKS,
            finalAction = EnforcementAction.LOCK,
            reason = if (hasTasks) EnforcementReason.LOCKED_INSUFFICIENT_TASKS else EnforcementReason.LOCKED_BY_VAULT_NO_TASK
        )
    }

    fun onOpenAddDialog() {
        _uiState.update {
            it.copy(
                isAddingApp = true,
                isLoadingDiscovered = true,
                searchQuery = "",
                discoveredApps = emptyList()
            )
        }
        viewModelScope.launch {
            try {
                val currentPackages = _uiState.value.vaultApps.map { it.packageName }.toSet()
                val discovered = discoveryService.getDiscoveredApps(currentPackages)
                _uiState.update {
                    it.copy(
                        discoveredApps = discovered,
                        isLoadingDiscovered = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoadingDiscovered = false,
                        bannerMessage = "Lỗi khám phá ứng dụng: ${e.message}"
                    )
                }
            }
        }
    }

    fun onDismissAddDialog() {
        _uiState.update {
            it.copy(isAddingApp = false, searchQuery = "")
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun onAddApp(discoveredApp: DiscoveredApp) {
        viewModelScope.launch {
            val result = addVaultAppUseCase(discoveredApp.packageName, discoveredApp.appName)
            result.onSuccess {
                // Update local discovered list status
                _uiState.update { state ->
                    val updatedDiscovered = state.discoveredApps.map {
                        if (it.packageName == discoveredApp.packageName) it.copy(isAlreadyInVault = true) else it
                    }
                    state.copy(
                        discoveredApps = updatedDiscovered,
                        bannerMessage = "Đã thu nạp [${discoveredApp.appName}] vào Bảo Khố!"
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(bannerMessage = "Lỗi thêm ứng dụng: ${error.message}")
                }
            }
        }
    }

    fun onPromptRemoveApp(app: VaultApp) {
        _uiState.update { it.copy(appPendingRemoval = app) }
    }

    fun onDismissRemoveDialog() {
        _uiState.update { it.copy(appPendingRemoval = null) }
    }

    fun onConfirmRemoveApp() {
        val app = _uiState.value.appPendingRemoval ?: return
        viewModelScope.launch {
            val result = removeVaultAppUseCase(app.packageName)
            result.onSuccess {
                _uiState.update {
                    it.copy(
                        appPendingRemoval = null,
                        bannerMessage = "Đã gỡ [${app.appName}] khỏi Bảo Khố."
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        appPendingRemoval = null,
                        bannerMessage = "Lỗi gỡ ứng dụng: ${error.message}"
                    )
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
            discoveryService: InstalledAppDiscoveryService,
            enforcementAdapter: TaskAppEnforcementAdapter? = null
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return VaultViewModel(
                    getVaultAppsUseCase = GetVaultAppsUseCase(repository),
                    addVaultAppUseCase = AddVaultAppUseCase(repository),
                    removeVaultAppUseCase = RemoveVaultAppUseCase(repository),
                    discoveryService = discoveryService,
                    enforcementAdapter = enforcementAdapter
                ) as T
            }
        }
    }
}
