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
 * Comprehensive Integration Test Suite for Phase 20: App Lock Integration Design.
 *
 * Covers:
 * 1. App not in Vault (unprotected)
 * 2. App in Vault without linked tasks
 * 3. App in Vault with linked tasks
 * 4. Multiple tasks linked to a single app
 * 5. Single task linked to multiple apps
 * 6. Archived tasks excluded from active evaluation
 * 7. Deleted tasks excluded from active evaluation
 * 8. Completed tasks do not trigger automatic unlock (OPEN-01 Hard Boundary)
 * 9. Technical App Lock operates independently
 * 10. Adapter never bypasses Technical Lock
 * 11. Fast synchronous snapshot evaluation for AccessibilityService
 * 12. Strict OPEN-01 governance reflection tests
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
    // 2. VAULT APP WITHOUT TASKS
    // ==================================================

    @Test
    fun test02_vaultApp_unlinked_isLockedPendingRule() = runBlocking {
        coreRepository.addVaultApp("com.google.android.youtube", "YouTube")

        val result = adapter.evaluate("com.google.android.youtube")
        assertEquals(EnforcementAction.LOCK, result.finalAction)
        assertEquals(AppEnforcementClassification.VAULT_APP_UNLINKED, result.classification)
        assertEquals(EnforcementReason.LOCKED_BY_VAULT_NO_TASK, result.reason)
        assertEquals(BusinessUnlockDecision.PENDING_OPEN_01, result.businessUnlockDecision)
        assertTrue(result.isVaultApp)
        assertEquals(0, result.totalLinkedTasksCount)
        assertEquals(0, result.activeLinkedTasksCount)
    }

    // ==================================================
    // 3. VAULT APP WITH LINKED TASKS
    // ==================================================

    @Test
    fun test03_vaultApp_withLinkedTasks_isLockedPendingOpen01() = runBlocking {
        coreRepository.addVaultApp("com.google.android.youtube", "YouTube")
        val taskId = coreRepository.createTask("Đọc sách tu dưỡng 30p")
        coreRepository.linkTaskToApp(taskId, "com.google.android.youtube")

        val result = adapter.evaluate("com.google.android.youtube")
        assertEquals(EnforcementAction.LOCK, result.finalAction)
        assertEquals(AppEnforcementClassification.VAULT_APP_WITH_TASKS, result.classification)
        assertEquals(EnforcementReason.LOCKED_PENDING_BUSINESS_RULE, result.reason)
        assertEquals(BusinessUnlockDecision.PENDING_OPEN_01, result.businessUnlockDecision)
        assertEquals(1, result.totalLinkedTasksCount)
        assertEquals(1, result.activeLinkedTasksCount)
        assertEquals(0, result.completedLinkedTasksCount)
        assertEquals(1, result.incompleteLinkedTasksCount)
    }

    // ==================================================
    // 4. MULTIPLE TASKS FOR SINGLE APP
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
        // Mark Task 1 completed
        coreRepository.setTaskCompletion(t1, today, true)

        val result = adapter.evaluate("com.game.gacha")
        assertEquals(3, result.totalLinkedTasksCount)
        assertEquals(3, result.activeLinkedTasksCount)
        assertEquals(1, result.completedLinkedTasksCount)
        assertEquals(2, result.incompleteLinkedTasksCount)
        assertEquals(EnforcementAction.LOCK, result.finalAction)
        assertEquals(BusinessUnlockDecision.PENDING_OPEN_01, result.businessUnlockDecision)
    }

    // ==================================================
    // 5. SINGLE TASK FOR MULTIPLE APPS (MANY-TO-MANY)
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
        assertEquals(EnforcementAction.LOCK, resultA.finalAction)
        assertEquals(EnforcementAction.LOCK, resultB.finalAction)

        // Complete task today
        val today = businessDayProvider.getBusinessDate(clock.wallTimeMillis(), testZone).toString()
        coreRepository.setTaskCompletion(taskId, today, true)

        val updatedA = adapter.evaluate("com.app.a")
        val updatedB = adapter.evaluate("com.app.b")

        assertEquals(1, updatedA.completedLinkedTasksCount)
        assertEquals(1, updatedB.completedLinkedTasksCount)
        // OPEN-01 hard barrier: neither app automatically unlocks
        assertEquals(EnforcementAction.LOCK, updatedA.finalAction)
        assertEquals(EnforcementAction.LOCK, updatedB.finalAction)
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

        // Archive t1
        coreRepository.archiveTask(t1)

        val result = adapter.evaluate("com.social.network")
        assertEquals(2, result.totalLinkedTasksCount)
        assertEquals(1, result.activeLinkedTasksCount)
        assertEquals(1, result.archivedLinkedTasksCount)
        assertEquals(AppEnforcementClassification.VAULT_APP_WITH_TASKS, result.classification)

        // Archive t2 as well -> becomes unlinked effectively
        coreRepository.archiveTask(t2)
        val resultAllArchived = adapter.evaluate("com.social.network")
        assertEquals(2, resultAllArchived.totalLinkedTasksCount)
        assertEquals(0, resultAllArchived.activeLinkedTasksCount)
        assertEquals(2, resultAllArchived.archivedLinkedTasksCount)
        assertEquals(AppEnforcementClassification.VAULT_APP_UNLINKED, resultAllArchived.classification)
        assertEquals(EnforcementReason.LOCKED_BY_VAULT_NO_TASK, resultAllArchived.reason)
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
    }

    // ==================================================
    // 8. ALL TASKS COMPLETED (OPEN-01 HARD BOUNDARY)
    // ==================================================

    @Test
    fun test08_completedTasks_doNotTriggerAutomaticUnlock_open01Protected() = runBlocking {
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

        // CRITICAL GOVERNANCE PROOF:
        // Even with 3/3 tasks completed, final action MUST REMAIN LOCK because OPEN-01 formula is pending.
        assertEquals("Vault app must not auto-unlock without canonical OPEN-01 rule", EnforcementAction.LOCK, result.finalAction)
        assertEquals(BusinessUnlockDecision.PENDING_OPEN_01, result.businessUnlockDecision)
        assertEquals(EnforcementReason.LOCKED_PENDING_BUSINESS_RULE, result.reason)
    }

    // ==================================================
    // 9. TECHNICAL APP LOCK OPERATES INDEPENDENTLY
    // ==================================================

    @Test
    fun test09_technicalAppLock_operatesIndependently_locksRegardlessOfVault() = runBlocking {
        // Chrome configured in TargetRepository as 24/7 locked target (Phase 06/07 behavior)
        fakeTargetRepo.apps["com.android.chrome"] = LockedApp(
            packageName = "com.android.chrome",
            enabled = true,
            schedule = null,
            timeLimit = null
        )

        // Chrome is NOT in Vault
        val result = adapter.evaluate("com.android.chrome")
        assertEquals(EnforcementAction.LOCK, result.finalAction)
        assertEquals(EnforcementReason.LOCKED_BY_POLICY, result.reason)
        assertTrue(result.isTechnicalLockActive)
        assertEquals(PolicyDecision.LOCK, result.technicalPolicyDecision)
        assertFalse(result.isVaultApp)
    }

    // ==================================================
    // 10. ADAPTER NEVER BYPASSES TECHNICAL LOCK
    // ==================================================

    @Test
    fun test10_adapterNeverBypassesTechnicalLock_evenIfVaultApp() = runBlocking {
        // Chrome is in TargetRepository AND in Vault
        fakeTargetRepo.apps["com.android.chrome"] = LockedApp(
            packageName = "com.android.chrome",
            enabled = true,
            schedule = null,
            timeLimit = null
        )
        coreRepository.addVaultApp("com.android.chrome", "Google Chrome")
        val t1 = coreRepository.createTask("Nhiệm vụ siêu cấp")
        coreRepository.linkTaskToApp(t1, "com.android.chrome")

        // Complete task
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
    }

    // ==================================================
    // 11. SYNCHRONOUS FAST PATH SNAPSHOT EVALUATION
    // ==================================================

    @Test
    fun test11_evaluateSync_usesInMemorySnapshot_fastNonBlocking() {
        adapter.updateCacheForApp(
            packageName = "com.test.fastapp",
            isVault = true,
            linkedTasks = emptyList()
        )

        val result = adapter.evaluateSync("com.test.fastapp")
        assertEquals(EnforcementAction.LOCK, result.finalAction)
        assertEquals(AppEnforcementClassification.VAULT_APP_UNLINKED, result.classification)
        assertEquals(EnforcementReason.LOCKED_BY_VAULT_NO_TASK, result.reason)

        // Non-vault app in sync fast path
        val nonVaultResult = adapter.evaluateSync("com.test.randomapp")
        assertEquals(EnforcementAction.ALLOW, nonVaultResult.finalAction)
        assertEquals(AppEnforcementClassification.NON_VAULT_APP, nonVaultResult.classification)
    }

    // ==================================================
    // 12. OPEN-01 GOVERNANCE & REFLECTION INTEGRITY
    // ==================================================

    @Test
    fun test12_open01Governance_noFormulaOrCalculationsInClass() = runBlocking {
        assertEquals(TaskAppEnforcementStatus.DISABLED_PENDING_OPEN_01, adapter.getEnforcementStatus())
        assertFalse(adapter.isTaskBasedUnlockApproved("com.android.chrome"))

        val methods = TaskAppEnforcementAdapter::class.java.declaredMethods
        for (m in methods) {
            val name = m.name.lowercase()
            assertFalse("Method must not calculate points: $name", name.contains("point"))
            assertFalse("Method must not calculate ratio: $name", name.contains("ratio"))
            assertFalse("Method must not calculate percentage: $name", name.contains("percent"))
            assertFalse("Method must not calculate threshold: $name", name.contains("threshold"))
        }
    }
}
