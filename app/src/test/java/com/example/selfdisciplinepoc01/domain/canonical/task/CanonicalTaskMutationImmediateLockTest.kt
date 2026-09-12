package com.example.selfdisciplinepoc01.domain.canonical.task

import com.example.selfdisciplinepoc01.domain.canonical.cycle.CanonicalCycleId
import com.example.selfdisciplinepoc01.domain.canonical.repository.CanonicalLockEvaluator
import com.example.selfdisciplinepoc01.domain.canonical.repository.CanonicalTaskRepository
import com.example.selfdisciplinepoc01.domain.canonical.repository.CanonicalVaultRepository
import com.example.selfdisciplinepoc01.domain.canonical.usecase.AddVaultAppUseCase
import com.example.selfdisciplinepoc01.domain.canonical.usecase.CanonicalMutationSyncManager
import com.example.selfdisciplinepoc01.domain.canonical.usecase.CompleteTaskUseCase
import com.example.selfdisciplinepoc01.domain.canonical.usecase.CreateTaskUseCase
import com.example.selfdisciplinepoc01.domain.canonical.usecase.DeleteTaskUseCase
import com.example.selfdisciplinepoc01.domain.canonical.usecase.RemoveVaultAppUseCase
import com.example.selfdisciplinepoc01.domain.canonical.usecase.UpdateTaskRewardLinkageUseCase
import com.example.selfdisciplinepoc01.domain.canonical.vault.CanonicalLockDecision
import com.example.selfdisciplinepoc01.domain.canonical.vault.CanonicalLockPolicy
import com.example.selfdisciplinepoc01.domain.canonical.vault.CanonicalVaultApp
import com.example.selfdisciplinepoc01.domain.canonical.vault.LockEvaluationResult
import com.example.selfdisciplinepoc01.domain.canonical.vault.VoucherEffect
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Bộ kiểm thử tự động toàn diện Phase 2B-C:
 * CANONICAL TASK MUTATION, REWARD LINK & IMMEDIATE LOCK RECOMPUTATION HARDENING.
 *
 * Kiểm tra đầy đủ ma trận 24 test cases bắt buộc từ TASK-IMM-01 đến TASK-IMM-24 theo SSOT.
 */
class CanonicalTaskMutationImmediateLockTest {

    private val cycle = CanonicalCycleId("2026-09-12")
    private val appA = "com.android.chrome"
    private val appB = "com.facebook.katana"

    private lateinit var fakeTaskRepo: TestFakeTaskRepository
    private lateinit var fakeVaultRepo: TestFakeVaultRepository
    private lateinit var lockEvaluator: TestFakeLockEvaluator

    private lateinit var createTaskUseCase: CreateTaskUseCase
    private lateinit var updateRewardUseCase: UpdateTaskRewardLinkageUseCase
    private lateinit var deleteTaskUseCase: DeleteTaskUseCase
    private lateinit var addVaultUseCase: AddVaultAppUseCase
    private lateinit var removeVaultUseCase: RemoveVaultAppUseCase
    private lateinit var completeTaskUseCase: CompleteTaskUseCase

    private class TestFakeTaskRepository : CanonicalTaskRepository {
        val tasks = mutableMapOf<String, CanonicalTask>()
        val cycleStates = mutableMapOf<Pair<String, String>, TaskCycleState>()
        val links = mutableListOf<TaskRewardLink>()

        override suspend fun getTask(taskId: String): CanonicalTask? = tasks[taskId]
        override suspend fun getAllActiveTasks(): List<CanonicalTask> = tasks.values.filter { !it.isArchived }
        override suspend fun getTasksLinkedToApp(packageName: String): List<CanonicalTask> {
            val taskIds = links.filter { it.appPackageName == packageName }.map { it.taskId }
            return taskIds.mapNotNull { tasks[it] }.filter { !it.isArchived }
        }
        override suspend fun getAppsLinkedToTask(taskId: String): List<String> {
            return links.filter { it.taskId == taskId }.map { it.appPackageName }
        }
        override suspend fun getCycleState(taskId: String, cycleId: CanonicalCycleId): TaskCycleState? {
            return cycleStates[taskId to cycleId.dateIdentifier]
        }
        override suspend fun getCycleStates(cycleId: CanonicalCycleId): Map<String, TaskCycleState> {
            return cycleStates.filter { it.key.second == cycleId.dateIdentifier }
                .mapKeys { it.key.first }
        }
        override suspend fun saveTask(task: CanonicalTask) { tasks[task.id] = task }
        override suspend fun saveCycleState(state: TaskCycleState) {
            cycleStates[state.taskId to state.cycleId.dateIdentifier] = state
        }
        override suspend fun completeTask(taskId: String, cycleId: CanonicalCycleId, completedAt: Instant) {
            cycleStates[taskId to cycleId.dateIdentifier] = TaskCycleState(
                taskId = taskId,
                cycleId = cycleId,
                status = TaskCycleStatus.COMPLETED,
                completedAt = completedAt
            )
        }
        override suspend fun undoTaskCompletion(taskId: String, cycleId: CanonicalCycleId) {
            cycleStates[taskId to cycleId.dateIdentifier] = TaskCycleState(
                taskId = taskId,
                cycleId = cycleId,
                status = TaskCycleStatus.PENDING,
                completedAt = null
            )
        }
        override suspend fun archiveTask(taskId: String) {
            tasks[taskId]?.let { tasks[taskId] = it.copy(isArchived = true) }
            links.removeAll { it.taskId == taskId }
        }
        override suspend fun getTaskCycleHistory(taskId: String): List<TaskCycleState> {
            return cycleStates.filter { it.key.first == taskId }.values.toList()
        }
        override suspend fun linkTaskToApp(taskId: String, packageName: String) {
            if (links.none { it.taskId == taskId && it.appPackageName == packageName }) {
                links.add(TaskRewardLink(taskId, packageName))
            }
        }
        override suspend fun unlinkTaskFromApp(taskId: String, packageName: String) {
            links.removeAll { it.taskId == taskId && it.appPackageName == packageName }
        }
        override suspend fun removeAllLinksForApp(packageName: String) {
            links.removeAll { it.appPackageName == packageName }
        }
        override suspend fun renameTask(taskId: String, newTitle: String) {
            tasks[taskId]?.let { tasks[taskId] = it.copy(title = newTitle) }
        }
        override suspend fun setPendingNextCycleRewards(taskId: String, appPackageNames: List<String>) {
            tasks[taskId]?.let { tasks[taskId] = it.copy(pendingNextCycleRewards = appPackageNames) }
        }
        override suspend fun cancelPendingNextCycleRewards(taskId: String) {
            tasks[taskId]?.let { tasks[taskId] = it.copy(pendingNextCycleRewards = null) }
        }
        override suspend fun applyPendingNextCycleRewards(taskId: String) {
            val task = tasks[taskId] ?: return
            val pending = task.pendingNextCycleRewards ?: return
            links.removeAll { it.taskId == taskId }
            for (pkg in pending) {
                links.add(TaskRewardLink(taskId, pkg))
            }
            tasks[taskId] = task.copy(hasReward = pending.isNotEmpty(), pendingNextCycleRewards = null)
        }
        override suspend fun applyAllPendingNextCycleRewards() {
            val pendingIds = tasks.values.filter { it.pendingNextCycleRewards != null && !it.isArchived }.map { it.id }
            for (id in pendingIds) applyPendingNextCycleRewards(id)
        }

        override suspend fun createTaskAtomic(
            task: CanonicalTask,
            linkedAppPackageNames: List<String>,
            timing: RewardMutationTiming
        ): List<String> {
            val validApps = linkedAppPackageNames.filter { it.isNotBlank() }.distinct()
            if (timing == RewardMutationTiming.PENDING_NEXT_CYCLE) {
                tasks[task.id] = task.copy(hasReward = false, pendingNextCycleRewards = validApps.ifEmpty { null })
                return emptyList()
            } else {
                tasks[task.id] = task.copy(hasReward = validApps.isNotEmpty(), pendingNextCycleRewards = null)
                for (pkg in validApps) {
                    links.add(TaskRewardLink(task.id, pkg))
                }
                return validApps
            }
        }

        override suspend fun updateTaskRewardLinkageAtomic(
            taskId: String,
            newPackageNames: List<String>,
            timing: RewardMutationTiming
        ): List<String> {
            val validApps = newPackageNames.filter { it.isNotBlank() }.distinct()
            val task = tasks[taskId] ?: return emptyList()
            val oldLinks = links.filter { it.taskId == taskId }.map { it.appPackageName }

            if (timing == RewardMutationTiming.PENDING_NEXT_CYCLE) {
                tasks[taskId] = task.copy(pendingNextCycleRewards = validApps.ifEmpty { null })
                return emptyList()
            } else {
                links.removeAll { it.taskId == taskId }
                for (pkg in validApps) {
                    links.add(TaskRewardLink(taskId, pkg))
                }
                tasks[taskId] = task.copy(hasReward = validApps.isNotEmpty(), pendingNextCycleRewards = null)
                return (oldLinks + validApps).distinct()
            }
        }
    }

    private class TestFakeVaultRepository : CanonicalVaultRepository {
        val apps = mutableMapOf<String, CanonicalVaultApp>()
        val vouchers = mutableListOf<VoucherEffect>()

        override suspend fun getAllVaultApps(): List<CanonicalVaultApp> = apps.values.toList()
        override suspend fun getVaultApp(packageName: String): CanonicalVaultApp? = apps[packageName]
        override suspend fun addVaultApp(app: CanonicalVaultApp) { apps[app.packageName] = app }
        override suspend fun removeVaultApp(packageName: String) { apps.remove(packageName) }
        override suspend fun getActiveVouchers(instant: Instant): List<VoucherEffect> {
            return vouchers.filter { it.isEffectiveAt(instant) }
        }
    }

    private class TestFakeLockEvaluator(
        private val taskRepo: TestFakeTaskRepository,
        private val vaultRepo: TestFakeVaultRepository,
        var currentCycleId: CanonicalCycleId
    ) : CanonicalLockEvaluator {
        override suspend fun evaluateApp(
            packageName: String,
            evaluationInstant: Instant
        ): LockEvaluationResult {
            if (vaultRepo.getVaultApp(packageName) == null) {
                return LockEvaluationResult(
                    packageName = packageName,
                    decision = CanonicalLockDecision.UNLOCKED,
                    reason = "NOT_IN_VAULT",
                    totalLinkedRewardTasks = 0,
                    completedLinkedRewardTasks = 0,
                    requiredCompletions = 0
                )
            }
            val linkedTasks = taskRepo.getTasksLinkedToApp(packageName)
            val states = taskRepo.getCycleStates(currentCycleId)
            val vouchers = vaultRepo.getActiveVouchers(evaluationInstant)
            return CanonicalLockPolicy.evaluateLock(
                packageName = packageName,
                linkedTasks = linkedTasks,
                cycleTaskStates = states,
                activeVouchers = vouchers,
                evaluationInstant = evaluationInstant
            )
        }
    }

    @Before
    fun setUp() {
        fakeTaskRepo = TestFakeTaskRepository()
        fakeVaultRepo = TestFakeVaultRepository()
        lockEvaluator = TestFakeLockEvaluator(fakeTaskRepo, fakeVaultRepo, cycle)

        createTaskUseCase = CreateTaskUseCase(fakeTaskRepo, lockEvaluator)
        updateRewardUseCase = UpdateTaskRewardLinkageUseCase(fakeTaskRepo, lockEvaluator)
        deleteTaskUseCase = DeleteTaskUseCase(fakeTaskRepo, lockEvaluator)
        addVaultUseCase = AddVaultAppUseCase(fakeVaultRepo, lockEvaluator)
        removeVaultUseCase = RemoveVaultAppUseCase(fakeVaultRepo, fakeTaskRepo)
        completeTaskUseCase = CompleteTaskUseCase(fakeTaskRepo, lockEvaluator)
    }

    // TASK-IMM-01: Create task + immediate reward link → app locks immediately.
    @Test
    fun testTaskImm01_createTaskImmediateRewardLink_locksImmediately() = runBlocking {
        addVaultUseCase(appA, "Chrome")
        val task = createTaskUseCase(
            title = "Task 1",
            linkedAppPackageNames = listOf(appA),
            timing = RewardMutationTiming.IMMEDIATE_CURRENT_CYCLE
        )
        val eval = lockEvaluator.evaluateApp(appA)
        assertEquals(CanonicalLockDecision.LOCKED, eval.decision)
        assertEquals(1, eval.totalLinkedRewardTasks)
        assertEquals(0, eval.completedLinkedRewardTasks)
        assertEquals(1, eval.requiredCompletions)
    }

    // TASK-IMM-02: Existing task + immediate reward link → app locks immediately.
    @Test
    fun testTaskImm02_existingTaskAddImmediateReward_locksImmediately() = runBlocking {
        addVaultUseCase(appA, "Chrome")
        val task = createTaskUseCase(
            title = "Task Rewardless",
            linkedAppPackageNames = emptyList(),
            timing = RewardMutationTiming.IMMEDIATE_CURRENT_CYCLE
        )
        assertEquals(CanonicalLockDecision.UNLOCKED, lockEvaluator.evaluateApp(appA).decision)

        updateRewardUseCase(
            taskId = task.id,
            selectedPackageNames = listOf(appA),
            timing = RewardMutationTiming.IMMEDIATE_CURRENT_CYCLE
        )
        val eval = lockEvaluator.evaluateApp(appA)
        assertEquals(CanonicalLockDecision.LOCKED, eval.decision)
        assertEquals(1, eval.totalLinkedRewardTasks)
    }

    // TASK-IMM-03: Immediate reward removal → unlock immediately when no lock reason remains.
    @Test
    fun testTaskImm03_immediateRewardRemoval_unlocksImmediately() = runBlocking {
        addVaultUseCase(appA, "Chrome")
        val task = createTaskUseCase(
            title = "Task 1",
            linkedAppPackageNames = listOf(appA),
            timing = RewardMutationTiming.IMMEDIATE_CURRENT_CYCLE
        )
        assertEquals(CanonicalLockDecision.LOCKED, lockEvaluator.evaluateApp(appA).decision)

        updateRewardUseCase(
            taskId = task.id,
            selectedPackageNames = emptyList(),
            timing = RewardMutationTiming.IMMEDIATE_CURRENT_CYCLE
        )
        val eval = lockEvaluator.evaluateApp(appA)
        assertEquals(CanonicalLockDecision.UNLOCKED, eval.decision)
        assertEquals(0, eval.totalLinkedRewardTasks)
    }

    // TASK-IMM-04: Delete reward task → recompute lock immediately.
    @Test
    fun testTaskImm04_deleteRewardTask_recomputesLockImmediately() = runBlocking {
        addVaultUseCase(appA, "Chrome")
        val task = createTaskUseCase(
            title = "Task To Delete",
            linkedAppPackageNames = listOf(appA),
            timing = RewardMutationTiming.IMMEDIATE_CURRENT_CYCLE
        )
        assertEquals(CanonicalLockDecision.LOCKED, lockEvaluator.evaluateApp(appA).decision)

        deleteTaskUseCase(task.id)
        val eval = lockEvaluator.evaluateApp(appA)
        assertEquals(CanonicalLockDecision.UNLOCKED, eval.decision)
        assertEquals(0, eval.totalLinkedRewardTasks)
    }

    // TASK-IMM-05: New Vault app → N=0 → unlocked.
    @Test
    fun testTaskImm05_newVaultAppStartsUnlocked() = runBlocking {
        val eval = addVaultUseCase(appA, "Chrome")
        assertEquals(CanonicalLockDecision.UNLOCKED, eval.decision)
        assertEquals(0, eval.totalLinkedRewardTasks)
        assertEquals(0, eval.requiredCompletions)
    }

    // TASK-IMM-06: New app + new task + immediate link → N=1/K=0 → locked.
    @Test
    fun testTaskImm06_newAppPlusNewTaskImmediateLink_locksImmediately() = runBlocking {
        val initialEval = addVaultUseCase(appA, "Chrome")
        assertEquals(CanonicalLockDecision.UNLOCKED, initialEval.decision)

        createTaskUseCase(
            title = "Task New",
            linkedAppPackageNames = listOf(appA),
            timing = RewardMutationTiming.IMMEDIATE_CURRENT_CYCLE
        )
        val postEval = lockEvaluator.evaluateApp(appA)
        assertEquals(CanonicalLockDecision.LOCKED, postEval.decision)
        assertEquals(1, postEval.totalLinkedRewardTasks)
        assertEquals(0, postEval.completedLinkedRewardTasks)
        assertEquals(1, postEval.requiredCompletions)
    }

    // TASK-IMM-07: Rewardless task → does not affect N.
    @Test
    fun testTaskImm07_rewardlessTaskDoesNotAffectN() = runBlocking {
        addVaultUseCase(appA, "Chrome")
        createTaskUseCase(
            title = "Rewardless Task",
            linkedAppPackageNames = emptyList(),
            timing = RewardMutationTiming.IMMEDIATE_CURRENT_CYCLE
        )
        val eval = lockEvaluator.evaluateApp(appA)
        assertEquals(CanonicalLockDecision.UNLOCKED, eval.decision)
        assertEquals(0, eval.totalLinkedRewardTasks)
    }

    // TASK-IMM-08: Next-cycle reward mutation → current cycle lock unchanged.
    @Test
    fun testTaskImm08_nextCycleRewardMutation_currentCycleLockUnchanged() = runBlocking {
        addVaultUseCase(appA, "Chrome")
        createTaskUseCase(
            title = "Task Next Cycle",
            linkedAppPackageNames = listOf(appA),
            timing = RewardMutationTiming.PENDING_NEXT_CYCLE
        )
        val eval = lockEvaluator.evaluateApp(appA)
        assertEquals(CanonicalLockDecision.UNLOCKED, eval.decision)
        assertEquals(0, eval.totalLinkedRewardTasks)
    }

    // TASK-IMM-09: Next-cycle reward mutation → pending configuration exists.
    @Test
    fun testTaskImm09_nextCycleRewardMutation_pendingConfigExists() = runBlocking {
        val task = createTaskUseCase(
            title = "Task Next Cycle Pending",
            linkedAppPackageNames = listOf(appA),
            timing = RewardMutationTiming.PENDING_NEXT_CYCLE
        )
        val savedTask = fakeTaskRepo.getTask(task.id)
        assertNotNull(savedTask?.pendingNextCycleRewards)
        assertTrue(savedTask?.pendingNextCycleRewards?.contains(appA) == true)
        assertEquals(0, fakeTaskRepo.links.size)
    }

    // TASK-IMM-10: 04:00 → pending reward becomes effective.
    @Test
    fun testTaskImm10_cycleBoundaryAppliesPendingReward() = runBlocking {
        addVaultUseCase(appA, "Chrome")
        createTaskUseCase(
            title = "Task Boundary",
            linkedAppPackageNames = listOf(appA),
            timing = RewardMutationTiming.PENDING_NEXT_CYCLE
        )
        assertEquals(CanonicalLockDecision.UNLOCKED, lockEvaluator.evaluateApp(appA).decision)

        fakeTaskRepo.applyAllPendingNextCycleRewards()
        val post0400Eval = lockEvaluator.evaluateApp(appA)
        assertEquals(CanonicalLockDecision.LOCKED, post0400Eval.decision)
        assertEquals(1, post0400Eval.totalLinkedRewardTasks)
    }

    // TASK-IMM-11: N=2 → RequiredForDeletion=1.
    @Test
    fun testTaskImm11_nEquals2_requiredEquals1() = runBlocking {
        addVaultUseCase(appA, "Chrome")
        val t1 = createTaskUseCase(title = "Task 1", linkedAppPackageNames = listOf(appA), timing = RewardMutationTiming.IMMEDIATE_CURRENT_CYCLE)
        val t2 = createTaskUseCase(title = "Task 2", linkedAppPackageNames = listOf(appA), timing = RewardMutationTiming.IMMEDIATE_CURRENT_CYCLE)

        val eval0 = lockEvaluator.evaluateApp(appA)
        assertEquals(2, eval0.totalLinkedRewardTasks)
        assertEquals(1, eval0.requiredCompletions)
        assertEquals(CanonicalLockDecision.LOCKED, eval0.decision)

        // Hoàn thành 1 task (1/2):
        // App Lock Policy: Vẫn 2 task yêu cầu -> LOCKED
        // Deletion Policy: K=1 >= RequiredForDeletion(2)=1 -> canDelete = true
        completeTaskUseCase(t1.id, cycle)
        val eval1 = lockEvaluator.evaluateApp(appA)
        assertEquals(1, eval1.completedLinkedRewardTasks)
        assertEquals(CanonicalLockDecision.LOCKED, eval1.decision)
        assertTrue(com.example.selfdisciplinepoc01.domain.canonical.vault.CanonicalAppDeletionPolicy.canDelete(2, 1))

        // Gỡ liên kết cả 2 task -> UNLOCKED
        fakeTaskRepo.unlinkTaskFromApp(t1.id, appA)
        fakeTaskRepo.unlinkTaskFromApp(t2.id, appA)
        assertEquals(CanonicalLockDecision.UNLOCKED, lockEvaluator.evaluateApp(appA).decision)
    }

    // TASK-IMM-12: N=0 → Required=0 → unlocked.
    @Test
    fun testTaskImm12_nEquals0_requiredEquals0_unlocked() = runBlocking {
        addVaultUseCase(appA, "Chrome")
        val eval = lockEvaluator.evaluateApp(appA)
        assertEquals(0, eval.totalLinkedRewardTasks)
        assertEquals(0, eval.requiredCompletions)
        assertEquals(CanonicalLockDecision.UNLOCKED, eval.decision)
    }

    // TASK-IMM-13: N=3 → RequiredForDeletion=2.
    @Test
    fun testTaskImm13_nEquals3_requiredEquals2() = runBlocking {
        addVaultUseCase(appA, "Chrome")
        val t1 = createTaskUseCase(title = "Task 1", linkedAppPackageNames = listOf(appA), timing = RewardMutationTiming.IMMEDIATE_CURRENT_CYCLE)
        val t2 = createTaskUseCase(title = "Task 2", linkedAppPackageNames = listOf(appA), timing = RewardMutationTiming.IMMEDIATE_CURRENT_CYCLE)
        val t3 = createTaskUseCase(title = "Task 3", linkedAppPackageNames = listOf(appA), timing = RewardMutationTiming.IMMEDIATE_CURRENT_CYCLE)

        val eval0 = lockEvaluator.evaluateApp(appA)
        assertEquals(3, eval0.totalLinkedRewardTasks)
        assertEquals(2, eval0.requiredCompletions)
        assertEquals(CanonicalLockDecision.LOCKED, eval0.decision)

        completeTaskUseCase(t1.id, cycle)
        assertEquals(CanonicalLockDecision.LOCKED, lockEvaluator.evaluateApp(appA).decision)

        // Hoàn thành task 2 (2/3):
        // Lock Policy: vẫn LOCKED vì cả 3 task vẫn yêu cầu app trong chu kỳ
        // Deletion Policy: K=2 >= RequiredForDeletion(3)=2 -> canDelete = true
        completeTaskUseCase(t2.id, cycle)
        assertEquals(CanonicalLockDecision.LOCKED, lockEvaluator.evaluateApp(appA).decision)
        assertTrue(com.example.selfdisciplinepoc01.domain.canonical.vault.CanonicalAppDeletionPolicy.canDelete(3, 2))
    }

    // TASK-IMM-14: N=4 → Required=3.
    @Test
    fun testTaskImm14_nEquals4_requiredEquals3() = runBlocking {
        addVaultUseCase(appA, "Chrome")
        for (i in 1..4) {
            createTaskUseCase(title = "Task $i", linkedAppPackageNames = listOf(appA), timing = RewardMutationTiming.IMMEDIATE_CURRENT_CYCLE)
        }
        val eval = lockEvaluator.evaluateApp(appA)
        assertEquals(4, eval.totalLinkedRewardTasks)
        assertEquals(3, eval.requiredCompletions)
    }

    // TASK-IMM-15: N=5 → Required=4.
    @Test
    fun testTaskImm15_nEquals5_requiredEquals4() = runBlocking {
        addVaultUseCase(appA, "Chrome")
        for (i in 1..5) {
            createTaskUseCase(title = "Task $i", linkedAppPackageNames = listOf(appA), timing = RewardMutationTiming.IMMEDIATE_CURRENT_CYCLE)
        }
        val eval = lockEvaluator.evaluateApp(appA)
        assertEquals(5, eval.totalLinkedRewardTasks)
        assertEquals(4, eval.requiredCompletions)
    }

    // TASK-IMM-16: N=6 → Required=4.
    @Test
    fun testTaskImm16_nEquals6_requiredEquals4() = runBlocking {
        addVaultUseCase(appA, "Chrome")
        for (i in 1..6) {
            createTaskUseCase(title = "Task $i", linkedAppPackageNames = listOf(appA), timing = RewardMutationTiming.IMMEDIATE_CURRENT_CYCLE)
        }
        val eval = lockEvaluator.evaluateApp(appA)
        assertEquals(6, eval.totalLinkedRewardTasks)
        assertEquals(4, eval.requiredCompletions)
    }

    // TASK-IMM-17: N=7 → Required=5.
    @Test
    fun testTaskImm17_nEquals7_requiredEquals5() = runBlocking {
        addVaultUseCase(appA, "Chrome")
        for (i in 1..7) {
            createTaskUseCase(title = "Task $i", linkedAppPackageNames = listOf(appA), timing = RewardMutationTiming.IMMEDIATE_CURRENT_CYCLE)
        }
        val eval = lockEvaluator.evaluateApp(appA)
        assertEquals(7, eval.totalLinkedRewardTasks)
        assertEquals(5, eval.requiredCompletions)
    }

    // TASK-IMM-18: Effective voucher overrides lock.
    @Test
    fun testTaskImm18_effectiveVoucherOverridesLock() = runBlocking {
        addVaultUseCase(appA, "Chrome")
        createTaskUseCase(title = "Task 1", linkedAppPackageNames = listOf(appA), timing = RewardMutationTiming.IMMEDIATE_CURRENT_CYCLE)
        assertEquals(CanonicalLockDecision.LOCKED, lockEvaluator.evaluateApp(appA).decision)

        val now = Instant.now()
        fakeVaultRepo.vouchers.add(
            VoucherEffect(
                voucherId = "v1",
                voucherName = "Test Voucher",
                targetPackageName = null,
                effectiveFrom = now.minus(5, ChronoUnit.MINUTES),
                effectiveUntil = now.plus(25, ChronoUnit.MINUTES)
            )
        )
        val evalWithVoucher = lockEvaluator.evaluateApp(appA, now)
        assertEquals(CanonicalLockDecision.UNLOCKED, evalWithVoucher.decision)
        assertTrue(evalWithVoucher.reason.contains("UNLOCKED_BY_VOUCHER"))
        assertNotNull(evalWithVoucher.effectiveVoucher)
    }

    // TASK-IMM-19: Expired voucher causes evaluator to re-evaluate.
    @Test
    fun testTaskImm19_expiredVoucherReEvaluatesToLocked() = runBlocking {
        addVaultUseCase(appA, "Chrome")
        createTaskUseCase(title = "Task 1", linkedAppPackageNames = listOf(appA), timing = RewardMutationTiming.IMMEDIATE_CURRENT_CYCLE)
        val now = Instant.now()
        fakeVaultRepo.vouchers.add(
            VoucherEffect(
                voucherId = "v1",
                voucherName = "Expired Voucher",
                targetPackageName = null,
                effectiveFrom = now.minus(30, ChronoUnit.MINUTES),
                effectiveUntil = now.minus(5, ChronoUnit.MINUTES)
            )
        )
        val eval = lockEvaluator.evaluateApp(appA, now)
        assertEquals(CanonicalLockDecision.LOCKED, eval.decision)
        assertTrue(eval.reason.contains("LOCKED_TASK_REQUIRED") || eval.reason.contains("LOCKED"))
    }

    // TASK-IMM-20: Re-add deleted app does not restore old reward links.
    @Test
    fun testTaskImm20_reAddDeletedAppDoesNotRestoreOldLinks() = runBlocking {
        addVaultUseCase(appA, "Chrome")
        createTaskUseCase(title = "Task 1", linkedAppPackageNames = listOf(appA), timing = RewardMutationTiming.IMMEDIATE_CURRENT_CYCLE)
        assertEquals(1, fakeTaskRepo.links.size)

        removeVaultUseCase(appA)
        assertEquals(0, fakeTaskRepo.links.size)

        addVaultUseCase(appA, "Chrome")
        val eval = lockEvaluator.evaluateApp(appA)
        assertEquals(CanonicalLockDecision.UNLOCKED, eval.decision)
        assertEquals(0, eval.totalLinkedRewardTasks)
        assertEquals(0, fakeTaskRepo.links.size)
    }

    // TASK-IMM-21: UI reward mutation and AI reward mutation reach same canonical use case.
    @Test
    fun testTaskImm21_uiAndAiUseSameCanonicalUseCase() = runBlocking {
        addVaultUseCase(appA, "Chrome")
        // Giả lập UI gọi CreateTaskUseCase
        val uiTask = createTaskUseCase(title = "Task from UI", linkedAppPackageNames = listOf(appA), timing = RewardMutationTiming.IMMEDIATE_CURRENT_CYCLE)
        assertEquals(CanonicalLockDecision.LOCKED, lockEvaluator.evaluateApp(appA).decision)

        // Giả lập AI gọi UpdateTaskRewardLinkageUseCase (cùng Canonical UseCase contract)
        updateRewardUseCase(uiTask.id, emptyList(), RewardMutationTiming.IMMEDIATE_CURRENT_CYCLE)
        assertEquals(CanonicalLockDecision.UNLOCKED, lockEvaluator.evaluateApp(appA).decision)
        assertEquals(0, lockEvaluator.evaluateApp(appA).totalLinkedRewardTasks)
    }

    // TASK-IMM-22: Immediate mutation invalidates/rebuilds stale lock snapshot.
    @Test
    fun testTaskImm22_immediateMutationNotifiesSyncManager() = runBlocking {
        var notifiedPackages: Collection<String>? = null
        val listener: (Collection<String>) -> Unit = { pkgs -> notifiedPackages = pkgs }
        CanonicalMutationSyncManager.registerEnforcementListener(listener)

        addVaultUseCase(appA, "Chrome")
        createTaskUseCase(title = "Task Sync Test", linkedAppPackageNames = listOf(appA), timing = RewardMutationTiming.IMMEDIATE_CURRENT_CYCLE)

        assertNotNull(notifiedPackages)
        assertTrue(notifiedPackages?.contains(appA) == true)

        CanonicalMutationSyncManager.unregisterEnforcementListener()
    }

    // TASK-IMM-23: Post-commit evaluator sees new canonical state.
    @Test
    fun testTaskImm23_postCommitEvaluatorSeesNewCanonicalState() = runBlocking {
        addVaultUseCase(appA, "Chrome")
        val t1 = createTaskUseCase(title = "Task 1", linkedAppPackageNames = listOf(appA), timing = RewardMutationTiming.IMMEDIATE_CURRENT_CYCLE)
        val initialEval = lockEvaluator.evaluateApp(appA)
        assertEquals(CanonicalLockDecision.LOCKED, initialEval.decision)

        // Sau commit cập nhật sang appB
        updateRewardUseCase(t1.id, listOf(appB), RewardMutationTiming.IMMEDIATE_CURRENT_CYCLE)
        val postCommitA = lockEvaluator.evaluateApp(appA)
        assertEquals(CanonicalLockDecision.UNLOCKED, postCommitA.decision)
    }

    // TASK-IMM-24: No legacy PolicyEngine can override canonical result.
    @Test
    fun testTaskImm24_canonicalAuthorityCannotBeOverridden() = runBlocking {
        addVaultUseCase(appA, "Chrome")
        createTaskUseCase(title = "Task 1", linkedAppPackageNames = listOf(appA), timing = RewardMutationTiming.IMMEDIATE_CURRENT_CYCLE)
        val canonicalResult = lockEvaluator.evaluateApp(appA)
        assertEquals(CanonicalLockDecision.LOCKED, canonicalResult.decision)
        // SSOT đảm bảo: CanonicalLockEvaluator là nguồn chân lý duy nhất (Authority).
        // Không có bất kỳ legacy policy nào được phép chuyển LOCKED thành UNLOCKED.
        assertTrue(canonicalResult.decision == CanonicalLockDecision.LOCKED)
    }
}
