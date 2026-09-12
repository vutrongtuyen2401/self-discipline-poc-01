package com.example.selfdisciplinepoc01.domain.canonical.task

import com.example.selfdisciplinepoc01.domain.canonical.cycle.CanonicalCycleId
import com.example.selfdisciplinepoc01.domain.canonical.repository.CanonicalLockEvaluator
import com.example.selfdisciplinepoc01.domain.canonical.repository.CanonicalTaskRepository
import com.example.selfdisciplinepoc01.domain.canonical.repository.CanonicalVaultRepository
import com.example.selfdisciplinepoc01.domain.canonical.usecase.AddVaultAppUseCase
import com.example.selfdisciplinepoc01.domain.canonical.usecase.CompleteTaskUseCase
import com.example.selfdisciplinepoc01.domain.canonical.usecase.CreateTaskUseCase
import com.example.selfdisciplinepoc01.domain.canonical.usecase.DeleteTaskUseCase
import com.example.selfdisciplinepoc01.domain.canonical.usecase.RemoveVaultAppUseCase
import com.example.selfdisciplinepoc01.domain.canonical.usecase.UndoTaskUseCase
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

/**
 * Suite kiểm thử chấp nhận (Acceptance Tests) cho Canonical Task Lifecycle & Reward Relationships.
 *
 * Tiêu chí SSOT:
 * 1. Task definition survives cycle boundary
 * 2. Cycle A COMPLETED does not complete Cycle B
 * 3. Cycle B starts PENDING
 * 4. Completion affects current cycle only
 * 5. Undo affects current cycle only
 * 6. Rewardless task remains excluded from N (INV-TASK-001)
 * 7. Deleting reward app preserves Task
 * 8. Deleting reward app removes reward link
 * 9. Re-adding app does not restore old link
 * 10. Deleting Task does not erase historical state
 */
class CanonicalTaskLifecycleTest {

    private val cycleA = CanonicalCycleId("2026-09-10")
    private val cycleB = CanonicalCycleId("2026-09-11")
    private val testAppPkg = "com.facebook.katana"

    // In-memory fake implementations for pure domain testing
    private lateinit var fakeTaskRepo: FakeTaskRepository
    private lateinit var fakeVaultRepo: FakeVaultRepository
    private lateinit var fakeLockEvaluator: FakeLockEvaluator

    private class FakeTaskRepository : CanonicalTaskRepository {
        val tasks = mutableMapOf<String, CanonicalTask>()
        val cycleStates = mutableMapOf<Pair<String, String>, TaskCycleState>() // (taskId, cycleId) -> state
        val links = mutableListOf<TaskRewardLink>()

        override suspend fun getTask(taskId: String): CanonicalTask? = tasks[taskId]
        override suspend fun getAllActiveTasks(): List<CanonicalTask> = tasks.values.filter { !it.isArchived }
        override suspend fun getTasksLinkedToApp(packageName: String): List<CanonicalTask> {
            val taskIds = links.filter { it.appPackageName == packageName }.map { it.taskId }
            return taskIds.mapNotNull { tasks[it] }.filter { !it.isArchived }
        }
        override suspend fun getAppsLinkedToTask(taskId: String): List<String> {
            return links.filter { it.taskId == taskId }.map { it.appPackageName }
        }
        override suspend fun getCycleState(taskId: String, cycleId: CanonicalCycleId): TaskCycleState? {
            return cycleStates[taskId to cycleId.dateIdentifier]
        }
        override suspend fun getCycleStates(cycleId: CanonicalCycleId): Map<String, TaskCycleState> {
            return cycleStates.filter { it.key.second == cycleId.dateIdentifier }
                .mapKeys { it.key.first }
        }
        override suspend fun saveTask(task: CanonicalTask) { tasks[task.id] = task }
        override suspend fun saveCycleState(state: TaskCycleState) {
            cycleStates[state.taskId to state.cycleId.dateIdentifier] = state
        }
        override suspend fun completeTask(taskId: String, cycleId: CanonicalCycleId, completedAt: Instant) {
            cycleStates[taskId to cycleId.dateIdentifier] = TaskCycleState(
                taskId = taskId,
                cycleId = cycleId,
                status = TaskCycleStatus.COMPLETED,
                completedAt = completedAt
            )
        }
        override suspend fun undoTaskCompletion(taskId: String, cycleId: CanonicalCycleId) {
            cycleStates[taskId to cycleId.dateIdentifier] = TaskCycleState(
                taskId = taskId,
                cycleId = cycleId,
                status = TaskCycleStatus.PENDING,
                completedAt = null
            )
        }
        override suspend fun archiveTask(taskId: String) {
            tasks[taskId]?.let { tasks[taskId] = it.copy(isArchived = true) }
            links.removeAll { it.taskId == taskId }
            // Bảo toàn cycleStates
        }
        override suspend fun getTaskCycleHistory(taskId: String): List<TaskCycleState> {
            return cycleStates.filter { it.key.first == taskId }.values.toList()
        }
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
            tasks[taskId]?.let { tasks[taskId] = it.copy(title = newTitle) }
        }
        override suspend fun setPendingNextCycleRewards(taskId: String, appPackageNames: List<String>) {
            tasks[taskId]?.let { tasks[taskId] = it.copy(pendingNextCycleRewards = appPackageNames) }
        }
        override suspend fun cancelPendingNextCycleRewards(taskId: String) {
            tasks[taskId]?.let { tasks[taskId] = it.copy(pendingNextCycleRewards = null) }
        }
        override suspend fun applyPendingNextCycleRewards(taskId: String) {
            val task = tasks[taskId] ?: return
            val pending = task.pendingNextCycleRewards ?: return
            links.removeAll { it.taskId == taskId }
            for (pkg in pending) {
                links.add(TaskRewardLink(taskId, pkg))
            }
            tasks[taskId] = task.copy(hasReward = pending.isNotEmpty(), pendingNextCycleRewards = null)
        }
        override suspend fun applyAllPendingNextCycleRewards() {
            val pendingIds = tasks.values.filter { it.pendingNextCycleRewards != null && !it.isArchived }.map { it.id }
            for (id in pendingIds) applyPendingNextCycleRewards(id)
        }
    }

    private class FakeVaultRepository : CanonicalVaultRepository {
        val apps = mutableMapOf<String, CanonicalVaultApp>()
        val vouchers = mutableListOf<VoucherEffect>()

        override suspend fun getAllVaultApps(): List<CanonicalVaultApp> = apps.values.toList()
        override suspend fun getVaultApp(packageName: String): CanonicalVaultApp? = apps[packageName]
        override suspend fun addVaultApp(app: CanonicalVaultApp) { apps[app.packageName] = app }
        override suspend fun removeVaultApp(packageName: String) { apps.remove(packageName) }
        override suspend fun getActiveVouchers(instant: Instant): List<VoucherEffect> {
            return vouchers.filter { it.isEffectiveAt(instant) }
        }
    }

    private class FakeLockEvaluator(
        private val taskRepo: FakeTaskRepository,
        private val vaultRepo: FakeVaultRepository
    ) : CanonicalLockEvaluator {
        var currentCycleId = CanonicalCycleId("2026-09-11")

        override suspend fun evaluateApp(
            packageName: String,
            evaluationInstant: Instant
        ): LockEvaluationResult {
            if (vaultRepo.getVaultApp(packageName) == null) {
                return LockEvaluationResult(
                    packageName = packageName,
                    decision = CanonicalLockDecision.UNLOCKED,
                    reason = "NOT_IN_VAULT",
                    totalLinkedRewardTasks = 0,
                    completedLinkedRewardTasks = 0,
                    requiredCompletions = 0
                )
            }
            val linkedTasks = taskRepo.getTasksLinkedToApp(packageName)
            val states = taskRepo.getCycleStates(currentCycleId)
            val vouchers = vaultRepo.getActiveVouchers(evaluationInstant)
            return CanonicalLockPolicy.evaluateLock(
                packageName = packageName,
                linkedTasks = linkedTasks,
                cycleTaskStates = states,
                activeVouchers = vouchers,
                evaluationInstant = evaluationInstant
            )
        }
    }

    @Before
    fun setUp() {
        fakeTaskRepo = FakeTaskRepository()
        fakeVaultRepo = FakeVaultRepository()
        fakeLockEvaluator = FakeLockEvaluator(fakeTaskRepo, fakeVaultRepo)
    }

    @Test
    fun test01_taskDefinitionSurvivesCycleBoundary() = runBlocking {
        val task = CanonicalTask(id = "task1", title = "Học tập 1 giờ", hasReward = true)
        fakeTaskRepo.saveTask(task)

        // Ở chu kỳ A hay chu kỳ B, định nghĩa nhiệm vụ vẫn tồn tại nguyên vẹn
        val retrieved = fakeTaskRepo.getTask("task1")
        assertNotNull(retrieved)
        assertEquals("task1", retrieved?.id)
        assertEquals("Học tập 1 giờ", retrieved?.title)
        assertFalse(retrieved!!.isArchived)
    }

    @Test
    fun test02_cycleACompletedDoesNotCompleteCycleB() = runBlocking {
        val task = CanonicalTask(id = "task2", title = "Chạy bộ", hasReward = true)
        fakeTaskRepo.saveTask(task)

        // Hoàn thành ở chu kỳ A
        fakeTaskRepo.completeTask("task2", cycleA)

        val stateA = fakeTaskRepo.getCycleState("task2", cycleA)
        val stateB = fakeTaskRepo.getCycleState("task2", cycleB)

        // Chu kỳ A đã hoàn thành
        assertNotNull(stateA)
        assertTrue(stateA!!.isCompleted)

        // Chu kỳ B chưa có trạng thái hoặc mặc định PENDING
        assertTrue("Chu kỳ B không bị ảnh hưởng bởi hoàn thành của chu kỳ A", stateB == null || !stateB.isCompleted)
    }

    @Test
    fun test03_completionAffectsCurrentCycleOnly() = runBlocking {
        val completeUseCase = CompleteTaskUseCase(fakeTaskRepo, fakeLockEvaluator)
        val task = CanonicalTask(id = "task3", title = "Đọc sách", hasReward = true)
        fakeTaskRepo.saveTask(task)

        fakeLockEvaluator.currentCycleId = cycleB
        completeUseCase(taskId = "task3", cycleId = cycleB)

        val stateB = fakeTaskRepo.getCycleState("task3", cycleB)
        val stateA = fakeTaskRepo.getCycleState("task3", cycleA)

        assertTrue("Chu kỳ B phải COMPLETED", stateB?.isCompleted == true)
        assertNull("Chu kỳ A không được bị đánh dấu hoàn thành", stateA)
    }

    @Test
    fun test04_undoAffectsCurrentCycleOnly() = runBlocking {
        val completeUseCase = CompleteTaskUseCase(fakeTaskRepo, fakeLockEvaluator)
        val undoUseCase = UndoTaskUseCase(fakeTaskRepo, fakeLockEvaluator)
        val task = CanonicalTask(id = "task4", title = "Viết nhật ký", hasReward = true)
        fakeTaskRepo.saveTask(task)

        // Hoàn thành cả 2 chu kỳ
        fakeTaskRepo.completeTask("task4", cycleA)
        fakeTaskRepo.completeTask("task4", cycleB)

        // Undo chỉ ở chu kỳ B
        undoUseCase(taskId = "task4", cycleId = cycleB)

        val stateB = fakeTaskRepo.getCycleState("task4", cycleB)
        val stateA = fakeTaskRepo.getCycleState("task4", cycleA)

        assertFalse("Chu kỳ B sau khi undo phải PENDING", stateB?.isCompleted == true)
        assertTrue("Chu kỳ A vẫn phải giữ nguyên COMPLETED", stateA?.isCompleted == true)
    }

    @Test
    fun test05_rewardlessTaskRemainsExcludedFromN() = runBlocking {
        val taskWithReward = CanonicalTask(id = "t_reward", title = "Task có thưởng", hasReward = true)
        val taskNoReward = CanonicalTask(id = "t_story", title = "Task cốt truyện", hasReward = false)

        fakeTaskRepo.saveTask(taskWithReward)
        fakeTaskRepo.saveTask(taskNoReward)
        fakeVaultRepo.addVaultApp(CanonicalVaultApp(testAppPkg, "Test App"))

        fakeTaskRepo.linkTaskToApp("t_reward", testAppPkg)
        fakeTaskRepo.linkTaskToApp("t_story", testAppPkg)

        val evalResult = fakeLockEvaluator.evaluateApp(testAppPkg, Instant.now())
        // Mẫu số N chỉ tính task có thưởng (N=1), bỏ qua task cốt truyện
        assertEquals("Mẫu số N phải loại trừ rewardless task theo INV-TASK-001", 1, evalResult.totalLinkedRewardTasks)
        assertEquals(1, evalResult.requiredCompletions)
    }

    @Test
    fun test06_deletingRewardAppPreservesTaskAndRemovesLinks() = runBlocking {
        val removeAppUseCase = RemoveVaultAppUseCase(fakeVaultRepo, fakeTaskRepo)
        val task = CanonicalTask(id = "t_shared", title = "Task dùng chung", hasReward = true)
        fakeTaskRepo.saveTask(task)

        val app1 = CanonicalVaultApp("com.app.one", "App 1")
        val app2 = CanonicalVaultApp("com.app.two", "App 2")
        fakeVaultRepo.addVaultApp(app1)
        fakeVaultRepo.addVaultApp(app2)

        fakeTaskRepo.linkTaskToApp("t_shared", "com.app.one")
        fakeTaskRepo.linkTaskToApp("t_shared", "com.app.two")

        // Xóa App 1 khỏi Vault
        removeAppUseCase("com.app.one")

        // 1. App 1 bị xóa khỏi Vault
        assertNull(fakeVaultRepo.getVaultApp("com.app.one"))
        // 2. Liên kết của App 1 bị xóa
        val linksForApp1 = fakeTaskRepo.getTasksLinkedToApp("com.app.one")
        assertTrue("Liên kết của App 1 phải bị xóa sạch", linksForApp1.isEmpty())

        // 3. Bản thân Task vẫn tồn tại nguyên vẹn
        val preservedTask = fakeTaskRepo.getTask("t_shared")
        assertNotNull("Task phải được giữ nguyên", preservedTask)

        // 4. Liên kết của App 2 vẫn nguyên vẹn
        val linksForApp2 = fakeTaskRepo.getTasksLinkedToApp("com.app.two")
        assertEquals(1, linksForApp2.size)
        assertEquals("t_shared", linksForApp2[0].id)
    }

    @Test
    fun test07_reAddingAppDoesNotRestoreOldLinks() = runBlocking {
        val removeAppUseCase = RemoveVaultAppUseCase(fakeVaultRepo, fakeTaskRepo)
        val addAppUseCase = AddVaultAppUseCase(fakeVaultRepo, fakeLockEvaluator)

        val task = CanonicalTask(id = "t_temp", title = "Task tạm", hasReward = true)
        fakeTaskRepo.saveTask(task)

        // Thêm app lần đầu và liên kết
        addAppUseCase("com.test.readd", "Readd App")
        fakeTaskRepo.linkTaskToApp("t_temp", "com.test.readd")
        assertEquals(1, fakeTaskRepo.getTasksLinkedToApp("com.test.readd").size)

        // Xóa app
        removeAppUseCase("com.test.readd")
        assertTrue(fakeTaskRepo.getTasksLinkedToApp("com.test.readd").isEmpty())

        // Thêm lại app lần 2
        val evalResult = addAppUseCase("com.test.readd", "Readd App")

        // Invariant SSOT: Thêm lại app KHÔNG tự khôi phục liên kết cũ!
        val currentLinks = fakeTaskRepo.getTasksLinkedToApp("com.test.readd")
        assertTrue("Thêm lại app không được tự khôi phục liên kết cũ", currentLinks.isEmpty())
        assertEquals(0, evalResult.totalLinkedRewardTasks)
        assertTrue("N=0 app mới thêm phải ở trạng thái UNLOCKED", evalResult.isUnlocked)
    }

    @Test
    fun test08_deletingTaskDoesNotEraseHistoricalState() = runBlocking {
        val deleteTaskUseCase = DeleteTaskUseCase(fakeTaskRepo, fakeLockEvaluator)
        val task = CanonicalTask(id = "t_history", title = "Task có lịch sử", hasReward = true)
        fakeTaskRepo.saveTask(task)

        // Ghi nhận hoàn thành ở chu kỳ A và chu kỳ B
        fakeTaskRepo.completeTask("t_history", cycleA)
        fakeTaskRepo.completeTask("t_history", cycleB)

        // Xóa task qua DeleteTaskUseCase
        deleteTaskUseCase("t_history")

        // 1. Task definition chuyển sang isArchived = true (hoặc không còn active)
        val activeTasks = fakeTaskRepo.getAllActiveTasks()
        assertTrue("Task bị xóa không được xuất hiện trong active tasks", activeTasks.none { it.id == "t_history" })
        assertTrue("Task phải được đánh dấu isArchived = true", fakeTaskRepo.getTask("t_history")?.isArchived == true)

        // 2. Lịch sử chu kỳ TaskCycleState PHẢI CÒN NGUYÊN VẸN!
        val history = fakeTaskRepo.getTaskCycleHistory("t_history")
        assertEquals("Lịch sử chu kỳ không được phép bị xóa khi xóa task", 2, history.size)
        assertTrue(history.any { it.cycleId == cycleA && it.isCompleted })
        assertTrue(history.any { it.cycleId == cycleB && it.isCompleted })
    }
}
