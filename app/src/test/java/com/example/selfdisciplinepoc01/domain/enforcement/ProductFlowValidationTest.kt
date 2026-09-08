package com.example.selfdisciplinepoc01.domain.enforcement

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.selfdisciplinepoc01.data.database.AppDatabase
import com.example.selfdisciplinepoc01.data.repository.CoreDataRepository
import com.example.selfdisciplinepoc01.data.repository.CoreDataRepositoryImpl
import com.example.selfdisciplinepoc01.domain.discovery.InstalledAppDiscoveryService
import com.example.selfdisciplinepoc01.domain.model.DiscoveredApp
import com.example.selfdisciplinepoc01.domain.usecase.AddVaultAppUseCase
import com.example.selfdisciplinepoc01.domain.usecase.ArchiveTaskUseCase
import com.example.selfdisciplinepoc01.domain.usecase.CompleteTaskUseCase
import com.example.selfdisciplinepoc01.domain.usecase.CreateTaskUseCase
import com.example.selfdisciplinepoc01.domain.usecase.GetMissionHallTasksUseCase
import com.example.selfdisciplinepoc01.domain.usecase.GetTaskLinkedAppsUseCase
import com.example.selfdisciplinepoc01.domain.usecase.GetVaultAppsUseCase
import com.example.selfdisciplinepoc01.domain.usecase.RemoveVaultAppUseCase
import com.example.selfdisciplinepoc01.domain.usecase.UpdateTaskLinkedAppsUseCase
import com.example.selfdisciplinepoc01.policy.PolicyEngine
import com.example.selfdisciplinepoc01.target.model.LockedApp
import com.example.selfdisciplinepoc01.target.model.TimeLimit
import com.example.selfdisciplinepoc01.target.model.TimeSchedule
import com.example.selfdisciplinepoc01.target.repository.TargetRepository
import com.example.selfdisciplinepoc01.time.BusinessDayProvider
import com.example.selfdisciplinepoc01.time.BusinessDayProviderImpl
import com.example.selfdisciplinepoc01.time.TestClock
import com.example.selfdisciplinepoc01.ui.vault.VaultViewModel
import com.example.selfdisciplinepoc01.usage.UsageProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * PHASE 24 — PRODUCT FLOW VALIDATION & UNLOCK UX TEST SUITE
 *
 * Kiểm chứng toàn diện end-to-end product flow theo đúng quy chuẩn:
 * Bảo Khố -> liên kết App với Task -> Nhiệm Vụ Đường -> hoàn thành Task
 * -> cập nhật progress -> đạt ceil(2*N/3) -> App được business unlock
 * -> qua mốc 04:00 -> cycle mới -> App trở lại LOCK.
 *
 * Đồng thời kiểm thử UI State / UX Integration:
 * - AppEnforcementDetails được map đúng cho UI
 * - Không có business logic trong UI
 * - Technical Lock precedence tuyệt đối
 * - 04:00 boundary reset
 * - Snapshot invalidation tức thì
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ProductFlowValidationTest {

    private val testZone = ZoneId.of("UTC")
    private val clock = TestClock.at(2026, 9, 7, 10, 0, zoneId = testZone)
    private val businessDayProvider: BusinessDayProvider = BusinessDayProviderImpl(boundaryHour = 4)

    private lateinit var database: AppDatabase
    private lateinit var coreRepository: CoreDataRepository
    private lateinit var fakeTargetRepo: FakeTargetRepository
    private lateinit var fakeUsageProvider: FakeUsageProvider
    private lateinit var policyEngine: PolicyEngine
    private lateinit var adapter: TaskAppEnforcementAdapter

    // Use cases
    private lateinit var addVaultAppUseCase: AddVaultAppUseCase
    private lateinit var getVaultAppsUseCase: GetVaultAppsUseCase
    private lateinit var removeVaultAppUseCase: RemoveVaultAppUseCase
    private lateinit var createTaskUseCase: CreateTaskUseCase
    private lateinit var completeTaskUseCase: CompleteTaskUseCase
    private lateinit var archiveTaskUseCase: ArchiveTaskUseCase
    private lateinit var getTaskLinkedAppsUseCase: GetTaskLinkedAppsUseCase
    private lateinit var updateTaskLinkedAppsUseCase: UpdateTaskLinkedAppsUseCase
    private lateinit var getMissionHallTasksUseCase: GetMissionHallTasksUseCase

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

    private class FakeDiscoveryService : InstalledAppDiscoveryService {
        override suspend fun getDiscoveredApps(existingVaultPackages: Set<String>): List<DiscoveredApp> = emptyList()
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        coreRepository = CoreDataRepositoryImpl(database)

        fakeTargetRepo = FakeTargetRepository()
        fakeUsageProvider = FakeUsageProvider()
        policyEngine = PolicyEngine(fakeTargetRepo, fakeUsageProvider)

        adapter = TaskAppEnforcementAdapter(
            coreDataRepository = coreRepository,
            policyEngine = policyEngine,
            businessDayProvider = businessDayProvider,
            clock = clock,
            zoneIdProvider = { testZone }
        )

        addVaultAppUseCase = AddVaultAppUseCase(coreRepository)
        getVaultAppsUseCase = GetVaultAppsUseCase(coreRepository)
        removeVaultAppUseCase = RemoveVaultAppUseCase(coreRepository)
        createTaskUseCase = CreateTaskUseCase(coreRepository)
        completeTaskUseCase = CompleteTaskUseCase(coreRepository, businessDayProvider, clock = clock, zoneIdProvider = { testZone })
        archiveTaskUseCase = ArchiveTaskUseCase(coreRepository)
        getTaskLinkedAppsUseCase = GetTaskLinkedAppsUseCase(coreRepository)
        updateTaskLinkedAppsUseCase = UpdateTaskLinkedAppsUseCase(coreRepository)
        getMissionHallTasksUseCase = GetMissionHallTasksUseCase(coreRepository, businessDayProvider, clock = clock, zoneIdProvider = { testZone })
    }

    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    @Test
    fun testEndToEnd_Scenario1_ThreeTasks_UnlockFlow() = runBlocking {
        val pkg = "com.game.xianxia"
        // 1. Thu nạp app vào Bảo Khố
        addVaultAppUseCase(pkg, "Tiên Hiệp Truyền Kỳ")

        // 2. Ban đầu N=0 -> LOCK
        adapter.recomputeSnapshot()
        val eval0 = adapter.evaluateSync(pkg)
        assertEquals(EnforcementAction.LOCK, eval0.finalAction)
        assertEquals(EnforcementReason.LOCKED_BY_VAULT_NO_TASK, eval0.reason)
        assertEquals(0, eval0.totalLinkedTasksCount)
        assertEquals(0, eval0.requiredTasksCount)

        // 3. Tạo 3 nhiệm vụ trong Nhiệm Vụ Đường (createTaskUseCase trả về Long taskId)
        val t1 = createTaskUseCase("Luyện Đan").getOrThrow()
        val t2 = createTaskUseCase("Đả Tọa").getOrThrow()
        val t3 = createTaskUseCase("Vung Kiếm 1000 lần").getOrThrow()

        // 4. Liên kết 3 nhiệm vụ tới App X
        updateTaskLinkedAppsUseCase(t1, listOf(pkg))
        updateTaskLinkedAppsUseCase(t2, listOf(pkg))
        updateTaskLinkedAppsUseCase(t3, listOf(pkg))
        adapter.recomputeSnapshot()

        // 5. Kiểm tra trạng thái 0/3: required = (2*3+2)/3 = 2 -> LOCK
        val eval0Of3 = adapter.evaluateSync(pkg)
        assertEquals(EnforcementAction.LOCK, eval0Of3.finalAction)
        assertEquals(EnforcementReason.LOCKED_INSUFFICIENT_TASKS, eval0Of3.reason)
        assertEquals(3, eval0Of3.totalLinkedTasksCount)
        assertEquals(0, eval0Of3.completedLinkedTasksCount)
        assertEquals(2, eval0Of3.requiredTasksCount)

        // 6. Hoàn thành Task 1 -> 1/3 -> LOCK
        completeTaskUseCase(t1)
        adapter.recomputeSnapshot()
        val eval1Of3 = adapter.evaluateSync(pkg)
        assertEquals(EnforcementAction.LOCK, eval1Of3.finalAction)
        assertEquals(EnforcementReason.LOCKED_INSUFFICIENT_TASKS, eval1Of3.reason)
        assertEquals(1, eval1Of3.completedLinkedTasksCount)
        assertEquals(2, eval1Of3.requiredTasksCount)

        // 7. Hoàn thành Task 2 -> 2/3 -> Đạt ngưỡng (2 >= 2) -> ALLOW
        completeTaskUseCase(t2)
        adapter.recomputeSnapshot()
        val eval2Of3 = adapter.evaluateSync(pkg)
        assertEquals(EnforcementAction.ALLOW, eval2Of3.finalAction)
        assertEquals(EnforcementReason.ALLOWED_UNLOCKED_BY_TASKS, eval2Of3.reason)
        assertEquals(2, eval2Of3.completedLinkedTasksCount)
        assertEquals(2, eval2Of3.requiredTasksCount)

        // 8. Hoàn thành Task 3 -> 3/3 -> Vẫn ALLOW
        completeTaskUseCase(t3)
        adapter.recomputeSnapshot()
        val eval3Of3 = adapter.evaluateSync(pkg)
        assertEquals(EnforcementAction.ALLOW, eval3Of3.finalAction)
        assertEquals(EnforcementReason.ALLOWED_UNLOCKED_BY_TASKS, eval3Of3.reason)
        assertEquals(3, eval3Of3.completedLinkedTasksCount)
        assertEquals(2, eval3Of3.requiredTasksCount)
    }

    @Test
    fun testEndToEnd_Scenario2_FourTasks_Boundary() = runBlocking {
        val pkg = "com.game.fourtasks"
        addVaultAppUseCase(pkg, "Tứ Đại Danh Bổ")

        val t1 = createTaskUseCase("Task 1").getOrThrow()
        val t2 = createTaskUseCase("Task 2").getOrThrow()
        val t3 = createTaskUseCase("Task 3").getOrThrow()
        val t4 = createTaskUseCase("Task 4").getOrThrow()

        updateTaskLinkedAppsUseCase(t1, listOf(pkg))
        updateTaskLinkedAppsUseCase(t2, listOf(pkg))
        updateTaskLinkedAppsUseCase(t3, listOf(pkg))
        updateTaskLinkedAppsUseCase(t4, listOf(pkg))

        // N = 4 -> required = (2*4+2)/3 = 3
        // 2/4 -> LOCK
        completeTaskUseCase(t1)
        completeTaskUseCase(t2)
        adapter.recomputeSnapshot()

        val eval2Of4 = adapter.evaluateSync(pkg)
        assertEquals(EnforcementAction.LOCK, eval2Of4.finalAction)
        assertEquals(EnforcementReason.LOCKED_INSUFFICIENT_TASKS, eval2Of4.reason)
        assertEquals(2, eval2Of4.completedLinkedTasksCount)
        assertEquals(4, eval2Of4.totalLinkedTasksCount)
        assertEquals(3, eval2Of4.requiredTasksCount)

        // 3/4 -> ALLOW
        completeTaskUseCase(t3)
        adapter.recomputeSnapshot()

        val eval3Of4 = adapter.evaluateSync(pkg)
        assertEquals(EnforcementAction.ALLOW, eval3Of4.finalAction)
        assertEquals(EnforcementReason.ALLOWED_UNLOCKED_BY_TASKS, eval3Of4.reason)
        assertEquals(3, eval3Of4.completedLinkedTasksCount)
    }

    @Test
    fun testEndToEnd_Scenario3_FiveTasks_Boundary() = runBlocking {
        val pkg = "com.game.fivetasks"
        addVaultAppUseCase(pkg, "Ngũ Hành Tông")

        val tasks = (1..5).map { createTaskUseCase("Nhiệm vụ $it").getOrThrow() }
        tasks.forEach { updateTaskLinkedAppsUseCase(it, listOf(pkg)) }

        // N = 5 -> required = (2*5+2)/3 = 4
        // Hoàn thành 3 tasks (3/5) -> LOCK
        completeTaskUseCase(tasks[0])
        completeTaskUseCase(tasks[1])
        completeTaskUseCase(tasks[2])
        adapter.recomputeSnapshot()

        val eval3Of5 = adapter.evaluateSync(pkg)
        assertEquals(EnforcementAction.LOCK, eval3Of5.finalAction)
        assertEquals(EnforcementReason.LOCKED_INSUFFICIENT_TASKS, eval3Of5.reason)
        assertEquals(3, eval3Of5.completedLinkedTasksCount)
        assertEquals(4, eval3Of5.requiredTasksCount)

        // Hoàn thành task thứ 4 (4/5) -> ALLOW
        completeTaskUseCase(tasks[3])
        adapter.recomputeSnapshot()

        val eval4Of5 = adapter.evaluateSync(pkg)
        assertEquals(EnforcementAction.ALLOW, eval4Of5.finalAction)
        assertEquals(EnforcementReason.ALLOWED_UNLOCKED_BY_TASKS, eval4Of5.reason)
        assertEquals(4, eval4Of5.completedLinkedTasksCount)
    }

    @Test
    fun testEndToEnd_Scenario4_TechnicalOverride_Precedence() = runBlocking {
        val pkg = "com.game.override"
        addVaultAppUseCase(pkg, "Ứng Dụng Khóa Kép")

        val t1 = createTaskUseCase("Task 1").getOrThrow()
        val t2 = createTaskUseCase("Task 2").getOrThrow()
        val t3 = createTaskUseCase("Task 3").getOrThrow()
        updateTaskLinkedAppsUseCase(t1, listOf(pkg))
        updateTaskLinkedAppsUseCase(t2, listOf(pkg))
        updateTaskLinkedAppsUseCase(t3, listOf(pkg))

        // Hoàn thành 3/3 nhiệm vụ -> Business đạt ALLOW
        completeTaskUseCase(t1)
        completeTaskUseCase(t2)
        completeTaskUseCase(t3)
        adapter.recomputeSnapshot()

        val evalAllowed = adapter.evaluateSync(pkg)
        assertEquals(EnforcementAction.ALLOW, evalAllowed.finalAction)
        assertEquals(EnforcementReason.ALLOWED_UNLOCKED_BY_TASKS, evalAllowed.reason)

        // Kích hoạt Technical Lock (Schedule cấm toàn bộ khung giờ)
        fakeTargetRepo.add(pkg)
        fakeTargetRepo.updatePolicy(
            pkg,
            schedule = TimeSchedule(enabled = true, startHour = 0, startMinute = 0, endHour = 23, endMinute = 59),
            timeLimit = null
        )

        val evalTechnicalOverride = adapter.evaluateSync(pkg)
        // Technical Lock luôn ghi đè tuyệt đối: finalAction BẮT BUỘC là LOCK
        assertEquals(EnforcementAction.LOCK, evalTechnicalOverride.finalAction)
        assertEquals(EnforcementReason.LOCKED_BY_POLICY, evalTechnicalOverride.reason)
        assertTrue(evalTechnicalOverride.isTechnicalLockActive)
    }

    @Test
    fun testEndToEnd_Scenario5_NewBusinessCycle_0400_Reset() = runBlocking {
        val pkg = "com.game.cycle"
        addVaultAppUseCase(pkg, "Nhật Nguyệt Thần Giáo")

        val t1 = createTaskUseCase("Task A").getOrThrow()
        val t2 = createTaskUseCase("Task B").getOrThrow()
        val t3 = createTaskUseCase("Task C").getOrThrow()
        updateTaskLinkedAppsUseCase(t1, listOf(pkg))
        updateTaskLinkedAppsUseCase(t2, listOf(pkg))
        updateTaskLinkedAppsUseCase(t3, listOf(pkg))

        // Lúc 03:59:59 (thuộc chu kỳ ngày hôm qua)
        clock.setWallTime(ZonedDateTime.of(2026, 9, 8, 3, 59, 59, 0, testZone).toInstant().toEpochMilli())
        completeTaskUseCase(t1)
        completeTaskUseCase(t2)
        adapter.recomputeSnapshot()

        // 2/3 hoàn thành trong chu kỳ cũ -> ALLOW
        val evalOldCycle = adapter.evaluateSync(pkg)
        assertEquals(EnforcementAction.ALLOW, evalOldCycle.finalAction)
        assertEquals(2, evalOldCycle.completedLinkedTasksCount)

        // Chuyển sang 04:00:00 (Chu kỳ ngày mới bắt đầu)
        clock.setWallTime(ZonedDateTime.of(2026, 9, 8, 4, 0, 0, 0, testZone).toInstant().toEpochMilli())
        adapter.recomputeSnapshot()

        // Trong chu kỳ mới, nhiệm vụ hôm qua không còn được tính -> 0/3 -> LOCK
        val evalNewCycle = adapter.evaluateSync(pkg)
        assertEquals(EnforcementAction.LOCK, evalNewCycle.finalAction)
        assertEquals(EnforcementReason.LOCKED_INSUFFICIENT_TASKS, evalNewCycle.reason)
        assertEquals(0, evalNewCycle.completedLinkedTasksCount)
        assertEquals(2, evalNewCycle.requiredTasksCount)
    }

    @Test
    fun testCascadeRemoveAndReAdd_DoesNotRestoreOldLinkage() = runBlocking {
        val pkg = "com.game.readd"
        addVaultAppUseCase(pkg, "Tẩy Tủy Kinh")

        val t1 = createTaskUseCase("Tẩy Tủy 1").getOrThrow()
        val t2 = createTaskUseCase("Tẩy Tủy 2").getOrThrow()
        updateTaskLinkedAppsUseCase(t1, listOf(pkg))
        updateTaskLinkedAppsUseCase(t2, listOf(pkg))
        adapter.recomputeSnapshot()

        val evalBefore = adapter.evaluateSync(pkg)
        assertEquals(2, evalBefore.totalLinkedTasksCount)

        // Gỡ app khỏi Vault -> cascade xóa quan hệ
        removeVaultAppUseCase(pkg)
        adapter.recomputeSnapshot()

        val evalRemoved = adapter.evaluateSync(pkg)
        assertEquals(EnforcementAction.ALLOW, evalRemoved.finalAction)
        assertEquals(EnforcementReason.ALLOWED_NOT_PROTECTED, evalRemoved.reason)

        // Thu nạp lại app vào Vault -> Phải là N=0 (Không phục hồi liên kết cũ)
        addVaultAppUseCase(pkg, "Tẩy Tủy Kinh Tái Lập")
        adapter.recomputeSnapshot()

        val evalReadded = adapter.evaluateSync(pkg)
        assertEquals(EnforcementAction.LOCK, evalReadded.finalAction)
        assertEquals(EnforcementReason.LOCKED_BY_VAULT_NO_TASK, evalReadded.reason)
        assertEquals(0, evalReadded.totalLinkedTasksCount)
    }

    @Test
    fun testArchivedTasks_AreExcludedFromEvaluation() = runBlocking {
        val pkg = "com.game.archived"
        addVaultAppUseCase(pkg, "Cổ Mộ Phái")

        val t1 = createTaskUseCase("Active 1").getOrThrow()
        val t2 = createTaskUseCase("Archived 2").getOrThrow()
        updateTaskLinkedAppsUseCase(t1, listOf(pkg))
        updateTaskLinkedAppsUseCase(t2, listOf(pkg))

        // Lưu trữ task 2
        archiveTaskUseCase(t2)
        adapter.recomputeSnapshot()

        // N hiệu lực chỉ còn 1 task (t1) -> required = (2*1+2)/3 = 1
        val eval = adapter.evaluateSync(pkg)
        assertEquals(1, eval.activeLinkedTasksCount)
        assertEquals(1, eval.requiredTasksCount)
        assertEquals(EnforcementAction.LOCK, eval.finalAction)

        // Hoàn thành task 1 -> 1/1 -> ALLOW
        completeTaskUseCase(t1)
        adapter.recomputeSnapshot()
        val evalUnlocked = adapter.evaluateSync(pkg)
        assertEquals(EnforcementAction.ALLOW, evalUnlocked.finalAction)
        assertEquals(1, evalUnlocked.completedLinkedTasksCount)
    }

    @Test
    fun testVaultViewModel_Integration_MapsEnforcementDetailsCorrectly() = runBlocking {
        val pkg = "com.game.ui.test"
        addVaultAppUseCase(pkg, "Ứng Dụng Giao Diện")

        val t1 = createTaskUseCase("Task UI 1").getOrThrow()
        val t2 = createTaskUseCase("Task UI 2").getOrThrow()
        val t3 = createTaskUseCase("Task UI 3").getOrThrow()
        updateTaskLinkedAppsUseCase(t1, listOf(pkg))
        updateTaskLinkedAppsUseCase(t2, listOf(pkg))
        updateTaskLinkedAppsUseCase(t3, listOf(pkg))

        completeTaskUseCase(t1)
        completeTaskUseCase(t2)
        adapter.recomputeSnapshot()

        // Khởi tạo VaultViewModel với adapter
        val viewModel = VaultViewModel(
            getVaultAppsUseCase = getVaultAppsUseCase,
            addVaultAppUseCase = addVaultAppUseCase,
            removeVaultAppUseCase = removeVaultAppUseCase,
            discoveryService = FakeDiscoveryService(),
            enforcementAdapter = adapter
        )

        // Đợi UI state load xong
        val uiState = withTimeout(3000) {
            viewModel.uiState.first { !it.isLoading && it.vaultApps.isNotEmpty() }
        }
        assertEquals(1, uiState.vaultApps.size)

        // Kiểm tra AppEnforcementDetails được map chính xác
        val details = uiState.appEnforcementMap[pkg]
        assertNotNull(details)
        assertEquals(pkg, details?.packageName)
        assertEquals(3, details?.totalLinkedTasksCount)
        assertEquals(2, details?.completedLinkedTasksCount)
        assertEquals(2, details?.requiredTasksCount)
        assertEquals(EnforcementAction.ALLOW, details?.finalAction)
        assertEquals(EnforcementReason.ALLOWED_UNLOCKED_BY_TASKS, details?.reason)
    }
}
