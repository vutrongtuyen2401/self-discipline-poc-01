package com.example.selfdisciplinepoc01.data.canonical

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.selfdisciplinepoc01.data.canonical.repository.CanonicalCycleRepositoryImpl
import com.example.selfdisciplinepoc01.data.canonical.repository.CanonicalLockEvaluatorImpl
import com.example.selfdisciplinepoc01.data.canonical.repository.CanonicalTaskRepositoryImpl
import com.example.selfdisciplinepoc01.data.canonical.repository.CanonicalVaultRepositoryImpl
import com.example.selfdisciplinepoc01.data.database.AppDatabase
import com.example.selfdisciplinepoc01.domain.canonical.cycle.CanonicalCycleId
import com.example.selfdisciplinepoc01.domain.canonical.task.CanonicalTask
import com.example.selfdisciplinepoc01.domain.canonical.task.TaskCycleState
import com.example.selfdisciplinepoc01.domain.canonical.task.TaskCycleStatus
import com.example.selfdisciplinepoc01.domain.canonical.vault.CanonicalLockDecision
import com.example.selfdisciplinepoc01.domain.canonical.vault.CanonicalVaultApp
import com.example.selfdisciplinepoc01.domain.canonical.vault.VoucherEffect
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
import java.time.Instant
import java.time.ZoneId

/**
 * Suite kiểm thử tích hợp (Persistence Integration Tests) cho Canonical Core backed by Room.
 *
 * Kiểm tra:
 * 1. Task Definition + Task Cycle State lưu trữ bền vững.
 * 2. Bảo toàn lịch sử chu kỳ qua Room (không xóa dữ liệu ngày cũ).
 * 3. Atomic Transaction khi Archive Task: gỡ liên kết nhưng bảo toàn lịch sử.
 * 4. Xóa Vault App: xóa liên kết nhưng bảo toàn Task.
 * 5. Đánh giá khóa CanonicalLockEvaluator từ cơ sở dữ liệu Room thực tế.
 */
@RunWith(RobolectricTestRunner::class)
class CanonicalPersistenceIntegrationTest {

    private lateinit var database: AppDatabase
    private lateinit var taskRepo: CanonicalTaskRepositoryImpl
    private lateinit var vaultRepo: CanonicalVaultRepositoryImpl
    private lateinit var cycleRepo: CanonicalCycleRepositoryImpl
    private lateinit var lockEvaluator: CanonicalLockEvaluatorImpl

    private val testZone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val cycleYesterday = CanonicalCycleId("2026-09-10")
    private val cycleToday = CanonicalCycleId("2026-09-11")
    private val testPackage = "com.facebook.katana"

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        taskRepo = CanonicalTaskRepositoryImpl(database)
        vaultRepo = CanonicalVaultRepositoryImpl(database)
        cycleRepo = CanonicalCycleRepositoryImpl(zoneIdProvider = { testZone })
        lockEvaluator = CanonicalLockEvaluatorImpl(taskRepo, vaultRepo, cycleRepo)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun test01_taskDefinitionAndCycleStatePersistence() = runBlocking {
        val task = CanonicalTask(
            id = "task_gym",
            title = "Đi tập gym 45 phút",
            description = "Rèn luyện thể lực",
            orderIndex = 1,
            hasReward = true
        )
        taskRepo.saveTask(task)

        val retrievedTask = taskRepo.getTask("task_gym")
        assertNotNull(retrievedTask)
        assertEquals("task_gym", retrievedTask?.id)
        assertEquals("Đi tập gym 45 phút", retrievedTask?.title)
        assertFalse(retrievedTask!!.isArchived)

        // Lưu trạng thái chu kỳ hôm qua
        val stateYesterday = TaskCycleState(
            taskId = "task_gym",
            cycleId = cycleYesterday,
            status = TaskCycleStatus.COMPLETED,
            completedAt = Instant.now().minusSeconds(86400)
        )
        taskRepo.saveCycleState(stateYesterday)

        // Lưu trạng thái chu kỳ hôm nay
        val stateToday = TaskCycleState(
            taskId = "task_gym",
            cycleId = cycleToday,
            status = TaskCycleStatus.PENDING,
            completedAt = null
        )
        taskRepo.saveCycleState(stateToday)

        // Kiểm tra độc lập giữa 2 chu kỳ
        val readStateYesterday = taskRepo.getCycleState("task_gym", cycleYesterday)
        val readStateToday = taskRepo.getCycleState("task_gym", cycleToday)

        assertTrue(readStateYesterday?.isCompleted == true)
        assertFalse(readStateToday?.isCompleted == true)
    }

    @Test
    fun test02_cycleTransitionPreservesHistoricalStateInRoom() = runBlocking {
        val taskId = "task_study"
        taskRepo.saveTask(CanonicalTask(id = taskId, title = "Học lập trình", hasReward = true))

        // Hoàn thành ở chu kỳ cũ
        taskRepo.completeTask(taskId, cycleYesterday)

        // Chu kỳ mới bắt đầu: chưa hoàn thành
        val stateInNewCycle = taskRepo.getCycleState(taskId, cycleToday)
        assertNull("Chu kỳ mới chưa có record => mặc định PENDING", stateInNewCycle)

        // Lịch sử chu kỳ cũ vẫn query được nguyên vẹn từ Room
        val history = taskRepo.getTaskCycleHistory(taskId)
        assertEquals(1, history.size)
        assertEquals(cycleYesterday, history[0].cycleId)
        assertTrue(history[0].isCompleted)
    }

    @Test
    fun test03_archiveTaskAtomicTransactionPreservesHistory() = runBlocking {
        val taskId = "task_archive_test"
        taskRepo.saveTask(CanonicalTask(id = taskId, title = "Task sắp xóa", hasReward = true))
        vaultRepo.addVaultApp(CanonicalVaultApp(testPackage, "Facebook"))
        taskRepo.linkTaskToApp(taskId, testPackage)

        // Ghi nhận trạng thái hoàn thành chu kỳ hôm qua
        taskRepo.completeTask(taskId, cycleYesterday)

        // Kiểm tra liên kết tồn tại
        assertEquals(1, taskRepo.getTasksLinkedToApp(testPackage).size)

        // Thực hiện Archive Task (Atomic Transaction)
        taskRepo.archiveTask(taskId)

        // 1. Task definition chuyển sang isArchived = true
        val taskAfter = taskRepo.getTask(taskId)
        assertTrue(taskAfter?.isArchived == true)
        // 2. Không còn trong danh sách active tasks
        val activeTasks = taskRepo.getAllActiveTasks()
        assertTrue(activeTasks.none { it.id == taskId })
        // 3. Liên kết với app bị xóa
        val linkedTasksAfter = taskRepo.getTasksLinkedToApp(testPackage)
        assertTrue(linkedTasksAfter.isEmpty())
        // 4. Lịch sử thực thi TaskCycleState VẪN NGUYÊN VẸN trong Room
        val history = taskRepo.getTaskCycleHistory(taskId)
        assertEquals("Lịch sử chu kỳ cũ không được phép bị xóa khi archive task", 1, history.size)
        assertEquals(cycleYesterday, history[0].cycleId)
        assertTrue(history[0].isCompleted)
    }

    @Test
    fun test04_removeVaultAppDeletesLinksPreservesTasks() = runBlocking {
        val taskId = "task_keep"
        taskRepo.saveTask(CanonicalTask(id = taskId, title = "Task giữ lại", hasReward = true))
        vaultRepo.addVaultApp(CanonicalVaultApp(testPackage, "Facebook"))
        taskRepo.linkTaskToApp(taskId, testPackage)

        // Xóa app khỏi Vault
        taskRepo.removeAllLinksForApp(testPackage)
        vaultRepo.removeVaultApp(testPackage)

        // App không còn trong Vault
        assertNull(vaultRepo.getVaultApp(testPackage))
        // Liên kết bị xóa
        assertTrue(taskRepo.getTasksLinkedToApp(testPackage).isEmpty())
        // Task vẫn còn nguyên vẹn trong Room
        assertNotNull(taskRepo.getTask(taskId))
    }

    @Test
    fun test05_canonicalLockEvaluatorFromRealDatabaseData() = runBlocking {
        // Cài đặt thời điểm đánh giá: 10:00 ngày 2026-09-11 (thuộc chu kỳ 2026-09-11)
        val evalInstant = Instant.parse("2026-09-11T03:00:00Z") // 10:00 GMT+7

        // 1. App chưa thêm vào Vault -> UNLOCKED (NOT_IN_VAULT)
        val resNotManaged = lockEvaluator.evaluateApp(testPackage, evalInstant)
        assertTrue(resNotManaged.isUnlocked)

        // 2. Thêm app vào Vault (N=0) -> UNLOCKED
        vaultRepo.addVaultApp(CanonicalVaultApp(testPackage, "Facebook"))
        val resN0 = lockEvaluator.evaluateApp(testPackage, evalInstant)
        assertTrue("N=0 mới thêm vào Vault phải UNLOCKED", resN0.isUnlocked)
        assertEquals(0, resN0.totalLinkedRewardTasks)

        // 3. Tạo 2 task và liên kết với app (N=2, req=1 do đặc xá SSOT)
        taskRepo.saveTask(CanonicalTask(id = "t1", title = "Task 1", hasReward = true))
        taskRepo.saveTask(CanonicalTask(id = "t2", title = "Task 2", hasReward = true))
        taskRepo.linkTaskToApp("t1", testPackage)
        taskRepo.linkTaskToApp("t2", testPackage)

        // Chưa hoàn thành task nào (0/2) -> LOCKED
        val resPending = lockEvaluator.evaluateApp(testPackage, evalInstant)
        assertTrue(resPending.isLocked)
        assertEquals(2, resPending.totalLinkedRewardTasks)
        assertEquals(1, resPending.requiredCompletions)

        // 4. Hoàn thành 1 task trong chu kỳ hiện tại (1/2):
        // App Lock Policy: Vẫn còn 2 reward tasks yêu cầu -> LOCKED
        // Deletion Policy: K=1 >= RequiredForDeletion(2)=1 -> canDelete = true
        taskRepo.completeTask("t1", cycleToday)
        val resStillLocked = lockEvaluator.evaluateApp(testPackage, evalInstant)
        assertTrue("App vẫn LOCK vì 2 task yêu cầu vẫn còn hiệu lực trong chu kỳ", resStillLocked.isLocked)
        assertEquals(1, resStillLocked.completedLinkedRewardTasks)
        assertEquals(1, resStillLocked.requiredCompletions)

        // 5. Thêm Thẻ bài miễn phong còn hạn
        val voucher = VoucherEffect(
            voucherId = "voucher_1",
            voucherName = "Kim Bài Miễn Phong",
            targetPackageName = testPackage,
            effectiveFrom = evalInstant.minusSeconds(60),
            effectiveUntil = evalInstant.plusSeconds(3600)
        )
        vaultRepo.saveVoucher(voucher)

        // Undo task hoàn thành (0/2) nhưng có voucher còn hạn -> UNLOCKED
        taskRepo.undoTaskCompletion("t1", cycleToday)
        val resVoucher = lockEvaluator.evaluateApp(testPackage, evalInstant)
        assertTrue("Thẻ bài còn hạn mở khóa ứng dụng ngay lập tức", resVoucher.isUnlocked)
        assertEquals(voucher.voucherId, resVoucher.effectiveVoucher?.voucherId)
    }
}
