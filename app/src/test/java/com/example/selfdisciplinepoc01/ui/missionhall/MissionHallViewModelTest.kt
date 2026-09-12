package com.example.selfdisciplinepoc01.ui.missionhall

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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class MissionHallViewModelTest {

    private lateinit var taskRepo: FakeTaskRepo
    private lateinit var vaultRepo: FakeVaultRepo
    private lateinit var cycleRepo: FakeCycleRepo
    private lateinit var lockEvaluator: CanonicalLockEvaluator

    private fun createViewModel(): MissionHallViewModel {
        return MissionHallViewModel(
            getTasksUseCase = GetMissionHallTasksCanonicalUseCase(taskRepo, vaultRepo, cycleRepo),
            createTaskUseCase = CreateTaskUseCase(taskRepo),
            renameTaskUseCase = RenameTaskUseCase(taskRepo),
            completeTaskUseCase = CompleteTaskUseCase(taskRepo, lockEvaluator),
            undoTaskUseCase = UndoTaskUseCase(taskRepo, lockEvaluator),
            deleteTaskUseCase = DeleteTaskUseCase(taskRepo, lockEvaluator),
            updateRewardLinkageUseCase = UpdateTaskRewardLinkageUseCase(taskRepo, lockEvaluator),
            vaultRepository = vaultRepo,
            cycleRepository = cycleRepo
        )
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        taskRepo = FakeTaskRepo()
        vaultRepo = FakeVaultRepo()
        cycleRepo = FakeCycleRepo()
        lockEvaluator = object : CanonicalLockEvaluator {
            override suspend fun evaluateApp(
                packageName: String,
                evaluationInstant: Instant
            ): LockEvaluationResult {
                val cycle = cycleRepo.getCurrentCycle(evaluationInstant)
                val linked = taskRepo.getTasksLinkedToApp(packageName)
                val states = taskRepo.getCycleStates(cycle.cycleId)
                return CanonicalLockPolicy.evaluateLock(
                    packageName = packageName,
                    linkedTasks = linked,
                    cycleTaskStates = states,
                    activeVouchers = emptyList(),
                    evaluationInstant = evaluationInstant
                )
            }
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testViewModel_initialState_isEmptyAndNotLoading() = runBlocking {
        val viewModel = createViewModel()
        val state = withTimeout(3000) {
            viewModel.uiState.first { !it.isLoading }
        }
        assertFalse(state.isLoading)
        assertTrue(state.isEmpty)
        assertFalse(state.isAddingTask)
        assertNull(state.inputError)
    }

    @Test
    fun testViewModel_openAndDismissDialog() = runBlocking {
        val viewModel = createViewModel()
        withTimeout(3000) { viewModel.uiState.first { !it.isLoading } }

        viewModel.onOpenAddDialog()
        assertTrue(viewModel.uiState.value.isAddingTask)

        viewModel.onInputNameChanged("Tập chống đẩy")
        assertEquals("Tập chống đẩy", viewModel.uiState.value.currentInputName)

        viewModel.onDismissAddDialog()
        assertFalse(viewModel.uiState.value.isAddingTask)
        assertEquals("", viewModel.uiState.value.currentInputName)
    }

    @Test
    fun testViewModel_addTask_emptyName_showsError() = runBlocking {
        val viewModel = createViewModel()
        withTimeout(3000) { viewModel.uiState.first { !it.isLoading } }

        viewModel.onOpenAddDialog()
        viewModel.onInputNameChanged("   ")
        viewModel.onConfirmAddTask()

        val state = withTimeout(3000) {
            viewModel.uiState.first { it.inputError != null }
        }
        assertTrue(state.isAddingTask)
        assertNotNull(state.inputError)
        assertEquals("Tên nhiệm vụ không được để trống", state.inputError)
    }

    @Test
    fun testViewModel_addTask_validName_addsToTasks() = runBlocking {
        val viewModel = createViewModel()
        withTimeout(3000) { viewModel.uiState.first { !it.isLoading } }

        viewModel.onOpenAddDialog()
        viewModel.onInputNameChanged("Đọc 20 trang sách")
        viewModel.onConfirmAddTask()

        val state = withTimeout(3000) {
            viewModel.uiState.first { !it.isAddingTask && it.totalActiveCount == 1 }
        }
        assertFalse(state.isAddingTask)
        assertNull(state.inputError)
        assertEquals(1, state.totalActiveCount)
        assertEquals("Đọc 20 trang sách", state.currentTask?.task?.title)
    }

    @Test
    fun testViewModel_renameTask_updatesTitleImmediately() = runBlocking {
        val viewModel = createViewModel()
        withTimeout(3000) { viewModel.uiState.first { !it.isLoading } }

        viewModel.onOpenAddDialog()
        viewModel.onInputNameChanged("Tên ban đầu")
        viewModel.onConfirmAddTask()

        val taskItem = withTimeout(3000) {
            viewModel.uiState.first { it.currentTask != null }.currentTask!!
        }

        viewModel.onOpenRenameDialog(taskItem)
        viewModel.onRenameInputChanged("Tên mới sau khi đổi")
        viewModel.onConfirmRename()

        val stateAfter = withTimeout(3000) {
            viewModel.uiState.first { it.currentTask?.task?.title == "Tên mới sau khi đổi" }
        }
        assertEquals("Tên mới sau khi đổi", stateAfter.currentTask?.task?.title)
    }

    @Test
    fun testViewModel_completeTask_updatesCompletionState() = runBlocking {
        val viewModel = createViewModel()
        withTimeout(3000) { viewModel.uiState.first { !it.isLoading } }

        viewModel.onOpenAddDialog()
        viewModel.onInputNameChanged("Task 1")
        viewModel.onConfirmAddTask()
        withTimeout(3000) { viewModel.uiState.first { it.totalActiveCount == 1 } }

        viewModel.onOpenAddDialog()
        viewModel.onInputNameChanged("Task 2")
        viewModel.onConfirmAddTask()
        withTimeout(3000) { viewModel.uiState.first { it.totalActiveCount == 2 } }

        val task1 = viewModel.uiState.value.currentTask!!
        assertEquals("Task 1", task1.task.title)

        // Complete Task 1
        viewModel.onCompleteTask(task1.task.id)
        val stateAfter = withTimeout(3000) {
            viewModel.uiState.first { it.completedCount == 1 }
        }
        assertEquals(1, stateAfter.completedCount)
        assertEquals("Task 2", stateAfter.currentTask?.task?.title)
    }

    @Test
    fun testViewModel_undoTask_withConfirmation_revertsCompletion() = runBlocking {
        val viewModel = createViewModel()
        withTimeout(3000) { viewModel.uiState.first { !it.isLoading } }

        viewModel.onOpenAddDialog()
        viewModel.onInputNameChanged("Task can hoan tac")
        viewModel.onConfirmAddTask()
        val taskItem = withTimeout(3000) { viewModel.uiState.first { it.currentTask != null }.currentTask!! }

        viewModel.onCompleteTask(taskItem.task.id)
        withTimeout(3000) { viewModel.uiState.first { it.completedCount == 1 } }

        val completedItem = viewModel.uiState.value.completedTasks.first()

        // Bấm hoàn tác -> hiện dialog xác nhận
        viewModel.onPromptUndoTask(completedItem)
        assertNotNull(viewModel.uiState.value.taskPendingUndo)

        // Xác nhận hoàn tác
        viewModel.onConfirmUndoTask()
        val stateAfterUndo = withTimeout(3000) {
            viewModel.uiState.first { it.completedCount == 0 && it.taskPendingUndo == null }
        }
        assertEquals(0, stateAfterUndo.completedCount)
        assertEquals(1, stateAfterUndo.incompleteTasks.size)
    }

    @Test
    fun testViewModel_deleteTask_withConfirmation_archivesTask() = runBlocking {
        val viewModel = createViewModel()
        withTimeout(3000) { viewModel.uiState.first { !it.isLoading } }

        viewModel.onOpenAddDialog()
        viewModel.onInputNameChanged("Task to delete")
        viewModel.onConfirmAddTask()
        val taskItem = withTimeout(3000) { viewModel.uiState.first { it.currentTask != null }.currentTask!! }

        // Bấm xóa -> hiện dialog xác nhận
        viewModel.onPromptDeleteTask(taskItem)
        assertNotNull(viewModel.uiState.value.taskPendingDelete)

        // Xác nhận xóa
        viewModel.onConfirmDeleteTask()
        val stateAfter = withTimeout(3000) {
            viewModel.uiState.first { it.totalActiveCount == 0 && it.taskPendingDelete == null }
        }
        assertEquals(0, stateAfter.totalActiveCount)
        assertNull(stateAfter.currentTask)
    }

    @Test
    fun testViewModel_linkTaskToVaultApps_success() = runBlocking {
        val viewModel = createViewModel()
        withTimeout(3000) { viewModel.uiState.first { !it.isLoading } }

        // Setup 1 task and 2 apps in Vault
        val task = CanonicalTask("task_a", "Task A")
        taskRepo.saveTask(task)
        vaultRepo.addVaultApp(CanonicalVaultApp("com.android.chrome", "Chrome"))
        vaultRepo.addVaultApp(CanonicalVaultApp("com.facebook.katana", "Facebook"))
        viewModel.refresh()

        val currentTask = withTimeout(3000) { viewModel.uiState.first { it.currentTask != null } }.currentTask!!

        viewModel.onOpenLinkageDialog(currentTask)
        val dialogState = withTimeout(3000) {
            viewModel.uiState.first { it.taskSelectedForLinkage != null && it.availableVaultApps.size == 2 }
        }
        assertEquals(2, dialogState.availableVaultApps.size)
        assertTrue(dialogState.selectedPackageNames.isEmpty())

        // Select Chrome
        viewModel.onToggleAppSelection("com.android.chrome")
        assertTrue(viewModel.uiState.value.selectedPackageNames.contains("com.android.chrome"))

        // Save
        viewModel.onSaveTaskLinkage()
        val stateAfterSave = withTimeout(3000) {
            viewModel.uiState.first { it.taskSelectedForLinkage == null && it.currentTask?.linkedApps?.size == 1 }
        }
        assertNull(stateAfterSave.taskSelectedForLinkage)
        assertEquals(1, stateAfterSave.currentTask?.linkedApps?.size)
        assertEquals("com.android.chrome", stateAfterSave.currentTask?.linkedApps?.first()?.packageName)
    }

    @Test
    fun testViewModel_pendingNextCycleRewards_setAndCancel() = runBlocking {
        val viewModel = createViewModel()
        withTimeout(3000) { viewModel.uiState.first { !it.isLoading } }

        val task = CanonicalTask("task_pending", "Task Pending")
        taskRepo.saveTask(task)
        vaultRepo.addVaultApp(CanonicalVaultApp("com.target.app", "Target App"))
        viewModel.refresh()

        val currentTask = withTimeout(3000) { viewModel.uiState.first { it.currentTask != null } }.currentTask!!

        viewModel.onOpenLinkageDialog(currentTask)
        viewModel.onToggleAppSelection("com.target.app")
        viewModel.onTogglePendingNextCycle(true) // Chọn chế độ pending next cycle

        viewModel.onSaveTaskLinkage()
        val stateAfter = withTimeout(3000) {
            viewModel.uiState.first { it.currentTask?.pendingNextCycleRewardPackageNames != null }
        }
        assertEquals(listOf("com.target.app"), stateAfter.currentTask?.pendingNextCycleRewardPackageNames)

        // Hủy pending
        viewModel.onCancelPendingRewards("task_pending")
        val stateAfterCancel = withTimeout(3000) {
            viewModel.uiState.first { it.currentTask?.pendingNextCycleRewardPackageNames == null }
        }
        assertNull(stateAfterCancel.currentTask?.pendingNextCycleRewardPackageNames)
    }

    // --- In-memory Fake Repos ---

    class FakeTaskRepo : CanonicalTaskRepository {
        private val tasks = mutableMapOf<String, CanonicalTask>()
        private val cycleStates = mutableMapOf<String, MutableMap<String, TaskCycleState>>()
        private val links = mutableListOf<TaskRewardLink>()

        override suspend fun getTask(taskId: String): CanonicalTask? = tasks[taskId]
        override suspend fun getAllActiveTasks(): List<CanonicalTask> =
            tasks.values.filter { !it.isArchived }.sortedBy { it.orderIndex }
        override suspend fun getTasksLinkedToApp(packageName: String): List<CanonicalTask> =
            links.filter { it.appPackageName == packageName }.mapNotNull { tasks[it.taskId] }.filter { !it.isArchived }
        override suspend fun getAppsLinkedToTask(taskId: String): List<String> =
            links.filter { it.taskId == taskId }.map { it.appPackageName }
        override suspend fun getCycleState(taskId: String, cycleId: CanonicalCycleId): TaskCycleState? =
            cycleStates[taskId]?.get(cycleId.dateIdentifier)
        override suspend fun getCycleStates(cycleId: CanonicalCycleId): Map<String, TaskCycleState> {
            val res = mutableMapOf<String, TaskCycleState>()
            for ((t, map) in cycleStates) {
                map[cycleId.dateIdentifier]?.let { res[t] = it }
            }
            return res
        }
        override suspend fun saveTask(task: CanonicalTask) { tasks[task.id] = task }
        override suspend fun saveCycleState(state: TaskCycleState) {
            cycleStates.getOrPut(state.taskId) { mutableMapOf() }[state.cycleId.dateIdentifier] = state
        }
        override suspend fun completeTask(taskId: String, cycleId: CanonicalCycleId, completedAt: Instant) {
            saveCycleState(TaskCycleState(taskId, cycleId, TaskCycleStatus.COMPLETED, completedAt))
        }
        override suspend fun undoTaskCompletion(taskId: String, cycleId: CanonicalCycleId) {
            saveCycleState(TaskCycleState(taskId, cycleId, TaskCycleStatus.PENDING, null))
        }
        override suspend fun archiveTask(taskId: String) {
            tasks[taskId] = tasks[taskId]!!.copy(isArchived = true)
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

    class FakeVaultRepo : CanonicalVaultRepository {
        private val apps = mutableMapOf<String, CanonicalVaultApp>()
        override suspend fun getAllVaultApps(): List<CanonicalVaultApp> = apps.values.toList()
        override suspend fun getVaultApp(packageName: String): CanonicalVaultApp? = apps[packageName]
        override suspend fun addVaultApp(app: CanonicalVaultApp) { apps[app.packageName] = app }
        override suspend fun removeVaultApp(packageName: String) { apps.remove(packageName) }
        override suspend fun getActiveVouchers(instant: Instant): List<VoucherEffect> = emptyList()
    }

    class FakeCycleRepo : CanonicalCycleRepository {
        private val zone = ZoneId.of("Asia/Ho_Chi_Minh")
        override fun getCurrentCycle(instant: Instant): CanonicalCycleBoundary =
            CycleEngine.getCurrentCycleBoundary(instant, zone)
        override fun getCycleBoundary(cycleId: CanonicalCycleId): CanonicalCycleBoundary =
            CycleEngine.resolveCycleBoundary(cycleId, zone)
    }
}
