package com.example.selfdisciplinepoc01.domain.enforcement

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.selfdisciplinepoc01.data.database.AppDatabase
import com.example.selfdisciplinepoc01.data.repository.CoreDataRepository
import com.example.selfdisciplinepoc01.data.repository.CoreDataRepositoryImpl
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
    fun test02_vaultApp_unlinked_isLockedNoTasks() = runBlocking {
        coreRepository.addVaultApp("com.google.android.youtube", "YouTube")

        val result = adapter.evaluate("com.google.android.youtube")
        assertEquals(EnforcementAction.LOCK, result.finalAction)
        assertEquals(AppEnforcementClassification.VAULT_APP_UNLINKED, result.classification)
        assertEquals(EnforcementReason.LOCKED_BY_VAULT_NO_TASK, result.reason)
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

        // Complete task today (1/1 for both apps, meets required = 1)
        val today = businessDayProvider.getBusinessDate(clock.wallTimeMillis(), testZone).toString()
        coreRepository.setTaskCompletion(taskId, today, true)

        val updatedA = adapter.evaluate("com.app.a")
        val updatedB = adapter.evaluate("com.app.b")

        assertEquals(1, updatedA.completedLinkedTasksCount)
        assertEquals(1, updatedB.completedLinkedTasksCount)
        assertEquals(EnforcementAction.ALLOW, updatedA.finalAction)
        assertEquals(EnforcementAction.ALLOW, updatedB.finalAction)
        assertEquals(BusinessUnlockDecision.UNLOCKED, updatedA.businessUnlockDecision)
        assertEquals(BusinessUnlockDecision.UNLOCKED, updatedB.businessUnlockDecision)
        assertEquals(EnforcementReason.ALLOWED_UNLOCKED_BY_TASKS, updatedA.reason)
        assertEquals(EnforcementReason.ALLOWED_UNLOCKED_BY_TASKS, updatedB.reason)
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

        // Complete t2 -> Should unlock (1/1 active completed)
        val today = businessDayProvider.getBusinessDate(clock.wallTimeMillis(), testZone).toString()
        coreRepository.setTaskCompletion(t2, today, true)

        val unlockedResult = adapter.evaluate("com.social.network")
        assertEquals(EnforcementAction.ALLOW, unlockedResult.finalAction)
        assertEquals(BusinessUnlockDecision.UNLOCKED, unlockedResult.businessUnlockDecision)

        // Archive t2 as well -> Active becomes 0 -> becomes unlinked effectively -> LOCK
        coreRepository.archiveTask(t2)
        val resultAllArchived = adapter.evaluate("com.social.network")
        assertEquals(2, resultAllArchived.totalLinkedTasksCount)
        assertEquals(0, resultAllArchived.activeLinkedTasksCount)
        assertEquals(2, resultAllArchived.archivedLinkedTasksCount)
        assertEquals(0, resultAllArchived.requiredTasksCount)
        assertEquals(AppEnforcementClassification.VAULT_APP_UNLINKED, resultAllArchived.classification)
        assertEquals(EnforcementReason.LOCKED_BY_VAULT_NO_TASK, resultAllArchived.reason)
        assertEquals(EnforcementAction.LOCK, resultAllArchived.finalAction)
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
        // Complete 3 out of 3 tasks (100% completed >= 2 required)
        coreRepository.setTaskCompletion(t1, today, true)
        coreRepository.setTaskCompletion(t2, today, true)
        coreRepository.setTaskCompletion(t3, today, true)

        val result = adapter.evaluate("com.video.stream")
        assertEquals(3, result.totalLinkedTasksCount)
        assertEquals(3, result.activeLinkedTasksCount)
        assertEquals(3, result.completedLinkedTasksCount)
        assertEquals(0, result.incompleteLinkedTasksCount)
        assertEquals(2, result.requiredTasksCount)

        // OPEN-01 implemented: 3/3 >= 2 -> ALLOW
        assertEquals(EnforcementAction.ALLOW, result.finalAction)
        assertEquals(BusinessUnlockDecision.UNLOCKED, result.businessUnlockDecision)
        assertEquals(EnforcementReason.ALLOWED_UNLOCKED_BY_TASKS, result.reason)
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

        val result = adapter.evaluate("com.android.chrome")
        assertEquals(EnforcementAction.LOCK, result.finalAction)
        assertEquals(EnforcementReason.LOCKED_BY_POLICY, result.reason)
        assertTrue(result.isTechnicalLockActive)
        assertEquals(PolicyDecision.LOCK, result.technicalPolicyDecision)
        assertFalse(result.isVaultApp)
    }

    // ==================================================
    // 10. SUPREME PRECEDENCE: TECHNICAL LOCK OVERRIDES BUSINESS UNLOCK
    // ==================================================

    @Test
    fun test10_adapterNeverBypassesTechnicalLock_evenIfThresholdReached() = runBlocking {
        // Chrome is in TargetRepository (Policy LOCK) AND in Vault
        fakeTargetRepo.apps["com.android.chrome"] = LockedApp(
            packageName = "com.android.chrome",
            enabled = true,
            schedule = null,
            timeLimit = null
        )
        coreRepository.addVaultApp("com.android.chrome", "Google Chrome")
        val t1 = coreRepository.createTask("Nhiệm vụ siêu cấp")
        coreRepository.linkTaskToApp(t1, "com.android.chrome")

        // Complete task (1/1 meets required 1)
        val today = businessDayProvider.getBusinessDate(clock.wallTimeMillis(), testZone).toString()
        coreRepository.setTaskCompletion(t1, today, true)

        val result = adapter.evaluate("com.android.chrome")

        // Supreme precedence: Technical Lock overrides everything
        assertTrue(result.isTechnicalLockActive)
        assertEquals(PolicyDecision.LOCK, result.technicalPolicyDecision)
        assertEquals(EnforcementAction.LOCK, result.finalAction)
        assertEquals(EnforcementReason.LOCKED_BY_POLICY, result.reason)
        assertTrue(result.isVaultApp)
        assertEquals(1, result.completedLinkedTasksCount)
        assertEquals(1, result.requiredTasksCount)
        // Business side evaluated to UNLOCKED, but final action is strictly LOCK
        assertEquals(BusinessUnlockDecision.UNLOCKED, result.businessUnlockDecision)
    }

    // ==================================================
    // 11. SYNCHRONOUS FAST PATH (EVALUATESYNC == EVALUATE)
    // ==================================================

    @Test
    fun test11_evaluateSync_matchesEvaluateSemantics() = runBlocking {
        coreRepository.addVaultApp("com.test.fastapp", "Fast App")
        val t1 = coreRepository.createTask("Tập thể dục 30p")
        coreRepository.linkTaskToApp(t1, "com.test.fastapp")

        // 1. Initial state (0/1 -> LOCK)
        val asyncBefore = adapter.evaluate("com.test.fastapp")
        val syncBefore = adapter.evaluateSync("com.test.fastapp")
        assertEquals(asyncBefore.finalAction, syncBefore.finalAction)
        assertEquals(asyncBefore.businessUnlockDecision, syncBefore.businessUnlockDecision)
        assertEquals(asyncBefore.reason, syncBefore.reason)
        assertEquals(EnforcementAction.LOCK, syncBefore.finalAction)

        // 2. Complete task (1/1 -> ALLOW)
        val today = businessDayProvider.getBusinessDate(clock.wallTimeMillis(), testZone).toString()
        coreRepository.setTaskCompletion(t1, today, true)

        val asyncAfter = adapter.evaluate("com.test.fastapp")
        val syncAfter = adapter.evaluateSync("com.test.fastapp")
        assertEquals(asyncAfter.finalAction, syncAfter.finalAction)
        assertEquals(asyncAfter.businessUnlockDecision, syncAfter.businessUnlockDecision)
        assertEquals(asyncAfter.reason, syncAfter.reason)
        assertEquals(EnforcementAction.ALLOW, syncAfter.finalAction)
    }

    // ==================================================
    // 12. ARCHITECTURAL INTEGRITY & DELEGATION
    // ==================================================

    @Test
    fun test12_adapterStatus_andDelegationIntegrity() = runBlocking {
        assertEquals(TaskAppEnforcementStatus.TASK_BASED_UNLOCK_ACTIVE, adapter.getEnforcementStatus())

        coreRepository.addVaultApp("com.app.test", "Test App")
        val t1 = coreRepository.createTask("Task 1")
        coreRepository.linkTaskToApp(t1, "com.app.test")

        assertFalse(adapter.isTaskBasedUnlockApproved("com.app.test"))

        val today = businessDayProvider.getBusinessDate(clock.wallTimeMillis(), testZone).toString()
        coreRepository.setTaskCompletion(t1, today, true)

        assertTrue(adapter.isTaskBasedUnlockApproved("com.app.test"))
    }

    // ==================================================
    // 13. CASE A: N = 3 (0/3 LOCK, 1/3 LOCK, 2/3 ALLOW, 3/3 ALLOW)
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

        // 0/3 -> LOCK (required = 2)
        var res = adapter.evaluate(pkg)
        assertEquals(EnforcementAction.LOCK, res.finalAction)
        assertEquals(2, res.requiredTasksCount)
        assertEquals(0, res.completedLinkedTasksCount)

        // 1/3 -> LOCK
        coreRepository.setTaskCompletion(t1, today, true)
        res = adapter.evaluate(pkg)
        assertEquals(EnforcementAction.LOCK, res.finalAction)
        assertEquals(1, res.completedLinkedTasksCount)

        // 2/3 -> ALLOW (exact threshold)
        coreRepository.setTaskCompletion(t2, today, true)
        res = adapter.evaluate(pkg)
        assertEquals(EnforcementAction.ALLOW, res.finalAction)
        assertEquals(BusinessUnlockDecision.UNLOCKED, res.businessUnlockDecision)
        assertEquals(2, res.completedLinkedTasksCount)

        // 3/3 -> ALLOW (100%)
        coreRepository.setTaskCompletion(t3, today, true)
        res = adapter.evaluate(pkg)
        assertEquals(EnforcementAction.ALLOW, res.finalAction)
        assertEquals(BusinessUnlockDecision.UNLOCKED, res.businessUnlockDecision)
        assertEquals(3, res.completedLinkedTasksCount)
    }

    // ==================================================
    // 14. CASE B: N = 4 (2/4 LOCK, 3/4 ALLOW)
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

        // 2/4 completed -> LOCK (required = (2*4+2)/3 = 3)
        coreRepository.setTaskCompletion(t1, today, true)
        coreRepository.setTaskCompletion(t2, today, true)
        var res = adapter.evaluate(pkg)
        assertEquals(3, res.requiredTasksCount)
        assertEquals(2, res.completedLinkedTasksCount)
        assertEquals(EnforcementAction.LOCK, res.finalAction)
        assertEquals(BusinessUnlockDecision.INSUFFICIENT_COMPLETION, res.businessUnlockDecision)

        // 3/4 completed -> ALLOW (meets required 3)
        coreRepository.setTaskCompletion(t3, today, true)
        res = adapter.evaluate(pkg)
        assertEquals(3, res.completedLinkedTasksCount)
        assertEquals(EnforcementAction.ALLOW, res.finalAction)
        assertEquals(BusinessUnlockDecision.UNLOCKED, res.businessUnlockDecision)
    }

    // ==================================================
    // 15. CASE C: N = 5 (3/5 LOCK, 4/5 ALLOW)
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

        // 3/5 completed -> LOCK (required = (2*5+2)/3 = 4)
        coreRepository.setTaskCompletion(t1, today, true)
        coreRepository.setTaskCompletion(t2, today, true)
        coreRepository.setTaskCompletion(t3, today, true)
        var res = adapter.evaluate(pkg)
        assertEquals(4, res.requiredTasksCount)
        assertEquals(3, res.completedLinkedTasksCount)
        assertEquals(EnforcementAction.LOCK, res.finalAction)
        assertEquals(BusinessUnlockDecision.INSUFFICIENT_COMPLETION, res.businessUnlockDecision)

        // 4/5 completed -> ALLOW (meets required 4)
        coreRepository.setTaskCompletion(t4, today, true)
        res = adapter.evaluate(pkg)
        assertEquals(4, res.completedLinkedTasksCount)
        assertEquals(EnforcementAction.ALLOW, res.finalAction)
        assertEquals(BusinessUnlockDecision.UNLOCKED, res.businessUnlockDecision)
    }

    // ==================================================
    // 16. CASE D & E: TECHNICAL OVERRIDES VS BUSINESS UNLOCK
    // ==================================================

    @Test
    fun testCaseD_and_CaseE_technicalPrecedence() = runBlocking {
        val pkg = "com.test.precedence"
        coreRepository.addVaultApp(pkg, "Precedence App")
        val t1 = coreRepository.createTask("Task P1")
        coreRepository.linkTaskToApp(t1, pkg)

        val today = businessDayProvider.getBusinessDate(clock.wallTimeMillis(), testZone).toString()
        coreRepository.setTaskCompletion(t1, today, true)

        // Case E: Technical Lock inactive + Business threshold reached -> ALLOW
        var res = adapter.evaluate(pkg)
        assertEquals(EnforcementAction.ALLOW, res.finalAction)
        assertEquals(BusinessUnlockDecision.UNLOCKED, res.businessUnlockDecision)

        // Case D: Technical Lock active + Business threshold reached -> LOCK
        fakeTargetRepo.apps[pkg] = LockedApp(packageName = pkg, enabled = true)
        res = adapter.evaluate(pkg)
        assertEquals(EnforcementAction.LOCK, res.finalAction)
        assertEquals(EnforcementReason.LOCKED_BY_POLICY, res.reason)
        assertEquals(BusinessUnlockDecision.UNLOCKED, res.businessUnlockDecision)
        assertTrue(res.isTechnicalLockActive)
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
        assertEquals(EnforcementAction.ALLOW, res.finalAction)
        assertEquals(1, res.completedLinkedTasksCount)

        // Advance clock past 04:00 next day: 2026-09-08 05:00 UTC -> Business Day: 2026-09-08
        clock.setWallTime(java.time.LocalDateTime.of(2026, 9, 8, 5, 0).toInstant(java.time.ZoneOffset.UTC).toEpochMilli())

        // Completion from day1 must NOT be counted for day2
        val resNextDay = adapter.evaluate(pkg)
        assertEquals(0, resNextDay.completedLinkedTasksCount)
        assertEquals(EnforcementAction.LOCK, resNextDay.finalAction)
        assertEquals(BusinessUnlockDecision.INSUFFICIENT_COMPLETION, resNextDay.businessUnlockDecision)

        // Mark completion for day2
        val day2 = businessDayProvider.getBusinessDate(clock.wallTimeMillis(), testZone).toString()
        coreRepository.setTaskCompletion(t1, day2, true)

        val resNextDayUnlocked = adapter.evaluate(pkg)
        assertEquals(1, resNextDayUnlocked.completedLinkedTasksCount)
        assertEquals(EnforcementAction.ALLOW, resNextDayUnlocked.finalAction)
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

        // evaluateSync immediately sees the unlocked action without delay
        val syncRes = adapter.evaluateSync(pkg)
        assertEquals(EnforcementAction.ALLOW, syncRes.finalAction)
        assertEquals(BusinessUnlockDecision.UNLOCKED, syncRes.businessUnlockDecision)
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
        assertEquals(EnforcementAction.ALLOW, adapter.evaluate(pkg).finalAction)

        // Remove from Vault
        coreRepository.removeVaultApp(pkg)
        assertEquals(EnforcementAction.ALLOW, adapter.evaluate(pkg).finalAction) // Non-vault app

        // Re-add to Vault: should have 0 linked tasks
        coreRepository.addVaultApp(pkg, "Re-add App")
        val resReadded = adapter.evaluate(pkg)
        assertEquals(0, resReadded.totalLinkedTasksCount)
        assertEquals(0, resReadded.activeLinkedTasksCount)
        assertEquals(EnforcementAction.LOCK, resReadded.finalAction)
        assertEquals(BusinessUnlockDecision.NO_LINKED_TASKS, resReadded.businessUnlockDecision)
        assertEquals(EnforcementReason.LOCKED_BY_VAULT_NO_TASK, resReadded.reason)
    }
}
