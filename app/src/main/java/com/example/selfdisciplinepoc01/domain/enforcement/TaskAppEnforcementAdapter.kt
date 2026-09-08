package com.example.selfdisciplinepoc01.domain.enforcement

import com.example.selfdisciplinepoc01.data.entity.AppEntity
import com.example.selfdisciplinepoc01.data.entity.TaskEntity
import com.example.selfdisciplinepoc01.data.repository.CoreDataRepository
import com.example.selfdisciplinepoc01.domain.policy.BusinessUnlockResult
import com.example.selfdisciplinepoc01.domain.policy.TaskUnlockPolicy
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
import java.util.concurrent.atomic.AtomicReference

/**
 * Enforcement status regarding Task-based sealing and unlocking.
 */
enum class TaskAppEnforcementStatus {
    /**
     * @deprecated OPEN-01 has been officially decided by Product Owner and implemented in Phase 23.
     */
    @Deprecated("OPEN-01 has been officially decided by Product Owner and implemented in Phase 23.")
    DISABLED_PENDING_OPEN_01,

    /**
     * Technical App Lock enforcement operates as technical foundation.
     */
    TECHNICAL_FOUNDATION_ACTIVE,

    /**
     * Task-based unlock policy (OPEN-01) is active and enforced.
     */
    TASK_BASED_UNLOCK_ACTIVE
}

/**
 * Phân loại ứng dụng phục vụ thực thi phong ấn.
 */
enum class AppEnforcementClassification {
    /** Ứng dụng không thuộc Bảo Khố (Vault) */
    NON_VAULT_APP,

    /** Ứng dụng thuộc Bảo Khố nhưng chưa/không có nhiệm vụ nào liên kết (N = 0) */
    VAULT_APP_UNLINKED,

    /** Ứng dụng thuộc Bảo Khố và có nhiệm vụ liên kết (N > 0) */
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
 * Hiện thực hóa Product Decision OPEN-01.
 */
enum class BusinessUnlockDecision {
    /** Không áp dụng mở khóa nghiệp vụ (không thuộc Vault) */
    NOT_APPLICABLE,

    /** Thuộc Bảo Khố nhưng chưa liên kết nhiệm vụ nào (N = 0) -> Không mở khóa */
    NO_LINKED_TASKS,

    /** Thuộc Bảo Khố, có nhiệm vụ nhưng chưa hoàn thành đủ số lượng theo công thức OPEN-01 -> Khóa */
    INSUFFICIENT_COMPLETION,

    /** Thuộc Bảo Khố, đã hoàn thành đủ số lượng nhiệm vụ yêu cầu (completed >= required) -> Đủ điều kiện mở */
    UNLOCKED,

    /**
     * @deprecated Replaced by explicit decisions above after OPEN-01 resolution.
     */
    @Deprecated("Replaced by explicit decisions above after OPEN-01 resolution.")
    PENDING_OPEN_01
}

/**
 * Lý do chi tiết của quyết định thực thi.
 */
enum class EnforcementReason {
    /** Bị khóa bởi chính sách kỹ thuật (Target 24/7, Schedule, hoặc Daily Limit) */
    LOCKED_BY_POLICY,

    /** Thuộc Bảo Khố nhưng không có nhiệm vụ liên kết để giải phong ấn (N = 0) */
    LOCKED_BY_VAULT_NO_TASK,

    /** Thuộc Bảo Khố, chưa hoàn thành đủ số lượng nhiệm vụ yêu cầu theo công thức OPEN-01 */
    LOCKED_INSUFFICIENT_TASKS,

    /** Được giải phong ấn do đã hoàn thành đủ số lượng nhiệm vụ yêu cầu (OPEN-01) */
    ALLOWED_UNLOCKED_BY_TASKS,

    /** Được phép mở do không thuộc danh sách phong ấn kỹ thuật và không thuộc Bảo Khố */
    ALLOWED_NOT_PROTECTED,

    /** Được phép mở theo chính sách kỹ thuật (ngoài khung giờ khóa hoặc chưa quá giới hạn) và không thuộc Bảo Khố */
    ALLOWED_BY_POLICY,

    /**
     * @deprecated Replaced by LOCKED_INSUFFICIENT_TASKS.
     */
    @Deprecated("Replaced by LOCKED_INSUFFICIENT_TASKS.")
    LOCKED_PENDING_BUSINESS_RULE
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
    val requiredTasksCount: Int,
    val businessUnlockDecision: BusinessUnlockDecision,
    val finalAction: EnforcementAction,
    val reason: EnforcementReason
)

/**
 * Immutable in-memory snapshot for 0ms, non-blocking synchronous evaluation in AccessibilityService.
 */
data class EnforcementSnapshot(
    val vaultApps: Map<String, AppEntity> = emptyMap(),
    val tasksForApp: Map<String, List<TaskEntity>> = emptyMap(),
    val completedTaskIds: Set<Long> = emptySet(),
    val businessDate: String = ""
)

/**
 * Boundary adapter between Vault/Task product domain and Technical App Lock enforcement.
 *
 * OFFICIAL PRODUCT DECISION — OPEN-01:
 * For a Vault App with N effective linked tasks (active, non-archived, non-deleted)
 * in the current business cycle (resetting at 04:00:00):
 *
 *   requiredCompletedTasks = (2 * N + 2) / 3
 *
 * Unlock condition:
 *   completedTasks >= requiredCompletedTasks AND N > 0
 *
 * TECHNICAL PRECEDENCE:
 * - Technical App Lock (ScheduleEvaluator, UsageTracker via DataStore) ALWAYS overrides
 *   business unlock. If technical lock is LOCK, final action is LOCK.
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
     * Backward-compatible constructor for Phase 19/20 usages.
     */
    constructor(repository: CoreDataRepository) : this(
        coreDataRepository = repository,
        policyEngine = null,
        businessDayProvider = BusinessDayProviderHolder.instance,
        clock = SystemClockImpl(),
        zoneIdProvider = { ZoneId.systemDefault() }
    )

    // Thread-safe atomic in-memory snapshot cache
    private val cachedSnapshot = AtomicReference(EnforcementSnapshot())

    init {
        startObserving()
    }

    private fun startObserving() {
        coroutineScope.launch {
            try {
                // Trigger initial compute
                recomputeSnapshot()

                // Observe vault apps changes
                launch {
                    coreDataRepository.observeVaultApps().collect {
                        recomputeSnapshot()
                    }
                }

                // Observe active tasks changes
                launch {
                    coreDataRepository.observeActiveTasks().collect {
                        recomputeSnapshot()
                    }
                }

                // Observe task-app cross reference associations changes
                launch {
                    coreDataRepository.observeAllCrossRefs().collect {
                        recomputeSnapshot()
                    }
                }

                // Observe daily completions changes
                launch {
                    val today = businessDayProvider.getBusinessDate(clock.wallTimeMillis(), zoneIdProvider()).toString()
                    coreDataRepository.observeCompletedTaskIdsForDate(today).collect {
                        recomputeSnapshot()
                    }
                }
            } catch (_: Exception) {
                // Ignore cancellation or repository initialization delay
            }
        }
    }

    /**
     * Recomputes the full in-memory snapshot from the database.
     */
    suspend fun recomputeSnapshot() {
        val today = businessDayProvider.getBusinessDate(clock.wallTimeMillis(), zoneIdProvider()).toString()
        val vaultApps = coreDataRepository.getAllVaultApps()
        val tasksMap = mutableMapOf<String, List<TaskEntity>>()
        for (app in vaultApps) {
            tasksMap[app.packageName] = coreDataRepository.getTasksForApp(app.packageName)
        }
        val completedIds = coreDataRepository.getCompletedTaskIdsForDate(today).toSet()

        cachedSnapshot.set(
            EnforcementSnapshot(
                vaultApps = vaultApps.associateBy { it.packageName },
                tasksForApp = tasksMap,
                completedTaskIds = completedIds,
                businessDate = today
            )
        )
    }

    /**
     * Triggers asynchronous recomputation of the snapshot cache.
     */
    fun refreshSnapshot() {
        coroutineScope.launch {
            try {
                recomputeSnapshot()
            } catch (_: Exception) {
            }
        }
    }

    fun getEnforcementStatus(): TaskAppEnforcementStatus {
        return TaskAppEnforcementStatus.TASK_BASED_UNLOCK_ACTIVE
    }

    /**
     * Evaluates whether an app has task-based unlock approval.
     */
    suspend fun isTaskBasedUnlockApproved(packageName: String): Boolean {
        val details = evaluate(packageName)
        return details.businessUnlockDecision == BusinessUnlockDecision.UNLOCKED
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

        val unlockResult = TaskUnlockPolicy.evaluate(activeTasks.size, completedCount)

        // Keep in-memory snapshot strictly in sync with database state
        recomputeSnapshot()

        return buildEnforcementDetails(
            packageName = packageName,
            isTechnicalLockActive = isTechnicalLockActive,
            technicalPolicyDecision = technicalDecision,
            isVaultApp = isVaultApp,
            allLinkedTasks = allLinkedTasks,
            activeTasks = activeTasks,
            archivedTasksCount = archivedTasksCount,
            completedCount = completedCount,
            incompleteCount = incompleteCount,
            unlockResult = unlockResult
        )
    }

    /**
     * Fast, non-blocking synchronous evaluation reading in-memory snapshot cache.
     * Guaranteed safe for Main Thread in AccessibilityService (< 0.05ms, O(1)).
     */
    fun evaluateSync(packageName: String): AppEnforcementDetails {
        val technicalDecision = policyEngine?.evaluate(packageName) ?: PolicyDecision.ALLOW
        val isTechnicalLockActive = (technicalDecision == PolicyDecision.LOCK)

        val snapshot = cachedSnapshot.get()
        val today = businessDayProvider.getBusinessDate(clock.wallTimeMillis(), zoneIdProvider()).toString()

        // If day rollover happened (passed 04:00), schedule an async recompute
        if (snapshot.businessDate.isNotEmpty() && snapshot.businessDate != today) {
            refreshSnapshot()
        }

        val isVaultApp = snapshot.vaultApps.containsKey(packageName)
        val allLinkedTasks = snapshot.tasksForApp[packageName] ?: emptyList()
        val activeTasks = allLinkedTasks.filter { !it.isArchived }
        val archivedTasksCount = allLinkedTasks.count { it.isArchived }

        var completedCount = 0
        for (task in activeTasks) {
            if (snapshot.completedTaskIds.contains(task.id)) {
                completedCount++
            }
        }
        val incompleteCount = activeTasks.size - completedCount

        val unlockResult = TaskUnlockPolicy.evaluate(activeTasks.size, completedCount)

        return buildEnforcementDetails(
            packageName = packageName,
            isTechnicalLockActive = isTechnicalLockActive,
            technicalPolicyDecision = technicalDecision,
            isVaultApp = isVaultApp,
            allLinkedTasks = allLinkedTasks,
            activeTasks = activeTasks,
            archivedTasksCount = archivedTasksCount,
            completedCount = completedCount,
            incompleteCount = incompleteCount,
            unlockResult = unlockResult
        )
    }

    /**
     * Manually updates the in-memory cache for synchronous fast paths (e.g. in tests or immediate updates).
     */
    fun updateCacheForApp(
        packageName: String,
        isVault: Boolean,
        linkedTasks: List<TaskEntity> = emptyList(),
        completedTaskIds: Set<Long> = emptySet()
    ) {
        val current = cachedSnapshot.get()
        val newVaultApps = current.vaultApps.toMutableMap()
        val newTasksMap = current.tasksForApp.toMutableMap()
        val newCompletedIds = current.completedTaskIds.toMutableSet()

        if (isVault) {
            newVaultApps[packageName] = AppEntity(packageName = packageName, appName = packageName)
            newTasksMap[packageName] = linkedTasks
        } else {
            newVaultApps.remove(packageName)
            newTasksMap.remove(packageName)
        }
        newCompletedIds.addAll(completedTaskIds)

        cachedSnapshot.set(
            current.copy(
                vaultApps = newVaultApps,
                tasksForApp = newTasksMap,
                completedTaskIds = newCompletedIds
            )
        )
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
        incompleteCount: Int,
        unlockResult: BusinessUnlockResult
    ): AppEnforcementDetails {
        // 1. Classification
        val classification = when {
            !isVaultApp -> AppEnforcementClassification.NON_VAULT_APP
            activeTasks.isEmpty() -> AppEnforcementClassification.VAULT_APP_UNLINKED
            else -> AppEnforcementClassification.VAULT_APP_WITH_TASKS
        }

        // 2. Technical Lock has SUPREME PRECEDENCE: never bypassed by any business unlock
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
                requiredTasksCount = unlockResult.requiredCompletedTasks,
                businessUnlockDecision = unlockResult.decision,
                finalAction = EnforcementAction.LOCK,
                reason = EnforcementReason.LOCKED_BY_POLICY
            )
        }

        // 3. Vault Sealing Evaluation
        if (isVaultApp) {
            val (action, reason) = when (unlockResult.decision) {
                BusinessUnlockDecision.UNLOCKED -> {
                    EnforcementAction.ALLOW to EnforcementReason.ALLOWED_UNLOCKED_BY_TASKS
                }
                BusinessUnlockDecision.INSUFFICIENT_COMPLETION -> {
                    EnforcementAction.LOCK to EnforcementReason.LOCKED_INSUFFICIENT_TASKS
                }
                BusinessUnlockDecision.NO_LINKED_TASKS,
                BusinessUnlockDecision.NOT_APPLICABLE,
                @Suppress("DEPRECATION") BusinessUnlockDecision.PENDING_OPEN_01 -> {
                    EnforcementAction.LOCK to EnforcementReason.LOCKED_BY_VAULT_NO_TASK
                }
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
                requiredTasksCount = unlockResult.requiredCompletedTasks,
                businessUnlockDecision = unlockResult.decision,
                finalAction = action,
                reason = reason
            )
        }

        // 4. Allowed App (Not in Vault, and either not a Target or outside locking window/limit)
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
            requiredTasksCount = 0,
            businessUnlockDecision = BusinessUnlockDecision.NOT_APPLICABLE,
            finalAction = EnforcementAction.ALLOW,
            reason = reason
        )
    }
}
