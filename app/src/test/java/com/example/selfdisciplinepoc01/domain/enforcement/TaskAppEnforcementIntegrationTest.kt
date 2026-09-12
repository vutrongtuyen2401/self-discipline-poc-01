package com.example.selfdisciplinepoc01.domain.enforcement

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.selfdisciplinepoc01.data.database.AppDatabase
import com.example.selfdisciplinepoc01.data.repository.CoreDataRepository
import com.example.selfdisciplinepoc01.data.repository.CoreDataRepositoryImpl
import com.example.selfdisciplinepoc01.domain.canonical.vault.CanonicalAppDeletionPolicy
import com.example.selfdisciplinepoc01.policy.PolicyDecision
import com.example.selfdisciplinepoc01.policy.PolicyEngine
import com.example.selfdisciplinepoc01.target.model.LockedApp
import com.example.selfdisciplinepoc01.target.model.TimeLimit
import com.example.selfdisciplinepoc01.target.model.TimeSchedule
import com.example.selfdisciplinepoc01.target.repository.TargetRepository
import com.example.selfdisciplinepoc01.time.BusinessDayProvider
import com.example.selfdisciplinepoc01.time.BusinessDayProviderImpl
import com.example.selfdisciplinepoc01.time.TestClock
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
import java.time.ZoneId

/**
 * Comprehensive Integration Test Suite for TaskAppEnforcementAdapter & OPEN-01 Task-Based Unlock.
 *
 * Covers:
 * 1. App not in Vault (unprotected)
 * 2. App in Vault without linked tasks (N=0 -> LOCK)
 * 3. App in Vault with linked tasks (N=1, 0/1 -> LOCK)
 * 4. Multiple tasks linked to a single app (N=3, 1/3 -> LOCK, required 2)
 * 5. Single task linked to multiple apps (1 completion unlocks all linked apps if threshold met)
 * 6. Archived tasks excluded from active evaluation
 * 7. Deleted tasks excluded from active evaluation
 * 8. 100% completion unlocks app per OPEN-01 (3/3 >= 2 -> ALLOW)
 * 9. Technical App Lock operates independently
 * 10. Supreme Precedence: Technical Lock overrides Business Unlock
 * 11. Synchronous fast-path evaluation (evaluateSync) identical to evaluate
 * 12. Architectural integrity: Adapter delegates formula to TaskUnlockPolicy
 * 13. Case A: N=3 (0/3 LOCK, 1/3 LOCK, 2/3 ALLOW, 3/3 ALLOW)
 * 14. Case B: N=4 (2/4 LOCK, 3/4 ALLOW)
 * 15. Case C: N=5 (3/5 LOCK, 4/5 ALLOW)
 * 16. Case D: Technical Lock active + threshold reached -> LOCK
 * 17. Case E: Threshold reached + Technical Lock inactive -> ALLOW
 * 18. Case F: 04:00 boundary reset (yesterday completion ignored today)
 * 19. Cache invalidation & atomic snapshot update
 * 20. App re-added to Vault does not inherit old linkage
 */
@RunWith(RobolectricTestRunner::class)
class TaskAppEnforcementIntegrationTest {

    private val testZone = ZoneId.of("UTC")
    private val clock = TestClock.at(2026, 9, 7, 10, 0, zoneId = testZone)
    private val businessDayProvider: BusinessDayProvider = BusinessDayProviderImpl(boundaryHour = 4)

    private lateinit var database: AppDatabase
    private lateinit var coreRepository: CoreDataRepository

    private lateinit var fakeTargetRepo: FakeTargetRepository
    private lateinit var fakeUsageProvider: FakeUsageProvider
    private lateinit var policyEngine: PolicyEngine

    private lateinit var adapter: TaskAppEnforcementAdapter

    private class FakeUsageProvider(private val usageMap: MutableMap<String, Long> = mutableMapOf()) : UsageProvider {
        override fun getTodayUsage(packageName: String): Long = usageMap[packageName] ?: 0L
        fun setUsage(packageName: String, millis: Long) {
            usageMap[packageName] = millis
        }
    }

    private class FakeTargetRepository(val apps: MutableMap<String, LockedApp> = mutableMapOf()) : TargetRepository {
        override fun getLockedPackages(): Flow<List<LockedApp>> = flowOf(apps.values.toList())
        override fun isLocked(packageName: String): Boolean = apps[packageName]?.enabled == true
        override fun getTarget(packageName: String): LockedApp? = apps[packageName]
        override suspend fun add(packageName: String) { apps[packageName] = LockedApp(packageName, true) }
        override suspend fun remove(packageName: String) { apps.remove(packageName) }
        override suspend fun setEnabled(packageName: String, enabled: Boolean) {
            apps[packageName] = (apps[packageName] ?: LockedApp(packageName)).copy(enabled = enabled)
        }
        override suspend fun updatePolicy(packageName: String, schedule: TimeSchedule?, timeLimit: TimeLimit?) {
            apps[packageName] = (apps[packageName] ?: LockedApp(packageName)).copy(schedule = schedule, timeLimit = timeLimit)
        }
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        coreRepository = CoreDataRepositoryImpl(database)

        fakeTargetRepo = FakeTargetRepository()
        fakeUsageProvider = FakeUsageProvider()
        policyEngine = PolicyEngine(fakeTargetRepo, fakeUsageProvider, clock, testZone)

        adapter = TaskAppEnforcementAdapter(
            coreDataRepository = coreRepository,
            policyEngine = policyEngine,
            businessDayProvider = businessDayProvider,
            clock = clock,
            zoneIdProvider = { testZone }
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ==================================================
    // 1. NON-VAULT APP EVALUATION
    // ==================================================

    @Test
    fun test01_nonVaultApp_withoutTechnicalLock_isAllowed() = runBlocking {
        val result = adapter.evaluate("com.android.settings")
        assertEquals(EnforcementAction.ALLOW, result.finalAction)
        assertEquals(AppEnforcementClassification.NON_VAULT_APP, result.classification)
        assertEquals(EnforcementReason.ALLOWED_NOT_PROTECTED, result.reason)
        assertEquals(BusinessUnlockDecision.NOT_APPLICABLE, result.businessUnlockDecision)
        assertFalse(result.isVaultApp)
        assertFalse(result.isTechnicalLockActive)
    }

    // ==================================================
    // 2. VAULT APP WITHOUT TASKS (N=0)
    // ==================================================

    @Test
    fun test02_vaultApp_unlinked_isUnlocked() = runBlocking {
        coreRepository.addVaultApp("com.google.android.youtube", "YouTube")

        val result = adapter.evaluate("com.google.android.youtube")
        // MASTER SSOT & Phase 2A: N=0 in Vault is UNLOCKED (ALLOW)
        assertEquals(EnforcementAction.ALLOW, result.finalAction)
        assertEquals(AppEnforcementClassification.VAULT_APP_UNLINKED, result.classification)
        assertEquals(EnforcementReason.ALLOWED_UNLOCKED_BY_TASKS, result.reason)
        assertEquals(BusinessUnlockDecision.NO_LINKED_TASKS, result.businessUnlockDecision)
        assertTrue(result.isVaultApp)
        assertEquals(0, result.totalLinkedTasksCount)
        assertEquals(0, result.activeLinkedTasksCount)
        assertEquals(0, result.requiredTasksCount)
    }

    // ==================================================
    // 3. VAULT APP WITH LINKED TASKS (N=1, 0/1 -> LOCK)
    // ==================================================

    @Test
    fun test03_vaultApp_withLinkedTasks_isLockedInsufficientCompletion() = runBlocking {
        coreRepository.addVaultApp("com.google.android.youtube", "YouTube")
        val taskId = coreRepository.createTask("Đọc sách tu dưỡng 30p")
        coreRepository.linkTaskToApp(taskId, "com.google.android.youtube")

        val result = adapter.evaluate("com.google.android.youtube")
        assertEquals(EnforcementAction.LOCK, result.finalAction)
        assertEquals(AppEnforcementClassification.VAULT_APP_WITH_TASKS, result.classification)
        assertEquals(EnforcementReason.LOCKED_INSUFFICIENT_TASKS, result.reason)
        assertEquals(BusinessUnlockDecision.INSUFFICIENT_COMPLETION, result.businessUnlockDecision)
        assertEquals(1, result.totalLinkedTasksCount)
        assertEquals(1, result.activeLinkedTasksCount)
        assertEquals(0, result.completedLinkedTasksCount)
        assertEquals(1, result.incompleteLinkedTasksCount)
        assertEquals(1, result.requiredTasksCount)
    }

    // ==================================================
    // 4. MULTIPLE TASKS FOR SINGLE APP (N=3, 1/3 -> LOCK)
    // ==================================================

    @Test
    fun test04_multipleTasks_singleApp_countsAndClassificationAccurate() = runBlocking {
        coreRepository.addVaultApp("com.game.gacha", "Gacha Game")
        val t1 = coreRepository.createTask("Chạy bộ 5km")
        val t2 = coreRepository.createTask("Học ngoại ngữ 45p")
        val t3 = coreRepository.createTask("Thiền định 20p")

        coreRepository.linkTaskToApp(t1, "com.game.gacha")
        coreRepository.linkTaskToApp(t2, "com.game.gacha")
        coreRepository.linkTaskToApp(t3, "com.game.gacha")

        val today = businessDayProvider.getBusinessDate(clock.wallTimeMillis(), testZone).toString()
        // Mark Task 1 completed (1/3, required = (2*3+2)/3 = 2)
        coreRepository.setTaskCompletion(t1, today, true)

        val result = adapter.evaluate("com.game.gacha")
        assertEquals(3, result.totalLinkedTasksCount)
        assertEquals(3, result.activeLinkedTasksCount)
        assertEquals(1, result.completedLinkedTasksCount)
        assertEquals(2, result.incompleteLinkedTasksCount)
        assertEquals(2, result.requiredTasksCount)
        assertEquals(EnforcementAction.LOCK, result.finalAction)
        assertEquals(BusinessUnlockDecision.INSUFFICIENT_COMPLETION, result.businessUnlockDecision)
        assertEquals(EnforcementReason.LOCKED_INSUFFICIENT_TASKS, result.reason)
    }

    // ==================================================
    // 5. SINGLE TASK FOR MULTIPLE APPS (M:N)
    // ==================================================

    @Test
    fun test05_singleTask_multipleApps_crossRefReflectedAccurately() = runBlocking {
        coreRepository.addVaultApp("com.app.a", "App Alpha")
        coreRepository.addVaultApp("com.app.b", "App Beta")

        val taskId = coreRepository.createTask("Luyện khí dưỡng thần")
        coreRepository.linkTaskToApps(taskId, listOf("com.app.a", "com.app.b"))

        val resultA = adapter.evaluate("com.app.a")
        val resultB = adapter.evaluate("com.app.b")

        assertEquals(1, resultA.totalLinkedTasksCount)
        assertEquals(1, resultB.totalLinkedTasksCount)
        assertEquals(1, resultA.requiredTasksCount)
        assertEquals(1, resultB.requiredTasksCount)
        assertEquals(EnforcementAction.LOCK, resultA.finalAction)
        assertEquals(EnforcementAction.LOCK, resultB.finalAction)

        // Complete task today (1/1 for both apps) -> VẪN LOCKED theo Lock Policy vì task link còn hiệu lực
        val today = businessDayProvider.getBusinessDate(clock.wallTimeMillis(), testZone).toString()
        coreRepository.setTaskCompletion(taskId, today, true)

        val updatedA = adapter.evaluate("com.app.a")
        val updatedB = adapter.evaluate("com.app.b")

        assertEquals(1, updatedA.completedLinkedTasksCount)
        assertEquals(1, updatedB.completedLinkedTasksCount)
        assertEquals(EnforcementAction.LOCK, updatedA.finalAction)
        assertEquals(EnforcementAction.LOCK, updatedB.finalAction)
        assertTrue(CanonicalAppDeletionPolicy.canDelete(1, 1))

        // Gỡ liên kết khỏi App A -> App A UNLOCKED, App B vẫn LOCKED
        coreRepository.unlinkTaskFromApp(taskId, "com.app.a")
        val afterUnlinkA = adapter.evaluate("com.app.a")
        val afterUnlinkB = adapter.evaluate("com.app.b")
        assertEquals(EnforcementAction.ALLOW, afterUnlinkA.finalAction)
        assertEquals(EnforcementAction.LOCK, afterUnlinkB.finalAction)
    }

    // ==================================================
    // 6. ARCHIVED TASKS EXCLUSION
    // ==================================================

    @Test
    fun test06_archivedTask_excludedFromActiveCount() = runBlocking {
        coreRepository.addVaultApp("com.social.network", "Social App")
        val t1 = coreRepository.createTask("Nhiệm vụ cũ")
        val t2 = coreRepository.createTask("Nhiệm vụ mới")

        coreRepository.linkTaskToApp(t1, "com.social.network")
        coreRepository.linkTaskToApp(t2, "com.social.network")

        // Archive t1 -> Active becomes 1 (required = 1)
        coreRepository.archiveTask(t1)

        val result = adapter.evaluate("com.social.network")
        assertEquals(2, result.totalLinkedTasksCount)
        assertEquals(1, result.activeLinkedTasksCount)
        assertEquals(1, result.archivedLinkedTasksCount)
        assertEquals(1, result.requiredTasksCount)
        assertEquals(AppEnforcementClassification.VAULT_APP_WITH_TASKS, result.classification)

        // Complete t2 -> VẪN LOCKED vì t2 vẫn active và yêu cầu app trong chu kỳ
        val today = businessDayProvider.getBusinessDate(clock.wallTimeMillis(), testZone).toString()
        coreRepository.setTaskCompletion(t2, today, true)

        val lockedResult = adapter.evaluate("com.social.network")
        assertEquals(EnforcementAction.LOCK, lockedResult.finalAction)
        assertTrue(CanonicalAppDeletionPolicy.canDelete(1, 1))

        // Archive t2 as well -> Active becomes 0 -> becomes unlinked effectively -> SSOT: UNLOCKED (ALLOW)
        coreRepository.archiveTask(t2)
        val resultAllArchived = adapter.evaluate("com.social.network")
        assertEquals(2, resultAllArchived.totalLinkedTasksCount)
        assertEquals(0, resultAllArchived.activeLinkedTasksCount)
        assertEquals(2, resultAllArchived.archivedLinkedTasksCount)
        assertEquals(0, resultAllArchived.requiredTasksCount)
        assertEquals(AppEnforcementClassification.VAULT_APP_UNLINKED, resultAllArchived.classification)
        assertEquals(EnforcementReason.ALLOWED_UNLOCKED_BY_TASKS, resultAllArchived.reason)
        assertEquals(EnforcementAction.ALLOW, resultAllArchived.finalAction)
    }

    // ==================================================
    // 7. DELETED TASKS EXCLUSION
    // ==================================================

    @Test
    fun test07_deletedTask_excludedFromActiveCount() = runBlocking {
        coreRepository.addVaultApp("com.social.chat", "Chat App")
        val t1 = coreRepository.createTask("Nhiệm vụ hủy")
        val t2 = coreRepository.createTask("Nhiệm vụ giữ")

        coreRepository.linkTaskToApp(t1, "com.social.chat")
        coreRepository.linkTaskToApp(t2, "com.social.chat")

        // Delete t1
        coreRepository.deleteTask(t1)

        val result = adapter.evaluate("com.social.chat")
        assertEquals(1, result.totalLinkedTasksCount)
        assertEquals(1, result.activeLinkedTasksCount)
        assertEquals(0, result.archivedLinkedTasksCount)
        assertEquals(1, result.requiredTasksCount)
    }

    // ==================================================
    // 8. ALL TASKS COMPLETED (OPEN-01 IMPLEMENTED)
    // ==================================================

    @Test
    fun test08_allTasksCompleted_unlocksApp_perOpen01Decision() = runBlocking {
        coreRepository.addVaultApp("com.video.stream", "Video Stream")
        val t1 = coreRepository.createTask("Luyện kiếm 60p")
        val t2 = coreRepository.createTask("Học bài 120p")
        val t3 = coreRepository.createTask("Dọn dẹp phòng")

        coreRepository.linkTaskToApp(t1, "com.video.stream")
        coreRepository.linkTaskToApp(t2, "com.video.stream")
        coreRepository.linkTaskToApp(t3, "com.video.stream")

        val today = businessDayProvider.getBusinessDate(clock.wallTimeMillis(), testZone).toString()
        // Complete 3 out of 3 tasks (100% completed)
        coreRepository.setTaskCompletion(t1, today, true)
        coreRepository.setTaskCompletion(t2, today, true)
        coreRepository.setTaskCompletion(t3, today, true)

        val result = adapter.evaluate("com.video.stream")
        assertEquals(3, result.totalLinkedTasksCount)
        assertEquals(3, result.activeLinkedTasksCount)
        assertEquals(3, result.completedLinkedTasksCount)
        assertEquals(0, result.incompleteLinkedTasksCount)
        assertEquals(2, result.requiredTasksCount)

        // MASTER SSOT: 3/3 hoàn thành nhưng links vẫn tồn tại -> VẪN LOCKED
        assertEquals(EnforcementAction.LOCK, result.finalAction)
        assertEquals(EnforcementReason.LOCKED_INSUFFICIENT_TASKS, result.reason)
        // Nhưng đủ điều kiện xóa khỏi Vault theo Deletion Policy
        assertTrue(CanonicalAppDeletionPolicy.canDelete(3, 3))

        // Gỡ toàn bộ liên kết -> UNLOCKED
        coreRepository.unlinkTaskFromApp(t1, "com.video.stream")
        coreRepository.unlinkTaskFromApp(t2, "com.video.stream")
        coreRepository.unlinkTaskFromApp(t3, "com.video.stream")
        val unlinkedResult = adapter.evaluate("com.video.stream")
        assertEquals(EnforcementAction.ALLOW, unlinkedResult.finalAction)
        assertEquals(BusinessUnlockDecision.NO_LINKED_TASKS, unlinkedResult.businessUnlockDecision)
    }

    // ==================================================
    // 9. TECHNICAL APP LOCK OPERATES INDEPENDENTLY
    // ==================================================

    @Test
    fun test09_technicalAppLock_operatesIndependently_locksRegardlessOfVault() = runBlocking {
        fakeTargetRepo.apps["com.android.chrome"] = LockedApp(
            packageName = "com.android.chrome",
            enabled = true,
            schedule = null,
            timeLimit = null
        )

        coreRepository.addVaultApp("com.android.chrome", "Chrome")
        val t1 = coreRepository.createTask("Task for Chrome")
        coreRepository.linkTaskToApp(t1, "com.android.chrome")

        val today = businessDayProvider.getBusinessDate(clock.wallTimeMillis(), testZone).toString()
        coreRepository.setTaskCompletion(t1, today, true)

        val result = adapter.evaluate("com.android.chrome")
        // Technical lock and task link both dictate LOCK
        assertEquals(EnforcementAction.LOCK, result.finalAction)
    }

    // ==================================================
    // 11. EVALUATE SYNC MATCHES EVALUATE SEMANTICS
    // ==================================================

    @Test
    fun test11_evaluateSync_matchesEvaluateSemantics() = runBlocking {
        coreRepository.addVaultApp("com.sync.test", "Sync Test")
        val t1 = coreRepository.createTask("Sync Task")
        coreRepository.linkTaskToApp(t1, "com.sync.test")

        val asyncResult = adapter.evaluate("com.sync.test")
        val syncResult = adapter.evaluateSync("com.sync.test")

        assertEquals(asyncResult.finalAction, syncResult.finalAction)
        assertEquals(asyncResult.classification, syncResult.classification)
        assertEquals(asyncResult.totalLinkedTasksCount, syncResult.totalLinkedTasksCount)
    }

    // ==================================================
    // 12. ADAPTER STATUS AND DELEGATION INTEGRITY
    // ==================================================

    @Test
    fun test12_adapterStatus_andDelegationIntegrity() = runBlocking {
        assertEquals(TaskAppEnforcementStatus.TASK_BASED_UNLOCK_ACTIVE, adapter.getEnforcementStatus())

        coreRepository.addVaultApp("com.app.test", "Test App")
        val t1 = coreRepository.createTask("Task 1")
        coreRepository.linkTaskToApp(t1, "com.app.test")

        // Có task liên kết -> Không được approve unlock
        assertFalse(adapter.isTaskBasedUnlockApproved("com.app.test"))

        val today = businessDayProvider.getBusinessDate(clock.wallTimeMillis(), testZone).toString()
        coreRepository.setTaskCompletion(t1, today, true)

        // Dù hoàn thành, theo Lock Policy app vẫn LOCK nên không approve unlock
        assertFalse(adapter.isTaskBasedUnlockApproved("com.app.test"))

        // Chỉ khi unlink khỏi app (N=0) -> Approved unlock
        coreRepository.unlinkTaskFromApp(t1, "com.app.test")
        assertTrue(adapter.isTaskBasedUnlockApproved("com.app.test"))
    }

    // ==================================================
    // 13. CASE A: N = 3 (0/3 LOCK, 1/3 LOCK, 2/3 LOCK, 3/3 LOCK; DELETE ELIGIBILITY APPLIES)
    // ==================================================

    @Test
    fun testCaseA_N3_thresholdProgression() = runBlocking {
        val pkg = "com.test.casea"
        coreRepository.addVaultApp(pkg, "Case A App")
        val t1 = coreRepository.createTask("Task A1")
        val t2 = coreRepository.createTask("Task A2")
        val t3 = coreRepository.createTask("Task A3")
        coreRepository.linkTaskToApp(t1, pkg)
        coreRepository.linkTaskToApp(t2, pkg)
        coreRepository.linkTaskToApp(t3, pkg)

        val today = businessDayProvider.getBusinessDate(clock.wallTimeMillis(), testZone).toString()

        // 0/3 -> LOCK (requiredForDeletion = 2)
        var res = adapter.evaluate(pkg)
        assertEquals(EnforcementAction.LOCK, res.finalAction)
        assertEquals(2, res.requiredTasksCount)
        assertEquals(0, res.completedLinkedTasksCount)
        assertFalse(CanonicalAppDeletionPolicy.canDelete(3, 0))

        // 1/3 -> LOCK
        coreRepository.setTaskCompletion(t1, today, true)
        res = adapter.evaluate(pkg)
        assertEquals(EnforcementAction.LOCK, res.finalAction)
        assertEquals(1, res.completedLinkedTasksCount)
        assertFalse(CanonicalAppDeletionPolicy.canDelete(3, 1))

        // 2/3 -> VẪN LOCKED theo Lock Policy, nhưng ĐỦ ĐIỀU KIỆN XÓA theo Deletion Policy
        coreRepository.setTaskCompletion(t2, today, true)
        res = adapter.evaluate(pkg)
        assertEquals(EnforcementAction.LOCK, res.finalAction)
        assertEquals(2, res.completedLinkedTasksCount)
        assertTrue(CanonicalAppDeletionPolicy.canDelete(3, 2))

        // 3/3 -> VẪN LOCKED vì còn tasks liên kết trong chu kỳ
        coreRepository.setTaskCompletion(t3, today, true)
        res = adapter.evaluate(pkg)
        assertEquals(EnforcementAction.LOCK, res.finalAction)
        assertEquals(3, res.completedLinkedTasksCount)
        assertTrue(CanonicalAppDeletionPolicy.canDelete(3, 3))
    }

    // ==================================================
    // 14. CASE B: N = 4 (2/4 LOCK, 3/4 LOCK; DELETION THRESHOLD AT 3)
    // ==================================================

    @Test
    fun testCaseB_N4_thresholdProgression() = runBlocking {
        val pkg = "com.test.caseb"
        coreRepository.addVaultApp(pkg, "Case B App")
        val t1 = coreRepository.createTask("Task B1")
        val t2 = coreRepository.createTask("Task B2")
        val t3 = coreRepository.createTask("Task B3")
        val t4 = coreRepository.createTask("Task B4")
        coreRepository.linkTaskToApp(t1, pkg)
        coreRepository.linkTaskToApp(t2, pkg)
        coreRepository.linkTaskToApp(t3, pkg)
        coreRepository.linkTaskToApp(t4, pkg)

        val today = businessDayProvider.getBusinessDate(clock.wallTimeMillis(), testZone).toString()

        // 2/4 completed -> LOCK (requiredForDeletion = 3)
        coreRepository.setTaskCompletion(t1, today, true)
        coreRepository.setTaskCompletion(t2, today, true)
        var res = adapter.evaluate(pkg)
        assertEquals(3, res.requiredTasksCount)
        assertEquals(2, res.completedLinkedTasksCount)
        assertEquals(EnforcementAction.LOCK, res.finalAction)
        assertFalse(CanonicalAppDeletionPolicy.canDelete(4, 2))

        // 3/4 completed -> VẪN LOCKED nhưng ĐỦ ĐIỀU KIỆN XÓA (K=3 >= RequiredForDeletion=3)
        coreRepository.setTaskCompletion(t3, today, true)
        res = adapter.evaluate(pkg)
        assertEquals(3, res.completedLinkedTasksCount)
        assertEquals(EnforcementAction.LOCK, res.finalAction)
        assertTrue(CanonicalAppDeletionPolicy.canDelete(4, 3))
    }

    // ==================================================
    // 15. CASE C: N = 5 (3/5 LOCK, 4/5 LOCK; DELETION THRESHOLD AT 4)
    // ==================================================

    @Test
    fun testCaseC_N5_thresholdProgression() = runBlocking {
        val pkg = "com.test.casec"
        coreRepository.addVaultApp(pkg, "Case C App")
        val t1 = coreRepository.createTask("Task C1")
        val t2 = coreRepository.createTask("Task C2")
        val t3 = coreRepository.createTask("Task C3")
        val t4 = coreRepository.createTask("Task C4")
        val t5 = coreRepository.createTask("Task C5")
        coreRepository.linkTaskToApp(t1, pkg)
        coreRepository.linkTaskToApp(t2, pkg)
        coreRepository.linkTaskToApp(t3, pkg)
        coreRepository.linkTaskToApp(t4, pkg)
        coreRepository.linkTaskToApp(t5, pkg)

        val today = businessDayProvider.getBusinessDate(clock.wallTimeMillis(), testZone).toString()

        // 3/5 completed -> LOCK (requiredForDeletion = 4)
        coreRepository.setTaskCompletion(t1, today, true)
        coreRepository.setTaskCompletion(t2, today, true)
        coreRepository.setTaskCompletion(t3, today, true)
        var res = adapter.evaluate(pkg)
        assertEquals(4, res.requiredTasksCount)
        assertEquals(3, res.completedLinkedTasksCount)
        assertEquals(EnforcementAction.LOCK, res.finalAction)
        assertFalse(CanonicalAppDeletionPolicy.canDelete(5, 3))

        // 4/5 completed -> VẪN LOCKED nhưng ĐỦ ĐIỀU KIỆN XÓA (K=4 >= RequiredForDeletion=4)
        coreRepository.setTaskCompletion(t4, today, true)
        res = adapter.evaluate(pkg)
        assertEquals(4, res.completedLinkedTasksCount)
        assertEquals(EnforcementAction.LOCK, res.finalAction)
        assertTrue(CanonicalAppDeletionPolicy.canDelete(5, 4))
    }

    // ==================================================
    // 16. CASE D & E: TECHNICAL OVERRIDES VS CANONICAL LOCK SOVEREIGNTY
    // ==================================================

    @Test
    fun testCaseD_and_CaseE_technicalPrecedence() = runBlocking {
        val pkg = "com.test.precedence"
        coreRepository.addVaultApp(pkg, "Precedence App")
        val t1 = coreRepository.createTask("Task P1")
        coreRepository.linkTaskToApp(t1, pkg)

        val today = businessDayProvider.getBusinessDate(clock.wallTimeMillis(), testZone).toString()
        coreRepository.setTaskCompletion(t1, today, true)

        // Case E: Có task liên kết -> VẪN LOCKED
        var res = adapter.evaluate(pkg)
        assertEquals(EnforcementAction.LOCK, res.finalAction)

        // Case D: Technical Lock active + task linked -> LOCK
        fakeTargetRepo.apps[pkg] = LockedApp(packageName = pkg, enabled = true)
        res = adapter.evaluate(pkg)
        assertEquals(EnforcementAction.LOCK, res.finalAction)
        assertTrue(res.isTechnicalLockActive)

        // Khi gỡ liên kết khỏi App (N=0) -> App thuộc Vault nhưng N=0 -> UNLOCKED (ALLOW)
        coreRepository.unlinkTaskFromApp(t1, pkg)
        fakeTargetRepo.apps.remove(pkg)
        res = adapter.evaluate(pkg)
        assertEquals(EnforcementAction.ALLOW, res.finalAction)
    }

    // ==================================================
    // 17. CASE F: 04:00 BUSINESS DAY BOUNDARY RESET
    // ==================================================

    @Test
    fun testCaseF_businessDay0400Reset() = runBlocking {
        val pkg = "com.test.reset0400"
        coreRepository.addVaultApp(pkg, "Reset App")
        val t1 = coreRepository.createTask("Task Day 1")
        coreRepository.linkTaskToApp(t1, pkg)

        // Current time: 2026-09-07 10:00 UTC -> Business Day: 2026-09-07
        val day1 = businessDayProvider.getBusinessDate(clock.wallTimeMillis(), testZone).toString()
        coreRepository.setTaskCompletion(t1, day1, true)

        var res = adapter.evaluate(pkg)
        assertEquals(EnforcementAction.LOCK, res.finalAction)
        assertEquals(1, res.completedLinkedTasksCount)
        assertTrue(CanonicalAppDeletionPolicy.canDelete(1, 1))

        // Advance clock past 04:00 next day: 2026-09-08 05:00 UTC -> Business Day: 2026-09-08
        clock.setWallTime(java.time.LocalDateTime.of(2026, 9, 8, 5, 0).toInstant(java.time.ZoneOffset.UTC).toEpochMilli())

        // Completion from day1 must NOT be counted for day2
        val resNextDay = adapter.evaluate(pkg)
        assertEquals(0, resNextDay.completedLinkedTasksCount)
        assertEquals(EnforcementAction.LOCK, resNextDay.finalAction)
        assertFalse(CanonicalAppDeletionPolicy.canDelete(1, 0))

        // Mark completion for day2 -> Vẫn LOCKED nhưng canDelete = true
        val day2 = businessDayProvider.getBusinessDate(clock.wallTimeMillis(), testZone).toString()
        coreRepository.setTaskCompletion(t1, day2, true)

        val resNextDayUnlocked = adapter.evaluate(pkg)
        assertEquals(1, resNextDayUnlocked.completedLinkedTasksCount)
        assertEquals(EnforcementAction.LOCK, resNextDayUnlocked.finalAction)
        assertTrue(CanonicalAppDeletionPolicy.canDelete(1, 1))
    }

    // ==================================================
    // 18. CACHE INVALIDATION & ATOMIC REFRESH
    // ==================================================

    @Test
    fun testCacheRefresh_afterCompletion() = runBlocking {
        val pkg = "com.test.cached"
        coreRepository.addVaultApp(pkg, "Cached App")
        val t1 = coreRepository.createTask("Task Cached")
        coreRepository.linkTaskToApp(t1, pkg)

        // Prime the cache via evaluate
        adapter.evaluate(pkg)
        assertEquals(EnforcementAction.LOCK, adapter.evaluateSync(pkg).finalAction)

        // Complete task in repository
        val today = businessDayProvider.getBusinessDate(clock.wallTimeMillis(), testZone).toString()
        coreRepository.setTaskCompletion(t1, today, true)

        // Recompute snapshot
        adapter.evaluate(pkg)

        // evaluateSync immediately sees the updated completion count
        val syncRes = adapter.evaluateSync(pkg)
        assertEquals(EnforcementAction.LOCK, syncRes.finalAction)
        assertEquals(1, syncRes.completedLinkedTasksCount)
        assertTrue(CanonicalAppDeletionPolicy.canDelete(1, 1))
    }

    // ==================================================
    // 19. APP REMOVED AND RE-ADDED CLEARS LINKAGE
    // ==================================================

    @Test
    fun testAppReAddedToVault_doesNotInheritOldLinkage() = runBlocking {
        val pkg = "com.test.readd"
        coreRepository.addVaultApp(pkg, "Re-add App")
        val t1 = coreRepository.createTask("Task Re-add")
        coreRepository.linkTaskToApp(t1, pkg)

        val today = businessDayProvider.getBusinessDate(clock.wallTimeMillis(), testZone).toString()
        coreRepository.setTaskCompletion(t1, today, true)
        // Vẫn LOCKED khi còn task
        assertEquals(EnforcementAction.LOCK, adapter.evaluate(pkg).finalAction)

        // Remove from Vault
        coreRepository.removeVaultApp(pkg)
        assertEquals(EnforcementAction.ALLOW, adapter.evaluate(pkg).finalAction) // Non-vault app

        // Re-add to Vault: should have 0 linked tasks -> SSOT: UNLOCKED (ALLOW)
        coreRepository.addVaultApp(pkg, "Re-add App")
        val resReadded = adapter.evaluate(pkg)
        assertEquals(0, resReadded.totalLinkedTasksCount)
        assertEquals(0, resReadded.activeLinkedTasksCount)
        assertEquals(EnforcementAction.ALLOW, resReadded.finalAction)
        assertEquals(BusinessUnlockDecision.NO_LINKED_TASKS, resReadded.businessUnlockDecision)
        assertEquals(EnforcementReason.ALLOWED_UNLOCKED_BY_TASKS, resReadded.reason)
    }
}
