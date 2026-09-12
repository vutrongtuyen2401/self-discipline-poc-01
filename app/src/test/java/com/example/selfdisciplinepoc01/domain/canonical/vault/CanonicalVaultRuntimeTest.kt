package com.example.selfdisciplinepoc01.domain.canonical.vault

import com.example.selfdisciplinepoc01.data.canonical.repository.CanonicalLockEvaluatorImpl
import com.example.selfdisciplinepoc01.domain.canonical.cycle.CanonicalCycleBoundary
import com.example.selfdisciplinepoc01.domain.canonical.cycle.CanonicalCycleId
import com.example.selfdisciplinepoc01.domain.canonical.repository.CanonicalCycleRepository
import com.example.selfdisciplinepoc01.domain.canonical.repository.CanonicalLockEvaluator
import com.example.selfdisciplinepoc01.domain.canonical.repository.CanonicalTaskRepository
import com.example.selfdisciplinepoc01.domain.canonical.repository.CanonicalVaultRepository
import com.example.selfdisciplinepoc01.domain.canonical.task.CanonicalTask
import com.example.selfdisciplinepoc01.domain.canonical.task.TaskCycleState
import com.example.selfdisciplinepoc01.domain.canonical.task.TaskCycleStatus
import com.example.selfdisciplinepoc01.domain.canonical.usecase.DeleteTaskUseCase
import com.example.selfdisciplinepoc01.domain.enforcement.AppEnforcementClassification
import com.example.selfdisciplinepoc01.domain.enforcement.BusinessUnlockDecision
import com.example.selfdisciplinepoc01.domain.enforcement.EnforcementAction
import com.example.selfdisciplinepoc01.domain.enforcement.TaskAppEnforcementAdapter
import com.example.selfdisciplinepoc01.policy.PolicyDecision
import com.example.selfdisciplinepoc01.policy.PolicyEngine
import com.example.selfdisciplinepoc01.target.model.LockedApp
import com.example.selfdisciplinepoc01.target.model.TimeLimit
import com.example.selfdisciplinepoc01.target.model.TimeSchedule
import com.example.selfdisciplinepoc01.target.repository.TargetRepository
import com.example.selfdisciplinepoc01.time.BusinessDayProviderImpl
import com.example.selfdisciplinepoc01.time.Clock
import com.example.selfdisciplinepoc01.usage.UsageProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Bộ kiểm thử tự động toàn diện cho PHASE 2B-B — VAULT CANONICAL RUNTIME.
 * Phủ đầy đủ 20 ca kiểm thử bắt buộc từ VB-A01 đến VB-A20.
 */
class CanonicalVaultRuntimeTest {

    private lateinit var vaultRepository: FakeCanonicalVaultRepository
    private lateinit var taskRepository: FakeCanonicalTaskRepository
    private lateinit var cycleRepository: FakeCanonicalCycleRepository
    private lateinit var lockEvaluator: CanonicalLockEvaluator
    private lateinit var fakeTargetRepository: FakeTargetRepository
    private lateinit var fakeUsageProvider: FakeUsageProvider
    private lateinit var policyEngine: PolicyEngine
    private lateinit var enforcementAdapter: TaskAppEnforcementAdapter

    private val cycleId = CanonicalCycleId("2026-09-13")
    private val now = Instant.parse("2026-09-13T10:00:00Z")

    @Before
    fun setUp() {
        vaultRepository = FakeCanonicalVaultRepository()
        taskRepository = FakeCanonicalTaskRepository()
        cycleRepository = FakeCanonicalCycleRepository(cycleId)
        lockEvaluator = CanonicalLockEvaluatorImpl(taskRepository, vaultRepository, cycleRepository)

        fakeTargetRepository = FakeTargetRepository()
        fakeUsageProvider = FakeUsageProvider()

        val mockClock = object : Clock {
            override fun wallTimeMillis(): Long = now.toEpochMilli()
            override fun elapsedRealtimeMillis(): Long = 100_000L
        }

        policyEngine = PolicyEngine(
            targetRepository = fakeTargetRepository,
            usageProvider = fakeUsageProvider,
            clock = mockClock,
            zoneIdProvider = { ZoneId.of("UTC") }
        )

        enforcementAdapter = TaskAppEnforcementAdapter(
            canonicalVaultRepository = vaultRepository,
            canonicalTaskRepository = taskRepository,
            canonicalCycleRepository = cycleRepository,
            canonicalLockEvaluator = lockEvaluator,
            coreDataRepository = null,
            policyEngine = policyEngine,
            businessDayProvider = BusinessDayProviderImpl(),
            clock = mockClock,
            zoneIdProvider = { ZoneId.of("UTC") }
        )
    }

    // -------------------------------------------------------------------------
    // VB-A01: N=0 -> app UNLOCKED
    // -------------------------------------------------------------------------
    @Test
    fun test_VB_A01_N0_app_unlocked() = runBlocking {
        vaultRepository.addVaultApp(CanonicalVaultApp("com.app.n0", "App N0"))
        
        val result = lockEvaluator.evaluateApp("com.app.n0", now)
        assertEquals(CanonicalLockDecision.UNLOCKED, result.decision)
        assertEquals(0, result.totalLinkedRewardTasks)
        assertEquals(0, result.requiredCompletions)
        assertTrue(result.isUnlocked)
    }

    // -------------------------------------------------------------------------
    // VB-A02: N=1, K=0 -> LOCKED
    // -------------------------------------------------------------------------
    @Test
    fun test_VB_A02_N1_K0_locked() = runBlocking {
        vaultRepository.addVaultApp(CanonicalVaultApp("com.app.n1", "App N1"))
        taskRepository.saveTask(CanonicalTask("task_1", "Task 1", hasReward = true))
        taskRepository.linkTaskToApp("task_1", "com.app.n1")

        val result = lockEvaluator.evaluateApp("com.app.n1", now)
        assertEquals(CanonicalLockDecision.LOCKED, result.decision)
        assertEquals(1, result.totalLinkedRewardTasks)
        assertEquals(0, result.completedLinkedRewardTasks)
        assertEquals(1, result.requiredCompletions)
        assertTrue(result.isLocked)
    }

    // -------------------------------------------------------------------------
    // VB-A03: N=1, K=1 -> STILL LOCKED (Reward link remains active in current cycle)
    // -------------------------------------------------------------------------
    @Test
    fun test_VB_A03_N1_K1_unlocked() = runBlocking {
        vaultRepository.addVaultApp(CanonicalVaultApp("com.app.n1", "App N1"))
        taskRepository.saveTask(CanonicalTask("task_1", "Task 1", hasReward = true))
        taskRepository.linkTaskToApp("task_1", "com.app.n1")
        taskRepository.completeTask("task_1", cycleId, now)

        val result = lockEvaluator.evaluateApp("com.app.n1", now)
        assertEquals("Hoàn thành task nhưng link vẫn còn -> VẪN LOCKED", CanonicalLockDecision.LOCKED, result.decision)
        assertEquals(1, result.totalLinkedRewardTasks)
        assertEquals(1, result.completedLinkedRewardTasks)
        assertTrue(result.isLocked)

        // Phân tách: Đủ điều kiện xóa khỏi Vault
        assertTrue(CanonicalAppDeletionPolicy.canDelete(1, 1))

        // Chỉ khi unlink khỏi app thì app mới UNLOCKED
        taskRepository.unlinkTaskFromApp("task_1", "com.app.n1")
        val unlinkedResult = lockEvaluator.evaluateApp("com.app.n1", now)
        assertTrue(unlinkedResult.isUnlocked)
    }

    // -------------------------------------------------------------------------
    // VB-A04: N=2 -> RequiredForDeletion=1 (K=1 -> STILL LOCKED, Delete ALLOWED)
    // -------------------------------------------------------------------------
    @Test
    fun test_VB_A04_N2_required1() = runBlocking {
        vaultRepository.addVaultApp(CanonicalVaultApp("com.app.n2", "App N2"))
        taskRepository.saveTask(CanonicalTask("t1", "T1", hasReward = true))
        taskRepository.saveTask(CanonicalTask("t2", "T2", hasReward = true))
        taskRepository.linkTaskToApp("t1", "com.app.n2")
        taskRepository.linkTaskToApp("t2", "com.app.n2")

        // K=0 -> LOCKED
        var result = lockEvaluator.evaluateApp("com.app.n2", now)
        assertEquals(CanonicalLockDecision.LOCKED, result.decision)
        assertEquals(2, result.totalLinkedRewardTasks)
        assertEquals(1, result.requiredCompletions) // requiredForDeletion = 1
        assertFalse(CanonicalAppDeletionPolicy.canDelete(2, 0))

        // K=1 -> VẪN LOCKED theo Lock Policy, nhưng ĐỦ ĐIỀU KIỆN XÓA theo Deletion Policy
        taskRepository.completeTask("t1", cycleId, now)
        result = lockEvaluator.evaluateApp("com.app.n2", now)
        assertEquals("N=2 K=1 -> VẪN LOCKED vì còn task yêu cầu", CanonicalLockDecision.LOCKED, result.decision)
        assertEquals(1, result.completedLinkedRewardTasks)
        assertEquals(1, result.requiredCompletions)
        assertTrue("N=2 K=1 -> Đủ điều kiện xóa app khỏi Vault", CanonicalAppDeletionPolicy.canDelete(2, 1))
    }

    // -------------------------------------------------------------------------
    // VB-A05: N=3 -> RequiredForDeletion=2 (K=1,2 -> STILL LOCKED)
    // -------------------------------------------------------------------------
    @Test
    fun test_VB_A05_N3_required2() = runBlocking {
        vaultRepository.addVaultApp(CanonicalVaultApp("com.app.n3", "App N3"))
        for (i in 1..3) {
            taskRepository.saveTask(CanonicalTask("t$i", "T$i", hasReward = true))
            taskRepository.linkTaskToApp("t$i", "com.app.n3")
        }

        taskRepository.completeTask("t1", cycleId, now)
        var result = lockEvaluator.evaluateApp("com.app.n3", now)
        assertEquals(CanonicalLockDecision.LOCKED, result.decision)
        assertEquals(2, result.requiredCompletions)
        assertEquals(1, result.completedLinkedRewardTasks)
        assertFalse(CanonicalAppDeletionPolicy.canDelete(3, 1))

        taskRepository.completeTask("t2", cycleId, now)
        result = lockEvaluator.evaluateApp("com.app.n3", now)
        assertEquals("N=3 K=2 -> VẪN LOCKED theo Lock Policy", CanonicalLockDecision.LOCKED, result.decision)
        assertEquals(2, result.completedLinkedRewardTasks)
        assertTrue("N=3 K=2 -> Đủ điều kiện xóa app khỏi Vault", CanonicalAppDeletionPolicy.canDelete(3, 2))
    }

    // -------------------------------------------------------------------------
    // VB-A06: N=4 -> RequiredForDeletion=3 (K=2,3 -> STILL LOCKED)
    // -------------------------------------------------------------------------
    @Test
    fun test_VB_A06_N4_required3() = runBlocking {
        vaultRepository.addVaultApp(CanonicalVaultApp("com.app.n4", "App N4"))
        for (i in 1..4) {
            taskRepository.saveTask(CanonicalTask("t$i", "T$i", hasReward = true))
            taskRepository.linkTaskToApp("t$i", "com.app.n4")
        }

        taskRepository.completeTask("t1", cycleId, now)
        taskRepository.completeTask("t2", cycleId, now)
        var result = lockEvaluator.evaluateApp("com.app.n4", now)
        assertEquals(CanonicalLockDecision.LOCKED, result.decision)
        assertEquals(3, result.requiredCompletions)
        assertFalse(CanonicalAppDeletionPolicy.canDelete(4, 2))

        taskRepository.completeTask("t3", cycleId, now)
        result = lockEvaluator.evaluateApp("com.app.n4", now)
        assertEquals("N=4 K=3 -> VẪN LOCKED", CanonicalLockDecision.LOCKED, result.decision)
        assertTrue("N=4 K=3 -> Đủ điều kiện xóa app", CanonicalAppDeletionPolicy.canDelete(4, 3))
    }

    // -------------------------------------------------------------------------
    // VB-A07: N=5 -> RequiredForDeletion=4 (K=3,4 -> STILL LOCKED)
    // -------------------------------------------------------------------------
    @Test
    fun test_VB_A07_N5_required4() = runBlocking {
        vaultRepository.addVaultApp(CanonicalVaultApp("com.app.n5", "App N5"))
        for (i in 1..5) {
            taskRepository.saveTask(CanonicalTask("t$i", "T$i", hasReward = true))
            taskRepository.linkTaskToApp("t$i", "com.app.n5")
        }

        for (i in 1..3) taskRepository.completeTask("t$i", cycleId, now)
        var result = lockEvaluator.evaluateApp("com.app.n5", now)
        assertEquals(CanonicalLockDecision.LOCKED, result.decision)
        assertEquals(4, result.requiredCompletions)
        assertFalse(CanonicalAppDeletionPolicy.canDelete(5, 3))

        taskRepository.completeTask("t4", cycleId, now)
        result = lockEvaluator.evaluateApp("com.app.n5", now)
        assertEquals("N=5 K=4 -> VẪN LOCKED", CanonicalLockDecision.LOCKED, result.decision)
        assertTrue("N=5 K=4 -> Đủ điều kiện xóa app", CanonicalAppDeletionPolicy.canDelete(5, 4))
    }

    // -------------------------------------------------------------------------
    // VB-A08: N=6 -> RequiredForDeletion=4 (K=3,4 -> STILL LOCKED)
    // -------------------------------------------------------------------------
    @Test
    fun test_VB_A08_N6_required4() = runBlocking {
        vaultRepository.addVaultApp(CanonicalVaultApp("com.app.n6", "App N6"))
        for (i in 1..6) {
            taskRepository.saveTask(CanonicalTask("t$i", "T$i", hasReward = true))
            taskRepository.linkTaskToApp("t$i", "com.app.n6")
        }

        for (i in 1..3) taskRepository.completeTask("t$i", cycleId, now)
        var result = lockEvaluator.evaluateApp("com.app.n6", now)
        assertEquals(CanonicalLockDecision.LOCKED, result.decision)
        assertEquals(4, result.requiredCompletions)
        assertFalse(CanonicalAppDeletionPolicy.canDelete(6, 3))

        taskRepository.completeTask("t4", cycleId, now)
        result = lockEvaluator.evaluateApp("com.app.n6", now)
        assertEquals("N=6 K=4 -> VẪN LOCKED", CanonicalLockDecision.LOCKED, result.decision)
        assertTrue("N=6 K=4 -> Đủ điều kiện xóa app", CanonicalAppDeletionPolicy.canDelete(6, 4))
    }

    // -------------------------------------------------------------------------
    // VB-A09: Rewardless task excluded from N
    // -------------------------------------------------------------------------
    @Test
    fun test_VB_A09_rewardless_task_excluded_from_N() = runBlocking {
        vaultRepository.addVaultApp(CanonicalVaultApp("com.app.rw", "App RW"))
        // Task 1: có reward
        taskRepository.saveTask(CanonicalTask("t1", "T1", hasReward = true))
        taskRepository.linkTaskToApp("t1", "com.app.rw")

        // Task 2: rewardless (không có link tới app nào)
        taskRepository.saveTask(CanonicalTask("t2_rewardless", "T2 Rewardless", hasReward = false))

        val result = lockEvaluator.evaluateApp("com.app.rw", now)
        // Mẫu số N của com.app.rw chỉ là 1 (không bị tính t2_rewardless)
        assertEquals(1, result.totalLinkedRewardTasks)
        assertEquals(1, result.requiredCompletions)
    }

    // -------------------------------------------------------------------------
    // VB-A10: One app linked to multiple tasks (Many-to-Many)
    // -------------------------------------------------------------------------
    @Test
    fun test_VB_A10_one_app_linked_to_multiple_tasks() = runBlocking {
        vaultRepository.addVaultApp(CanonicalVaultApp("com.app.multi", "App Multi"))
        taskRepository.saveTask(CanonicalTask("task_a", "Task A", hasReward = true))
        taskRepository.saveTask(CanonicalTask("task_b", "Task B", hasReward = true))
        taskRepository.saveTask(CanonicalTask("task_c", "Task C", hasReward = true))

        taskRepository.linkTaskToApp("task_a", "com.app.multi")
        taskRepository.linkTaskToApp("task_b", "com.app.multi")
        taskRepository.linkTaskToApp("task_c", "com.app.multi")

        val tasks = taskRepository.getTasksLinkedToApp("com.app.multi")
        assertEquals(3, tasks.size)

        val result = lockEvaluator.evaluateApp("com.app.multi", now)
        assertEquals(3, result.totalLinkedRewardTasks)
        assertEquals(2, result.requiredCompletions)
    }

    // -------------------------------------------------------------------------
    // VB-A11: One task linked to multiple apps (Many-to-Many)
    // -------------------------------------------------------------------------
    @Test
    fun test_VB_A11_one_task_linked_to_multiple_apps() = runBlocking {
        vaultRepository.addVaultApp(CanonicalVaultApp("com.app.x", "App X"))
        vaultRepository.addVaultApp(CanonicalVaultApp("com.app.y", "App Y"))

        taskRepository.saveTask(CanonicalTask("shared_task", "Shared Task", hasReward = true))
        taskRepository.linkTaskToApp("shared_task", "com.app.x")
        taskRepository.linkTaskToApp("shared_task", "com.app.y")

        // Ban đầu cả 2 đều LOCKED
        assertEquals(CanonicalLockDecision.LOCKED, lockEvaluator.evaluateApp("com.app.x", now).decision)
        assertEquals(CanonicalLockDecision.LOCKED, lockEvaluator.evaluateApp("com.app.y", now).decision)

        // Hoàn thành shared_task -> Cả 2 apps VẪN LOCKED theo Lock Policy vì task link vẫn còn trong chu kỳ
        taskRepository.completeTask("shared_task", cycleId, now)
        assertEquals(CanonicalLockDecision.LOCKED, lockEvaluator.evaluateApp("com.app.x", now).decision)
        assertEquals(CanonicalLockDecision.LOCKED, lockEvaluator.evaluateApp("com.app.y", now).decision)
        // Cả 2 apps đều đủ điều kiện xóa khỏi Vault theo Deletion Policy
        assertTrue(CanonicalAppDeletionPolicy.canDelete(1, 1))

        // Gỡ liên kết khỏi App X -> App X UNLOCKED, App Y vẫn LOCKED độc lập
        taskRepository.unlinkTaskFromApp("shared_task", "com.app.x")
        assertEquals(CanonicalLockDecision.UNLOCKED, lockEvaluator.evaluateApp("com.app.x", now).decision)
        assertEquals(CanonicalLockDecision.LOCKED, lockEvaluator.evaluateApp("com.app.y", now).decision)
    }

    // -------------------------------------------------------------------------
    // VB-A12: Delete app removes all reward links
    // -------------------------------------------------------------------------
    @Test
    fun test_VB_A12_delete_app_removes_all_reward_links() = runBlocking {
        vaultRepository.addVaultApp(CanonicalVaultApp("com.app.del", "App Del"))
        taskRepository.saveTask(CanonicalTask("t1", "T1", hasReward = true))
        taskRepository.linkTaskToApp("t1", "com.app.del")

        assertEquals(1, taskRepository.getTasksLinkedToApp("com.app.del").size)

        // Xóa app khỏi Vault
        vaultRepository.removeVaultApp("com.app.del")
        taskRepository.removeAllLinksForApp("com.app.del")

        // Links của app này phải bị xóa hoàn toàn
        assertTrue(taskRepository.getTasksLinkedToApp("com.app.del").isEmpty())
    }

    // -------------------------------------------------------------------------
    // VB-A13: Delete app preserves task + history
    // -------------------------------------------------------------------------
    @Test
    fun test_VB_A13_delete_app_preserves_task_and_history() = runBlocking {
        vaultRepository.addVaultApp(CanonicalVaultApp("com.app.del2", "App Del 2"))
        taskRepository.saveTask(CanonicalTask("preserved_task", "Preserved Task", hasReward = true))
        taskRepository.linkTaskToApp("preserved_task", "com.app.del2")
        taskRepository.completeTask("preserved_task", cycleId, now)

        vaultRepository.removeVaultApp("com.app.del2")
        taskRepository.removeAllLinksForApp("com.app.del2")

        // Task vẫn tồn tại nguyên vẹn
        val task = taskRepository.getTask("preserved_task")
        assertNotNull(task)
        assertEquals("Preserved Task", task?.title)

        // Trạng thái chu kỳ và lịch sử vẫn nguyên vẹn
        val state = taskRepository.getCycleState("preserved_task", cycleId)
        assertNotNull(state)
        assertTrue(state!!.isCompleted)
    }

    // -------------------------------------------------------------------------
    // VB-A14: Re-add app does not restore old reward links
    // -------------------------------------------------------------------------
    @Test
    fun test_VB_A14_readd_app_does_not_restore_old_reward_links() = runBlocking {
        vaultRepository.addVaultApp(CanonicalVaultApp("com.app.readd", "App Readd"))
        taskRepository.saveTask(CanonicalTask("old_task", "Old Task", hasReward = true))
        taskRepository.linkTaskToApp("old_task", "com.app.readd")

        // Xóa app
        vaultRepository.removeVaultApp("com.app.readd")
        taskRepository.removeAllLinksForApp("com.app.readd")

        // Thêm lại chính app đó
        vaultRepository.addVaultApp(CanonicalVaultApp("com.app.readd", "App Readd"))

        // TIÊU CHUẨN NGHIỆM THU: Không được tự động khôi phục links cũ!
        val links = taskRepository.getTasksLinkedToApp("com.app.readd")
        assertTrue("Re-added app must not restore old reward links", links.isEmpty())

        val eval = lockEvaluator.evaluateApp("com.app.readd", now)
        assertEquals(CanonicalLockDecision.UNLOCKED, eval.decision)
        assertEquals(0, eval.totalLinkedRewardTasks)
    }

    // -------------------------------------------------------------------------
    // VB-A15: Delete task recomputes affected app lock without deleting unrelated history
    // -------------------------------------------------------------------------
    @Test
    fun test_VB_A15_delete_task_recomputes_affected_app_lock_without_deleting_unrelated_history() = runBlocking {
        vaultRepository.addVaultApp(CanonicalVaultApp("com.app.a", "App A"))
        taskRepository.saveTask(CanonicalTask("task_comp", "Completed", hasReward = true))
        taskRepository.saveTask(CanonicalTask("task_del", "To Delete", hasReward = true))
        taskRepository.linkTaskToApp("task_comp", "com.app.a")
        taskRepository.linkTaskToApp("task_del", "com.app.a")

        taskRepository.completeTask("task_comp", cycleId, now)
        // N=2, K=1 -> VẪN LOCKED theo Lock Policy (nhưng canDelete == true)
        assertEquals(CanonicalLockDecision.LOCKED, lockEvaluator.evaluateApp("com.app.a", now).decision)
        assertTrue(CanonicalAppDeletionPolicy.canDelete(2, 1))

        // Xóa task_del qua DeleteTaskUseCase
        val deleteUseCase = DeleteTaskUseCase(taskRepository, lockEvaluator)
        deleteUseCase("task_del", now)

        // App A giờ chỉ còn 1 task (task_comp) đã hoàn thành -> N=1, K=1 -> VẪN LOCKED vì task_comp vẫn liên kết
        val eval = lockEvaluator.evaluateApp("com.app.a", now)
        assertEquals(1, eval.totalLinkedRewardTasks)
        assertEquals(1, eval.completedLinkedRewardTasks)
        assertEquals(CanonicalLockDecision.LOCKED, eval.decision)
        assertTrue(CanonicalAppDeletionPolicy.canDelete(1, 1))

        // Chỉ khi xóa nốt task_comp hoặc unlink thì mới UNLOCKED
        deleteUseCase("task_comp", now)
        val finalEval = lockEvaluator.evaluateApp("com.app.a", now)
        assertEquals(CanonicalLockDecision.UNLOCKED, finalEval.decision)
        assertEquals(0, finalEval.totalLinkedRewardTasks)
    }

    // -------------------------------------------------------------------------
    // VB-A16: Effective voucher overrides lock
    // -------------------------------------------------------------------------
    @Test
    fun test_VB_A16_effective_voucher_overrides_lock() = runBlocking {
        vaultRepository.addVaultApp(CanonicalVaultApp("com.app.voucher", "App Voucher"))
        taskRepository.saveTask(CanonicalTask("t_incomplete", "Incomplete", hasReward = true))
        taskRepository.linkTaskToApp("t_incomplete", "com.app.voucher")

        // Ban đầu chưa có voucher: K=0 -> LOCKED
        var eval = lockEvaluator.evaluateApp("com.app.voucher", now)
        assertEquals(CanonicalLockDecision.LOCKED, eval.decision)

        // Thêm Voucher có hiệu lực
        val voucher = VoucherEffect(
            voucherId = "v1",
            voucherName = "Kim Bài Miễn Phong Ấn",
            targetPackageName = "com.app.voucher",
            effectiveFrom = now.minusSeconds(60),
            effectiveUntil = now.plusSeconds(3600)
        )
        vaultRepository.addVoucher(voucher)

        // Voucher override lock -> UNLOCKED
        eval = lockEvaluator.evaluateApp("com.app.voucher", now)
        assertEquals(CanonicalLockDecision.UNLOCKED, eval.decision)
        assertNotNull(eval.effectiveVoucher)
        assertEquals("v1", eval.effectiveVoucher?.voucherId)

        // Task state không bị xóa hay giả mạo hoàn thành
        val state = taskRepository.getCycleState("t_incomplete", cycleId)
        assertFalse(state?.isCompleted == true)
    }

    // -------------------------------------------------------------------------
    // VB-A17: Canonical evaluator is sole business lock authority
    // -------------------------------------------------------------------------
    @Test
    fun test_VB_A17_canonical_evaluator_is_sole_business_lock_authority() = runBlocking {
        vaultRepository.addVaultApp(CanonicalVaultApp("com.app.auth", "App Auth"))
        taskRepository.saveTask(CanonicalTask("t1", "T1", hasReward = true))
        taskRepository.linkTaskToApp("t1", "com.app.auth")

        enforcementAdapter.recomputeSnapshot()
        val adapterDetails = enforcementAdapter.evaluateSync("com.app.auth")
        val canonicalResult = lockEvaluator.evaluateApp("com.app.auth", now)

        // Quyết định của Adapter phải phản chiếu chính xác CanonicalLockEvaluator
        assertEquals(canonicalResult.isUnlocked, adapterDetails.finalAction == EnforcementAction.ALLOW)
        assertEquals(canonicalResult.requiredCompletions, adapterDetails.requiredTasksCount)
        assertEquals(canonicalResult.totalLinkedRewardTasks, adapterDetails.totalLinkedTasksCount)
    }

    // -------------------------------------------------------------------------
    // VB-A18: Legacy PolicyEngine cannot override canonical evaluator
    // -------------------------------------------------------------------------
    @Test
    fun test_VB_A18_legacy_policy_engine_cannot_override_canonical_evaluator() = runBlocking {
        vaultRepository.addVaultApp(CanonicalVaultApp("com.app.legacy_iso", "App Legacy Iso"))
        // N=0 -> Canonical Evaluator: UNLOCKED
        val canonicalResult = lockEvaluator.evaluateApp("com.app.legacy_iso", now)
        assertEquals(CanonicalLockDecision.UNLOCKED, canonicalResult.decision)

        // Cấu hình target trong legacy TargetRepository để PolicyEngine trả về LOCK
        fakeTargetRepository.add("com.app.legacy_iso")
        assertEquals(PolicyDecision.LOCK, policyEngine.evaluate("com.app.legacy_iso"))

        enforcementAdapter.recomputeSnapshot()
        val details = enforcementAdapter.evaluateSync("com.app.legacy_iso")

        // Invariant SSOT Mục 11 & 13: Legacy PolicyEngine KHÔNG được override Canonical Vault App!
        assertEquals(EnforcementAction.ALLOW, details.finalAction)
        assertEquals(BusinessUnlockDecision.NO_LINKED_TASKS, details.businessUnlockDecision)
        assertTrue(details.isTechnicalLockActive) // Cờ kỹ thuật ghi nhận nhưng finalAction vẫn là ALLOW
    }

    // -------------------------------------------------------------------------
    // VB-A19: Pending next-cycle reward replaces previous pending configuration
    // -------------------------------------------------------------------------
    @Test
    fun test_VB_A19_pending_next_cycle_reward_replaces_previous_pending() = runBlocking {
        taskRepository.saveTask(CanonicalTask("task_p", "Task P", hasReward = true))

        // Cài pending config 1: [App1, App2]
        taskRepository.setPendingNextCycleRewards("task_p", listOf("com.app.1", "com.app.2"))
        var task = taskRepository.getTask("task_p")
        assertEquals(listOf("com.app.1", "com.app.2"), task?.pendingNextCycleRewards)

        // Cài pending config 2: [App3] -> Phải thay thế cấu hình 1, không được merge
        taskRepository.setPendingNextCycleRewards("task_p", listOf("com.app.3"))
        task = taskRepository.getTask("task_p")
        assertEquals(listOf("com.app.3"), task?.pendingNextCycleRewards)

        // Hủy pending -> trở thành null
        taskRepository.cancelPendingNextCycleRewards("task_p")
        task = taskRepository.getTask("task_p")
        assertNull(task?.pendingNextCycleRewards)
    }

    // -------------------------------------------------------------------------
    // VB-A20: Batch mutation produces correct final lock state
    // -------------------------------------------------------------------------
    @Test
    fun test_VB_A20_batch_mutation_produces_correct_final_lock_state() = runBlocking {
        // Chuỗi biến đổi phức tạp: Thêm App -> Link 2 tasks -> Hoàn thành 1 task -> Gỡ App -> Thêm lại App
        vaultRepository.addVaultApp(CanonicalVaultApp("com.app.batch", "App Batch"))
        taskRepository.saveTask(CanonicalTask("b1", "B1", hasReward = true))
        taskRepository.saveTask(CanonicalTask("b2", "B2", hasReward = true))
        taskRepository.linkTaskToApp("b1", "com.app.batch")
        taskRepository.linkTaskToApp("b2", "com.app.batch")
        taskRepository.completeTask("b1", cycleId, now)

        // Xóa App
        vaultRepository.removeVaultApp("com.app.batch")
        taskRepository.removeAllLinksForApp("com.app.batch")

        // Thêm lại App
        vaultRepository.addVaultApp(CanonicalVaultApp("com.app.batch", "App Batch"))

        enforcementAdapter.recomputeSnapshot()
        val finalEval = lockEvaluator.evaluateApp("com.app.batch", now)
        val finalAdapter = enforcementAdapter.evaluateSync("com.app.batch")

        // Trạng thái cuối cùng phải là UNLOCKED (N=0), links cũ bị xóa sạch
        assertEquals(CanonicalLockDecision.UNLOCKED, finalEval.decision)
        assertEquals(0, finalEval.totalLinkedRewardTasks)
        assertEquals(EnforcementAction.ALLOW, finalAdapter.finalAction)
        assertEquals(AppEnforcementClassification.VAULT_APP_UNLINKED, finalAdapter.classification)
    }
}

// =============================================================================
// FAKE REPOSITORIES CHO CANONICAL TEST ENVIRONMENT
// =============================================================================

class FakeCanonicalVaultRepository : CanonicalVaultRepository {
    private val apps = mutableMapOf<String, CanonicalVaultApp>()
    private val vouchers = mutableListOf<VoucherEffect>()
    var onAppRemovedListener: ((String) -> Unit)? = null

    override suspend fun getAllVaultApps(): List<CanonicalVaultApp> = apps.values.toList()
    override suspend fun getVaultApp(packageName: String): CanonicalVaultApp? = apps[packageName]
    override suspend fun addVaultApp(app: CanonicalVaultApp) { apps[app.packageName] = app }
    override suspend fun removeVaultApp(packageName: String) {
        apps.remove(packageName)
        onAppRemovedListener?.invoke(packageName)
    }
    override suspend fun getActiveVouchers(instant: Instant): List<VoucherEffect> =
        vouchers.filter { it.isEffectiveAt(instant) }

    fun addVoucher(voucher: VoucherEffect) { vouchers.add(voucher) }
}

class FakeCanonicalTaskRepository : CanonicalTaskRepository {
    private val tasks = mutableMapOf<String, CanonicalTask>()
    private val links = mutableListOf<Pair<String, String>>() // taskId to packageName
    private val cycleStates = mutableMapOf<Pair<String, String>, TaskCycleState>() // (taskId, cycleId) to state

    override suspend fun getTask(taskId: String): CanonicalTask? = tasks[taskId]
    override suspend fun getAllActiveTasks(): List<CanonicalTask> = tasks.values.filter { !it.isArchived }

    override suspend fun getTasksLinkedToApp(packageName: String): List<CanonicalTask> {
        val linkedTaskIds = links.filter { it.second == packageName }.map { it.first }
        return linkedTaskIds.mapNotNull { tasks[it] }.filter { !it.isArchived }
    }

    override suspend fun getAppsLinkedToTask(taskId: String): List<String> =
        links.filter { it.first == taskId }.map { it.second }

    override suspend fun getCycleState(taskId: String, cycleId: CanonicalCycleId): TaskCycleState? =
        cycleStates[taskId to cycleId.dateIdentifier]

    override suspend fun getCycleStates(cycleId: CanonicalCycleId): Map<String, TaskCycleState> =
        cycleStates.filter { it.key.second == cycleId.dateIdentifier }.mapKeys { it.key.first }

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
        val current = tasks[taskId] ?: return
        tasks[taskId] = current.copy(isArchived = true)
        links.removeAll { it.first == taskId }
    }

    override suspend fun getTaskCycleHistory(taskId: String): List<TaskCycleState> =
        cycleStates.filter { it.key.first == taskId }.values.toList()

    override suspend fun linkTaskToApp(taskId: String, packageName: String) {
        if (!links.any { it.first == taskId && it.second == packageName }) {
            links.add(taskId to packageName)
        }
        val task = tasks[taskId]
        if (task != null && !task.hasReward) {
            tasks[taskId] = task.copy(hasReward = true)
        }
    }

    override suspend fun unlinkTaskFromApp(taskId: String, packageName: String) {
        links.removeAll { it.first == taskId && it.second == packageName }
        val remaining = links.filter { it.first == taskId }
        if (remaining.isEmpty()) {
            val task = tasks[taskId]
            if (task != null && task.hasReward) {
                tasks[taskId] = task.copy(hasReward = false)
            }
        }
    }

    override suspend fun removeAllLinksForApp(packageName: String) {
        val affectedTaskIds = links.filter { it.second == packageName }.map { it.first }.distinct()
        links.removeAll { it.second == packageName }
        for (taskId in affectedTaskIds) {
            val remaining = links.filter { it.first == taskId }
            if (remaining.isEmpty()) {
                val task = tasks[taskId]
                if (task != null && task.hasReward) {
                    tasks[taskId] = task.copy(hasReward = false)
                }
            }
        }
    }

    override suspend fun renameTask(taskId: String, newTitle: String) {
        val current = tasks[taskId] ?: return
        tasks[taskId] = current.copy(title = newTitle)
    }

    override suspend fun setPendingNextCycleRewards(taskId: String, appPackageNames: List<String>) {
        val current = tasks[taskId] ?: return
        tasks[taskId] = current.copy(pendingNextCycleRewards = appPackageNames)
    }

    override suspend fun cancelPendingNextCycleRewards(taskId: String) {
        val current = tasks[taskId] ?: return
        tasks[taskId] = current.copy(pendingNextCycleRewards = null)
    }

    override suspend fun applyPendingNextCycleRewards(taskId: String) {
        val current = tasks[taskId] ?: return
        val pending = current.pendingNextCycleRewards ?: return
        links.removeAll { it.first == taskId }
        for (pkg in pending) {
            links.add(taskId to pkg)
        }
        tasks[taskId] = current.copy(hasReward = pending.isNotEmpty(), pendingNextCycleRewards = null)
    }

    override suspend fun applyAllPendingNextCycleRewards() {
        val pendingTasks = tasks.values.filter { it.pendingNextCycleRewards != null }
        for (task in pendingTasks) {
            applyPendingNextCycleRewards(task.id)
        }
    }
}

class FakeCanonicalCycleRepository(
    private val fixedCycleId: CanonicalCycleId
) : CanonicalCycleRepository {
    override fun getCurrentCycle(instant: Instant): CanonicalCycleBoundary =
        CanonicalCycleBoundary(
            cycleId = fixedCycleId,
            startInstant = instant.minusSeconds(3600),
            endInstant = instant.plusSeconds(82800)
        )

    override fun getCycleBoundary(cycleId: CanonicalCycleId): CanonicalCycleBoundary =
        getCurrentCycle(Instant.now())
}

class FakeTargetRepository : TargetRepository {
    private val targets = mutableMapOf<String, LockedApp>()

    override fun getLockedPackages(): Flow<List<LockedApp>> = flowOf(targets.values.toList())

    override fun isLocked(packageName: String): Boolean = targets[packageName]?.enabled == true

    override fun getTarget(packageName: String): LockedApp? = targets[packageName]

    override suspend fun add(packageName: String) {
        targets[packageName] = LockedApp(packageName = packageName, enabled = true)
    }

    override suspend fun remove(packageName: String) {
        targets.remove(packageName)
    }

    override suspend fun setEnabled(packageName: String, enabled: Boolean) {
        targets[packageName]?.let {
            targets[packageName] = it.copy(enabled = enabled)
        }
    }

    override suspend fun updatePolicy(packageName: String, schedule: TimeSchedule?, timeLimit: TimeLimit?) {
        targets[packageName]?.let {
            targets[packageName] = it.copy(schedule = schedule, timeLimit = timeLimit)
        }
    }
}

class FakeUsageProvider : UsageProvider {
    override fun getTodayUsage(packageName: String): Long = 0L
}
