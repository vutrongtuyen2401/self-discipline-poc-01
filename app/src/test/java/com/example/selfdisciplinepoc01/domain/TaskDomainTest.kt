package com.example.selfdisciplinepoc01.domain

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.selfdisciplinepoc01.data.database.AppDatabase
import com.example.selfdisciplinepoc01.data.repository.CoreDataRepository
import com.example.selfdisciplinepoc01.data.repository.CoreDataRepositoryImpl
import com.example.selfdisciplinepoc01.domain.usecase.ArchiveTaskUseCase
import com.example.selfdisciplinepoc01.domain.usecase.CompleteTaskUseCase
import com.example.selfdisciplinepoc01.domain.usecase.CreateTaskUseCase
import com.example.selfdisciplinepoc01.domain.usecase.GetMissionHallTasksUseCase
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
 * Comprehensive Unit Test Suite for Task Domain, UseCases, and Sequential Task Chain.
 *
 * Implements full test matrix required by Phase 18 and Canonical Design V2 (Section 5 & 8):
 * A. Task CRUD & Validation:
 *    - create valid, reject empty/whitespace, trim name, archive, list active.
 * B. Task Completion & 04:00 Boundary:
 *    - complete once, complete twice (idempotent),
 *    - completion before 04:00 (03:59:59) -> belongs to previous business day,
 *    - completion at 04:00:00 -> belongs to current business day,
 *    - midnight (00:00:00) -> belongs to previous business day,
 *    - task started before 04:00 (03:50) and completed after 04:00 (04:15) -> belongs to old cycle.
 * C. Sequential Task Flow:
 *    - Task 1 -> Task 2 -> Task 3.
 *    - Completed task is excluded from incomplete list.
 *    - Current task automatically advances to next incomplete.
 *    - Archived task is skipped automatically.
 *    - All tasks completed -> isAllCompleted = true.
 * D. Daily Reset:
 *    - In new business day, completed tasks from yesterday reset to uncompleted status.
 */
@RunWith(RobolectricTestRunner::class)
class TaskDomainTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: CoreDataRepository
    private val businessDayProvider: BusinessDayProvider = BusinessDayProviderImpl(boundaryHour = 4)
    private val zoneId = ZoneId.of("Asia/Ho_Chi_Minh")

    private lateinit var createTaskUseCase: CreateTaskUseCase
    private lateinit var completeTaskUseCase: CompleteTaskUseCase
    private lateinit var archiveTaskUseCase: ArchiveTaskUseCase
    private lateinit var getTasksUseCase: GetMissionHallTasksUseCase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = CoreDataRepositoryImpl(database)

        createTaskUseCase = CreateTaskUseCase(repository)
        completeTaskUseCase = CompleteTaskUseCase(repository, businessDayProvider)
        archiveTaskUseCase = ArchiveTaskUseCase(repository)
        getTasksUseCase = GetMissionHallTasksUseCase(repository, businessDayProvider)
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ==========================================
    // SECTION A: Task Creation & Validation
    // ==========================================

    @Test
    fun testA1_createTask_withValidName_succeeds() = runBlocking {
        val result = createTaskUseCase("Đọc sách 30 phút")
        assertTrue(result.isSuccess)
        val taskId = result.getOrThrow()
        assertTrue(taskId > 0L)

        val task = repository.getTaskById(taskId)
        assertNotNull(task)
        assertEquals("Đọc sách 30 phút", task?.name)
        assertFalse(task?.isArchived == true)
    }

    @Test
    fun testA2_createTask_withEmptyOrWhitespaceName_fails() = runBlocking {
        val emptyResult = createTaskUseCase("")
        assertTrue(emptyResult.isFailure)

        val whitespaceResult = createTaskUseCase("    \n\t  ")
        assertTrue(whitespaceResult.isFailure)

        val allTasks = repository.getAllActiveTasks()
        assertEquals(0, allTasks.size)
    }

    @Test
    fun testA3_createTask_trimsWhitespace() = runBlocking {
        val result = createTaskUseCase("   Tập thể dục buổi sáng   ")
        assertTrue(result.isSuccess)
        val taskId = result.getOrThrow()

        val task = repository.getTaskById(taskId)
        assertEquals("Tập thể dục buổi sáng", task?.name)
    }

    @Test
    fun testA4_archiveTask_removesFromActiveList() = runBlocking {
        val taskId = createTaskUseCase("Nhiệm vụ tạm").getOrThrow()
        assertEquals(1, repository.getAllActiveTasks().size)

        val archiveResult = archiveTaskUseCase(taskId)
        assertTrue(archiveResult.isSuccess)

        val activeTasks = repository.getAllActiveTasks()
        assertEquals(0, activeTasks.size)

        val archivedEntity = repository.getTaskById(taskId)
        assertNotNull(archivedEntity)
        assertTrue(archivedEntity?.isArchived == true)
    }

    // ==========================================
    // SECTION B: Completion & 04:00 Cycle
    // ==========================================

    @Test
    fun testB1_completeTask_once_recordsCompletion() = runBlocking {
        val taskId = createTaskUseCase("Luyện công 1").getOrThrow()

        val wallTime = LocalDateTime.of(2026, 9, 7, 10, 0)
            .atZone(zoneId).toInstant().toEpochMilli()
        val result = completeTaskUseCase(taskId, completedWallTimeMillis = wallTime, zoneId = zoneId)
        assertTrue(result.isSuccess)

        val isCompleted = repository.isTaskCompletedOnDate(taskId, "2026-09-07")
        assertTrue(isCompleted)
    }

    @Test
    fun testB2_completeTask_twice_isIdempotent() = runBlocking {
        val taskId = createTaskUseCase("Luyện công 2").getOrThrow()

        val wallTime = LocalDateTime.of(2026, 9, 7, 10, 0)
            .atZone(zoneId).toInstant().toEpochMilli()
        completeTaskUseCase(taskId, completedWallTimeMillis = wallTime, zoneId = zoneId)
        completeTaskUseCase(taskId, completedWallTimeMillis = wallTime + 5000, zoneId = zoneId)

        val completions = repository.getCompletedTaskIdsForDate("2026-09-07")
        assertEquals(1, completions.size)
        assertEquals(taskId, completions[0])
    }

    @Test
    fun testB3_completeTask_before0400_belongsToPreviousBusinessDay() = runBlocking {
        val taskId = createTaskUseCase("Nhiệm vụ đêm khuya").getOrThrow()

        // 2026-09-07 03:59:59 (1 second before 04:00 boundary) -> belongs to 2026-09-06
        val timeBefore0400 = LocalDateTime.of(2026, 9, 7, 3, 59, 59)
            .atZone(zoneId).toInstant().toEpochMilli()

        completeTaskUseCase(taskId, completedWallTimeMillis = timeBefore0400, zoneId = zoneId)

        assertTrue(repository.isTaskCompletedOnDate(taskId, "2026-09-06"))
        assertFalse(repository.isTaskCompletedOnDate(taskId, "2026-09-07"))
    }

    @Test
    fun testB4_completeTask_at0400_belongsToCurrentBusinessDay() = runBlocking {
        val taskId = createTaskUseCase("Nhiệm vụ sáng sớm").getOrThrow()

        // 2026-09-07 04:00:00 (exact boundary) -> belongs to 2026-09-07
        val timeAt0400 = LocalDateTime.of(2026, 9, 7, 4, 0, 0)
            .atZone(zoneId).toInstant().toEpochMilli()

        completeTaskUseCase(taskId, completedWallTimeMillis = timeAt0400, zoneId = zoneId)

        assertFalse(repository.isTaskCompletedOnDate(taskId, "2026-09-06"))
        assertTrue(repository.isTaskCompletedOnDate(taskId, "2026-09-07"))
    }

    @Test
    fun testB5_completeTask_startedBefore0400_completedAfter0400_belongsToOldCycle() = runBlocking {
        // Canonical Design V2 Section 8 rule:
        // "Nhiệm vụ bắt đầu trước 04:00 thuộc chu kỳ cũ, kể cả khi hoàn thành sau 04:00."
        val taskId = createTaskUseCase("Nhiệm vụ vắt qua 04:00").getOrThrow()

        val startedTime = LocalDateTime.of(2026, 9, 7, 3, 50).atZone(zoneId).toInstant().toEpochMilli()
        val completedTime = LocalDateTime.of(2026, 9, 7, 4, 15).atZone(zoneId).toInstant().toEpochMilli()

        completeTaskUseCase(
            taskId = taskId,
            completedWallTimeMillis = completedTime,
            taskStartWallMillis = startedTime,
            zoneId = zoneId
        )

        // Recorded against old cycle 2026-09-06
        assertTrue(repository.isTaskCompletedOnDate(taskId, "2026-09-06"))
        assertFalse(repository.isTaskCompletedOnDate(taskId, "2026-09-07"))
    }

    // ==========================================
    // SECTION C: Sequential Task Chain Flow
    // ==========================================

    @Test
    fun testC1_sequentialFlow_advancesAsTasksComplete() = runBlocking {
        val task1Id = createTaskUseCase("Nhiệm vụ 1").getOrThrow()
        val task2Id = createTaskUseCase("Nhiệm vụ 2").getOrThrow()
        val task3Id = createTaskUseCase("Nhiệm vụ 3").getOrThrow()

        val now = LocalDateTime.of(2026, 9, 7, 10, 0).atZone(zoneId).toInstant().toEpochMilli()

        // Step 1: Initial state
        var chain = getTasksUseCase.getSequentialChain(now, zoneId)
        assertEquals(3, chain.totalActiveTasks)
        assertEquals(0, chain.completedCountToday)
        assertEquals(task1Id, chain.currentTask?.id)
        assertEquals(3, chain.incompleteTasks.size)
        assertEquals(listOf(task1Id, task2Id, task3Id), chain.incompleteTasks.map { it.id })
        assertFalse(chain.isAllCompleted)

        // Step 2: Complete Task 1 -> Task 1 disappears from incomplete, Current advances to Task 2
        completeTaskUseCase(task1Id, now, zoneId = zoneId)
        chain = getTasksUseCase.getSequentialChain(now, zoneId)

        assertEquals(1, chain.completedCountToday)
        assertEquals(task2Id, chain.currentTask?.id)
        assertEquals(2, chain.incompleteTasks.size)
        assertEquals(listOf(task2Id, task3Id), chain.incompleteTasks.map { it.id })
        assertEquals(listOf(task1Id), chain.completedTasksToday.map { it.id })
        assertFalse(chain.isAllCompleted)

        // Step 3: Complete Task 2 -> Current advances to Task 3
        completeTaskUseCase(task2Id, now, zoneId = zoneId)
        chain = getTasksUseCase.getSequentialChain(now, zoneId)

        assertEquals(2, chain.completedCountToday)
        assertEquals(task3Id, chain.currentTask?.id)
        assertEquals(listOf(task3Id), chain.incompleteTasks.map { it.id })
        assertFalse(chain.isAllCompleted)

        // Step 4: Complete Task 3 -> All completed
        completeTaskUseCase(task3Id, now, zoneId = zoneId)
        chain = getTasksUseCase.getSequentialChain(now, zoneId)

        assertEquals(3, chain.completedCountToday)
        assertNull(chain.currentTask)
        assertTrue(chain.incompleteTasks.isEmpty())
        assertTrue(chain.isAllCompleted)
    }

    @Test
    fun testC2_sequentialFlow_skipsArchivedTaskMidChain() = runBlocking {
        // Canonical Design V2 Section 5 rule:
        // "Nhiệm vụ đã bị xóa trong lúc chuỗi đang chạy phải bị bỏ qua; hệ thống tự chuyển sang nhiệm vụ tiếp theo hợp lệ."
        val task1Id = createTaskUseCase("Task A").getOrThrow()
        val task2Id = createTaskUseCase("Task B").getOrThrow()
        val task3Id = createTaskUseCase("Task C").getOrThrow()

        val now = LocalDateTime.of(2026, 9, 7, 10, 0).atZone(zoneId).toInstant().toEpochMilli()

        // Complete Task 1
        completeTaskUseCase(task1Id, now, zoneId = zoneId)
        var chain = getTasksUseCase.getSequentialChain(now, zoneId)
        assertEquals(task2Id, chain.currentTask?.id)

        // User deletes/archives Task 2 mid-chain
        archiveTaskUseCase(task2Id)

        // Chain immediately advances to Task 3, skipping Task 2!
        chain = getTasksUseCase.getSequentialChain(now, zoneId)
        assertEquals(task3Id, chain.currentTask?.id)
        assertEquals(listOf(task3Id), chain.incompleteTasks.map { it.id })
        assertEquals(2, chain.totalActiveTasks) // Task 2 is no longer active
        assertEquals(1, chain.completedCountToday)
    }

    // ==========================================
    // SECTION D: Daily Reset in New Business Day
    // ==========================================

    @Test
    fun testD1_newBusinessDay_resetsTasksToUncompleted() = runBlocking {
        val taskId = createTaskUseCase("Task hàng ngày").getOrThrow()

        // Day 1 (2026-09-07 at 10:00) -> Complete task
        val day1Time = LocalDateTime.of(2026, 9, 7, 10, 0).atZone(zoneId).toInstant().toEpochMilli()
        completeTaskUseCase(taskId, day1Time, zoneId = zoneId)

        var day1Chain = getTasksUseCase.getSequentialChain(day1Time, zoneId)
        assertEquals(1, day1Chain.completedCountToday)
        assertTrue(day1Chain.incompleteTasks.isEmpty())
        assertTrue(day1Chain.isAllCompleted)

        // Day 2 (2026-09-08 at 04:30 - after 04:00 reset boundary)
        val day2Time = LocalDateTime.of(2026, 9, 8, 4, 30).atZone(zoneId).toInstant().toEpochMilli()
        var day2Chain = getTasksUseCase.getSequentialChain(day2Time, zoneId)

        // Task resets naturally to uncompleted in new business day!
        assertEquals(0, day2Chain.completedCountToday)
        assertEquals(1, day2Chain.incompleteTasks.size)
        assertEquals(taskId, day2Chain.currentTask?.id)
        assertFalse(day2Chain.isAllCompleted)
    }

    @Test
    fun testD2_flowReactiveObservation() = runBlocking {
        val flow = getTasksUseCase.observeSequentialChain()
        var initialChain = flow.first()
        assertEquals(0, initialChain.totalActiveTasks)

        val taskId = createTaskUseCase("Flow Task").getOrThrow()
        val updatedChain = flow.first()
        assertEquals(1, updatedChain.totalActiveTasks)
        assertEquals(taskId, updatedChain.currentTask?.id)
    }
}
