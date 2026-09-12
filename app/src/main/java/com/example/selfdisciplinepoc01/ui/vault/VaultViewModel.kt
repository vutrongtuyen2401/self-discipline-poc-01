package com.example.selfdisciplinepoc01.ui.vault

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.selfdisciplinepoc01.domain.canonical.repository.CanonicalLockEvaluator
import com.example.selfdisciplinepoc01.domain.canonical.repository.CanonicalRepositoryProvider
import com.example.selfdisciplinepoc01.domain.canonical.repository.CanonicalTaskRepository
import com.example.selfdisciplinepoc01.domain.canonical.repository.CanonicalVaultRepository
import com.example.selfdisciplinepoc01.domain.canonical.usecase.AddVaultAppUseCase
import com.example.selfdisciplinepoc01.domain.canonical.usecase.CanonicalVaultAppItem
import com.example.selfdisciplinepoc01.domain.canonical.usecase.GetCanonicalVaultAppsUseCase
import com.example.selfdisciplinepoc01.domain.canonical.usecase.RemoveVaultAppUseCase
import com.example.selfdisciplinepoc01.domain.canonical.vault.CanonicalLockDecision
import com.example.selfdisciplinepoc01.domain.canonical.vault.CanonicalVaultApp
import com.example.selfdisciplinepoc01.domain.canonical.vault.LockEvaluationResult
import com.example.selfdisciplinepoc01.domain.discovery.InstalledAppDiscoveryService
import com.example.selfdisciplinepoc01.domain.discovery.InstalledAppDiscoveryServiceImpl
import com.example.selfdisciplinepoc01.domain.enforcement.AppEnforcementClassification
import com.example.selfdisciplinepoc01.domain.enforcement.AppEnforcementDetails
import com.example.selfdisciplinepoc01.domain.enforcement.BusinessUnlockDecision
import com.example.selfdisciplinepoc01.domain.enforcement.EnforcementAction
import com.example.selfdisciplinepoc01.domain.enforcement.EnforcementReason
import com.example.selfdisciplinepoc01.domain.enforcement.TaskAppEnforcementAdapter
import com.example.selfdisciplinepoc01.domain.enforcement.TaskAppEnforcementAdapterProvider
import com.example.selfdisciplinepoc01.domain.model.DiscoveredApp
import com.example.selfdisciplinepoc01.domain.model.VaultApp
import com.example.selfdisciplinepoc01.policy.PolicyDecision
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VaultUiState(
    val vaultApps: List<CanonicalVaultAppItem> = emptyList(),
    val appEnforcementMap: Map<String, AppEnforcementDetails> = emptyMap(),
    val isLoading: Boolean = true,
    val isAddingApp: Boolean = false,
    val discoveredApps: List<DiscoveredApp> = emptyList(),
    val isLoadingDiscovered: Boolean = false,
    val searchQuery: String = "",
    val appPendingRemoval: CanonicalVaultAppItem? = null,
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
    private val getCanonicalVaultAppsUseCase: GetCanonicalVaultAppsUseCase?,
    private val canonicalAddVaultAppUseCase: AddVaultAppUseCase?,
    private val canonicalRemoveVaultAppUseCase: RemoveVaultAppUseCase?,
    private val legacyGetVaultAppsUseCase: com.example.selfdisciplinepoc01.domain.usecase.GetVaultAppsUseCase? = null,
    private val legacyAddVaultAppUseCase: com.example.selfdisciplinepoc01.domain.usecase.AddVaultAppUseCase? = null,
    private val legacyRemoveVaultAppUseCase: com.example.selfdisciplinepoc01.domain.usecase.RemoveVaultAppUseCase? = null,
    private val discoveryService: InstalledAppDiscoveryService,
    private val enforcementAdapter: TaskAppEnforcementAdapter? = null
) : ViewModel() {

    // Canonical primary constructor
    constructor(
        getCanonicalVaultAppsUseCase: GetCanonicalVaultAppsUseCase,
        addVaultAppUseCase: AddVaultAppUseCase,
        removeVaultAppUseCase: RemoveVaultAppUseCase,
        discoveryService: InstalledAppDiscoveryService,
        enforcementAdapter: TaskAppEnforcementAdapter? = null
    ) : this(
        getCanonicalVaultAppsUseCase = getCanonicalVaultAppsUseCase,
        canonicalAddVaultAppUseCase = addVaultAppUseCase,
        canonicalRemoveVaultAppUseCase = removeVaultAppUseCase,
        legacyGetVaultAppsUseCase = null,
        legacyAddVaultAppUseCase = null,
        legacyRemoveVaultAppUseCase = null,
        discoveryService = discoveryService,
        enforcementAdapter = enforcementAdapter
    )

    // Backward-compatible constructor for existing tests
    @Deprecated("Legacy constructor for test compatibility")
    constructor(
        getVaultAppsUseCase: com.example.selfdisciplinepoc01.domain.usecase.GetVaultAppsUseCase,
        addVaultAppUseCase: com.example.selfdisciplinepoc01.domain.usecase.AddVaultAppUseCase,
        removeVaultAppUseCase: com.example.selfdisciplinepoc01.domain.usecase.RemoveVaultAppUseCase,
        discoveryService: InstalledAppDiscoveryService,
        enforcementAdapter: TaskAppEnforcementAdapter? = null
    ) : this(
        getCanonicalVaultAppsUseCase = null,
        canonicalAddVaultAppUseCase = null,
        canonicalRemoveVaultAppUseCase = null,
        legacyGetVaultAppsUseCase = getVaultAppsUseCase,
        legacyAddVaultAppUseCase = addVaultAppUseCase,
        legacyRemoveVaultAppUseCase = removeVaultAppUseCase,
        discoveryService = discoveryService,
        enforcementAdapter = enforcementAdapter
    )

    private val _uiState = MutableStateFlow(VaultUiState())
    val uiState: StateFlow<VaultUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            try {
                enforcementAdapter?.recomputeSnapshot()
                val items: List<CanonicalVaultAppItem> = if (getCanonicalVaultAppsUseCase != null) {
                    getCanonicalVaultAppsUseCase.invoke()
                } else if (legacyGetVaultAppsUseCase != null) {
                    val legacyApps = legacyGetVaultAppsUseCase.getAllVaultApps()
                    legacyApps.map { app ->
                        CanonicalVaultAppItem(
                            app = CanonicalVaultApp(
                                packageName = app.packageName,
                                displayName = app.appName
                            ),
                            linkedTasks = emptyList(),
                            lockResult = LockEvaluationResult(
                                packageName = app.packageName,
                                decision = CanonicalLockDecision.UNLOCKED,
                                reason = "LEGACY_FALLBACK",
                                totalLinkedRewardTasks = app.linkedTasksCount,
                                completedLinkedRewardTasks = 0,
                                requiredCompletions = 0
                            )
                        )
                    }
                } else {
                    emptyList()
                }

                val enforcements = items.associate { item ->
                    item.packageName to (enforcementAdapter?.evaluateSync(item.packageName) ?: createDefaultEnforcement(item))
                }
                _uiState.update {
                    it.copy(
                        vaultApps = items,
                        appEnforcementMap = enforcements,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, bannerMessage = "Lỗi tải Bảo Khố: ${e.message}") }
            }
        }
    }

    fun refreshEnforcement() {
        refresh()
    }

    private fun createDefaultEnforcement(appItem: CanonicalVaultAppItem): AppEnforcementDetails {
        val hasTasks = appItem.linkedTasksCount > 0
        val isUnlocked = appItem.isUnlocked
        return AppEnforcementDetails(
            packageName = appItem.packageName,
            isTechnicalLockActive = false,
            technicalPolicyDecision = PolicyDecision.ALLOW,
            isVaultApp = true,
            classification = if (hasTasks) AppEnforcementClassification.VAULT_APP_WITH_TASKS else AppEnforcementClassification.VAULT_APP_UNLINKED,
            totalLinkedTasksCount = appItem.linkedTasksCount,
            activeLinkedTasksCount = appItem.linkedTasksCount,
            completedLinkedTasksCount = appItem.lockResult.completedLinkedRewardTasks,
            incompleteLinkedTasksCount = appItem.linkedTasksCount - appItem.lockResult.completedLinkedRewardTasks,
            archivedLinkedTasksCount = 0,
            requiredTasksCount = appItem.lockResult.requiredCompletions,
            businessUnlockDecision = if (isUnlocked) {
                if (hasTasks) BusinessUnlockDecision.UNLOCKED else BusinessUnlockDecision.NO_LINKED_TASKS
            } else {
                BusinessUnlockDecision.INSUFFICIENT_COMPLETION
            },
            finalAction = if (isUnlocked) EnforcementAction.ALLOW else EnforcementAction.LOCK,
            reason = if (isUnlocked) {
                EnforcementReason.ALLOWED_UNLOCKED_BY_TASKS
            } else {
                if (hasTasks) EnforcementReason.LOCKED_INSUFFICIENT_TASKS else EnforcementReason.LOCKED_BY_VAULT_NO_TASK
            },
            canonicalLockResult = appItem.lockResult
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
            try {
                canonicalAddVaultAppUseCase?.invoke(discoveredApp.packageName, discoveredApp.appName)
                legacyAddVaultAppUseCase?.invoke(discoveredApp.packageName, discoveredApp.appName)
                enforcementAdapter?.recomputeSnapshot()
                refresh()
                _uiState.update { state ->
                    val updatedDiscovered = state.discoveredApps.map {
                        if (it.packageName == discoveredApp.packageName) it.copy(isAlreadyInVault = true) else it
                    }
                    state.copy(
                        discoveredApps = updatedDiscovered,
                        bannerMessage = "Đã thu nạp [${discoveredApp.appName}] vào Bảo Khố!"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(bannerMessage = "Lỗi thêm ứng dụng: ${e.message}")
                }
            }
        }
    }

    fun onPromptRemoveApp(app: CanonicalVaultAppItem) {
        _uiState.update { it.copy(appPendingRemoval = app) }
    }

    fun onPromptRemoveApp(app: VaultApp) {
        val found = _uiState.value.vaultApps.firstOrNull { it.packageName == app.packageName }
            ?: CanonicalVaultAppItem(
                app = CanonicalVaultApp(app.packageName, app.appName),
                linkedTasks = emptyList(),
                lockResult = LockEvaluationResult(
                    packageName = app.packageName,
                    decision = CanonicalLockDecision.UNLOCKED,
                    reason = "",
                    totalLinkedRewardTasks = 0,
                    completedLinkedRewardTasks = 0,
                    requiredCompletions = 0
                )
            )
        _uiState.update { it.copy(appPendingRemoval = found) }
    }

    fun onDismissRemoveDialog() {
        _uiState.update { it.copy(appPendingRemoval = null) }
    }

    fun onConfirmRemoveApp() {
        val app = _uiState.value.appPendingRemoval ?: return
        viewModelScope.launch {
            try {
                canonicalRemoveVaultAppUseCase?.invoke(app.packageName)
                legacyRemoveVaultAppUseCase?.invoke(app.packageName)
                enforcementAdapter?.recomputeSnapshot()
                refresh()
                _uiState.update {
                    it.copy(
                        appPendingRemoval = null,
                        bannerMessage = "Đã gỡ [${app.appName}] khỏi Bảo Khố."
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        appPendingRemoval = null,
                        bannerMessage = "Lỗi gỡ ứng dụng: ${e.message}"
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
            context: Context,
            enforcementAdapter: TaskAppEnforcementAdapter? = null
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val appContext = context.applicationContext
                val vaultRepo = CanonicalRepositoryProvider.getVaultRepository(appContext)
                val taskRepo = CanonicalRepositoryProvider.getTaskRepository(appContext)
                val lockEvaluator = CanonicalRepositoryProvider.getLockEvaluator(appContext)
                val discoveryService = InstalledAppDiscoveryServiceImpl(appContext)
                val effectiveAdapter = enforcementAdapter ?: TaskAppEnforcementAdapterProvider.getAdapter(appContext)

                return VaultViewModel(
                    getCanonicalVaultAppsUseCase = GetCanonicalVaultAppsUseCase(vaultRepo, taskRepo, lockEvaluator),
                    addVaultAppUseCase = AddVaultAppUseCase(vaultRepo, lockEvaluator),
                    removeVaultAppUseCase = RemoveVaultAppUseCase(vaultRepo, taskRepo),
                    discoveryService = discoveryService,
                    enforcementAdapter = effectiveAdapter
                ) as T
            }
        }
    }
}
