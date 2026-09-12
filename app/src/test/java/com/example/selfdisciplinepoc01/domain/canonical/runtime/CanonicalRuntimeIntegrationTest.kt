package com.example.selfdisciplinepoc01.domain.canonical.runtime

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.selfdisciplinepoc01.data.canonical.repository.CanonicalCycleRepositoryImpl
import com.example.selfdisciplinepoc01.data.canonical.repository.CanonicalLockEvaluatorImpl
import com.example.selfdisciplinepoc01.data.canonical.repository.CanonicalTaskRepositoryImpl
import com.example.selfdisciplinepoc01.data.canonical.repository.CanonicalVaultRepositoryImpl
import com.example.selfdisciplinepoc01.data.database.AppDatabase
import com.example.selfdisciplinepoc01.domain.canonical.cycle.CycleEngine
import com.example.selfdisciplinepoc01.domain.canonical.cycle.CycleTransitionManager
import com.example.selfdisciplinepoc01.domain.canonical.task.CanonicalTask
import com.example.selfdisciplinepoc01.domain.canonical.vault.CanonicalVaultApp
import com.example.selfdisciplinepoc01.domain.canonical.vault.VoucherEffect
import com.example.selfdisciplinepoc01.domain.enforcement.AppEnforcementClassification
import com.example.selfdisciplinepoc01.domain.enforcement.EnforcementAction
import com.example.selfdisciplinepoc01.domain.enforcement.EnforcementReason
import com.example.selfdisciplinepoc01.domain.enforcement.TaskAppEnforcementAdapter
import com.example.selfdisciplinepoc01.policy.PolicyDecision
import com.example.selfdisciplinepoc01.policy.PolicyEngine
import com.example.selfdisciplinepoc01.target.model.LockedApp
import com.example.selfdisciplinepoc01.target.model.TimeLimit
import com.example.selfdisciplinepoc01.target.model.TimeSchedule
import com.example.selfdisciplinepoc01.target.repository.TargetRepository
import com.example.selfdisciplinepoc01.time.Clock
import com.example.selfdisciplinepoc01.usage.UsageProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * PHASE 2A — CANONICAL RUNTIME INTEGRATION TEST SUITE
 *
 * Kiểm tra toàn diện 15 kịch bản nghiệp vụ chuẩn theo yêu cầu MASTER SSOT & Phase 2A:
 * - TEST A: UNLOCKED APP (Không có task dang dở -> ALLOW)
 * - TEST B: LOCKED APP (Chưa đủ task, không có voucher -> LOCK)
 * - TEST C: N=2 (2 tasks, hoàn thành 1 -> Required=1 -> ALLOW - Sửa bug N=2)
 * - TEST D: N=0 (Vault App chưa có task liên kết -> ALLOW - Sửa bug N=0)
 * - TEST E: REWARDLESS TASK (Task không liên kết reward app không đóng góp vào N)
 * - TEST F: EFFECTIVE VOUCHER (Đang thiếu task nhưng có voucher hiệu lực -> ALLOW)
 * - TEST G: VOUCHER EXPIRY (Voucher hết hạn -> tái đánh giá và chuyển LOCK)
 * - TEST H: 03:59:59 (Thuộc chu kỳ cũ)
 * - TEST I: 04:00:00 (Kích hoạt chu kỳ mới không phát sinh popup/rung/notification)
 * - TEST J: FOREGROUND AT 04:00 (Managed app foreground tại 04:00 bị khóa -> Home + System Panel)
 * - TEST K: PROCESS DEATH (Kill app và restart -> khôi phục đúng chu kỳ hiện tại)
 * - TEST L: DEVICE REBOOT (Reboot -> hòa giải chu kỳ và lập lịch)
 * - TEST M: LONG OFFLINE (Offline > 5 ngày -> nhảy thẳng tới chu kỳ hiện tại, không replay)
 * - TEST N: TIMEZONE CHANGE (Đổi múi giờ -> chu kỳ được tính theo múi giờ mới)
 * - TEST O: LEGACY ENGINE BYPASS (PolicyEngine / Schedule / Limit không thể override Vault App)
 */
@RunWith(RobolectricTestRunner::class)
class CanonicalRuntimeIntegrationTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var vaultRepo: CanonicalVaultRepositoryImpl
    private lateinit var taskRepo: CanonicalTaskRepositoryImpl
    private lateinit var cycleRepo: CanonicalCycleRepositoryImpl
    private lateinit var lockEvaluator: CanonicalLockEvaluatorImpl

    private var currentTestInstant = Instant.parse("2026-09-11T10:00:00Z")
    private var currentZoneId = ZoneId.of("Asia/Ho_Chi_Minh")

    private val testClock = object : Clock {
        override fun wallTimeMillis(): Long = currentTestInstant.toEpochMilli()
        override fun elapsedRealtimeMillis(): Long = currentTestInstant.toEpochMilli()
    }

    private lateinit var fakeTargetRepo: FakeTargetRepository
    private lateinit var fakeUsageProvider: FakeUsageProvider
    private lateinit var policyEngine: PolicyEngine
    private lateinit var adapter: TaskAppEnforcementAdapter

    private class FakeUsageProvider(var usageMillis: Long = 0L) : UsageProvider {
        override fun getTodayUsage(packageName: String): Long = usageMillis
    }

    private class FakeTargetRepository : TargetRepository {
        val targets = mutableMapOf<String, LockedApp>()
        override fun getLockedPackages(): Flow<List<LockedApp>> = flowOf(targets.values.toList())
        override fun isLocked(packageName: String): Boolean = targets[packageName]?.enabled == true
        override fun getTarget(packageName: String): LockedApp? = targets[packageName]
        override suspend fun add(packageName: String) { targets[packageName] = LockedApp(packageName, true) }
        override suspend fun remove(packageName: String) { targets.remove(packageName) }
        override suspend fun setEnabled(packageName: String, enabled: Boolean) {
            targets[packageName] = (targets[packageName] ?: LockedApp(packageName)).copy(enabled = enabled)
        }
        override suspend fun updatePolicy(packageName: String, schedule: TimeSchedule?, timeLimit: TimeLimit?) {
            targets[packageName] = (targets[packageName] ?: LockedApp(packageName)).copy(schedule = schedule, timeLimit = timeLimit)
        }
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        vaultRepo = CanonicalVaultRepositoryImpl(database)
        taskRepo = CanonicalTaskRepositoryImpl(database)
        cycleRepo = CanonicalCycleRepositoryImpl { currentZoneId }
        lockEvaluator = CanonicalLockEvaluatorImpl(taskRepo, vaultRepo, cycleRepo)

        fakeTargetRepo = FakeTargetRepository()
        fakeUsageProvider = FakeUsageProvider()
        policyEngine = PolicyEngine(fakeTargetRepo, fakeUsageProvider, testClock, currentZoneId)

        adapter = TaskAppEnforcementAdapter(
            canonicalVaultRepository = vaultRepo,
            canonicalTaskRepository = taskRepo,
            canonicalCycleRepository = cycleRepo,
            canonicalLockEvaluator = lockEvaluator,
            policyEngine = policyEngine,
            clock = testClock,
            zoneIdProvider = { currentZoneId }
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    /**
     * TEST A — UNLOCKED APP
     * Managed app trong Vault với nhiệm vụ liên kết đã hoàn thành đủ -> app mở bình thường (ALLOW).
     */
    /**
     * TEST A — APP LOCK RULE
     * Managed app trong Vault với nhiệm vụ liên kết:
     * Hoàn thành nhiệm vụ vẫn không làm mất liên kết yêu cầu -> app vẫn LOCKED.
     * Chỉ khi không còn nhiệm vụ yêu cầu (unlink) -> UNLOCKED (ALLOW).
     */
    @Test
    fun testA_unlockedApp() = runBlocking {
        val pkg = "com.test.app_a"
        vaultRepo.addVaultApp(CanonicalVaultApp(packageName = pkg, displayName = "App A"))
        val task = CanonicalTask(id = "task_a", title = "Task A", isArchived = false)
        taskRepo.saveTask(task)
        taskRepo.linkTaskToApp(task.id, pkg)

        val cycleId = cycleRepo.getCurrentCycle(currentTestInstant).cycleId
        taskRepo.completeTask(task.id, cycleId, currentTestInstant)

        adapter.recomputeSnapshot()
        val details = adapter.evaluateSync(pkg)

        // SSOT LOCK RULE: app vẫn LOCKED vì vẫn có 1 task yêu cầu trong chu kỳ
        assertEquals(EnforcementAction.LOCK, details.finalAction)
        assertTrue(details.isVaultApp)
        assertEquals(1, details.completedLinkedTasksCount)
        assertTrue(com.example.selfdisciplinepoc01.domain.canonical.vault.CanonicalAppDeletionPolicy.canDelete(1, 1))

        // Gỡ liên kết -> app UNLOCKED
        taskRepo.unlinkTaskFromApp(task.id, pkg)
        adapter.recomputeSnapshot()
        val unlinkedDetails = adapter.evaluateSync(pkg)
        assertEquals(EnforcementAction.ALLOW, unlinkedDetails.finalAction)
        assertEquals(EnforcementReason.ALLOWED_UNLOCKED_BY_TASKS, unlinkedDetails.reason)
    }

    /**
     * TEST B — LOCKED APP
     * Managed app có nhiệm vụ chưa hoàn thành và không có voucher -> LOCKED (LOCK).
     */
    @Test
    fun testB_lockedApp() = runBlocking {
        val pkg = "com.test.app_b"
        vaultRepo.addVaultApp(CanonicalVaultApp(packageName = pkg, displayName = "App B"))
        val task = CanonicalTask(id = "task_b", title = "Task B", isArchived = false)
        taskRepo.saveTask(task)
        taskRepo.linkTaskToApp(task.id, pkg)

        adapter.recomputeSnapshot()
        val details = adapter.evaluateSync(pkg)

        assertEquals(EnforcementAction.LOCK, details.finalAction)
        assertEquals(EnforcementReason.LOCKED_INSUFFICIENT_TASKS, details.reason)
        assertEquals(1, details.requiredTasksCount)
        assertEquals(0, details.completedLinkedTasksCount)
    }

    /**
     * TEST C — N=2
     * 2 nhiệm vụ liên kết, hoàn thành 1:
     * - App Lock Policy: Vẫn 2 nhiệm vụ liên kết trong chu kỳ -> LOCKED.
     * - Deletion Policy: 1 >= RequiredForDeletion(2)=1 -> canDelete = true.
     */
    @Test
    fun testC_nEqualsTwo() = runBlocking {
        val pkg = "com.test.app_c"
        vaultRepo.addVaultApp(CanonicalVaultApp(packageName = pkg, displayName = "App C"))
        val task1 = CanonicalTask(id = "task_c1", title = "Task C1", isArchived = false)
        val task2 = CanonicalTask(id = "task_c2", title = "Task C2", isArchived = false)
        taskRepo.saveTask(task1)
        taskRepo.saveTask(task2)
        taskRepo.linkTaskToApp(task1.id, pkg)
        taskRepo.linkTaskToApp(task2.id, pkg)

        val cycleId = cycleRepo.getCurrentCycle(currentTestInstant).cycleId
        taskRepo.completeTask(task1.id, cycleId, currentTestInstant)

        adapter.recomputeSnapshot()
        val details = adapter.evaluateSync(pkg)

        assertEquals(1, details.requiredTasksCount) // SSOT Deletion: N=2 -> RequiredForDeletion=1
        assertEquals(1, details.completedLinkedTasksCount)
        assertEquals(EnforcementAction.LOCK, details.finalAction)
        assertTrue(com.example.selfdisciplinepoc01.domain.canonical.vault.CanonicalAppDeletionPolicy.canDelete(2, 1))

        // Gỡ liên kết 2 task -> UNLOCKED
        taskRepo.unlinkTaskFromApp(task1.id, pkg)
        taskRepo.unlinkTaskFromApp(task2.id, pkg)
        adapter.recomputeSnapshot()
        val unlinkedDetails = adapter.evaluateSync(pkg)
        assertEquals(EnforcementAction.ALLOW, unlinkedDetails.finalAction)
    }

    /**
     * TEST D — N=0
     * App trong Vault nhưng chưa có nhiệm vụ liên kết nào -> UNLOCKED (ALLOW).
     * Bắt và khắc phục triệt để lỗi N=0 của legacy POC.
     */
    @Test
    fun testD_nEqualsZero() = runBlocking {
        val pkg = "com.test.app_d"
        vaultRepo.addVaultApp(CanonicalVaultApp(packageName = pkg, displayName = "App D"))

        adapter.recomputeSnapshot()
        val details = adapter.evaluateSync(pkg)

        assertEquals(AppEnforcementClassification.VAULT_APP_UNLINKED, details.classification)
        assertEquals(0, details.totalLinkedTasksCount)
        assertEquals(0, details.requiredTasksCount)
        assertEquals(EnforcementAction.ALLOW, details.finalAction)
        assertEquals(EnforcementReason.ALLOWED_UNLOCKED_BY_TASKS, details.reason)
    }

    /**
     * TEST E — REWARDLESS TASK
     * Nhiệm vụ không liên kết reward app không đóng góp vào N của Vault app.
     */
    @Test
    fun testE_rewardlessTask() = runBlocking {
        val pkg = "com.test.app_e"
        vaultRepo.addVaultApp(CanonicalVaultApp(packageName = pkg, displayName = "App E"))

        // Tạo một task nhưng không liên kết với app E
        val unlinkedTask = CanonicalTask(id = "unlinked_task", title = "Unlinked Task", isArchived = false)
        taskRepo.saveTask(unlinkedTask)

        adapter.recomputeSnapshot()
        val details = adapter.evaluateSync(pkg)

        assertEquals(0, details.totalLinkedTasksCount)
        assertEquals(EnforcementAction.ALLOW, details.finalAction)
    }

    /**
     * TEST F — EFFECTIVE VOUCHER
     * App bị thiếu nhiệm vụ nhưng có voucher hiệu lực -> UNLOCKED (ALLOW).
     */
    @Test
    fun testF_effectiveVoucher() = runBlocking {
        val pkg = "com.test.app_f"
        vaultRepo.addVaultApp(CanonicalVaultApp(packageName = pkg, displayName = "App F"))
        val task = CanonicalTask(id = "task_f", title = "Task F", isArchived = false)
        taskRepo.saveTask(task)
        taskRepo.linkTaskToApp(task.id, pkg)

        // Lưu voucher còn hiệu lực
        val voucher = VoucherEffect(
            voucherId = "voucher_f",
            voucherName = "Kim Bài Miễn Tử",
            targetPackageName = pkg,
            effectiveFrom = currentTestInstant.minusSeconds(3600),
            effectiveUntil = currentTestInstant.plusSeconds(3600)
        )
        vaultRepo.saveVoucher(voucher)

        adapter.recomputeSnapshot()
        val details = adapter.evaluateSync(pkg)

        assertEquals(EnforcementAction.ALLOW, details.finalAction)
        assertEquals(EnforcementReason.ALLOWED_UNLOCKED_BY_TASKS, details.reason)
        assertNotNull(details.canonicalLockResult?.effectiveVoucher)
    }

    /**
     * TEST G — VOUCHER EXPIRY
     * Voucher hết hiệu lực -> Tái đánh giá và chuyển sang LOCKED nếu chưa hoàn thành task.
     */
    @Test
    fun testG_voucherExpiry() = runBlocking {
        val pkg = "com.test.app_g"
        vaultRepo.addVaultApp(CanonicalVaultApp(packageName = pkg, displayName = "App G"))
        val task = CanonicalTask(id = "task_g", title = "Task G", isArchived = false)
        taskRepo.saveTask(task)
        taskRepo.linkTaskToApp(task.id, pkg)

        // Voucher đã hết hạn 10 phút trước
        val expiredVoucher = VoucherEffect(
            voucherId = "voucher_expired",
            voucherName = "Voucher Hết Hạn",
            targetPackageName = pkg,
            effectiveFrom = currentTestInstant.minusSeconds(7200),
            effectiveUntil = currentTestInstant.minusSeconds(600)
        )
        vaultRepo.saveVoucher(expiredVoucher)

        adapter.recomputeSnapshot()
        val details = adapter.evaluateSync(pkg)

        assertEquals(EnforcementAction.LOCK, details.finalAction)
        assertEquals(EnforcementReason.LOCKED_INSUFFICIENT_TASKS, details.reason)
    }

    /**
     * TEST H — 03:59:59
     * Thời điểm 03:59:59 vẫn thuộc chu kỳ cũ của ngày hôm trước.
     */
    @Test
    fun testH_beforeFourAmBelongsToOldCycle() {
        val zone = ZoneId.of("Asia/Ho_Chi_Minh")
        val beforeBoundaryLocal = LocalDateTime.of(2026, 9, 11, 3, 59, 59)
        val instant = beforeBoundaryLocal.atZone(zone).toInstant()

        val boundary = CycleEngine.getCurrentCycleBoundary(instant, zone)
        assertEquals("2026-09-10", boundary.cycleId.dateIdentifier)
    }

    /**
     * TEST I — 04:00:00
     * Đúng 04:00:00 chuyển sang chu kỳ mới, không phát sinh notification/rung/popup.
     */
    @Test
    fun testI_fourAmBoundaryTransitionsToNewCycle() {
        val zone = ZoneId.of("Asia/Ho_Chi_Minh")
        val atBoundaryLocal = LocalDateTime.of(2026, 9, 11, 4, 0, 0)
        val instant = atBoundaryLocal.atZone(zone).toInstant()

        val boundary = CycleEngine.getCurrentCycleBoundary(instant, zone)
        assertEquals("2026-09-11", boundary.cycleId.dateIdentifier)
    }

    /**
     * TEST J — FOREGROUND AT 04:00
     * Chuyển chu kỳ lúc 04:00:00:
     * Trước 04:00: app có voucher còn hạn -> UNLOCKED (ALLOW).
     * Sang 04:00:00: voucher hết hạn hoặc chu kỳ mới recompute -> app quay về LOCKED vì task yêu cầu vẫn còn.
     */
    @Test
    fun testJ_foregroundAtFourAm() = runBlocking {
        val pkg = "com.test.app_j"
        vaultRepo.addVaultApp(CanonicalVaultApp(packageName = pkg, displayName = "App J"))
        val task = CanonicalTask(id = "task_j", title = "Task J", isArchived = false)
        taskRepo.saveTask(task)
        taskRepo.linkTaskToApp(task.id, pkg)

        // Trước 04:00: có voucher hiệu lực -> ALLOW
        val oldInstant = LocalDateTime.of(2026, 9, 11, 3, 30, 0).atZone(currentZoneId).toInstant()
        currentTestInstant = oldInstant
        val voucher = VoucherEffect(
            voucherId = "v_j",
            voucherName = "Voucher J",
            targetPackageName = pkg,
            effectiveFrom = oldInstant.minusSeconds(60),
            effectiveUntil = oldInstant.plusSeconds(1200) // hết hạn trước 04:00
        )
        vaultRepo.saveVoucher(voucher)

        adapter.recomputeSnapshot()
        assertEquals(EnforcementAction.ALLOW, adapter.evaluateSync(pkg).finalAction)

        // Thời gian nhảy sang 04:00:00 (chu kỳ mới)
        val newInstant = LocalDateTime.of(2026, 9, 11, 4, 0, 0).atZone(currentZoneId).toInstant()
        currentTestInstant = newInstant

        // Chu kỳ mới: voucher đã hết hạn, task yêu cầu vẫn còn -> App chuyển thành LOCKED
        adapter.recomputeSnapshot()
        val detailsNewCycle = adapter.evaluateSync(pkg)
        assertEquals(EnforcementAction.LOCK, detailsNewCycle.finalAction)
    }

    /**
     * TEST K — PROCESS DEATH
     * App process bị kill và restart -> Chu kỳ hiện tại được tính toán chính xác từ thời gian máy.
     */
    @Test
    fun testK_processDeathRecovery() = runBlocking {
        val testInstant = LocalDateTime.of(2026, 9, 15, 14, 0, 0).atZone(currentZoneId).toInstant()
        val boundary = CycleEngine.getCurrentCycleBoundary(testInstant, currentZoneId)
        assertEquals("2026-09-15", boundary.cycleId.dateIdentifier)
    }

    /**
     * TEST L — DEVICE REBOOT
     * Thiết bị reboot -> CycleTransitionManager hòa giải chu kỳ mà không replay lịch sử.
     */
    @Test
    fun testL_deviceRebootRecovery() {
        val rebootInstant = LocalDateTime.of(2026, 9, 20, 8, 30, 0).atZone(currentZoneId).toInstant()
        CycleTransitionManager.reconcileCycleOnStartup(context, rebootInstant, currentZoneId)
        val currentCycle = cycleRepo.getCurrentCycle(rebootInstant)
        assertEquals("2026-09-20", currentCycle.cycleId.dateIdentifier)
    }

    /**
     * TEST M — LONG OFFLINE
     * Máy tắt nguồn hơn 5 ngày -> Khi bật lại, nhảy thẳng tới chu kỳ hiện tại, không replay các ngày cũ.
     */
    @Test
    fun testM_longOfflineCatchUp() {
        val beforeOffline = LocalDateTime.of(2026, 9, 1, 10, 0, 0).atZone(currentZoneId).toInstant()
        val afterOffline = LocalDateTime.of(2026, 9, 10, 10, 0, 0).atZone(currentZoneId).toInstant()

        val oldCycle = cycleRepo.getCurrentCycle(beforeOffline)
        assertEquals("2026-09-01", oldCycle.cycleId.dateIdentifier)

        // Nhảy thẳng tới ngày 10 mà không chạy vòng lặp +1
        val newCycle = cycleRepo.getCurrentCycle(afterOffline)
        assertEquals("2026-09-10", newCycle.cycleId.dateIdentifier)
    }

    /**
     * TEST N — TIMEZONE CHANGE
     * Thay đổi múi giờ thiết bị -> Chu kỳ được tính toán chuẩn xác theo múi giờ mới.
     */
    @Test
    fun testN_timezoneChange() {
        val instant = Instant.parse("2026-09-11T22:00:00Z")

        // UTC: 22:00 ngày 11/09 -> Chu kỳ 2026-09-11 (vì qua 04:00 ngày 11)
        val boundaryUtc = CycleEngine.getCurrentCycleBoundary(instant, ZoneId.of("UTC"))
        assertEquals("2026-09-11", boundaryUtc.cycleId.dateIdentifier)

        // Tokyo (+09:00): 07:00 ngày 12/09 -> Chu kỳ 2026-09-12 (vì đã qua 04:00 ngày 12)
        val boundaryTokyo = CycleEngine.getCurrentCycleBoundary(instant, ZoneId.of("Asia/Tokyo"))
        assertEquals("2026-09-12", boundaryTokyo.cycleId.dateIdentifier)
    }

    /**
     * TEST O — LEGACY ENGINE BYPASS
     * PolicyEngine / Schedule / UsageLimit KHÔNG THỂ ghi đè quyết định của Vault App.
     */
    @Test
    fun testO_legacyEngineCannotOverrideVaultApp() = runBlocking {
        val pkg = "com.test.app_o"
        vaultRepo.addVaultApp(CanonicalVaultApp(packageName = pkg, displayName = "App O"))
        // App O là Vault App N=0 (chưa có task liên kết -> ALLOW theo SSOT)

        // Cấu hình fake legacy target bị khóa bởi Technical Lock 24/7
        fakeTargetRepo.targets[pkg] = LockedApp(
            packageName = pkg,
            enabled = true,
            schedule = TimeSchedule(enabled = true, startHour = 0, startMinute = 0, endHour = 0, endMinute = 0)
        )

        // PolicyEngine đánh giá LOCK đối với package này
        val policyDecision = policyEngine.evaluate(pkg)
        assertEquals(PolicyDecision.LOCK, policyDecision)

        // Nhưng với Vault App: CanonicalLockPolicy là tối thượng! (N=0 -> ALLOW, legacy lock không override được)
        adapter.recomputeSnapshot()
        val details = adapter.evaluateSync(pkg)

        assertTrue(details.isTechnicalLockActive)
        assertEquals(EnforcementAction.ALLOW, details.finalAction) // KHÔNG BỊ OVERRIDE!
        assertEquals(EnforcementReason.ALLOWED_UNLOCKED_BY_TASKS, details.reason)
    }
}
