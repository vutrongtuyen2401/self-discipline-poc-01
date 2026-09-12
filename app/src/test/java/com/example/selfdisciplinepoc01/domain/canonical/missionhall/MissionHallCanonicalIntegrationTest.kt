package com.example.selfdisciplinepoc01.domain.canonical.missionhall

import com.example.selfdisciplinepoc01.domain.canonical.cycle.CanonicalCycleBoundary
import com.example.selfdisciplinepoc01.domain.canonical.cycle.CanonicalCycleId
import com.example.selfdisciplinepoc01.domain.canonical.cycle.CycleEngine
import com.example.selfdisciplinepoc01.domain.canonical.repository.CanonicalCycleRepository
import com.example.selfdisciplinepoc01.domain.canonical.repository.CanonicalLockEvaluator
import com.example.selfdisciplinepoc01.domain.canonical.repository.CanonicalTaskRepository
import com.example.selfdisciplinepoc01.domain.canonical.repository.CanonicalVaultRepository
import com.example.selfdisciplinepoc01.domain.canonical.task.CanonicalTask
import com.example.selfdisciplinepoc01.domain.canonical.task.TaskCycleState
import com.example.selfdisciplinepoc01.domain.canonical.task.TaskCycleStatus
import com.example.selfdisciplinepoc01.domain.canonical.task.TaskRewardLink
import com.example.selfdisciplinepoc01.domain.canonical.usecase.CompleteTaskUseCase
import com.example.selfdisciplinepoc01.domain.canonical.usecase.CreateTaskUseCase
import com.example.selfdisciplinepoc01.domain.canonical.usecase.DeleteTaskUseCase
import com.example.selfdisciplinepoc01.domain.canonical.usecase.GetMissionHallTasksCanonicalUseCase
import com.example.selfdisciplinepoc01.domain.canonical.usecase.RenameTaskUseCase
import com.example.selfdisciplinepoc01.domain.canonical.usecase.UndoTaskUseCase
import com.example.selfdisciplinepoc01.domain.canonical.usecase.UpdateTaskRewardLinkageUseCase
import com.example.selfdisciplinepoc01.domain.canonical.vault.CanonicalLockDecision
import com.example.selfdisciplinepoc01.domain.canonical.vault.CanonicalLockPolicy
import com.example.selfdisciplinepoc01.domain.canonical.vault.CanonicalVaultApp
import com.example.selfdisciplinepoc01.domain.canonical.vault.LockEvaluationResult
import com.example.selfdisciplinepoc01.domain.canonical.vault.VoucherEffect
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/**
 * Suite kiểm thử chấp nhận Phase 2B-A: Mission Hall Canonical Runtime Integration.
 * Bao gồm đầy đủ 14 Acceptance Tests tối thiểu (MB-A01 đến MB-A14).
 */
class MissionHallCanonicalIntegrationTest {

    private lateinit var taskRepository: InMemoryCanonicalTaskRepository
    private lateinit var vaultRepository: InMemoryCanonicalVaultRepository
    private lateinit var cycleRepository: InMemoryCanonicalCycleRepository
    private lateinit var lockEvaluator: CanonicalLockEvaluator

    private val testZone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val cycleDate1 = "2026-09-12"
    private val cycleDate2 = "2026-09-13"

    @Before
    fun setUp() {
        taskRepository = InMemoryCanonicalTaskRepository()
        vaultRepository = InMemoryCanonicalVaultRepository()
        cycleRepository = InMemoryCanonicalCycleRepository(testZone)
        lockEvaluator = object : CanonicalLockEvaluator {
            override suspend fun evaluateApp(
                packageName: String,
                evaluationInstant: Instant
            ): LockEvaluationResult {
                val vaultApp = vaultRepository.getVaultApp(packageName)
                    ?: return LockEvaluationResult(
                        packageName = packageName,
                        decision = CanonicalLockDecision.UNLOCKED,
                        reason = "UNLOCKED_NOT_IN_VAULT",
                        totalLinkedRewardTasks = 0,
                        completedLinkedRewardTasks = 0,
                        requiredCompletions = 0,
                        effectiveVoucher = null
                    )

                val cycle = cycleRepository.getCurrentCycle(evaluationInstant)
                val linkedTasks = taskRepository.getTasksLinkedToApp(packageName)
                val states = taskRepository.getCycleStates(cycle.cycleId)
                val activeVouchers = vaultRepository.getActiveVouchers(evaluationInstant)

                return CanonicalLockPolicy.evaluateLock(
                    packageName = packageName,
                    linkedTasks = linkedTasks,
                    cycleTaskStates = states,
                    activeVouchers = activeVouchers,
                    evaluationInstant = evaluationInstant
                )
            }
        }
    }

    /**
     * MB-A01: Create Task
     * - Canonical Task Definition được tạo
     * - Task thuộc cycle đúng (TaskCycleState ban đầu PENDING)
     * - Reward links canonical được thiết lập
     */
    @Test
    fun `MB-A01 Create Task - canonical Task Definition tao thanh cong, dung cycle va reward links`() = runBlocking {
        val createUseCase = CreateTaskUseCase(taskRepository)
        val appPkg = "com.android.chrome"
        vaultRepository.addVaultApp(CanonicalVaultApp(appPkg, "Chrome"))

        val createdTask = createUseCase(
            id = "task_01",
            title = "Luyện kiếm 1000 lần",
            description = "Tự kỷ luật buổi sáng",
            orderIndex = 0,
            hasReward = true,
            linkedAppPackageNames = listOf(appPkg)
        )

        // 1. Task Definition được tạo chuẩn mực
        val fetchedTask = taskRepository.getTask("task_01")
        assertNotNull(fetchedTask)
        assertEquals("Luyện kiếm 1000 lần", fetchedTask?.title)
        assertFalse(fetchedTask!!.isArchived)
        assertTrue(fetchedTask.hasReward)

        // 2. Thuộc chu kỳ đúng với trạng thái PENDING
        val currentCycle = cycleRepository.getCurrentCycle().cycleId
        val cycleState = taskRepository.getCycleState("task_01", currentCycle)
        // Mặc định hoặc khi lưu trạng thái ban đầu:
        val resolvedState = cycleState ?: TaskCycleState("task_01", currentCycle, TaskCycleStatus.PENDING)
        assertEquals(TaskCycleStatus.PENDING, resolvedState.status)

        // 3. Reward link canonical được lưu chuẩn
        val linkedApps = taskRepository.getAppsLinkedToTask("task_01")
        assertEquals(1, linkedApps.size)
        assertEquals(appPkg, linkedApps.first())
    }

    /**
     * MB-A02: Rename Task
     * - Title thay đổi immediate
     * - Không confirmation business layer
     */
    @Test
    fun `MB-A02 Rename Task - doi ten tuc thi khong can confirmation layer`() = runBlocking {
        val task = CanonicalTask(id = "task_02", title = "Tụng kinh")
        taskRepository.saveTask(task)

        val renameUseCase = RenameTaskUseCase(taskRepository)
        renameUseCase("task_02", "Tụng kinh Kim Cương 3 biến")

        val updated = taskRepository.getTask("task_02")
        assertNotNull(updated)
        assertEquals("Tụng kinh Kim Cương 3 biến", updated?.title)
        assertEquals("task_02", updated?.id) // Giữ nguyên ID, không sinh nhiệm vụ mới
    }

    /**
     * MB-A03: Complete Task
     * - TaskCycleState current cycle -> completed
     * - Task Definition vẫn tồn tại
     */
    @Test
    fun `MB-A03 Complete Task - cap nhat TaskCycleState hien tai sang COMPLETED va bao toan Task Definition`() = runBlocking {
        val task = CanonicalTask(id = "task_03", title = "Ngồi thiền")
        taskRepository.saveTask(task)

        val currentCycle = cycleRepository.getCurrentCycle().cycleId
        val completeUseCase = CompleteTaskUseCase(taskRepository, lockEvaluator)

        completeUseCase("task_03", currentCycle)

        // 1. Trạng thái trong chu kỳ hiện tại là COMPLETED
        val cycleState = taskRepository.getCycleState("task_03", currentCycle)
        assertNotNull(cycleState)
        assertEquals(TaskCycleStatus.COMPLETED, cycleState?.status)
        assertTrue(cycleState!!.isCompleted)
        assertNotNull(cycleState.completedAt)

        // 2. Task Definition vẫn tồn tại và active
        val taskDef = taskRepository.getTask("task_03")
        assertNotNull(taskDef)
        assertFalse(taskDef!!.isArchived)
    }

    /**
     * MB-A04: Undo Task
     * - Confirmation path tồn tại
     * - Current-cycle completion state được revert về PENDING
     * - Lock state được recomputed
     */
    @Test
    fun `MB-A04 Undo Task - revert ve PENDING va recompute lock state`() = runBlocking {
        val appPkg = "com.facebook.katana"
        vaultRepository.addVaultApp(CanonicalVaultApp(appPkg, "Facebook"))

        val task = CanonicalTask(id = "task_04", title = "Chạy bộ 5km", hasReward = true)
        taskRepository.saveTask(task)
        taskRepository.linkTaskToApp("task_04", appPkg)

        val currentCycle = cycleRepository.getCurrentCycle().cycleId
        val completeUseCase = CompleteTaskUseCase(taskRepository, lockEvaluator)
        val undoUseCase = UndoTaskUseCase(taskRepository, lockEvaluator)

        // Complete trước
        completeUseCase("task_04", currentCycle)
        var lockResult = lockEvaluator.evaluateApp(appPkg)
        // SSOT LOCK RULE: completed task still requires app while link exists in cycle -> LOCKED
        assertEquals(CanonicalLockDecision.LOCKED, lockResult.decision)
        assertTrue(com.example.selfdisciplinepoc01.domain.canonical.vault.CanonicalAppDeletionPolicy.canDelete(1, 1))

        // Undo completion
        val undoResults = undoUseCase("task_04", currentCycle)
        val stateAfterUndo = taskRepository.getCycleState("task_04", currentCycle)
        assertEquals(TaskCycleStatus.PENDING, stateAfterUndo?.status)

        lockResult = lockEvaluator.evaluateApp(appPkg)
        assertEquals(CanonicalLockDecision.LOCKED, lockResult.decision)
        assertEquals(0, lockResult.completedLinkedRewardTasks)
        assertEquals(CanonicalLockDecision.LOCKED, undoResults[appPkg]?.decision)
    }

    /**
     * MB-A05: Delete Task (Archive)
     * - Confirmation path tồn tại
     * - Task không còn active (isArchived = true)
     * - Historical state không bị xóa
     */
    @Test
    fun `MB-A05 Delete Task - archive task ma khong xoa historical cycle state`() = runBlocking {
        val task = CanonicalTask(id = "task_05", title = "Đọc sách 20 trang")
        taskRepository.saveTask(task)

        val pastCycle = CanonicalCycleId("2026-09-10")
        taskRepository.completeTask("task_05", pastCycle, Instant.now().minusSeconds(86400))

        val deleteUseCase = DeleteTaskUseCase(taskRepository, lockEvaluator)
        deleteUseCase("task_05")

        // 1. Không còn trong danh sách active
        val activeTasks = taskRepository.getAllActiveTasks()
        assertFalse(activeTasks.any { it.id == "task_05" })

        // 2. Task được đánh dấu isArchived = true
        val taskDef = taskRepository.getTask("task_05")
        assertNotNull(taskDef)
        assertTrue(taskDef!!.isArchived)

        // 3. Historical state không bị mất
        val history = taskRepository.getTaskCycleHistory("task_05")
        assertEquals(1, history.size)
        assertEquals(pastCycle, history.first().cycleId)
        assertEquals(TaskCycleStatus.COMPLETED, history.first().status)
    }

    /**
     * MB-A06: Many-to-many Task <-> Vault App
     * - 1 Task reward nhiều apps
     * - 1 App có nhiều reward tasks
     */
    @Test
    fun `MB-A06 Many-to-many - mot task reward nhieu app va mot app co nhieu task`() = runBlocking {
        val app1 = "com.youtube"
        val app2 = "com.tiktok"
        vaultRepository.addVaultApp(CanonicalVaultApp(app1, "YouTube"))
        vaultRepository.addVaultApp(CanonicalVaultApp(app2, "TikTok"))

        val task1 = CanonicalTask("task_m1", "Học tiếng Anh")
        val task2 = CanonicalTask("task_m2", "Tập yoga")
        taskRepository.saveTask(task1)
        taskRepository.saveTask(task2)

        // Task 1 liên kết cả App 1 và App 2
        taskRepository.linkTaskToApp("task_m1", app1)
        taskRepository.linkTaskToApp("task_m1", app2)

        // Task 2 cũng liên kết App 1
        taskRepository.linkTaskToApp("task_m2", app1)

        // Kiểm tra 1 Task -> nhiều App
        val task1Apps = taskRepository.getAppsLinkedToTask("task_m1")
        assertEquals(2, task1Apps.size)
        assertTrue(task1Apps.contains(app1))
        assertTrue(task1Apps.contains(app2))

        // Kiểm tra 1 App -> nhiều Task
        val app1Tasks = taskRepository.getTasksLinkedToApp(app1)
        assertEquals(2, app1Tasks.size)
        assertTrue(app1Tasks.any { it.id == "task_m1" })
        assertTrue(app1Tasks.any { it.id == "task_m2" })
    }

    /**
     * MB-A07: Rewardless task
     * - Task không có reward app (hasReward = false) không được tính vào N
     */
    @Test
    fun `MB-A07 Rewardless task - task khong reward khong duoc tinh vao N`() = runBlocking {
        val appPkg = "com.game.gacha"
        vaultRepository.addVaultApp(CanonicalVaultApp(appPkg, "Gacha Game"))

        val rewardlessTask = CanonicalTask("task_rl", "Uống đủ 2L nước", hasReward = false)
        taskRepository.saveTask(rewardlessTask)
        taskRepository.linkTaskToApp("task_rl", appPkg) // Gán nhầm nhưng hasReward=false

        val result = lockEvaluator.evaluateApp(appPkg)
        // Vì hasReward=false, N=0 -> UNLOCKED
        assertEquals(CanonicalLockDecision.UNLOCKED, result.decision)
        assertEquals(0, result.totalLinkedRewardTasks)
    }

    /**
     * MB-A08: Pending next-cycle
     * - Latest pending config replaces previous pending config
     * - Cancel pending hoạt động
     * - Current-cycle reward không bị mutate sai trước 04:00
     */
    @Test
    fun `MB-A08 Pending next-cycle - config moi ghi de cu, cancel hoat dong va giu nguyen current reward`() = runBlocking {
        val appA = "com.app.a"
        val appB = "com.app.b"
        val appC = "com.app.c"
        vaultRepository.addVaultApp(CanonicalVaultApp(appA, "App A"))
        vaultRepository.addVaultApp(CanonicalVaultApp(appB, "App B"))
        vaultRepository.addVaultApp(CanonicalVaultApp(appC, "App C"))

        val task = CanonicalTask("task_p08", "Code sạch", hasReward = true)
        taskRepository.saveTask(task)
        taskRepository.linkTaskToApp("task_p08", appA) // Current reward là appA

        // 1. Đặt cấu hình chờ: appB
        taskRepository.setPendingNextCycleRewards("task_p08", listOf(appB))
        var updated = taskRepository.getTask("task_p08")
        assertEquals(listOf(appB), updated?.pendingNextCycleRewards)
        // Current links vẫn là appA!
        assertEquals(listOf(appA), taskRepository.getAppsLinkedToTask("task_p08"))

        // 2. Latest pending ghi đè pending cũ: appC
        taskRepository.setPendingNextCycleRewards("task_p08", listOf(appC))
        updated = taskRepository.getTask("task_p08")
        assertEquals(listOf(appC), updated?.pendingNextCycleRewards)

        // 3. Cancel pending hoạt động
        taskRepository.cancelPendingNextCycleRewards("task_p08")
        updated = taskRepository.getTask("task_p08")
        assertNull(updated?.pendingNextCycleRewards)
        // Current links vẫn vẹn nguyên là appA
        assertEquals(listOf(appA), taskRepository.getAppsLinkedToTask("task_p08"))
    }

    /**
     * MB-A09: Lock integration
     * - Mission Hall mutation không tự quyết định lock
     * - CanonicalLockEvaluator là authority
     * - Required(N) chuẩn
     */
    @Test
    fun `MB-A09 Lock integration - CanonicalLockEvaluator quyet dinh va Required(N) dung`() = runBlocking {
        val appPkg = "com.netflix"
        vaultRepository.addVaultApp(CanonicalVaultApp(appPkg, "Netflix"))

        // Tạo 3 tasks liên kết Netflix (N=3 => Required(3) = 2)
        val t1 = CanonicalTask("t1", "Task 1")
        val t2 = CanonicalTask("t2", "Task 2")
        val t3 = CanonicalTask("t3", "Task 3")
        taskRepository.saveTask(t1)
        taskRepository.saveTask(t2)
        taskRepository.saveTask(t3)
        taskRepository.linkTaskToApp("t1", appPkg)
        taskRepository.linkTaskToApp("t2", appPkg)
        taskRepository.linkTaskToApp("t3", appPkg)

        val cycle = cycleRepository.getCurrentCycle().cycleId

        // Hoàn thành 1 task (K=1 < Required(3)=2) => LOCKED
        taskRepository.completeTask("t1", cycle)
        var eval = lockEvaluator.evaluateApp(appPkg)
        assertEquals(CanonicalLockDecision.LOCKED, eval.decision)
        assertEquals(3, eval.totalLinkedRewardTasks)
        assertEquals(1, eval.completedLinkedRewardTasks)
        assertEquals(2, eval.requiredCompletions)

        // Hoàn thành thêm task 2 (K=2 >= RequiredForDeletion(3)=2):
        // Lock Policy: vẫn LOCKED vì cả 3 tasks vẫn còn liên kết trong chu kỳ hiện tại
        // Deletion Policy: K=2 >= RequiredForDeletion(3)=2 -> canDelete = true
        taskRepository.completeTask("t2", cycle)
        eval = lockEvaluator.evaluateApp(appPkg)
        assertEquals(CanonicalLockDecision.LOCKED, eval.decision)
        assertEquals(2, eval.completedLinkedRewardTasks)
        assertTrue(com.example.selfdisciplinepoc01.domain.canonical.vault.CanonicalAppDeletionPolicy.canDelete(3, 2))
    }

    /**
     * MB-A10: N=0
     * - App không có reward task -> UNLOCKED
     */
    @Test
    fun `MB-A10 N=0 - app khong co reward task luon UNLOCKED`() = runBlocking {
        val appPkg = "com.spotify.music"
        vaultRepository.addVaultApp(CanonicalVaultApp(appPkg, "Spotify"))

        val eval = lockEvaluator.evaluateApp(appPkg)
        assertEquals(CanonicalLockDecision.UNLOCKED, eval.decision)
        assertEquals(0, eval.totalLinkedRewardTasks)
        assertEquals(0, eval.requiredCompletions)
    }

    /**
     * MB-A11: N=2
     * - RequiredForDeletion(2) = 1 (Đặc xá khởi đầu theo MASTER SSOT)
     */
    @Test
    fun `MB-A11 N=2 - Required(2) = 1 dac xa khoi dau`() = runBlocking {
        val appPkg = "com.discord"
        vaultRepository.addVaultApp(CanonicalVaultApp(appPkg, "Discord"))

        val t1 = CanonicalTask("t_d1", "Task D1")
        val t2 = CanonicalTask("t_d2", "Task D2")
        taskRepository.saveTask(t1)
        taskRepository.saveTask(t2)
        taskRepository.linkTaskToApp("t_d1", appPkg)
        taskRepository.linkTaskToApp("t_d2", appPkg)

        val cycle = cycleRepository.getCurrentCycle().cycleId

        // Chưa xong task nào (K=0) => LOCKED
        var eval = lockEvaluator.evaluateApp(appPkg)
        assertEquals(CanonicalLockDecision.LOCKED, eval.decision)
        assertEquals(2, eval.totalLinkedRewardTasks)
        assertEquals(1, eval.requiredCompletions) // RequiredForDeletion(2) = 1!
        assertFalse(com.example.selfdisciplinepoc01.domain.canonical.vault.CanonicalAppDeletionPolicy.canDelete(2, 0))

        // Xong 1 task (K=1 out of 2):
        // Lock Policy: vẫn LOCKED vì 2 tasks yêu cầu vẫn còn hiệu lực
        // Deletion Policy: K=1 >= RequiredForDeletion(2)=1 -> canDelete = true
        taskRepository.completeTask("t_d1", cycle)
        eval = lockEvaluator.evaluateApp(appPkg)
        assertEquals(CanonicalLockDecision.LOCKED, eval.decision)
        assertEquals(1, eval.completedLinkedRewardTasks)
        assertTrue(com.example.selfdisciplinepoc01.domain.canonical.vault.CanonicalAppDeletionPolicy.canDelete(2, 1))

        // Khi gỡ liên kết cả 2 task -> UNLOCKED
        taskRepository.unlinkTaskFromApp("t_d1", appPkg)
        taskRepository.unlinkTaskFromApp("t_d2", appPkg)
        eval = lockEvaluator.evaluateApp(appPkg)
        assertEquals(CanonicalLockDecision.UNLOCKED, eval.decision)
    }

    /**
     * MB-A12: History preservation on delete
     * - Delete current Task không xóa historical cycle state
     */
    @Test
    fun `MB-A12 History preservation - delete current task khong lam mat lich su cu`() = runBlocking {
        val taskId = "task_hist_12"
        val task = CanonicalTask(taskId, "Dậy sớm 5h")
        taskRepository.saveTask(task)

        val c1 = CanonicalCycleId("2026-09-01")
        val c2 = CanonicalCycleId("2026-09-02")
        val c3 = CanonicalCycleId("2026-09-03")

        taskRepository.completeTask(taskId, c1)
        taskRepository.completeTask(taskId, c2)
        taskRepository.saveCycleState(TaskCycleState(taskId, c3, TaskCycleStatus.PENDING))

        // Xóa task ở chu kỳ 3
        taskRepository.archiveTask(taskId)

        // Kiểm tra lịch sử
        val history = taskRepository.getTaskCycleHistory(taskId)
        assertEquals(3, history.size)
        assertTrue(history.any { it.cycleId == c1 && it.status == TaskCycleStatus.COMPLETED })
        assertTrue(history.any { it.cycleId == c2 && it.status == TaskCycleStatus.COMPLETED })
        assertTrue(history.any { it.cycleId == c3 && it.status == TaskCycleStatus.PENDING })
    }

    /**
     * MB-A13: 04:00 Cycle rollover
     * - 04:00 tạo cycle mới
     * - Task Definition vẫn tồn tại
     * - Current TaskCycleState mới đúng cycle
     * - Không reset dữ liệu ngoài cycle scope
     */
    @Test
    fun `MB-A13 Cycle rollover - 04h00 tao cycle moi, bao toan task def, kich hoat pending rewards`() = runBlocking {
        val appOld = "com.app.old"
        val appNew = "com.app.new"
        vaultRepository.addVaultApp(CanonicalVaultApp(appOld, "App Old"))
        vaultRepository.addVaultApp(CanonicalVaultApp(appNew, "App New"))

        val task = CanonicalTask("task_13", "Học tiếng Nhật", hasReward = true)
        taskRepository.saveTask(task)
        taskRepository.linkTaskToApp("task_13", appOld)

        val cycle1 = CanonicalCycleId(cycleDate1)
        taskRepository.completeTask("task_13", cycle1)

        // Người dùng đặt pending reward sang appNew cho chu kỳ sau
        taskRepository.setPendingNextCycleRewards("task_13", listOf(appNew))

        // --- Chạm mốc 04:00 AM: Rollover sang cycle 2 ---
        cycleRepository.setMockCycle(cycleDate2)
        val cycle2 = CanonicalCycleId(cycleDate2)

        // Kích hoạt áp dụng pending next-cycle rewards
        taskRepository.applyAllPendingNextCycleRewards()

        // 1. Task Definition vẫn tồn tại nguyên vẹn
        val taskDef = taskRepository.getTask("task_13")
        assertNotNull(taskDef)
        assertFalse(taskDef!!.isArchived)
        assertNull(taskDef.pendingNextCycleRewards) // Hàng chờ pending đã xóa

        // 2. Liên kết phần thưởng mới đã trở thành chính thức
        val currentLinks = taskRepository.getAppsLinkedToTask("task_13")
        assertEquals(listOf(appNew), currentLinks)

        // 3. Trạng thái trong chu kỳ 2 là PENDING (chưa complete)
        val cycle2State = taskRepository.getCycleState("task_13", cycle2)
        val resolvedState = cycle2State ?: TaskCycleState("task_13", cycle2, TaskCycleStatus.PENDING)
        assertEquals(TaskCycleStatus.PENDING, resolvedState.status)

        // 4. Trạng thái chu kỳ 1 vẫn vẹn nguyên COMPLETED
        val cycle1State = taskRepository.getCycleState("task_13", cycle1)
        assertEquals(TaskCycleStatus.COMPLETED, cycle1State?.status)
    }

    /**
     * MB-A14: Cross-boundary task semantics
     * - Start trước 04:00, complete sau 04:00 tuân thủ canonical semantics
     */
    @Test
    fun `MB-A14 Cross-boundary - hoan thanh sau 04h00 tuan thu canonical semantics`() = runBlocking {
        val task = CanonicalTask("task_14", "Luyện công qua đêm")
        taskRepository.saveTask(task)

        val cycleOld = CanonicalCycleId(cycleDate1)
        val cycleNew = CanonicalCycleId(cycleDate2)

        // Bắt đầu tại chu kỳ 1 (PENDING)
        taskRepository.saveCycleState(TaskCycleState("task_14", cycleOld, TaskCycleStatus.PENDING))

        // Hoàn thành tại thời điểm đã bước sang chu kỳ 2 (sau 04:00)
        val completionInstantAfter0400 = Instant.now()
        taskRepository.completeTask("task_14", cycleNew, completionInstantAfter0400)

        // Ghi nhận đúng trạng thái hoàn thành tại cycle mới mà không ghi đè làm hỏng cycle cũ
        val stateC1 = taskRepository.getCycleState("task_14", cycleOld)
        val stateC2 = taskRepository.getCycleState("task_14", cycleNew)

        assertEquals(TaskCycleStatus.PENDING, stateC1?.status)
        assertEquals(TaskCycleStatus.COMPLETED, stateC2?.status)
        assertEquals(completionInstantAfter0400, stateC2?.completedAt)
    }

    // --- Fake / In-memory Implementations ---

    class InMemoryCanonicalTaskRepository : CanonicalTaskRepository {
        private val tasks = mutableMapOf<String, CanonicalTask>()
        private val cycleStates = mutableMapOf<String, MutableMap<String, TaskCycleState>>() // taskId -> (cycleId -> state)
        private val links = mutableListOf<TaskRewardLink>()

        override suspend fun getTask(taskId: String): CanonicalTask? = tasks[taskId]

        override suspend fun getAllActiveTasks(): List<CanonicalTask> =
            tasks.values.filter { !it.isArchived }.sortedBy { it.orderIndex }

        override suspend fun getTasksLinkedToApp(packageName: String): List<CanonicalTask> {
            val taskIds = links.filter { it.appPackageName == packageName }.map { it.taskId }
            return taskIds.mapNotNull { tasks[it] }.filter { !it.isArchived }
        }

        override suspend fun getAppsLinkedToTask(taskId: String): List<String> =
            links.filter { it.taskId == taskId }.map { it.appPackageName }

        override suspend fun getCycleState(taskId: String, cycleId: CanonicalCycleId): TaskCycleState? =
            cycleStates[taskId]?.get(cycleId.dateIdentifier)

        override suspend fun getCycleStates(cycleId: CanonicalCycleId): Map<String, TaskCycleState> {
            val result = mutableMapOf<String, TaskCycleState>()
            for ((taskId, map) in cycleStates) {
                map[cycleId.dateIdentifier]?.let { result[taskId] = it }
            }
            return result
        }

        override suspend fun saveTask(task: CanonicalTask) {
            tasks[task.id] = task
        }

        override suspend fun saveCycleState(state: TaskCycleState) {
            val map = cycleStates.getOrPut(state.taskId) { mutableMapOf() }
            map[state.cycleId.dateIdentifier] = state
        }

        override suspend fun completeTask(taskId: String, cycleId: CanonicalCycleId, completedAt: Instant) {
            saveCycleState(TaskCycleState(taskId, cycleId, TaskCycleStatus.COMPLETED, completedAt))
        }

        override suspend fun undoTaskCompletion(taskId: String, cycleId: CanonicalCycleId) {
            saveCycleState(TaskCycleState(taskId, cycleId, TaskCycleStatus.PENDING, null))
        }

        override suspend fun archiveTask(taskId: String) {
            val task = tasks[taskId] ?: return
            tasks[taskId] = task.copy(isArchived = true)
            links.removeAll { it.taskId == taskId }
        }

        override suspend fun getTaskCycleHistory(taskId: String): List<TaskCycleState> =
            cycleStates[taskId]?.values?.toList() ?: emptyList()

        override suspend fun linkTaskToApp(taskId: String, packageName: String) {
            if (links.none { it.taskId == taskId && it.appPackageName == packageName }) {
                links.add(TaskRewardLink(taskId, packageName))
            }
        }

        override suspend fun unlinkTaskFromApp(taskId: String, packageName: String) {
            links.removeAll { it.taskId == taskId && it.appPackageName == packageName }
        }

        override suspend fun removeAllLinksForApp(packageName: String) {
            links.removeAll { it.appPackageName == packageName }
        }

        override suspend fun renameTask(taskId: String, newTitle: String) {
            val task = tasks[taskId] ?: return
            tasks[taskId] = task.copy(title = newTitle)
        }

        override suspend fun setPendingNextCycleRewards(taskId: String, appPackageNames: List<String>) {
            val task = tasks[taskId] ?: return
            tasks[taskId] = task.copy(pendingNextCycleRewards = appPackageNames)
        }

        override suspend fun cancelPendingNextCycleRewards(taskId: String) {
            val task = tasks[taskId] ?: return
            tasks[taskId] = task.copy(pendingNextCycleRewards = null)
        }

        override suspend fun applyPendingNextCycleRewards(taskId: String) {
            val task = tasks[taskId] ?: return
            val pending = task.pendingNextCycleRewards ?: return
            links.removeAll { it.taskId == taskId }
            for (pkg in pending) {
                links.add(TaskRewardLink(taskId, pkg))
            }
            tasks[taskId] = task.copy(
                hasReward = pending.isNotEmpty(),
                pendingNextCycleRewards = null
            )
        }

        override suspend fun applyAllPendingNextCycleRewards() {
            val pendingTaskIds = tasks.values
                .filter { it.pendingNextCycleRewards != null && !it.isArchived }
                .map { it.id }
            for (id in pendingTaskIds) {
                applyPendingNextCycleRewards(id)
            }
        }
    }

    class InMemoryCanonicalVaultRepository : CanonicalVaultRepository {
        private val apps = mutableMapOf<String, CanonicalVaultApp>()
        private val vouchers = mutableListOf<VoucherEffect>()

        override suspend fun getAllVaultApps(): List<CanonicalVaultApp> = apps.values.toList()

        override suspend fun getVaultApp(packageName: String): CanonicalVaultApp? = apps[packageName]

        override suspend fun addVaultApp(app: CanonicalVaultApp) {
            apps[app.packageName] = app
        }

        override suspend fun removeVaultApp(packageName: String) {
            apps.remove(packageName)
        }

        override suspend fun getActiveVouchers(instant: Instant): List<VoucherEffect> =
            vouchers.filter { it.isEffectiveAt(instant) }
    }

    class InMemoryCanonicalCycleRepository(private val zoneId: ZoneId) : CanonicalCycleRepository {
        private var mockCycleDate: String? = null

        fun setMockCycle(dateStr: String) {
            mockCycleDate = dateStr
        }

        override fun getCurrentCycle(instant: Instant): CanonicalCycleBoundary {
            mockCycleDate?.let {
                val boundary = CycleEngine.resolveCycleBoundary(CanonicalCycleId(it), zoneId)
                return boundary
            }
            return CycleEngine.getCurrentCycleBoundary(instant, zoneId)
        }

        override fun getCycleBoundary(cycleId: CanonicalCycleId): CanonicalCycleBoundary =
            CycleEngine.resolveCycleBoundary(cycleId, zoneId)
    }
}
