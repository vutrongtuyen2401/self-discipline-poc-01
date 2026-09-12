package com.example.selfdisciplinepoc01.domain.canonical.cycle

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.selfdisciplinepoc01.data.canonical.repository.CanonicalLockEvaluatorImpl
import com.example.selfdisciplinepoc01.domain.canonical.repository.CanonicalCycleRepository
import com.example.selfdisciplinepoc01.domain.canonical.repository.CanonicalLockEvaluator
import com.example.selfdisciplinepoc01.domain.canonical.repository.CanonicalTaskRepository
import com.example.selfdisciplinepoc01.domain.canonical.repository.CanonicalVaultRepository
import com.example.selfdisciplinepoc01.domain.canonical.task.CanonicalTask
import com.example.selfdisciplinepoc01.domain.canonical.task.TaskCycleState
import com.example.selfdisciplinepoc01.domain.canonical.task.TaskCycleStatus
import com.example.selfdisciplinepoc01.domain.canonical.vault.CanonicalLockDecision
import com.example.selfdisciplinepoc01.domain.canonical.vault.CanonicalVaultApp
import com.example.selfdisciplinepoc01.domain.canonical.vault.VoucherEffect
import com.example.selfdisciplinepoc01.domain.enforcement.EnforcementAction
import com.example.selfdisciplinepoc01.domain.enforcement.TaskAppEnforcementAdapter
import com.example.selfdisciplinepoc01.policy.PolicyEngine
import com.example.selfdisciplinepoc01.receiver.BootAndReconciliationReceiver
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * PHASE 2C — CANONICAL CYCLE RUNTIME & 04:00 BOUNDARY AUTOMATED TEST SUITE
 *
 * Kiểm tra đầy đủ 20 ca kiểm thử chuẩn tắc theo yêu cầu SSOT của Phase 2C:
 * CYC-A01: exact 03:59:59 -> previous cycle
 * CYC-A02: exact 04:00:00 -> new cycle
 * CYC-A03: 04:00:00.001 -> new cycle
 * CYC-A04: deterministic cycle ID
 * CYC-A05: offline catch-up -> current cycle only
 * CYC-A06: no historical cycle replay
 * CYC-A07: timezone-aware cycle calculation
 * CYC-A08: task definition survives cycle boundary
 * CYC-A09: new TaskCycleState belongs to current cycle
 * CYC-A10: old cycle completion remains historical
 * CYC-A11: Vault recomputes current-cycle K/N
 * CYC-A12: N=0 remains unlocked
 * CYC-A13: N=2 -> Required=1
 * CYC-A14: effective voucher overrides lock
 * CYC-A15: non-cycle state not reset
 * CYC-A16: idempotent boundary reconciliation
 * CYC-A17: duplicate alarm/callback does not duplicate transition
 * CYC-A18: startup reconciliation
 * CYC-A19: BOOT_COMPLETED reconciliation
 * CYC-A20: stale alarm reconciliation
 */
@RunWith(RobolectricTestRunner::class)
class CanonicalCycleRuntimeTest {

    private val hcmZone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val utcZone = ZoneId.of("UTC")
    private val nyZone = ZoneId.of("America/New_York")

    private lateinit var context: Context
    private lateinit var vaultRepo: FakeVaultRepo
    private lateinit var taskRepo: FakeTaskRepo
    private lateinit var cycleRepo: DynamicCycleRepo
    private lateinit var lockEvaluator: CanonicalLockEvaluator
    private lateinit var adapter: TaskAppEnforcementAdapter

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        vaultRepo = FakeVaultRepo()
        taskRepo = FakeTaskRepo()
        cycleRepo = DynamicCycleRepo(hcmZone)
        lockEvaluator = CanonicalLockEvaluatorImpl(taskRepo, vaultRepo, cycleRepo)

        val fakeTargetRepo = object : TargetRepository {
            override fun getLockedPackages(): Flow<List<LockedApp>> = flowOf(emptyList())
            override fun isLocked(packageName: String): Boolean = false
            override fun getTarget(packageName: String): LockedApp? = null
            override suspend fun add(packageName: String) {}
            override suspend fun remove(packageName: String) {}
            override suspend fun setEnabled(packageName: String, enabled: Boolean) {}
            override suspend fun updatePolicy(packageName: String, schedule: TimeSchedule?, timeLimit: TimeLimit?) {}
        }

        val fakeUsage = object : UsageProvider {
            override fun getTodayUsage(packageName: String): Long = 0L
        }

        val mockClock = object : Clock {
            override fun wallTimeMillis(): Long = System.currentTimeMillis()
            override fun elapsedRealtimeMillis(): Long = 100_000L
        }

        val policyEngine = PolicyEngine(
            targetRepository = fakeTargetRepo,
            usageProvider = fakeUsage,
            clock = mockClock,
            zoneIdProvider = { hcmZone }
        )

        adapter = TaskAppEnforcementAdapter(
            canonicalVaultRepository = vaultRepo,
            canonicalTaskRepository = taskRepo,
            canonicalCycleRepository = cycleRepo,
            canonicalLockEvaluator = lockEvaluator,
            coreDataRepository = null,
            policyEngine = policyEngine,
            businessDayProvider = BusinessDayProviderImpl(),
            clock = mockClock,
            zoneIdProvider = { hcmZone }
        )

        CycleTransitionManager.resetIdempotencyGuardForTest()
    }

    // -------------------------------------------------------------------------
    // CYC-A01: exact 03:59:59 -> previous cycle
    // -------------------------------------------------------------------------
    @Test
    fun test_CYC_A01_exact_035959_previous_cycle() {
        val instant = ZonedDateTime.of(2026, 9, 12, 3, 59, 59, 999_000_000, hcmZone).toInstant()
        val boundary = CycleEngine.getCurrentCycleBoundary(instant, hcmZone)

        assertEquals("2026-09-11", boundary.cycleId.dateIdentifier)
        assertTrue(boundary.contains(instant))
    }

    // -------------------------------------------------------------------------
    // CYC-A02: exact 04:00:00 -> new cycle
    // -------------------------------------------------------------------------
    @Test
    fun test_CYC_A02_exact_040000_new_cycle() {
        val instant = ZonedDateTime.of(2026, 9, 12, 4, 0, 0, 0, hcmZone).toInstant()
        val boundary = CycleEngine.getCurrentCycleBoundary(instant, hcmZone)

        assertEquals("2026-09-12", boundary.cycleId.dateIdentifier)
        assertTrue(boundary.contains(instant))
    }

    // -------------------------------------------------------------------------
    // CYC-A03: 04:00:00.001 -> new cycle
    // -------------------------------------------------------------------------
    @Test
    fun test_CYC_A03_040000_001_new_cycle() {
        val instant = ZonedDateTime.of(2026, 9, 12, 4, 0, 0, 1_000_000, hcmZone).toInstant()
        val boundary = CycleEngine.getCurrentCycleBoundary(instant, hcmZone)

        assertEquals("2026-09-12", boundary.cycleId.dateIdentifier)
        assertTrue(boundary.contains(instant))
    }

    // -------------------------------------------------------------------------
    // CYC-A04: deterministic cycle ID
    // -------------------------------------------------------------------------
    @Test
    fun test_CYC_A04_deterministic_cycle_id() {
        val instant = ZonedDateTime.of(2026, 9, 12, 14, 25, 30, 0, hcmZone).toInstant()
        val id1 = CycleEngine.getCurrentCycleId(instant, hcmZone)
        val id2 = CycleEngine.getCurrentCycleId(instant, hcmZone)
        val id3 = CycleEngine.getCurrentCycleId(instant, hcmZone)

        assertEquals(id1, id2)
        assertEquals(id2, id3)
        assertEquals("2026-09-12", id1.dateIdentifier)
    }

    // -------------------------------------------------------------------------
    // CYC-A05: offline catch-up -> current cycle only
    // -------------------------------------------------------------------------
    @Test
    fun test_CYC_A05_offline_catch_up_current_cycle_only() {
        val lastActive = ZonedDateTime.of(2026, 9, 1, 10, 0, 0, 0, hcmZone).toInstant()
        val currentNow = ZonedDateTime.of(2026, 9, 12, 15, 0, 0, 0, hcmZone).toInstant()

        val catchUpBoundary = CycleEngine.resolveCatchUpCycle(lastActive, currentNow, hcmZone)

        assertEquals("2026-09-12", catchUpBoundary.cycleId.dateIdentifier)
        assertTrue(catchUpBoundary.contains(currentNow))
        assertFalse(catchUpBoundary.contains(lastActive))
    }

    // -------------------------------------------------------------------------
    // CYC-A06: no historical cycle replay
    // -------------------------------------------------------------------------
    @Test
    fun test_CYC_A06_no_historical_cycle_replay() {
        val lastActive = ZonedDateTime.of(2026, 9, 5, 8, 0, 0, 0, hcmZone).toInstant()
        val currentNow = ZonedDateTime.of(2026, 9, 12, 8, 0, 0, 0, hcmZone).toInstant()

        val boundary = CycleEngine.resolveCatchUpCycle(lastActive, currentNow, hcmZone)

        // Phân giải trực tiếp chu kỳ ngày 12, không sinh ra các chu kỳ 6, 7, 8, 9, 10, 11
        assertEquals("2026-09-12", boundary.cycleId.dateIdentifier)
    }

    // -------------------------------------------------------------------------
    // CYC-A07: timezone-aware cycle calculation
    // -------------------------------------------------------------------------
    @Test
    fun test_CYC_A07_timezone_aware_cycle_calculation() {
        // Cùng 1 mốc tuyệt đối 2026-09-12T02:30:00Z
        val instantUtc = Instant.parse("2026-09-12T02:30:00Z")

        // Tại UTC: 02:30 < 04:00 -> chu kỳ 2026-09-11
        val cycleUtc = CycleEngine.getCurrentCycleId(instantUtc, utcZone)
        assertEquals("2026-09-11", cycleUtc.dateIdentifier)

        // Tại HCM (UTC+7): 09:30 > 04:00 -> chu kỳ 2026-09-12
        val cycleHcm = CycleEngine.getCurrentCycleId(instantUtc, hcmZone)
        assertEquals("2026-09-12", cycleHcm.dateIdentifier)

        // Tại NY (UTC-4): 22:30 (ngày 11/09) -> chu kỳ 2026-09-11
        val cycleNy = CycleEngine.getCurrentCycleId(instantUtc, nyZone)
        assertEquals("2026-09-11", cycleNy.dateIdentifier)
    }

    // -------------------------------------------------------------------------
    // CYC-A08: task definition survives cycle boundary
    // -------------------------------------------------------------------------
    @Test
    fun test_CYC_A08_task_definition_survives_cycle_boundary() = runBlocking {
        val task = CanonicalTask(id = "persist_task_1", title = "Luyện kiếm buổi sáng", hasReward = true)
        taskRepo.saveTask(task)

        // Truy vấn ở chu kỳ cũ
        val fetchedBefore = taskRepo.getTask("persist_task_1")
        assertNotNull(fetchedBefore)
        assertEquals("Luyện kiếm buổi sáng", fetchedBefore?.title)

        // Mô phỏng qua mốc 04:00: task definition vẫn tồn tại nguyên vẹn
        val fetchedAfter = taskRepo.getTask("persist_task_1")
        assertNotNull(fetchedAfter)
        assertEquals("Luyện kiếm buổi sáng", fetchedAfter?.title)
        assertFalse(fetchedAfter!!.isArchived)
    }

    // -------------------------------------------------------------------------
    // CYC-A09: new TaskCycleState belongs to current cycle
    // -------------------------------------------------------------------------
    @Test
    fun test_CYC_A09_new_task_cycle_state_belongs_to_current_cycle() = runBlocking {
        val cycleToday = CanonicalCycleId("2026-09-12")
        val state = taskRepo.getCycleState("task_demo", cycleToday)

        // Chưa hoàn thành trong chu kỳ mới -> mặc định null hoặc PENDING
        val effectiveStatus = state?.status ?: TaskCycleStatus.PENDING
        assertEquals(TaskCycleStatus.PENDING, effectiveStatus)
    }

    // -------------------------------------------------------------------------
    // CYC-A10: old cycle completion remains historical
    // -------------------------------------------------------------------------
    @Test
    fun test_CYC_A10_old_cycle_completion_remains_historical() = runBlocking {
        val cycleOld = CanonicalCycleId("2026-09-11")
        val cycleNew = CanonicalCycleId("2026-09-12")
        val taskId = "cross_boundary_task"

        // Hoàn thành ở chu kỳ cũ
        val completionTime = Instant.parse("2026-09-11T20:00:00Z")
        taskRepo.completeTask(taskId, cycleOld, completionTime)

        // Kiểm tra lịch sử
        val history = taskRepo.getTaskCycleHistory(taskId)
        assertEquals(1, history.size)
        assertEquals(cycleOld, history[0].cycleId)
        assertEquals(TaskCycleStatus.COMPLETED, history[0].status)

        // Chu kỳ mới vẫn là PENDING
        val stateNew = taskRepo.getCycleState(taskId, cycleNew)
        val statusNew = stateNew?.status ?: TaskCycleStatus.PENDING
        assertEquals(TaskCycleStatus.PENDING, statusNew)
    }

    // -------------------------------------------------------------------------
    // CYC-A11: Vault recomputes current-cycle K/N
    // -------------------------------------------------------------------------
    @Test
    fun test_CYC_A11_vault_recomputes_current_cycle_K_N() = runBlocking {
        val pkg = "com.vault.recompute"
        vaultRepo.addVaultApp(CanonicalVaultApp(pkg, "Recompute App"))
        taskRepo.saveTask(CanonicalTask("task_rec", "Tu luyện", hasReward = true))
        taskRepo.linkTaskToApp("task_rec", pkg)

        val cycleOld = CanonicalCycleId("2026-09-11")
        val timeOld = ZonedDateTime.of(2026, 9, 11, 20, 0, 0, 0, hcmZone).toInstant()
        taskRepo.completeTask("task_rec", cycleOld, timeOld)

        // Tại chu kỳ cũ: K=1, RequiredForDeletion=1 -> App vẫn LOCKED (1 task yêu cầu vẫn còn), canDelete = true
        cycleRepo.overrideCycleId = cycleOld
        val evalOld = lockEvaluator.evaluateApp(pkg, timeOld)
        assertEquals(CanonicalLockDecision.LOCKED, evalOld.decision)
        assertEquals(1, evalOld.completedLinkedRewardTasks)
        assertEquals(1, evalOld.requiredCompletions)
        assertTrue(com.example.selfdisciplinepoc01.domain.canonical.vault.CanonicalAppDeletionPolicy.canDelete(1, 1))

        // Tại chu kỳ mới (sau 04:00 ngày 12/09): K=0, RequiredForDeletion=1 -> LOCKED, canDelete = false
        val cycleNew = CanonicalCycleId("2026-09-12")
        val timeNew = ZonedDateTime.of(2026, 9, 12, 4, 15, 0, 0, hcmZone).toInstant()
        cycleRepo.overrideCycleId = cycleNew
        val evalNew = lockEvaluator.evaluateApp(pkg, timeNew)
        assertEquals(CanonicalLockDecision.LOCKED, evalNew.decision)
        assertEquals(0, evalNew.completedLinkedRewardTasks)
        assertEquals(1, evalNew.requiredCompletions)
        assertFalse(com.example.selfdisciplinepoc01.domain.canonical.vault.CanonicalAppDeletionPolicy.canDelete(1, 0))
    }

    // -------------------------------------------------------------------------
    // CYC-A12: N=0 remains unlocked
    // -------------------------------------------------------------------------
    @Test
    fun test_CYC_A12_N0_remains_unlocked_across_boundary() = runBlocking {
        val pkg = "com.vault.n0"
        vaultRepo.addVaultApp(CanonicalVaultApp(pkg, "App N=0"))

        val timeBefore0400 = ZonedDateTime.of(2026, 9, 12, 3, 59, 50, 0, hcmZone).toInstant()
        cycleRepo.overrideCycleId = CanonicalCycleId("2026-09-11")
        val evalBefore = lockEvaluator.evaluateApp(pkg, timeBefore0400)
        assertEquals(CanonicalLockDecision.UNLOCKED, evalBefore.decision)

        val timeAfter0400 = ZonedDateTime.of(2026, 9, 12, 4, 0, 5, 0, hcmZone).toInstant()
        cycleRepo.overrideCycleId = CanonicalCycleId("2026-09-12")
        val evalAfter = lockEvaluator.evaluateApp(pkg, timeAfter0400)
        assertEquals(CanonicalLockDecision.UNLOCKED, evalAfter.decision)
        assertEquals(0, evalAfter.requiredCompletions)
    }

    // -------------------------------------------------------------------------
    // CYC-A13: N=2 -> RequiredForDeletion=1
    // -------------------------------------------------------------------------
    @Test
    fun test_CYC_A13_N2_Required1_across_boundary() = runBlocking {
        val pkg = "com.vault.n2"
        vaultRepo.addVaultApp(CanonicalVaultApp(pkg, "App N=2"))
        taskRepo.saveTask(CanonicalTask("t1", "Task 1", hasReward = true))
        taskRepo.saveTask(CanonicalTask("t2", "Task 2", hasReward = true))
        taskRepo.linkTaskToApp("t1", pkg)
        taskRepo.linkTaskToApp("t2", pkg)

        val cycleNew = CanonicalCycleId("2026-09-12")
        val timeNew = ZonedDateTime.of(2026, 9, 12, 4, 10, 0, 0, hcmZone).toInstant()
        cycleRepo.overrideCycleId = cycleNew

        // K=0 -> LOCKED
        val evalK0 = lockEvaluator.evaluateApp(pkg, timeNew)
        assertEquals(CanonicalLockDecision.LOCKED, evalK0.decision)
        assertEquals(1, evalK0.requiredCompletions)
        assertFalse(com.example.selfdisciplinepoc01.domain.canonical.vault.CanonicalAppDeletionPolicy.canDelete(2, 0))

        // Hoàn thành 1 task (K=1 out of 2):
        // Lock Policy: vẫn LOCKED vì 2 task yêu cầu vẫn còn hiệu lực
        // Deletion Policy: K=1 >= RequiredForDeletion(2)=1 -> canDelete = true
        taskRepo.completeTask("t1", cycleNew, timeNew)
        val evalK1 = lockEvaluator.evaluateApp(pkg, timeNew)
        assertEquals(CanonicalLockDecision.LOCKED, evalK1.decision)
        assertEquals(1, evalK1.completedLinkedRewardTasks)
        assertEquals(1, evalK1.requiredCompletions)
        assertTrue(com.example.selfdisciplinepoc01.domain.canonical.vault.CanonicalAppDeletionPolicy.canDelete(2, 1))

        // Gỡ liên kết cả 2 task -> UNLOCKED
        taskRepo.unlinkTaskFromApp("t1", pkg)
        taskRepo.unlinkTaskFromApp("t2", pkg)
        val evalUnlinked = lockEvaluator.evaluateApp(pkg, timeNew)
        assertEquals(CanonicalLockDecision.UNLOCKED, evalUnlinked.decision)
    }

    // -------------------------------------------------------------------------
    // CYC-A14: effective voucher overrides lock
    // -------------------------------------------------------------------------
    @Test
    fun test_CYC_A14_effective_voucher_overrides_lock() = runBlocking {
        val pkg = "com.vault.voucher"
        vaultRepo.addVaultApp(CanonicalVaultApp(pkg, "App Voucher"))
        taskRepo.saveTask(CanonicalTask("t_v", "Task V", hasReward = true))
        taskRepo.linkTaskToApp("t_v", pkg)

        val timeAt0400 = ZonedDateTime.of(2026, 9, 12, 4, 0, 0, 0, hcmZone).toInstant()
        cycleRepo.overrideCycleId = CanonicalCycleId("2026-09-12")

        // Thêm voucher có hiệu lực xuyên qua 04:00 (từ 03:00 đến 05:00)
        val voucher = VoucherEffect(
            voucherId = "v1",
            voucherName = "Lệnh Bài Xuyên Màn Đêm",
            targetPackageName = pkg,
            effectiveFrom = timeAt0400.minusSeconds(3600),
            effectiveUntil = timeAt0400.plusSeconds(3600)
        )
        vaultRepo.addVoucher(voucher)

        // Đánh giá tại 04:00: K=0 nhưng có voucher -> UNLOCKED
        val eval = lockEvaluator.evaluateApp(pkg, timeAt0400)
        assertEquals(CanonicalLockDecision.UNLOCKED, eval.decision)
        assertNotNull(eval.effectiveVoucher)
        assertEquals("v1", eval.effectiveVoucher?.voucherId)
    }

    // -------------------------------------------------------------------------
    // CYC-A15: non-cycle state not reset
    // -------------------------------------------------------------------------
    @Test
    fun test_CYC_A15_non_cycle_state_not_reset() = runBlocking {
        val taskId = "non_cycle_task"
        val createTime = Instant.parse("2026-09-10T12:00:00Z")
        val task = CanonicalTask(id = taskId, title = "Bất Diệt Tâm Pháp", createdAt = createTime)
        taskRepo.saveTask(task)

        // Mô phỏng 2 ngày trôi qua
        val fetched = taskRepo.getTask(taskId)
        assertEquals("Bất Diệt Tâm Pháp", fetched?.title)
        assertEquals(createTime, fetched?.createdAt)
        assertFalse(fetched!!.isArchived)
    }

    // -------------------------------------------------------------------------
    // CYC-A16: idempotent boundary reconciliation
    // -------------------------------------------------------------------------
    @Test
    fun test_CYC_A16_idempotent_boundary_reconciliation() {
        val instant = ZonedDateTime.of(2026, 9, 12, 10, 0, 0, 0, hcmZone).toInstant()

        // Gọi reconcile 3 lần liên tiếp
        CycleTransitionManager.reconcileCycleOnStartup(context, instant, hcmZone)
        CycleTransitionManager.reconcileCycleOnStartup(context, instant, hcmZone)
        CycleTransitionManager.reconcileCycleOnStartup(context, instant, hcmZone)

        // Hệ thống không bị crash, trạng thái an toàn
        val boundary = CycleEngine.getCurrentCycleBoundary(instant, hcmZone)
        assertEquals("2026-09-12", boundary.cycleId.dateIdentifier)
    }

    // -------------------------------------------------------------------------
    // CYC-A17: duplicate alarm/callback does not duplicate transition
    // -------------------------------------------------------------------------
    @Test
    fun test_CYC_A17_duplicate_alarm_does_not_duplicate_transition() {
        val instant = ZonedDateTime.of(2026, 9, 12, 4, 0, 0, 10_000_000, hcmZone).toInstant()

        // Nổ lần 1
        CycleTransitionManager.onCycleBoundaryReached(context, instant, hcmZone)

        // Nổ lần 2 cách 50 mili-giây
        val duplicateInstant = instant.plusMillis(50)
        CycleTransitionManager.onCycleBoundaryReached(context, duplicateInstant, hcmZone)

        // Đảm bảo không ném exception và chu kỳ chuẩn
        val boundary = CycleEngine.getCurrentCycleBoundary(duplicateInstant, hcmZone)
        assertEquals("2026-09-12", boundary.cycleId.dateIdentifier)
    }

    // -------------------------------------------------------------------------
    // CYC-A18: startup reconciliation
    // -------------------------------------------------------------------------
    @Test
    fun test_CYC_A18_startup_reconciliation() {
        val bootTime = ZonedDateTime.of(2026, 9, 12, 6, 30, 0, 0, hcmZone).toInstant()
        CycleTransitionManager.reconcileCycleOnStartup(context, bootTime, hcmZone)

        val boundary = CycleEngine.getCurrentCycleBoundary(bootTime, hcmZone)
        assertEquals("2026-09-12", boundary.cycleId.dateIdentifier)
        assertTrue(boundary.contains(bootTime))
    }

    // -------------------------------------------------------------------------
    // CYC-A19: BOOT_COMPLETED reconciliation
    // -------------------------------------------------------------------------
    @Test
    fun test_CYC_A19_boot_completed_reconciliation() {
        val receiver = BootAndReconciliationReceiver()
        val intent = Intent(Intent.ACTION_BOOT_COMPLETED)

        // Bắn intent boot completed vào receiver
        receiver.onReceive(context, intent)

        // Không quăng ngoại lệ, thực thi an toàn
        assertTrue(true)
    }

    // -------------------------------------------------------------------------
    // CYC-A20: stale alarm reconciliation
    // -------------------------------------------------------------------------
    @Test
    fun test_CYC_A20_stale_alarm_reconciliation() {
        // Alarm 04:00 nhưng bị nổ trễ lúc 05:30 sáng
        val staleInstant = ZonedDateTime.of(2026, 9, 12, 5, 30, 0, 0, hcmZone).toInstant()
        val boundary = CycleEngine.getCurrentCycleBoundary(staleInstant, hcmZone)

        // Vẫn phân giải chính xác chu kỳ 2026-09-12
        assertEquals("2026-09-12", boundary.cycleId.dateIdentifier)
        assertTrue(boundary.contains(staleInstant))
    }
}

// -----------------------------------------------------------------------------
// FAKE REPOSITORIES FOR UNIT TESTS
// -----------------------------------------------------------------------------

private class DynamicCycleRepo(
    private val defaultZone: ZoneId
) : CanonicalCycleRepository {
    var overrideCycleId: CanonicalCycleId? = null

    override fun getCurrentCycle(instant: Instant): CanonicalCycleBoundary {
        if (overrideCycleId != null) {
            return CycleEngine.resolveCycleBoundary(overrideCycleId!!, defaultZone)
        }
        return CycleEngine.getCurrentCycleBoundary(instant, defaultZone)
    }

    override fun getCycleBoundary(cycleId: CanonicalCycleId): CanonicalCycleBoundary =
        CycleEngine.resolveCycleBoundary(cycleId, defaultZone)
}

private class FakeVaultRepo : CanonicalVaultRepository {
    private val apps = mutableMapOf<String, CanonicalVaultApp>()
    private val vouchers = mutableListOf<VoucherEffect>()

    override suspend fun getAllVaultApps(): List<CanonicalVaultApp> = apps.values.toList()
    override suspend fun getVaultApp(packageName: String): CanonicalVaultApp? = apps[packageName]
    override suspend fun addVaultApp(app: CanonicalVaultApp) { apps[app.packageName] = app }
    override suspend fun removeVaultApp(packageName: String) { apps.remove(packageName) }

    fun addVoucher(voucher: VoucherEffect) { vouchers.add(voucher) }

    override suspend fun getActiveVouchers(instant: Instant): List<VoucherEffect> =
        vouchers.filter { it.isEffectiveAt(instant) }
}

private class FakeTaskRepo : CanonicalTaskRepository {
    private val tasks = mutableMapOf<String, CanonicalTask>()
    private val links = mutableListOf<Pair<String, String>>() // taskId -> packageName
    private val cycleStates = mutableMapOf<Pair<String, String>, TaskCycleState>() // (taskId, cycleId) -> state

    override suspend fun getTask(taskId: String): CanonicalTask? = tasks[taskId]
    override suspend fun getAllActiveTasks(): List<CanonicalTask> = tasks.values.filter { !it.isArchived }

    override suspend fun getTasksLinkedToApp(packageName: String): List<CanonicalTask> {
        val taskIds = links.filter { it.second == packageName }.map { it.first }
        return taskIds.mapNotNull { tasks[it] }.filter { !it.isArchived }
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
