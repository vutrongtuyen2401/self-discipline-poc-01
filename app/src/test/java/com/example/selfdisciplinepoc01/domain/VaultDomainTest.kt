package com.example.selfdisciplinepoc01.domain

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.selfdisciplinepoc01.data.database.AppDatabase
import com.example.selfdisciplinepoc01.data.repository.CoreDataRepository
import com.example.selfdisciplinepoc01.data.repository.CoreDataRepositoryImpl
import com.example.selfdisciplinepoc01.domain.enforcement.TaskAppEnforcementAdapter
import com.example.selfdisciplinepoc01.domain.enforcement.TaskAppEnforcementStatus
import com.example.selfdisciplinepoc01.domain.usecase.AddVaultAppUseCase
import com.example.selfdisciplinepoc01.domain.usecase.GetTaskLinkedAppsUseCase
import com.example.selfdisciplinepoc01.domain.usecase.GetVaultAppsUseCase
import com.example.selfdisciplinepoc01.domain.usecase.RemoveVaultAppUseCase
import com.example.selfdisciplinepoc01.domain.usecase.UpdateTaskLinkedAppsUseCase
import com.example.selfdisciplinepoc01.time.BusinessDayProvider
import com.example.selfdisciplinepoc01.time.BusinessDayProviderImpl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Comprehensive Test Suite for Phase 19: Vault Integration & Task ↔ App Linkage.
 *
 * Covers Test Matrix:
 * A. Vault Lifecycle (Tests 1-6)
 * B. Many-to-Many Relationships (Tests 7-10)
 * C. Cascade Deletion & Referential Integrity (Tests 11-14)
 * D. Mission Hall Linkage Flow (Tests 15-19)
 * E. OPEN-01 Safety Barriers (Tests 20-22)
 *
 * Per Canonical Design V2 (Section 4, 5, 6, 7).
 */
@RunWith(RobolectricTestRunner::class)
class VaultDomainTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: CoreDataRepository
    private val businessDayProvider: BusinessDayProvider = BusinessDayProviderImpl(boundaryHour = 4)

    private lateinit var addVaultAppUseCase: AddVaultAppUseCase
    private lateinit var removeVaultAppUseCase: RemoveVaultAppUseCase
    private lateinit var getVaultAppsUseCase: GetVaultAppsUseCase
    private lateinit var getTaskLinkedAppsUseCase: GetTaskLinkedAppsUseCase
    private lateinit var updateTaskLinkedAppsUseCase: UpdateTaskLinkedAppsUseCase
    private lateinit var enforcementAdapter: TaskAppEnforcementAdapter

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = CoreDataRepositoryImpl(database)

        addVaultAppUseCase = AddVaultAppUseCase(repository)
        removeVaultAppUseCase = RemoveVaultAppUseCase(repository)
        getVaultAppsUseCase = GetVaultAppsUseCase(repository)
        getTaskLinkedAppsUseCase = GetTaskLinkedAppsUseCase(repository)
        updateTaskLinkedAppsUseCase = UpdateTaskLinkedAppsUseCase(repository)
        enforcementAdapter = TaskAppEnforcementAdapter(repository)
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ==================================================
    // GROUP A: VAULT LIFECYCLE
    // ==================================================

    @Test
    fun test1_addInstalledAppToVault() = runBlocking {
        val result = addVaultAppUseCase("com.android.chrome", "Google Chrome")
        assertTrue(result.isSuccess)

        val app = repository.getApp("com.android.chrome")
        assertNotNull(app)
        assertEquals("com.android.chrome", app?.packageName)
        assertEquals("Google Chrome", app?.appName)
        assertTrue(app?.isVaultManaged == true)
    }

    @Test
    fun test2_listVaultApps() = runBlocking {
        addVaultAppUseCase("com.android.chrome", "Google Chrome")
        addVaultAppUseCase("com.tencent.tmgp.sgame", "Liên Quân Mobile")

        val apps = getVaultAppsUseCase.getAllVaultApps()
        assertEquals(2, apps.size)
        val packageNames = apps.map { it.packageName }.toSet()
        assertTrue(packageNames.contains("com.android.chrome"))
        assertTrue(packageNames.contains("com.tencent.tmgp.sgame"))
    }

    @Test
    fun test3_removeAppFromVault() = runBlocking {
        addVaultAppUseCase("com.android.chrome", "Google Chrome")
        assertEquals(1, getVaultAppsUseCase.getAllVaultApps().size)

        val result = removeVaultAppUseCase("com.android.chrome")
        assertTrue(result.isSuccess)

        assertEquals(0, getVaultAppsUseCase.getAllVaultApps().size)
        assertNull(repository.getApp("com.android.chrome"))
    }

    @Test
    fun test4_persistenceAfterRestart() = runBlocking {
        addVaultAppUseCase("com.android.settings", "Cài Đặt")

        // Simulate repository restart / new instance with same db
        val newRepo = CoreDataRepositoryImpl(database)
        val app = newRepo.getApp("com.android.settings")
        assertNotNull(app)
        assertEquals("Cài Đặt", app?.appName)
    }

    @Test
    fun test5_duplicateAddIsIdempotent() = runBlocking {
        val res1 = addVaultAppUseCase("com.android.chrome", "Chrome 1")
        val res2 = addVaultAppUseCase("com.android.chrome", "Chrome 2")

        assertTrue(res1.isSuccess)
        assertTrue(res2.isSuccess)

        val apps = getVaultAppsUseCase.getAllVaultApps()
        assertEquals(1, apps.size)
        assertEquals("Chrome 2", apps[0].appName)
    }

    @Test
    fun test6_reAddDoesNotRestoreOldTaskLinks() = runBlocking {
        // Canonical Design V2 (Section 6): Re-adding an app does NOT restore old task links!
        val taskId = repository.createTask("Nhiệm vụ tu tiên")
        addVaultAppUseCase("com.game.sample", "Game Sample")

        // Link task to app
        updateTaskLinkedAppsUseCase(taskId, listOf("com.game.sample"))
        assertEquals(1, getTaskLinkedAppsUseCase(taskId).size)

        // Remove app from Vault
        removeVaultAppUseCase("com.game.sample")
        assertEquals(0, getTaskLinkedAppsUseCase(taskId).size)

        // Re-add app to Vault
        addVaultAppUseCase("com.game.sample", "Game Sample")

        // Link MUST remain unlinked (0 links)
        val linkedAfterReAdd = getTaskLinkedAppsUseCase(taskId)
        assertEquals(0, linkedAfterReAdd.size)
    }

    // ==================================================
    // GROUP B: MANY-TO-MANY RELATIONSHIPS
    // ==================================================

    @Test
    fun test7_oneTaskToOneApp() = runBlocking {
        val taskId = repository.createTask("Task A")
        addVaultAppUseCase("com.app.x", "App X")

        updateTaskLinkedAppsUseCase(taskId, listOf("com.app.x"))

        val linkedApps = getTaskLinkedAppsUseCase(taskId)
        assertEquals(1, linkedApps.size)
        assertEquals("com.app.x", linkedApps[0].packageName)
    }

    @Test
    fun test8_oneTaskToMultipleApps() = runBlocking {
        val taskId = repository.createTask("Task A")
        addVaultAppUseCase("com.app.x", "App X")
        addVaultAppUseCase("com.app.y", "App Y")

        updateTaskLinkedAppsUseCase(taskId, listOf("com.app.x", "com.app.y"))

        val linkedApps = getTaskLinkedAppsUseCase(taskId)
        assertEquals(2, linkedApps.size)
        val pkgs = linkedApps.map { it.packageName }.toSet()
        assertTrue(pkgs.contains("com.app.x"))
        assertTrue(pkgs.contains("com.app.y"))
    }

    @Test
    fun test9_multipleTasksToSameApp_areDistinct() = runBlocking {
        // Canonical Design V2 (Section 5): If app links to 2 tasks, they count as 2 distinct tasks, NOT merged
        val task1 = repository.createTask("Task 1")
        val task2 = repository.createTask("Task 2")
        addVaultAppUseCase("com.app.x", "App X")

        updateTaskLinkedAppsUseCase(task1, listOf("com.app.x"))
        updateTaskLinkedAppsUseCase(task2, listOf("com.app.x"))

        val tasksForApp = repository.getTasksForApp("com.app.x")
        assertEquals(2, tasksForApp.size)
        val taskIds = tasksForApp.map { it.id }.toSet()
        assertTrue(taskIds.contains(task1))
        assertTrue(taskIds.contains(task2))
    }

    @Test
    fun test10_removeOneLinkPreservesOtherLinks() = runBlocking {
        val task1 = repository.createTask("Task 1")
        val task2 = repository.createTask("Task 2")
        addVaultAppUseCase("com.app.x", "App X")

        updateTaskLinkedAppsUseCase(task1, listOf("com.app.x"))
        updateTaskLinkedAppsUseCase(task2, listOf("com.app.x"))

        // Unlink from Task 1 only
        updateTaskLinkedAppsUseCase(task1, emptyList())

        assertEquals(0, getTaskLinkedAppsUseCase(task1).size)
        // Task 2 link is preserved
        val task2Links = getTaskLinkedAppsUseCase(task2)
        assertEquals(1, task2Links.size)
        assertEquals("com.app.x", task2Links[0].packageName)
    }

    // ==================================================
    // GROUP C: CASCADE DELETION & INTEGRITY
    // ==================================================

    @Test
    fun test11_deleteVaultApp_removesCrossRefs() = runBlocking {
        val taskId = repository.createTask("Task A")
        addVaultAppUseCase("com.app.x", "App X")
        updateTaskLinkedAppsUseCase(taskId, listOf("com.app.x"))

        assertEquals(1, getTaskLinkedAppsUseCase(taskId).size)

        removeVaultAppUseCase("com.app.x")

        assertEquals(0, getTaskLinkedAppsUseCase(taskId).size)
    }

    @Test
    fun test12_deleteVaultApp_taskRemains() = runBlocking {
        val taskId = repository.createTask("Task A")
        addVaultAppUseCase("com.app.x", "App X")
        updateTaskLinkedAppsUseCase(taskId, listOf("com.app.x"))

        removeVaultAppUseCase("com.app.x")

        val task = repository.getTaskById(taskId)
        assertNotNull(task)
        assertEquals("Task A", task?.name)
    }

    @Test
    fun test13_deleteVaultApp_completionHistoryRemains() = runBlocking {
        val taskId = repository.createTask("Task A")
        addVaultAppUseCase("com.app.x", "App X")
        updateTaskLinkedAppsUseCase(taskId, listOf("com.app.x"))

        val todayDate = businessDayProvider.getBusinessDate(System.currentTimeMillis(), java.time.ZoneId.systemDefault()).toString()
        repository.setTaskCompletion(taskId, todayDate, true)
        assertTrue(repository.isTaskCompletedOnDate(taskId, todayDate))

        // Delete app from Vault
        removeVaultAppUseCase("com.app.x")

        // Completion status remains 100% intact
        assertTrue(repository.isTaskCompletedOnDate(taskId, todayDate))
        val completedIds = repository.getCompletedTaskIdsForDate(todayDate)
        assertEquals(listOf(taskId), completedIds)
    }

    @Test
    fun test14_deleteVaultApp_otherAppsRemainLinked() = runBlocking {
        val taskId = repository.createTask("Task A")
        addVaultAppUseCase("com.app.x", "App X")
        addVaultAppUseCase("com.app.y", "App Y")

        updateTaskLinkedAppsUseCase(taskId, listOf("com.app.x", "com.app.y"))
        assertEquals(2, getTaskLinkedAppsUseCase(taskId).size)

        // Delete App X
        removeVaultAppUseCase("com.app.x")

        // App Y still linked to Task A
        val remainingLinks = getTaskLinkedAppsUseCase(taskId)
        assertEquals(1, remainingLinks.size)
        assertEquals("com.app.y", remainingLinks[0].packageName)
    }

    // ==================================================
    // GROUP D: MISSION HALL LINKAGE FLOW
    // ==================================================

    @Test
    fun test15_taskShowsLinkedApps() = runBlocking {
        val taskId = repository.createTask("Task A")
        addVaultAppUseCase("com.facebook.katana", "Facebook")

        updateTaskLinkedAppsUseCase(taskId, listOf("com.facebook.katana"))

        val apps = getTaskLinkedAppsUseCase(taskId)
        assertEquals(1, apps.size)
        assertEquals("Facebook", apps[0].appName)
    }

    @Test
    fun test16_selectVaultAppForTask() = runBlocking {
        val taskId = repository.createTask("Task A")
        addVaultAppUseCase("com.facebook.katana", "Facebook")
        addVaultAppUseCase("com.zhiliaoapp.musically", "TikTok")

        // Select Facebook
        val res = updateTaskLinkedAppsUseCase(taskId, listOf("com.facebook.katana"))
        assertTrue(res.isSuccess)

        val linked = getTaskLinkedAppsUseCase(taskId)
        assertEquals(1, linked.size)
        assertEquals("com.facebook.katana", linked[0].packageName)
    }

    @Test
    fun test17_deselectAppFromTask() = runBlocking {
        val taskId = repository.createTask("Task A")
        addVaultAppUseCase("com.facebook.katana", "Facebook")
        updateTaskLinkedAppsUseCase(taskId, listOf("com.facebook.katana"))
        assertEquals(1, getTaskLinkedAppsUseCase(taskId).size)

        // Deselect all
        updateTaskLinkedAppsUseCase(taskId, emptyList())
        assertEquals(0, getTaskLinkedAppsUseCase(taskId).size)
    }

    @Test
    fun test18_multipleSelectionForTask() = runBlocking {
        val taskId = repository.createTask("Task A")
        addVaultAppUseCase("com.app.1", "App 1")
        addVaultAppUseCase("com.app.2", "App 2")
        addVaultAppUseCase("com.app.3", "App 3")

        updateTaskLinkedAppsUseCase(taskId, listOf("com.app.1", "com.app.3"))

        val linked = getTaskLinkedAppsUseCase(taskId)
        assertEquals(2, linked.size)
        val pkgs = linked.map { it.packageName }.toSet()
        assertTrue(pkgs.contains("com.app.1"))
        assertTrue(pkgs.contains("com.app.3"))
        assertFalse(pkgs.contains("com.app.2"))
    }

    @Test
    fun test19_linkageRejectsAppsNotInVault() = runBlocking {
        // Canonical Design V2 (Section 5 & 6): Only apps currently present in Vault can be linked!
        val taskId = repository.createTask("Task A")
        addVaultAppUseCase("com.valid.vault", "Vault App")

        // Attempt to link one valid app and one non-vault app
        updateTaskLinkedAppsUseCase(taskId, listOf("com.valid.vault", "com.unregistered.app"))

        val linked = getTaskLinkedAppsUseCase(taskId)
        assertEquals(1, linked.size)
        assertEquals("com.valid.vault", linked[0].packageName)
    }

    // ==================================================
    // GROUP E: OPEN-01 SAFETY BARRIERS
    // ==================================================

    @Test
    fun test20_open01Protection_enforcementIsExplicitlyDisabled() {
        val status = enforcementAdapter.getEnforcementStatus()
        assertEquals(TaskAppEnforcementStatus.DISABLED_PENDING_OPEN_01, status)
    }

    @Test
    fun test21_open01Protection_noUnlockFormulaCalculated() = runBlocking {
        // No 2/3 calculation or unlock approval exists
        val isApproved = enforcementAdapter.isTaskBasedUnlockApproved("com.android.chrome")
        assertFalse(isApproved)
    }

    @Test
    fun test22_open01Protection_noPointsOrPercentages() {
        // Confirm class and methods contain no point or ratio calculators
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
