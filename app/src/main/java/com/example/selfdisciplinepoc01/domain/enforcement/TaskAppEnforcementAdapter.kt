package com.example.selfdisciplinepoc01.domain.enforcement

import com.example.selfdisciplinepoc01.data.entity.AppEntity
import com.example.selfdisciplinepoc01.data.entity.TaskEntity
import com.example.selfdisciplinepoc01.data.repository.CoreDataRepository
import com.example.selfdisciplinepoc01.policy.PolicyDecision
import com.example.selfdisciplinepoc01.policy.PolicyEngine
import com.example.selfdisciplinepoc01.time.BusinessDayProvider
import com.example.selfdisciplinepoc01.time.BusinessDayProviderHolder
import com.example.selfdisciplinepoc01.time.Clock
import com.example.selfdisciplinepoc01.time.SystemClockImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap

/**
 * Enforcement status regarding Task-based sealing and unlocking.
 * Explicitly guards OPEN-01.
 */
enum class TaskAppEnforcementStatus {
    /**
     * OPEN-01 remains OPEN in Canonical Design V2 (Section 7).
     * The exact unlock formula (2/3 rounding, thresholds, edge cases) is NOT yet finalized.
     * Therefore, task-based unlocking/sealing enforcement remains disabled pending canonical resolution.
     */
    DISABLED_PENDING_OPEN_01,

    /**
     * Technical App Lock enforcement (ScheduleEvaluator, UsageTracker via DataStore)
     * continues to operate as the frozen technical foundation.
     */
    TECHNICAL_FOUNDATION_ACTIVE
}

/**
 * Phân loại ứng dụng phục vụ thực thi phong ấn.
 */
enum class AppEnforcementClassification {
    /** Ứng dụng không thuộc Bảo Khố (Vault) */
    NON_VAULT_APP,

    /** Ứng dụng thuộc Bảo Khố nhưng chưa/không có nhiệm vụ nào liên kết */
    VAULT_APP_UNLINKED,

    /** Ứng dụng thuộc Bảo Khố và có nhiệm vụ liên kết */
    VAULT_APP_WITH_TASKS
}

/**
 * Hành động thực thi cuối cùng của hệ thống phong ấn.
 */
enum class EnforcementAction {
    /** Ứng dụng bị phong ấn (khóa) */
    LOCK,

    /** Ứng dụng được phép mở */
    ALLOW
}

/**
 * Quyết định mở khóa theo nghiệp vụ (nhiệm vụ).
 * Bảo vệ tuyệt đối ranh giới OPEN-01.
 */
enum class BusinessUnlockDecision {
    /** Không áp dụng mở khóa nghiệp vụ (không thuộc Vault) */
    NOT_APPLICABLE,

    /**
     * Quy tắc mở khóa đang chờ quyết định chính thức từ Ký chủ (OPEN-01).
     * Tuyệt đối không tự động giải phong ấn khi chưa có công thức.
     */
    PENDING_OPEN_01
}

/**
 * Lý do chi tiết của quyết định thực thi.
 */
enum class EnforcementReason {
    /** Bị khóa bởi chính sách kỹ thuật (Target 24/7, Schedule, hoặc Daily Limit) */
    LOCKED_BY_POLICY,

    /** Thuộc Bảo Khố nhưng không có nhiệm vụ liên kết để giải phong ấn */
    LOCKED_BY_VAULT_NO_TASK,

    /** Thuộc Bảo Khố, có nhiệm vụ nhưng công thức giải phong ấn (OPEN-01) chưa chốt */
    LOCKED_PENDING_BUSINESS_RULE,

    /** Được phép mở do không thuộc danh sách phong ấn kỹ thuật và không thuộc Bảo Khố */
    ALLOWED_NOT_PROTECTED,

    /** Được phép mở theo chính sách kỹ thuật (ngoài khung giờ khóa hoặc chưa quá giới hạn) và không thuộc Bảo Khố */
    ALLOWED_BY_POLICY
}

/**
 * Chi tiết đánh giá thực thi toàn diện cho một ứng dụng.
 */
data class AppEnforcementDetails(
    val packageName: String,
    val isTechnicalLockActive: Boolean,
    val technicalPolicyDecision: PolicyDecision,
    val isVaultApp: Boolean,
    val classification: AppEnforcementClassification,
    val totalLinkedTasksCount: Int,
    val activeLinkedTasksCount: Int,
    val completedLinkedTasksCount: Int,
    val incompleteLinkedTasksCount: Int,
    val archivedLinkedTasksCount: Int,
    val businessUnlockDecision: BusinessUnlockDecision,
    val finalAction: EnforcementAction,
    val reason: EnforcementReason
)

/**
 * Boundary adapter between Vault/Task product domain and Technical App Lock enforcement.
 *
 * CRITICAL GOVERNANCE:
 * - Does NOT implement 2/3 unlock formula.
 * - Does NOT implement percentage thresholds.
 * - Does NOT calculate automatic unlocking based on task completion count.
 * - Prevents conflating Room Vault membership with DataStore target configuration.
 * - Preserves precedence: Technical Policy Lock ALWAYS overrides any allow state.
 */
class TaskAppEnforcementAdapter(
    private val coreDataRepository: CoreDataRepository,
    private val policyEngine: PolicyEngine? = null,
    private val businessDayProvider: BusinessDayProvider = BusinessDayProviderHolder.instance,
    private val clock: Clock = SystemClockImpl(),
    private val zoneIdProvider: () -> ZoneId = { ZoneId.systemDefault() },
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {

    /**
     * Backward-compatible constructor for Phase 19 tests and usages.
     */
    constructor(repository: CoreDataRepository) : this(
        coreDataRepository = repository,
        policyEngine = null,
        businessDayProvider = BusinessDayProviderHolder.instance,
        clock = SystemClockImpl(),
        zoneIdProvider = { ZoneId.systemDefault() }
    )

    // In-memory Snapshot Cache for 0ms, non-blocking synchronous evaluation in Accessibility Service
    private val cachedVaultApps = ConcurrentHashMap<String, AppEntity>()
    private val cachedTasksForApp = ConcurrentHashMap<String, List<TaskEntity>>()
    private val cachedCompletedTaskIds = ConcurrentHashMap<Long, Boolean>()

    init {
        startObserving()
    }

    private fun startObserving() {
        coroutineScope.launch {
            try {
                coreDataRepository.observeVaultApps().collect { apps ->
                    val newMap = mutableMapOf<String, AppEntity>()
                    for (app in apps) {
                        newMap[app.packageName] = app
                    }
                    cachedVaultApps.clear()
                    cachedVaultApps.putAll(newMap)
                }
            } catch (_: Exception) {
                // Ignore cancellation or repository initialization delay
            }
        }
    }

    fun getEnforcementStatus(): TaskAppEnforcementStatus {
        return TaskAppEnforcementStatus.DISABLED_PENDING_OPEN_01
    }

    /**
     * Evaluates whether an app has task-based unlock approval.
     * Currently returns false because OPEN-01 is OPEN.
     */
    suspend fun isTaskBasedUnlockApproved(packageName: String): Boolean {
        // OPEN-01 remains OPEN. No invented formula.
        return false
    }

    /**
     * Direct evaluation querying database (asynchronous / suspend).
     */
    suspend fun evaluate(packageName: String): AppEnforcementDetails {
        val technicalDecision = policyEngine?.evaluate(packageName) ?: PolicyDecision.ALLOW
        val isTechnicalLockActive = (technicalDecision == PolicyDecision.LOCK)

        val vaultApp = coreDataRepository.getApp(packageName)
        val isVaultApp = (vaultApp != null)

        val allLinkedTasks = if (isVaultApp) {
            coreDataRepository.getTasksForApp(packageName)
        } else {
            emptyList()
        }

        val activeTasks = allLinkedTasks.filter { !it.isArchived }
        val archivedTasksCount = allLinkedTasks.count { it.isArchived }

        val today = businessDayProvider.getBusinessDate(clock.wallTimeMillis(), zoneIdProvider()).toString()
        var completedCount = 0
        for (task in activeTasks) {
            if (coreDataRepository.isTaskCompletedOnDate(task.id, today)) {
                completedCount++
            }
        }
        val incompleteCount = activeTasks.size - completedCount

        // Update in-memory cache
        if (vaultApp != null) {
            cachedVaultApps[packageName] = vaultApp
            cachedTasksForApp[packageName] = allLinkedTasks
        } else {
            cachedVaultApps.remove(packageName)
            cachedTasksForApp.remove(packageName)
        }

        return buildEnforcementDetails(
            packageName = packageName,
            isTechnicalLockActive = isTechnicalLockActive,
            technicalPolicyDecision = technicalDecision,
            isVaultApp = isVaultApp,
            allLinkedTasks = allLinkedTasks,
            activeTasks = activeTasks,
            archivedTasksCount = archivedTasksCount,
            completedCount = completedCount,
            incompleteCount = incompleteCount
        )
    }

    /**
     * Fast, non-blocking synchronous evaluation reading in-memory snapshot cache.
     * Guaranteed safe for Main Thread in AccessibilityService.
     */
    fun evaluateSync(packageName: String): AppEnforcementDetails {
        val technicalDecision = policyEngine?.evaluate(packageName) ?: PolicyDecision.ALLOW
        val isTechnicalLockActive = (technicalDecision == PolicyDecision.LOCK)

        val isVaultApp = cachedVaultApps.containsKey(packageName)
        val allLinkedTasks = cachedTasksForApp[packageName] ?: emptyList()
        val activeTasks = allLinkedTasks.filter { !it.isArchived }
        val archivedTasksCount = allLinkedTasks.count { it.isArchived }

        var completedCount = 0
        for (task in activeTasks) {
            if (cachedCompletedTaskIds[task.id] == true) {
                completedCount++
            }
        }
        val incompleteCount = activeTasks.size - completedCount

        return buildEnforcementDetails(
            packageName = packageName,
            isTechnicalLockActive = isTechnicalLockActive,
            technicalPolicyDecision = technicalDecision,
            isVaultApp = isVaultApp,
            allLinkedTasks = allLinkedTasks,
            activeTasks = activeTasks,
            archivedTasksCount = archivedTasksCount,
            completedCount = completedCount,
            incompleteCount = incompleteCount
        )
    }

    /**
     * Manually updates the in-memory cache for synchronous fast paths (e.g. in test or on specific updates).
     */
    fun updateCacheForApp(
        packageName: String,
        isVault: Boolean,
        linkedTasks: List<TaskEntity> = emptyList(),
        completedTaskIds: Set<Long> = emptySet()
    ) {
        if (isVault) {
            cachedVaultApps[packageName] = AppEntity(packageName = packageName, appName = packageName)
            cachedTasksForApp[packageName] = linkedTasks
        } else {
            cachedVaultApps.remove(packageName)
            cachedTasksForApp.remove(packageName)
        }
        for (id in completedTaskIds) {
            cachedCompletedTaskIds[id] = true
        }
    }

    private fun buildEnforcementDetails(
        packageName: String,
        isTechnicalLockActive: Boolean,
        technicalPolicyDecision: PolicyDecision,
        isVaultApp: Boolean,
        allLinkedTasks: List<TaskEntity>,
        activeTasks: List<TaskEntity>,
        archivedTasksCount: Int,
        completedCount: Int,
        incompleteCount: Int
    ): AppEnforcementDetails {
        // 1. Classification
        val classification = when {
            !isVaultApp -> AppEnforcementClassification.NON_VAULT_APP
            activeTasks.isEmpty() -> AppEnforcementClassification.VAULT_APP_UNLINKED
            else -> AppEnforcementClassification.VAULT_APP_WITH_TASKS
        }

        // 2. Business Unlock Decision (OPEN-01 Hard Barrier: never approve automatic unlock)
        val businessUnlockDecision = when {
            !isVaultApp -> BusinessUnlockDecision.NOT_APPLICABLE
            else -> BusinessUnlockDecision.PENDING_OPEN_01
        }

        // 3. Technical Lock has SUPREME PRECEDENCE: never bypassed by any Vault or Task state
        if (isTechnicalLockActive) {
            return AppEnforcementDetails(
                packageName = packageName,
                isTechnicalLockActive = true,
                technicalPolicyDecision = technicalPolicyDecision,
                isVaultApp = isVaultApp,
                classification = classification,
                totalLinkedTasksCount = allLinkedTasks.size,
                activeLinkedTasksCount = activeTasks.size,
                completedLinkedTasksCount = completedCount,
                incompleteLinkedTasksCount = incompleteCount,
                archivedLinkedTasksCount = archivedTasksCount,
                businessUnlockDecision = businessUnlockDecision,
                finalAction = EnforcementAction.LOCK,
                reason = EnforcementReason.LOCKED_BY_POLICY
            )
        }

        // 4. If not technical lock, evaluate Vault Sealing
        if (isVaultApp) {
            val reason = if (activeTasks.isEmpty()) {
                EnforcementReason.LOCKED_BY_VAULT_NO_TASK
            } else {
                EnforcementReason.LOCKED_PENDING_BUSINESS_RULE
            }
            return AppEnforcementDetails(
                packageName = packageName,
                isTechnicalLockActive = false,
                technicalPolicyDecision = technicalPolicyDecision,
                isVaultApp = true,
                classification = classification,
                totalLinkedTasksCount = allLinkedTasks.size,
                activeLinkedTasksCount = activeTasks.size,
                completedLinkedTasksCount = completedCount,
                incompleteLinkedTasksCount = incompleteCount,
                archivedLinkedTasksCount = archivedTasksCount,
                businessUnlockDecision = businessUnlockDecision,
                finalAction = EnforcementAction.LOCK,
                reason = reason
            )
        }

        // 5. Allowed App (Not in Vault, and either not a Target or outside locking window/limit)
        val isConfiguredTarget = (policyEngine?.isTargetConfigured(packageName) == true)
        val reason = if (isConfiguredTarget) {
            EnforcementReason.ALLOWED_BY_POLICY
        } else {
            EnforcementReason.ALLOWED_NOT_PROTECTED
        }

        return AppEnforcementDetails(
            packageName = packageName,
            isTechnicalLockActive = false,
            technicalPolicyDecision = technicalPolicyDecision,
            isVaultApp = false,
            classification = classification,
            totalLinkedTasksCount = 0,
            activeLinkedTasksCount = 0,
            completedLinkedTasksCount = 0,
            incompleteLinkedTasksCount = 0,
            archivedLinkedTasksCount = 0,
            businessUnlockDecision = businessUnlockDecision,
            finalAction = EnforcementAction.ALLOW,
            reason = reason
        )
    }
}
