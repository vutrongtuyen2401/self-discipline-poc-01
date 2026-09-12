package com.example.selfdisciplinepoc01.domain.canonical.correction

import com.example.selfdisciplinepoc01.domain.canonical.cycle.CanonicalCycleId
import com.example.selfdisciplinepoc01.domain.canonical.missionhall.MissionHallCanonicalIntegrationTest
import com.example.selfdisciplinepoc01.domain.canonical.repository.CanonicalLockEvaluator
import com.example.selfdisciplinepoc01.domain.canonical.task.CanonicalTask
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
import com.example.selfdisciplinepoc01.ui.missionhall.MissionHallUiState
import com.example.selfdisciplinepoc01.ui.missionhall.MissionHallViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
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

/**
 * Suite kiểm thử chấp nhận Phase 2B-A Correction:
 * Mission Hall / System Panel Completion Boundary theo SSOT.
 *
 * Yêu cầu kiểm thử:
 * - CORR-A01: Critical Bypass Test — Mission Hall không thể dùng để unlock app thông qua completion.
 * - CORR-A02: System Panel Completion Test — System Panel là completion entry point trong blocked-app flow.
 * - CORR-A03: No Standalone Completion Test — Audit Mission Hall không có standalone completion mutation/confirmation.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MissionHallSystemPanelCorrectionTest {

    private lateinit var taskRepository: MissionHallCanonicalIntegrationTest.InMemoryCanonicalTaskRepository
    private lateinit var vaultRepository: MissionHallCanonicalIntegrationTest.InMemoryCanonicalVaultRepository
    private lateinit var cycleRepository: MissionHallCanonicalIntegrationTest.InMemoryCanonicalCycleRepository
    private lateinit var lockEvaluator: CanonicalLockEvaluator

    private val testZone = ZoneId.of("Asia/Ho_Chi_Minh")

    @Before
    fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        taskRepository = MissionHallCanonicalIntegrationTest.InMemoryCanonicalTaskRepository()
        vaultRepository = MissionHallCanonicalIntegrationTest.InMemoryCanonicalVaultRepository()
        cycleRepository = MissionHallCanonicalIntegrationTest.InMemoryCanonicalCycleRepository(testZone)
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

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createMissionHallViewModel(): MissionHallViewModel {
        return MissionHallViewModel(
            getTasksUseCase = GetMissionHallTasksCanonicalUseCase(taskRepository, vaultRepository, cycleRepository),
            createTaskUseCase = CreateTaskUseCase(taskRepository),
            renameTaskUseCase = RenameTaskUseCase(taskRepository),
            undoTaskUseCase = UndoTaskUseCase(taskRepository, lockEvaluator),
            deleteTaskUseCase = DeleteTaskUseCase(taskRepository, lockEvaluator),
            updateRewardLinkageUseCase = UpdateTaskRewardLinkageUseCase(taskRepository, lockEvaluator),
            vaultRepository = vaultRepository,
            cycleRepository = cycleRepository
        )
    }

    /**
     * CORR-A01: Critical Bypass Test
     * Scenario:
     * - App có 1 reward-bearing Task.
     * - Task đang PENDING (K = 0, Required(1) = 1).
     * - App đang LOCKED.
     * Action:
     * - Mở Mission Hall (khởi tạo ViewModel và lấy state UI).
     * - Kiểm tra các hành động của Mission Hall.
     * Expected:
     * - Không có completion action có thể mutate TaskCycleState.
     * - Task vẫn PENDING.
     * - App vẫn LOCKED.
     */
    @Test
    fun testCORR_A01_criticalBypass_missionHallCannotCompleteTaskToUnlockApp() = runBlocking {
        val targetPackage = "com.cloudflare.onedotonedotonedotone"
        vaultRepository.addVaultApp(CanonicalVaultApp(targetPackage, "1.1.1.1"))

        val task = CanonicalTask(id = "task_meditation", title = "Ngồi Thiền 30 Phút")
        taskRepository.saveTask(task)
        taskRepository.linkTaskToApp(task.id, targetPackage)

        val cycle = cycleRepository.getCurrentCycle()

        // 1. Kiểm tra ban đầu: Task PENDING, App LOCKED
        val stateInitial = taskRepository.getCycleState(task.id, cycle.cycleId)
        val isCompletedInitial = stateInitial?.isCompleted ?: false
        assertFalse("Task phải ở trạng thái PENDING ban đầu", isCompletedInitial)

        val evalInitial = lockEvaluator.evaluateApp(targetPackage)
        assertTrue("App phải đang LOCKED (K=0 < Required=1)", evalInitial.isLocked)
        assertEquals(1, evalInitial.totalLinkedRewardTasks)
        assertEquals(0, evalInitial.completedLinkedRewardTasks)
        assertEquals(1, evalInitial.requiredCompletions)

        // 2. Mở Mission Hall
        val viewModel = createMissionHallViewModel()
        val uiState = viewModel.uiState.first { !it.isLoading }

        // 3. Xác nhận Mission Hall hiển thị task nhưng không có completion mutation entry point
        assertEquals(1, uiState.totalActiveCount)
        assertEquals(0, uiState.completedCount)
        assertEquals("Ngồi Thiền 30 Phút", uiState.currentTask?.task?.title)

        // 4. Kiểm tra qua Reflection: MissionHallViewModel không có hàm complete nào
        val methodNames = viewModel.javaClass.declaredMethods.map { it.name }
        assertFalse(
            "MissionHallViewModel tuyệt đối không được có method completeTask/onCompleteTask",
            methodNames.any { it.contains("complete", ignoreCase = true) }
        )

        // 5. Sau khi tương tác với Mission Hall, Task vẫn PENDING và App vẫn LOCKED
        val stateAfter = taskRepository.getCycleState(task.id, cycle.cycleId)
        assertFalse("Task vẫn phải PENDING", stateAfter?.isCompleted ?: false)

        val evalAfter = lockEvaluator.evaluateApp(targetPackage)
        assertTrue("App vẫn phải LOCKED, không thể bị unlock qua Mission Hall", evalAfter.isLocked)
    }

    /**
     * CORR-A02: System Panel Completion Test
     * Scenario:
     * - App LOCKED.
     * - Task reward app tương ứng.
     * - Task PENDING.
     * Action:
     * - Trigger blocked-app flow (System Panel xuất hiện).
     * - Thực hiện completion action theo System Panel (CompleteTaskUseCase).
     * Expected:
     * - TaskCycleState -> COMPLETED.
     * - CanonicalLockEvaluator được gọi/evaluate.
     * - Threshold đạt (K=1 >= Required(1)=1) -> App UNLOCKED!
     */
    @Test
    fun testCORR_A02_systemPanelCompletion_unlocksAppWhenThresholdMet() = runBlocking {
        val targetPackage = "com.cloudflare.onedotonedotonedotone"
        vaultRepository.addVaultApp(CanonicalVaultApp(targetPackage, "1.1.1.1"))

        val task = CanonicalTask(id = "task_read_book", title = "Đọc Sách Đạo Đức Kinh 20 Trang")
        taskRepository.saveTask(task)
        taskRepository.linkTaskToApp(task.id, targetPackage)

        val cycle = cycleRepository.getCurrentCycle()

        // Kiểm tra app đang locked
        val initialEval = lockEvaluator.evaluateApp(targetPackage)
        assertTrue("App ban đầu phải LOCKED", initialEval.isLocked)

        // System Panel completion interaction:
        // Caller hợp thức theo SSOT: System Panel (LockScreenActivity)
        val completeTaskUseCase = CompleteTaskUseCase(taskRepository, lockEvaluator)
        val result = completeTaskUseCase(task.id, cycle.cycleId)

        assertTrue("CompleteTaskUseCase phải thẩm định lại targetPackage", result.containsKey(targetPackage))
        assertFalse("App phải được UNLOCKED sau thẩm định", result[targetPackage]!!.isLocked)

        // Kiểm tra TaskCycleState đã chuyển COMPLETED
        val cycleState = taskRepository.getCycleState(task.id, cycle.cycleId)
        assertNotNull("CycleState không được null", cycleState)
        assertEquals(TaskCycleStatus.COMPLETED, cycleState!!.status)
        assertTrue("TaskCycleState.isCompleted phải là true", cycleState.isCompleted)

        // Thẩm định lại bằng CanonicalLockEvaluator
        val postEval = lockEvaluator.evaluateApp(targetPackage)
        assertFalse("App phải được UNLOCKED sau khi hoàn thành đủ số nhiệm vụ", postEval.isLocked)
        assertEquals(1, postEval.completedLinkedRewardTasks)
        assertEquals(1, postEval.requiredCompletions)
        assertEquals(CanonicalLockDecision.UNLOCKED, postEval.decision)
    }

    /**
     * CORR-A03: No Standalone Completion Test
     * Scenario:
     * - Kiểm tra Mission Hall architecture contract theo SSOT.
     * Expected:
     * - Không có standalone completion confirmation dialog trong Mission Hall UI State.
     * - Không có hidden completion action hay usecase trong Mission Hall ViewModel.
     * - MissionHallUiState chỉ có taskPendingUndo (phục vụ Undo theo SSOT), không có taskPendingCompletion.
     */
    @Test
    fun testCORR_A03_noStandaloneCompletionInMissionHall() {
        // 1. Audit ViewModel constructor parameters: không được phụ thuộc CompleteTaskUseCase
        val constructors = MissionHallViewModel::class.java.constructors
        for (constructor in constructors) {
            val paramTypes = constructor.parameterTypes
            assertFalse(
                "MissionHallViewModel constructor không được chứa CompleteTaskUseCase",
                paramTypes.any { it.simpleName == "CompleteTaskUseCase" }
            )
        }

        // 2. Audit ViewModel methods: không có hàm complete
        val methods = MissionHallViewModel::class.java.declaredMethods
        val completeMethods = methods.filter { it.name.contains("complete", ignoreCase = true) }
        assertTrue(
            "MissionHallViewModel không được có method chứa 'complete': ${completeMethods.map { it.name }}",
            completeMethods.isEmpty()
        )

        // 3. Audit MissionHallUiState fields: không có trường taskPendingCompletion
        val stateFields = MissionHallUiState::class.java.declaredFields.map { it.name }
        assertFalse(
            "MissionHallUiState không được có trường taskPendingCompletion",
            stateFields.contains("taskPendingCompletion")
        )
        assertTrue(
            "MissionHallUiState vẫn phải có taskPendingUndo phục vụ Undo theo SSOT",
            stateFields.contains("taskPendingUndo")
        )
    }
}
