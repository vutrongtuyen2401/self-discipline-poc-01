package com.example.selfdisciplinepoc01.domain.enforcement

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.selfdisciplinepoc01.data.database.AppDatabase
import com.example.selfdisciplinepoc01.data.repository.CoreDataRepository
import com.example.selfdisciplinepoc01.data.repository.CoreDataRepositoryImpl
import com.example.selfdisciplinepoc01.domain.canonical.vault.CanonicalAppDeletionPolicy
import com.example.selfdisciplinepoc01.policy.PolicyEngine
import com.example.selfdisciplinepoc01.target.model.LockedApp
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.ZoneId

/**
 * PHASE 27.1 — TaskAppEnforcementAdapter Regression Test Suite
 *
 * Covers all 7 required regression scenarios:
 * 1. link task updates enforcement state
 * 2. linked task is visible to adapter immediately
 * 3. incomplete linked task causes LOCK
 * 4. completed linked task causes ALLOW
 * 5. removing linkage refreshes enforcement
 * 6. re-adding app does not restore old linkage
 * 7. stale snapshot cannot bypass new linkage
 */
@RunWith(RobolectricTestRunner::class)
class TaskAppEnforcementAdapterRegressionTest {

    private val testZone = ZoneId.of("UTC")
    private val clock = TestClock.at(2026, 9, 8, 10, 0, zoneId = testZone)
    private val businessDayProvider: BusinessDayProvider = BusinessDayProviderImpl(boundaryHour = 4)
    private val today = "2026-09-08"

    private lateinit var database: AppDatabase
    private lateinit var coreRepository: CoreDataRepository
    private lateinit var fakeTargetRepo: FakeTargetRepository
    private lateinit var fakeUsageProvider: FakeUsageProvider
    private lateinit var policyEngine: PolicyEngine
    private lateinit var adapter: TaskAppEnforcementAdapter

    private class FakeUsageProvider(private val usageMap: MutableMap<String, Long> = mutableMapOf()) : UsageProvider {
        override fun getTodayUsage(packageName: String): Long = usageMap[packageName] ?: 0L
    }

    private class FakeTargetRepository(val apps: MutableMap<String, LockedApp> = mutableMapOf()) : TargetRepository {
        override fun getLockedPackages(): Flow<List<LockedApp>> = flowOf(apps.values.toList())
        override fun isLocked(packageName: String): Boolean = apps[packageName]?.enabled == true
        override fun getTarget(packageName: String): LockedApp? = apps[packageName]
        override suspend fun add(packageName: String) { apps[packageName] = LockedApp(packageName, true) }
        override suspend fun remove(packageName: String) { apps.remove(packageName) }
        override suspend fun setEnabled(packageName: String, enabled: Boolean) {}
        override suspend fun updatePolicy(packageName: String, schedule: com.example.selfdisciplinepoc01.target.model.TimeSchedule?, timeLimit: com.example.selfdisciplinepoc01.target.model.TimeLimit?) {}
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

    /**
     * Scenario 1: link task updates enforcement state
     * Initial: Vault App with N=0 -> Reason is LOCKED_BY_VAULT_NO_TASK.
     * After linking task: Enforcement details reflect N=1 and Reason is LOCKED_INSUFFICIENT_TASKS.
     */
    @Test
    fun test01_linkTaskUpdatesEnforcementState() = runBlocking {
        val pkg = "com.test.scenario1"
        coreRepository.addVaultApp(pkg, "Scenario 1 App")
        adapter.recomputeSnapshot()

        // MASTER SSOT & Phase 2A: N=0 in Vault is UNLOCKED (ALLOW)
        val detailsBefore = adapter.evaluateSync(pkg)
        assertEquals(EnforcementAction.ALLOW, detailsBefore.finalAction)
        assertEquals(EnforcementReason.ALLOWED_UNLOCKED_BY_TASKS, detailsBefore.reason)
        assertEquals(0, detailsBefore.totalLinkedTasksCount)

        // Create and link task -> N=1 incomplete task -> transitions to LOCK
        val taskId = coreRepository.createTask("Task for Scenario 1")
        coreRepository.linkTaskToApp(taskId, pkg)
        adapter.recomputeSnapshot()

        val detailsAfter = adapter.evaluateSync(pkg)
        assertEquals(EnforcementAction.LOCK, detailsAfter.finalAction)
        assertEquals(EnforcementReason.LOCKED_INSUFFICIENT_TASKS, detailsAfter.reason)
        assertEquals(1, detailsAfter.totalLinkedTasksCount)
        assertEquals(1, detailsAfter.requiredTasksCount)
        assertEquals(0, detailsAfter.completedLinkedTasksCount)
    }

    /**
     * Scenario 2: linked task is visible to adapter immediately
     * After linking, adapter immediately reflects the linked task count and details.
     */
    @Test
    fun test02_linkedTaskIsVisibleToAdapterImmediately() = runBlocking {
        val pkg = "com.test.scenario2"
        coreRepository.addVaultApp(pkg, "Scenario 2 App")
        val taskId = coreRepository.createTask("Immediate Visibility Task")
        coreRepository.linkTaskToApp(taskId, pkg)
        adapter.recomputeSnapshot()

        val details = adapter.evaluateSync(pkg)
        assertEquals(1, details.totalLinkedTasksCount)
        assertEquals(1, details.activeLinkedTasksCount)
        assertEquals(EnforcementAction.LOCK, details.finalAction)
        assertEquals(EnforcementReason.LOCKED_INSUFFICIENT_TASKS, details.reason)
    }

    /**
     * Scenario 3: incomplete linked task causes LOCK
     * When task is linked but not completed, OPEN-01 rule requires (2*1+2)/3 = 1 completion.
     * With 0 completed, evaluation must result in LOCK.
     */
    @Test
    fun test03_incompleteLinkedTaskCausesLock() = runBlocking {
        val pkg = "com.test.scenario3"
        coreRepository.addVaultApp(pkg, "Scenario 3 App")
        val taskId = coreRepository.createTask("Incomplete Task")
        coreRepository.linkTaskToApp(taskId, pkg)
        adapter.recomputeSnapshot()

        val details = adapter.evaluateSync(pkg)
        assertEquals(EnforcementAction.LOCK, details.finalAction)
        assertEquals(BusinessUnlockDecision.INSUFFICIENT_COMPLETION, details.businessUnlockDecision)
        assertEquals(EnforcementReason.LOCKED_INSUFFICIENT_TASKS, details.reason)
        assertEquals(1, details.incompleteLinkedTasksCount)
        assertEquals(0, details.completedLinkedTasksCount)
    }

    /**
     * Scenario 4: completed linked task still causes LOCK according to MASTER SSOT
     * (App is locked iff at least one current-cycle task requires it; completed task still requires app).
     * Deletion policy allows deletion, but lock policy keeps it locked.
     */
    @Test
    fun test04_completedLinkedTaskCausesAllow() = runBlocking {
        val pkg = "com.test.scenario4"
        coreRepository.addVaultApp(pkg, "Scenario 4 App")
        val taskId = coreRepository.createTask("Complete Me Task")
        coreRepository.linkTaskToApp(taskId, pkg)
        coreRepository.setTaskCompletion(taskId, today, true)
        adapter.recomputeSnapshot()

        val details = adapter.evaluateSync(pkg)
        assertEquals("Có task yêu cầu -> VẪN LOCKED dù task đã hoàn thành", EnforcementAction.LOCK, details.finalAction)
        assertEquals(1, details.completedLinkedTasksCount)
        assertEquals(1, details.requiredTasksCount)
        assertTrue("Đủ điều kiện xóa khỏi Vault", CanonicalAppDeletionPolicy.canDelete(1, 1))
    }

    /**
     * Scenario 5: removing linkage refreshes enforcement
     * App was locked with 1 completed task.
     * When linkage is removed, app becomes N=0 in Vault -> SSOT: UNLOCKED (ALLOW).
     */
    @Test
    fun test05_removingLinkageRefreshesEnforcement() = runBlocking {
        val pkg = "com.test.scenario5"
        coreRepository.addVaultApp(pkg, "Scenario 5 App")
        val taskId = coreRepository.createTask("Temporary Link Task")
        coreRepository.linkTaskToApp(taskId, pkg)
        coreRepository.setTaskCompletion(taskId, today, true)
        adapter.recomputeSnapshot()

        // Verify initial state: LOCK (vì vẫn còn task link yêu cầu app)
        assertEquals(EnforcementAction.LOCK, adapter.evaluateSync(pkg).finalAction)

        // Remove linkage
        coreRepository.unlinkTaskFromApp(taskId, pkg)
        adapter.recomputeSnapshot()

        // Verify updated state: N=0 is UNLOCKED per MASTER SSOT
        val details = adapter.evaluateSync(pkg)
        assertEquals(EnforcementAction.ALLOW, details.finalAction)
        assertEquals(BusinessUnlockDecision.NO_LINKED_TASKS, details.businessUnlockDecision)
        assertEquals(EnforcementReason.ALLOWED_UNLOCKED_BY_TASKS, details.reason)
        assertEquals(0, details.totalLinkedTasksCount)
    }

    /**
     * Scenario 6: re-adding app does not restore old linkage
     * App is removed from Vault, which cleans up cross-references.
     * When re-added, it starts clean with N=0 and is UNLOCKED per SSOT.
     */
    @Test
    fun test06_reAddingAppDoesNotRestoreOldLinkage() = runBlocking {
        val pkg = "com.test.scenario6"
        coreRepository.addVaultApp(pkg, "Scenario 6 App")
        val taskId = coreRepository.createTask("Old Linked Task")
        coreRepository.linkTaskToApp(taskId, pkg)
        adapter.recomputeSnapshot()
        assertEquals(1, adapter.evaluateSync(pkg).totalLinkedTasksCount)

        // Remove app from Vault
        coreRepository.removeVaultApp(pkg)
        adapter.recomputeSnapshot()
        // Now not in Vault and not in Technical lock -> ALLOW (unprotected)
        assertEquals(EnforcementAction.ALLOW, adapter.evaluateSync(pkg).finalAction)

        // Re-add app to Vault
        coreRepository.addVaultApp(pkg, "Scenario 6 App Re-added")
        adapter.recomputeSnapshot()

        // Re-added with N=0 -> SSOT: UNLOCKED, old linkage was NOT restored
        val details = adapter.evaluateSync(pkg)
        assertEquals(EnforcementAction.ALLOW, details.finalAction)
        assertEquals(EnforcementReason.ALLOWED_UNLOCKED_BY_TASKS, details.reason)
        assertEquals(0, details.totalLinkedTasksCount)
    }

    /**
     * Scenario 7: stale snapshot cannot bypass new linkage
     * When evaluate() is called, fresh data evaluation prevents stale in-memory snapshot
     * from incorrectly allowing access.
     */
    @Test
    fun test07_staleSnapshotCannotBypassNewLinkage() = runBlocking {
        val pkg = "com.test.scenario7"
        // Initially app is NOT in vault and NOT in technical lock -> ALLOW
        assertEquals(EnforcementAction.ALLOW, adapter.evaluateSync(pkg).finalAction)

        // Add to vault with an incomplete task
        coreRepository.addVaultApp(pkg, "Scenario 7 App")
        val taskId = coreRepository.createTask("Scenario 7 Task")
        coreRepository.linkTaskToApp(taskId, pkg)

        // Even before recomputeSnapshot() is called on cachedSnapshot,
        // calling evaluate(pkg) executes real-time evaluation and guarantees LOCK.
        val evalResult = adapter.evaluate(pkg)
        assertEquals(EnforcementAction.LOCK, evalResult.finalAction)

        // After snapshot refresh, evaluateSync also reflects LOCK
        adapter.recomputeSnapshot()
        assertEquals(EnforcementAction.LOCK, adapter.evaluateSync(pkg).finalAction)
    }
}
