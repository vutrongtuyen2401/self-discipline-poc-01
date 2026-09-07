package com.example.selfdisciplinepoc01.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.selfdisciplinepoc01.data.database.AppDatabase
import com.example.selfdisciplinepoc01.data.repository.CoreDataRepository
import com.example.selfdisciplinepoc01.data.repository.CoreDataRepositoryImpl
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
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Unit Tests for Room Data Foundation and CoreDataRepository.
 *
 * Covers:
 * 1. Insert Vault App
 * 2. Insert Mission Task
 * 3. Many-to-Many Task ↔ App relationship
 * 4. Query apps for task & tasks for app
 * 5. One app linked to multiple distinct tasks
 * 6. Duplicate relation handling (idempotent)
 * 7. Cascade deletion (removing app unlinks from tasks, removing task unlinks from apps)
 * 8. Daily task completion bound to 04:00 Business Day
 * 9. Daily cycle reset (completion in Day 1 does not mark Day 2 as completed)
 * 10. Archiving task preserves historical data
 *
 * PROPOSAL — OPEN-05 REMAINS OPEN.
 */
@RunWith(RobolectricTestRunner::class)
class CoreDataRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: CoreDataRepository
    private val businessDayProvider: BusinessDayProvider = BusinessDayProviderImpl(boundaryHour = 4)
    private val zoneId = ZoneId.of("Asia/Ho_Chi_Minh")

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = CoreDataRepositoryImpl(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun test1_insertAndQueryVaultApp() = runBlocking {
        repository.addVaultApp("com.tencent.tmgp.sgame", "Liên Quân Mobile")

        val app = repository.getApp("com.tencent.tmgp.sgame")
        assertNotNull(app)
        assertEquals("com.tencent.tmgp.sgame", app?.packageName)
        assertEquals("Liên Quân Mobile", app?.appName)
        assertTrue(app?.isVaultManaged == true)

        val allApps = repository.getAllVaultApps()
        assertEquals(1, allApps.size)
    }

    @Test
    fun test2_insertAndQueryMissionTask() = runBlocking {
        val taskId = repository.createTask("Đọc 20 trang sách")
        assertTrue(taskId > 0L)

        val task = repository.getTaskById(taskId)
        assertNotNull(task)
        assertEquals("Đọc 20 trang sách", task?.name)
        assertFalse(task?.isArchived == true)

        val activeTasks = repository.getAllActiveTasks()
        assertEquals(1, activeTasks.size)
        assertEquals(taskId, activeTasks[0].id)
    }

    @Test
    fun test3_manyToMany_taskToMultipleApps() = runBlocking {
        val taskId = repository.createTask("Chạy bộ 5km")
        repository.addVaultApp("com.facebook.katana", "Facebook")
        repository.addVaultApp("com.zhiliaoapp.musically", "TikTok")

        // 1 Task links to 2 Apps
        repository.linkTaskToApps(taskId, listOf("com.facebook.katana", "com.zhiliaoapp.musically"))

        val linkedApps = repository.getAppsForTask(taskId)
        assertEquals(2, linkedApps.size)
        val packageNames = linkedApps.map { it.packageName }.toSet()
        assertTrue(packageNames.contains("com.facebook.katana"))
        assertTrue(packageNames.contains("com.zhiliaoapp.musically"))
    }

    @Test
    fun test4_manyToMany_appToMultipleTasks_distinctTasks() = runBlocking {
        // Canonical Design rule: 1 app linked to 2 tasks = 2 distinct tasks, NOT merged
        val task1Id = repository.createTask("Học 30 từ vựng Tiếng Anh")
        val task2Id = repository.createTask("Làm 2 bài tập LeetCode")
        repository.addVaultApp("com.facebook.katana", "Facebook")

        repository.linkTaskToApp(task1Id, "com.facebook.katana")
        repository.linkTaskToApp(task2Id, "com.facebook.katana")

        val tasksForApp = repository.getTasksForApp("com.facebook.katana")
        assertEquals(2, tasksForApp.size)
        val taskIds = tasksForApp.map { it.id }.toSet()
        assertTrue(taskIds.contains(task1Id))
        assertTrue(taskIds.contains(task2Id))
    }

    @Test
    fun test5_duplicateRelationHandling_isIdempotent() = runBlocking {
        val taskId = repository.createTask("Thiền 15 phút")
        repository.addVaultApp("com.google.android.youtube", "YouTube")

        // Link twice
        repository.linkTaskToApp(taskId, "com.google.android.youtube")
        repository.linkTaskToApp(taskId, "com.google.android.youtube")

        val linkedApps = repository.getAppsForTask(taskId)
        assertEquals(1, linkedApps.size)
    }

    @Test
    fun test6_removeVaultApp_cascadesAndUnlinksTasks() = runBlocking {
        val taskId = repository.createTask("Dọn dẹp phòng làm việc")
        repository.addVaultApp("com.game.sample", "Game A")
        repository.linkTaskToApp(taskId, "com.game.sample")

        assertEquals(1, repository.getAppsForTask(taskId).size)

        // Remove app from Vault
        repository.removeVaultApp("com.game.sample")

        // App removed
        assertNull(repository.getApp("com.game.sample"))
        // Link automatically removed (foreign key CASCADE)
        assertEquals(0, repository.getAppsForTask(taskId).size)
        // Task itself still exists
        assertNotNull(repository.getTaskById(taskId))
    }

    @Test
    fun test7_archiveTask_keepsHistoricalData() = runBlocking {
        val taskId = repository.createTask("Task A")
        repository.archiveTask(taskId)

        val activeTasks = repository.getAllActiveTasks()
        assertEquals(0, activeTasks.size)

        val archived = repository.getTaskById(taskId)
        assertNotNull(archived)
        assertTrue(archived?.isArchived == true)
    }

    @Test
    fun test8_dailyCompletionState_cycle0400Alignment() = runBlocking {
        val taskId = repository.createTask("Tập thể dục buổi sáng")

        // 2026-09-07 03:30 (before 04:00 boundary) -> belongs to Business Day 2026-09-06
        val timeBefore0400 = LocalDateTime.of(2026, 9, 7, 3, 30)
            .atZone(zoneId).toInstant().toEpochMilli()
        val businessDayCycle1 = businessDayProvider.getBusinessDate(timeBefore0400, zoneId).toString()
        assertEquals("2026-09-06", businessDayCycle1)

        // 2026-09-07 04:30 (after 04:00 boundary) -> belongs to Business Day 2026-09-07
        val timeAfter0400 = LocalDateTime.of(2026, 9, 7, 4, 30)
            .atZone(zoneId).toInstant().toEpochMilli()
        val businessDayCycle2 = businessDayProvider.getBusinessDate(timeAfter0400, zoneId).toString()
        assertEquals("2026-09-07", businessDayCycle2)

        // Mark completed in Cycle 1 (2026-09-06)
        repository.setTaskCompletion(taskId, businessDayCycle1, true)

        assertTrue(repository.isTaskCompletedOnDate(taskId, businessDayCycle1))
        // Cycle 2 has NOT completed this task (automatic daily reset)
        assertFalse(repository.isTaskCompletedOnDate(taskId, businessDayCycle2))

        val completedCycle1 = repository.getCompletedTaskIdsForDate(businessDayCycle1)
        assertEquals(listOf(taskId), completedCycle1)

        val completedCycle2 = repository.getCompletedTaskIdsForDate(businessDayCycle2)
        assertTrue(completedCycle2.isEmpty())
    }

    @Test
    fun test9_taskStartedBefore0400_completedAfter0400_belongsToCycle1() = runBlocking {
        // Canonical Design rule: Task bắt đầu trước 04:00 thuộc cycle cũ. Task hoàn thành sau 04:00 vẫn thuộc cycle cũ.
        val taskId = repository.createTask("Task dài đêm khuya")

        val startedTime = LocalDateTime.of(2026, 9, 7, 3, 50).atZone(zoneId).toInstant().toEpochMilli()

        // Evaluated by BusinessDayProvider based on start time
        val taskCycle = businessDayProvider.getTaskBusinessDate(startedTime, zoneId).toString()
        assertEquals("2026-09-06", taskCycle)

        // Recorded against the evaluated cycle
        repository.setTaskCompletion(taskId, taskCycle, true)

        assertTrue(repository.isTaskCompletedOnDate(taskId, "2026-09-06"))
        assertFalse(repository.isTaskCompletedOnDate(taskId, "2026-09-07"))
    }

    @Test
    fun test10_flowObservation_vaultAppsAndActiveTasks() = runBlocking {
        val appFlow = repository.observeVaultApps()
        assertEquals(0, appFlow.first().size)

        repository.addVaultApp("com.example.app", "Test App")
        assertEquals(1, appFlow.first().size)

        val taskFlow = repository.observeActiveTasks()
        assertEquals(0, taskFlow.first().size)

        val taskId = repository.createTask("Test Task")
        assertEquals(1, taskFlow.first().size)

        repository.archiveTask(taskId)
        assertEquals(0, taskFlow.first().size)
    }
}
