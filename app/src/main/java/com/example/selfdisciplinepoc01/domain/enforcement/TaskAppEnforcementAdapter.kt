package com.example.selfdisciplinepoc01.domain.enforcement

import com.example.selfdisciplinepoc01.data.entity.AppEntity
import com.example.selfdisciplinepoc01.data.entity.TaskEntity
import com.example.selfdisciplinepoc01.data.repository.CoreDataRepository
import com.example.selfdisciplinepoc01.domain.canonical.cycle.CanonicalCycleBoundary
import com.example.selfdisciplinepoc01.domain.canonical.cycle.CanonicalCycleId
import com.example.selfdisciplinepoc01.domain.canonical.cycle.CycleEngine
import com.example.selfdisciplinepoc01.domain.canonical.repository.CanonicalCycleRepository
import com.example.selfdisciplinepoc01.domain.canonical.repository.CanonicalLockEvaluator
import com.example.selfdisciplinepoc01.domain.canonical.repository.CanonicalTaskRepository
import com.example.selfdisciplinepoc01.domain.canonical.repository.CanonicalVaultRepository
import com.example.selfdisciplinepoc01.domain.canonical.task.CanonicalTask
import com.example.selfdisciplinepoc01.domain.canonical.task.TaskCycleState
import com.example.selfdisciplinepoc01.domain.canonical.task.TaskCycleStatus
import com.example.selfdisciplinepoc01.domain.canonical.vault.CanonicalLockDecision
import com.example.selfdisciplinepoc01.domain.canonical.vault.CanonicalLockPolicy
import com.example.selfdisciplinepoc01.domain.canonical.vault.CanonicalVaultApp
import com.example.selfdisciplinepoc01.domain.canonical.vault.LockEvaluationResult
import com.example.selfdisciplinepoc01.domain.canonical.vault.VoucherEffect
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
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicReference

/**
 * Enforcement status regarding Task-based sealing and unlocking.
 */
enum class TaskAppEnforcementStatus {
    @Deprecated("OPEN-01 has been officially decided by Product Owner and implemented in Phase 23.")
    DISABLED_PENDING_OPEN_01,

    TECHNICAL_FOUNDATION_ACTIVE,

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
 */
enum class BusinessUnlockDecision {
    /** Không áp dụng mở khóa nghiệp vụ (không thuộc Vault) */
    NOT_APPLICABLE,

    /** Thuộc Bảo Khố nhưng chưa liên kết nhiệm vụ nào (N = 0) -> SSOT: UNLOCKED */
    NO_LINKED_TASKS,

    /** Thuộc Bảo Khố, có nhiệm vụ nhưng chưa hoàn thành đủ số lượng theo công thức chuẩn -> Khóa */
    INSUFFICIENT_COMPLETION,

    /** Thuộc Bảo Khố, đã hoàn thành đủ số lượng nhiệm vụ yêu cầu (completed >= required) -> Đủ điều kiện mở */
    UNLOCKED,

    @Deprecated("Replaced by explicit decisions above.")
    PENDING_OPEN_01
}

/**
 * Lý do chi tiết của quyết định thực thi.
 */
enum class EnforcementReason {
    /** Bị khóa bởi chính sách kỹ thuật của non-vault app (Target 24/7, Schedule, hoặc Daily Limit) */
    LOCKED_BY_POLICY,

    /** Thuộc Bảo Khố nhưng không có nhiệm vụ liên kết (N = 0) */
    LOCKED_BY_VAULT_NO_TASK,

    /** Thuộc Bảo Khố, chưa hoàn thành đủ số lượng nhiệm vụ yêu cầu theo CanonicalLockPolicy */
    LOCKED_INSUFFICIENT_TASKS,

    /** Được giải phong ấn do đã hoàn thành đủ số lượng nhiệm vụ yêu cầu hoặc có Voucher hiệu lực */
    ALLOWED_UNLOCKED_BY_TASKS,

    /** Được phép mở do không thuộc danh sách phong ấn kỹ thuật và không thuộc Bảo Khố */
    ALLOWED_NOT_PROTECTED,

    /** Được phép mở theo chính sách kỹ thuật (ngoài khung giờ khóa hoặc chưa quá giới hạn) và không thuộc Bảo Khố */
    ALLOWED_BY_POLICY,

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
    val reason: EnforcementReason,
    val canonicalLockResult: LockEvaluationResult? = null
)

/**
 * Immutable in-memory snapshot for 0ms, non-blocking synchronous evaluation in AccessibilityService.
 */
data class EnforcementSnapshot(
    val canonicalVaultApps: Map<String, CanonicalVaultApp> = emptyMap(),
    val canonicalTasksForApp: Map<String, List<CanonicalTask>> = emptyMap(),
    val cycleTaskStates: Map<String, TaskCycleState> = emptyMap(),
    val activeVouchers: List<VoucherEffect> = emptyList(),
    val currentCycleBoundary: CanonicalCycleBoundary? = null,
    val businessDate: String = "",
    // Legacy compatibility fields
    val legacyVaultApps: Map<String, AppEntity> = emptyMap(),
    val legacyTasksForApp: Map<String, List<TaskEntity>> = emptyMap(),
    val legacyCompletedTaskIds: Set<Long> = emptySet()
)

/**
 * Boundary adapter between Canonical Vault/Task domain and Android Runtime enforcement.
 *
 * CANONICAL SSOT AUTHORITY:
 * 1. ONE business lock authority: [CanonicalLockPolicy] / [CanonicalLockEvaluator].
 * 2. [PolicyEngine], Schedule, UsageLimit MUST NOT override Canonical Vault lock state.
 * 3. [TaskUnlockPolicy] is DEPRECATED and strictly removed from the runtime decision path.
 * 4. N = 0 -> UNLOCKED (ALLOW).
 * 5. N = 2 -> Required = 1.
 * 6. Effective Voucher -> UNLOCKED (ALLOW).
 */
class TaskAppEnforcementAdapter(
    private val canonicalVaultRepository: CanonicalVaultRepository? = null,
    private val canonicalTaskRepository: CanonicalTaskRepository? = null,
    private val canonicalCycleRepository: CanonicalCycleRepository? = null,
    private val canonicalLockEvaluator: CanonicalLockEvaluator? = null,
    private val coreDataRepository: CoreDataRepository? = null,
    private val policyEngine: PolicyEngine? = null,
    private val businessDayProvider: BusinessDayProvider = BusinessDayProviderHolder.instance,
    private val clock: Clock = SystemClockImpl(),
    private val zoneIdProvider: () -> ZoneId = { ZoneId.systemDefault() },
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {

    /**
     * Backward-compatible constructor for Phase 19/20/27 usages.
     */
    constructor(
        coreDataRepository: CoreDataRepository,
        policyEngine: PolicyEngine? = null,
        businessDayProvider: BusinessDayProvider = BusinessDayProviderHolder.instance,
        clock: Clock = SystemClockImpl(),
        zoneIdProvider: () -> ZoneId = { ZoneId.systemDefault() }
    ) : this(
        canonicalVaultRepository = null,
        canonicalTaskRepository = null,
        canonicalCycleRepository = null,
        canonicalLockEvaluator = null,
        coreDataRepository = coreDataRepository,
        policyEngine = policyEngine,
        businessDayProvider = businessDayProvider,
        clock = clock,
        zoneIdProvider = zoneIdProvider
    )

    // Thread-safe atomic in-memory snapshot cache
    private val cachedSnapshot = AtomicReference(EnforcementSnapshot())

    init {
        startObserving()
    }

    private fun startObserving() {
        coroutineScope.launch {
            try {
                // Initial compute
                recomputeSnapshot()

                // Observe legacy repository changes if present
                coreDataRepository?.let { repo ->
                    launch {
                        repo.observeVaultApps().collect { recomputeSnapshot() }
                    }
                    launch {
                        repo.observeActiveTasks().collect { recomputeSnapshot() }
                    }
                    launch {
                        repo.observeAllCrossRefs().collect { recomputeSnapshot() }
                    }
                    launch {
                        val today = businessDayProvider.getBusinessDate(clock.wallTimeMillis(), zoneIdProvider()).toString()
                        repo.observeCompletedTaskIdsForDate(today).collect { recomputeSnapshot() }
                    }
                }
            } catch (_: Exception) {
                // Ignore cancellation
            }
        }
    }

    /**
     * Recomputes the full in-memory snapshot from the database (Canonical primary, Legacy fallback).
     */
    suspend fun recomputeSnapshot() {
        val nowInstant = Instant.ofEpochMilli(clock.wallTimeMillis())
        val zoneId = zoneIdProvider()
        val today = businessDayProvider.getBusinessDate(clock.wallTimeMillis(), zoneId).toString()
        val currentCycle = canonicalCycleRepository?.getCurrentCycle(nowInstant)
            ?: CycleEngine.getCurrentCycleBoundary(nowInstant, zoneId)

        // 1. Fetch Canonical Data if available
        val canonicalVaultMap = mutableMapOf<String, CanonicalVaultApp>()
        val canonicalTasksMap = mutableMapOf<String, List<CanonicalTask>>()
        var cycleStatesMap = emptyMap<String, TaskCycleState>()
        var vouchersList = emptyList<VoucherEffect>()

        canonicalVaultRepository?.let { vRepo ->
            val apps = vRepo.getAllVaultApps()
            canonicalVaultMap.putAll(apps.associateBy { it.packageName })
            canonicalTaskRepository?.let { tRepo ->
                for (app in apps) {
                    canonicalTasksMap[app.packageName] = tRepo.getTasksLinkedToApp(app.packageName)
                }
                cycleStatesMap = tRepo.getCycleStates(currentCycle.cycleId)
            }
            vouchersList = vRepo.getActiveVouchers(nowInstant)
        }

        // 2. Fetch Legacy Data if available
        val legacyVaultMap = mutableMapOf<String, AppEntity>()
        val legacyTasksMap = mutableMapOf<String, List<TaskEntity>>()
        var legacyCompletedIds = emptySet<Long>()

        coreDataRepository?.let { repo ->
            val legacyApps = repo.getAllVaultApps()
            legacyVaultMap.putAll(legacyApps.associateBy { it.packageName })
            for (app in legacyApps) {
                legacyTasksMap[app.packageName] = repo.getTasksForApp(app.packageName)
            }
            legacyCompletedIds = repo.getCompletedTaskIdsForDate(today).toSet()
        }

        cachedSnapshot.set(
            EnforcementSnapshot(
                canonicalVaultApps = canonicalVaultMap,
                canonicalTasksForApp = canonicalTasksMap,
                cycleTaskStates = cycleStatesMap,
                activeVouchers = vouchersList,
                currentCycleBoundary = currentCycle,
                businessDate = today,
                legacyVaultApps = legacyVaultMap,
                legacyTasksForApp = legacyTasksMap,
                legacyCompletedTaskIds = legacyCompletedIds
            )
        )
    }

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

    suspend fun isTaskBasedUnlockApproved(packageName: String): Boolean {
        val details = evaluate(packageName)
        return details.businessUnlockDecision == BusinessUnlockDecision.UNLOCKED
    }

    /**
     * Direct evaluation querying database (asynchronous / suspend).
     */
    suspend fun evaluate(packageName: String): AppEnforcementDetails {
        recomputeSnapshot()
        return evaluateSync(packageName)
    }

    /**
     * Fast, non-blocking synchronous evaluation reading in-memory snapshot cache.
     * Guaranteed safe for Main Thread in AccessibilityService (< 0.05ms, O(1)).
     */
    fun evaluateSync(packageName: String): AppEnforcementDetails {
        val snapshot = cachedSnapshot.get()
        val nowInstant = Instant.ofEpochMilli(clock.wallTimeMillis())
        val zoneId = zoneIdProvider()
        val today = businessDayProvider.getBusinessDate(clock.wallTimeMillis(), zoneId).toString()

        // If cycle rollover happened (passed 04:00), schedule an async recompute
        if (snapshot.businessDate.isNotEmpty() && snapshot.businessDate != today) {
            refreshSnapshot()
        }

        val isCanonicalVault = snapshot.canonicalVaultApps.containsKey(packageName)
        val isLegacyVault = snapshot.legacyVaultApps.containsKey(packageName)
        val isVaultApp = isCanonicalVault || isLegacyVault

        val technicalDecision = policyEngine?.evaluate(packageName) ?: PolicyDecision.ALLOW
        val isTechnicalLockActive = (technicalDecision == PolicyDecision.LOCK)

        // 1. CANONICAL VAULT APP EVALUATION
        if (isVaultApp) {
            val (canonicalTasks, cycleStates, activeVouchers) = resolveTasksAndStates(
                packageName = packageName,
                snapshot = snapshot,
                nowInstant = nowInstant
            )

            // Evaluate via MASTER SSOT CanonicalLockPolicy
            val lockResult = CanonicalLockPolicy.evaluateLock(
                packageName = packageName,
                linkedTasks = canonicalTasks,
                cycleTaskStates = cycleStates,
                activeVouchers = activeVouchers,
                evaluationInstant = nowInstant
            )

            val totalLinked = canonicalTasks.size
            val activeTasks = canonicalTasks.filter { !it.isArchived }
            val archivedCount = canonicalTasks.count { it.isArchived }
            val completedCount = lockResult.completedLinkedRewardTasks
            val incompleteCount = lockResult.totalLinkedRewardTasks - completedCount

            val classification = when {
                activeTasks.isEmpty() -> AppEnforcementClassification.VAULT_APP_UNLINKED
                else -> AppEnforcementClassification.VAULT_APP_WITH_TASKS
            }

            val finalAction = if (lockResult.isUnlocked) {
                EnforcementAction.ALLOW
            } else {
                EnforcementAction.LOCK
            }

            val reason = if (lockResult.isUnlocked) {
                EnforcementReason.ALLOWED_UNLOCKED_BY_TASKS
            } else {
                EnforcementReason.LOCKED_INSUFFICIENT_TASKS
            }

            val businessUnlockDecision = if (lockResult.isUnlocked) {
                if (activeTasks.isEmpty()) BusinessUnlockDecision.NO_LINKED_TASKS else BusinessUnlockDecision.UNLOCKED
            } else {
                BusinessUnlockDecision.INSUFFICIENT_COMPLETION
            }

            // CRITICAL SSOT RULE: Technical lock CANNOT override Canonical Vault lock state!
            return AppEnforcementDetails(
                packageName = packageName,
                isTechnicalLockActive = isTechnicalLockActive,
                technicalPolicyDecision = technicalDecision,
                isVaultApp = true,
                classification = classification,
                totalLinkedTasksCount = totalLinked,
                activeLinkedTasksCount = activeTasks.size,
                completedLinkedTasksCount = completedCount,
                incompleteLinkedTasksCount = incompleteCount,
                archivedLinkedTasksCount = archivedCount,
                requiredTasksCount = lockResult.requiredCompletions,
                businessUnlockDecision = businessUnlockDecision,
                finalAction = finalAction,
                reason = reason,
                canonicalLockResult = lockResult
            )
        }

        // 2. NON-VAULT APP EVALUATION (Legacy technical locking applies only to non-vault apps)
        val isConfiguredTarget = (policyEngine?.isTargetConfigured(packageName) == true)
        val finalAction = if (isTechnicalLockActive) EnforcementAction.LOCK else EnforcementAction.ALLOW
        val reason = when {
            isTechnicalLockActive -> EnforcementReason.LOCKED_BY_POLICY
            isConfiguredTarget -> EnforcementReason.ALLOWED_BY_POLICY
            else -> EnforcementReason.ALLOWED_NOT_PROTECTED
        }

        return AppEnforcementDetails(
            packageName = packageName,
            isTechnicalLockActive = isTechnicalLockActive,
            technicalPolicyDecision = technicalDecision,
            isVaultApp = false,
            classification = AppEnforcementClassification.NON_VAULT_APP,
            totalLinkedTasksCount = 0,
            activeLinkedTasksCount = 0,
            completedLinkedTasksCount = 0,
            incompleteLinkedTasksCount = 0,
            archivedLinkedTasksCount = 0,
            requiredTasksCount = 0,
            businessUnlockDecision = BusinessUnlockDecision.NOT_APPLICABLE,
            finalAction = finalAction,
            reason = reason,
            canonicalLockResult = null
        )
    }

    private fun resolveTasksAndStates(
        packageName: String,
        snapshot: EnforcementSnapshot,
        nowInstant: Instant
    ): Triple<List<CanonicalTask>, Map<String, TaskCycleState>, List<VoucherEffect>> {
        if (snapshot.canonicalVaultApps.containsKey(packageName)) {
            val tasks = snapshot.canonicalTasksForApp[packageName] ?: emptyList()
            val states = snapshot.cycleTaskStates
            val vouchers = snapshot.activeVouchers
            return Triple(tasks, states, vouchers)
        }

        // Fallback mapping from legacy structures to Canonical domain
        val legacyTasks = snapshot.legacyTasksForApp[packageName] ?: emptyList()
        val canonicalTasks = legacyTasks.map {
            CanonicalTask(
                id = it.id.toString(),
                title = it.name,
                isArchived = it.isArchived,
                createdAt = Instant.ofEpochMilli(it.createdAtWallMillis)
            )
        }
        val currentCycleId = snapshot.currentCycleBoundary?.cycleId
            ?: CycleEngine.getCurrentCycleBoundary(nowInstant, zoneIdProvider()).cycleId

        val cycleStates = legacyTasks.associate { task ->
            val isCompleted = snapshot.legacyCompletedTaskIds.contains(task.id)
            task.id.toString() to TaskCycleState(
                taskId = task.id.toString(),
                cycleId = currentCycleId,
                status = if (isCompleted) TaskCycleStatus.COMPLETED else TaskCycleStatus.PENDING,
                completedAt = if (isCompleted) nowInstant else null
            )
        }
        return Triple(canonicalTasks, cycleStates, snapshot.activeVouchers)
    }

    /**
     * Manually updates the in-memory cache for synchronous fast paths (e.g. in legacy tests).
     */
    fun updateCacheForApp(
        packageName: String,
        isVault: Boolean,
        linkedTasks: List<TaskEntity> = emptyList(),
        completedTaskIds: Set<Long> = emptySet()
    ) {
        val current = cachedSnapshot.get()
        val newVaultApps = current.legacyVaultApps.toMutableMap()
        val newTasksMap = current.legacyTasksForApp.toMutableMap()
        val newCompletedIds = current.legacyCompletedTaskIds.toMutableSet()

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
                legacyVaultApps = newVaultApps,
                legacyTasksForApp = newTasksMap,
                legacyCompletedTaskIds = newCompletedIds
            )
        )
    }

    /**
     * Directly updates canonical in-memory cache for unit testing or real-time event updates.
     */
    fun updateCanonicalCacheForApp(
        packageName: String,
        isVault: Boolean,
        linkedTasks: List<CanonicalTask> = emptyList(),
        completedTaskIds: Set<String> = emptySet(),
        activeVouchers: List<VoucherEffect> = emptyList(),
        cycleId: CanonicalCycleId? = null
    ) {
        val current = cachedSnapshot.get()
        val nowInstant = Instant.ofEpochMilli(clock.wallTimeMillis())
        val effectiveCycleId = cycleId ?: current.currentCycleBoundary?.cycleId
            ?: CycleEngine.getCurrentCycleBoundary(nowInstant, zoneIdProvider()).cycleId

        val newVaultApps = current.canonicalVaultApps.toMutableMap()
        val newTasksMap = current.canonicalTasksForApp.toMutableMap()
        val newStatesMap = current.cycleTaskStates.toMutableMap()

        if (isVault) {
            newVaultApps[packageName] = CanonicalVaultApp(packageName = packageName, displayName = packageName)
            newTasksMap[packageName] = linkedTasks
            for (task in linkedTasks) {
                val isCompleted = completedTaskIds.contains(task.id)
                newStatesMap[task.id] = TaskCycleState(
                    taskId = task.id,
                    cycleId = effectiveCycleId,
                    status = if (isCompleted) TaskCycleStatus.COMPLETED else TaskCycleStatus.PENDING,
                    completedAt = if (isCompleted) nowInstant else null
                )
            }
        } else {
            newVaultApps.remove(packageName)
            newTasksMap.remove(packageName)
        }

        cachedSnapshot.set(
            current.copy(
                canonicalVaultApps = newVaultApps,
                canonicalTasksForApp = newTasksMap,
                cycleTaskStates = newStatesMap,
                activeVouchers = activeVouchers
            )
        )
    }
}
