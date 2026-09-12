package com.example.selfdisciplinepoc01.domain.canonical.usecase

import com.example.selfdisciplinepoc01.domain.canonical.cycle.CanonicalCycleId
import com.example.selfdisciplinepoc01.domain.canonical.repository.CanonicalCycleRepository
import com.example.selfdisciplinepoc01.domain.canonical.repository.CanonicalLockEvaluator
import com.example.selfdisciplinepoc01.domain.canonical.repository.CanonicalTaskRepository
import com.example.selfdisciplinepoc01.domain.canonical.repository.CanonicalVaultRepository
import com.example.selfdisciplinepoc01.domain.canonical.task.CanonicalTask
import com.example.selfdisciplinepoc01.domain.canonical.vault.CanonicalVaultApp
import com.example.selfdisciplinepoc01.domain.canonical.vault.LockEvaluationResult
import java.time.Instant

/**
 * Use Case: Tạo nhiệm vụ mới trong Sảnh Nhiệm Vụ (Mission Hall).
 * Hỗ trợ tạo nguyên tử (Atomic Mutation) kèm liên kết phần thưởng
 * và lựa chọn thời điểm hiệu lực (IMMEDIATE_CURRENT_CYCLE hoặc PENDING_NEXT_CYCLE) theo SSOT.
 */
class CreateTaskUseCase(
    private val taskRepository: CanonicalTaskRepository,
    private val lockEvaluator: CanonicalLockEvaluator? = null
) {
    suspend fun execute(
        id: String = java.util.UUID.randomUUID().toString(),
        title: String,
        description: String = "",
        orderIndex: Int = 0,
        hasReward: Boolean = true,
        linkedAppPackageNames: List<String> = emptyList(),
        timing: com.example.selfdisciplinepoc01.domain.canonical.task.RewardMutationTiming = com.example.selfdisciplinepoc01.domain.canonical.task.RewardMutationTiming.IMMEDIATE_CURRENT_CYCLE,
        instant: Instant = Instant.now()
    ): com.example.selfdisciplinepoc01.domain.canonical.task.TaskMutationResult {
        val task = CanonicalTask(
            id = id,
            title = title,
            description = description,
            orderIndex = orderIndex,
            hasReward = hasReward,
            createdAt = instant
        )
        val affectedApps = taskRepository.createTaskAtomic(task, linkedAppPackageNames, timing)
        val evaluationMap = mutableMapOf<String, LockEvaluationResult>()
        if (lockEvaluator != null) {
            for (pkg in affectedApps) {
                evaluationMap[pkg] = lockEvaluator.evaluateApp(pkg, instant)
            }
        }
        if (affectedApps.isNotEmpty()) {
            CanonicalMutationSyncManager.notifyMutationCommitted(affectedApps)
        }
        val savedTask = taskRepository.getTask(id) ?: task
        return com.example.selfdisciplinepoc01.domain.canonical.task.TaskMutationResult(
            task = savedTask,
            affectedAppsEvaluation = evaluationMap,
            timing = timing
        )
    }

    suspend operator fun invoke(
        id: String = java.util.UUID.randomUUID().toString(),
        title: String,
        description: String = "",
        orderIndex: Int = 0,
        hasReward: Boolean = true,
        linkedAppPackageNames: List<String> = emptyList(),
        timing: com.example.selfdisciplinepoc01.domain.canonical.task.RewardMutationTiming = com.example.selfdisciplinepoc01.domain.canonical.task.RewardMutationTiming.IMMEDIATE_CURRENT_CYCLE
    ): CanonicalTask {
        return execute(
            id = id,
            title = title,
            description = description,
            orderIndex = orderIndex,
            hasReward = hasReward,
            linkedAppPackageNames = linkedAppPackageNames,
            timing = timing
        ).task ?: CanonicalTask(id = id, title = title, description = description, orderIndex = orderIndex, hasReward = hasReward)
    }
}

/**
 * Use Case: Người dùng tự đánh dấu hoàn thành nhiệm vụ trong chu kỳ hiện tại.
 *
 * Invariant SSOT:
 * - Chỉ ảnh hưởng tới chu kỳ được chỉ định (cycleId).
 * - Tự động tính toán lại trạng thái khóa của tất cả các Vault App được liên kết.
 */
class CompleteTaskUseCase(
    private val taskRepository: CanonicalTaskRepository,
    private val lockEvaluator: CanonicalLockEvaluator
) {
    suspend operator fun invoke(
        taskId: String,
        cycleId: CanonicalCycleId,
        completedAt: Instant = Instant.now()
    ): Map<String, LockEvaluationResult> {
        taskRepository.completeTask(taskId, cycleId, completedAt)

        val linkedApps = taskRepository.getAppsLinkedToTask(taskId)
        val evaluationResults = mutableMapOf<String, LockEvaluationResult>()
        for (pkg in linkedApps) {
            val result = lockEvaluator.evaluateApp(pkg, completedAt)
            evaluationResults[pkg] = result
        }
        if (linkedApps.isNotEmpty()) {
            CanonicalMutationSyncManager.notifyMutationCommitted(linkedApps)
        }
        return evaluationResults
    }
}

/**
 * Use Case: Hoàn tác trạng thái hoàn thành nhiệm vụ trong chu kỳ hiện tại.
 *
 * Invariant SSOT:
 * - Chỉ ảnh hưởng tới chu kỳ được chỉ định (cycleId).
 * - Tự động tính toán lại trạng thái khóa của các ứng dụng liên quan.
 */
class UndoTaskUseCase(
    private val taskRepository: CanonicalTaskRepository,
    private val lockEvaluator: CanonicalLockEvaluator
) {
    suspend operator fun invoke(
        taskId: String,
        cycleId: CanonicalCycleId,
        undoInstant: Instant = Instant.now()
    ): Map<String, LockEvaluationResult> {
        taskRepository.undoTaskCompletion(taskId, cycleId)

        val linkedApps = taskRepository.getAppsLinkedToTask(taskId)
        val evaluationResults = mutableMapOf<String, LockEvaluationResult>()
        for (pkg in linkedApps) {
            val result = lockEvaluator.evaluateApp(pkg, undoInstant)
            evaluationResults[pkg] = result
        }
        if (linkedApps.isNotEmpty()) {
            CanonicalMutationSyncManager.notifyMutationCommitted(linkedApps)
        }
        return evaluationResults
    }
}

/**
 * Use Case: Xóa nhiệm vụ (Archive Task).
 *
 * Invariant SSOT:
 * - KHÔNG xóa cứng dữ liệu lịch sử (TaskCycleState của các ngày cũ vẫn nguyên vẹn).
 * - Xóa các TaskRewardLink của nhiệm vụ này.
 * - Kích hoạt tính toán lại trạng thái khóa của các ứng dụng bị ảnh hưởng.
 */
class DeleteTaskUseCase(
    private val taskRepository: CanonicalTaskRepository,
    private val lockEvaluator: CanonicalLockEvaluator
) {
    suspend operator fun invoke(
        taskId: String,
        instant: Instant = Instant.now()
    ): Map<String, LockEvaluationResult> {
        val affectedApps = taskRepository.getAppsLinkedToTask(taskId)
        taskRepository.archiveTask(taskId)

        val evaluationResults = mutableMapOf<String, LockEvaluationResult>()
        for (pkg in affectedApps) {
            val result = lockEvaluator.evaluateApp(pkg, instant)
            evaluationResults[pkg] = result
        }
        if (affectedApps.isNotEmpty()) {
            CanonicalMutationSyncManager.notifyMutationCommitted(affectedApps)
        }
        return evaluationResults
    }
}

/**
 * Use Case: Thêm ứng dụng vào Bảo Khố (Vault).
 *
 * Invariant SSOT:
 * - App mới thêm vào Vault chưa gán nhiệm vụ thưởng (N=0) => luôn UNLOCKED.
 * - Thêm lại ứng dụng cũ KHÔNG tự động khôi phục các liên kết TaskRewardLink cũ.
 */
class AddVaultAppUseCase(
    private val vaultRepository: CanonicalVaultRepository,
    private val lockEvaluator: CanonicalLockEvaluator
) {
    suspend operator fun invoke(
        packageName: String,
        displayName: String,
        addedAt: Instant = Instant.now()
    ): LockEvaluationResult {
        val app = CanonicalVaultApp(
            packageName = packageName,
            displayName = displayName,
            addedAt = addedAt
        )
        vaultRepository.addVaultApp(app)
        CanonicalMutationSyncManager.notifyMutationCommitted(listOf(packageName))
        return lockEvaluator.evaluateApp(packageName, addedAt)
    }
}

/**
 * Use Case: Xóa ứng dụng khỏi Bảo Khố (Vault).
 *
 * Invariant SSOT:
 * - Xóa app khỏi Vault.
 * - Xóa toàn bộ TaskRewardLink của app đó.
 * - Giữ nguyên CanonicalTask và toàn bộ lịch sử TaskCycleState.
 * - Ứng dụng không còn bị quản lý nên không bị phong ấn.
 */
class RemoveVaultAppUseCase(
    private val vaultRepository: CanonicalVaultRepository,
    private val taskRepository: CanonicalTaskRepository
) {
    suspend operator fun invoke(packageName: String) {
        taskRepository.removeAllLinksForApp(packageName)
        vaultRepository.removeVaultApp(packageName)
        CanonicalMutationSyncManager.notifyMutationCommitted(listOf(packageName))
    }
}

/**
 * Use Case: Thẩm định trạng thái phong ấn của ứng dụng.
 */
class EvaluateVaultAppUseCase(
    private val lockEvaluator: CanonicalLockEvaluator
) {
    suspend operator fun invoke(
        packageName: String,
        evaluationInstant: Instant = Instant.now()
    ): LockEvaluationResult {
        return lockEvaluator.evaluateApp(packageName, evaluationInstant)
    }
}

/**
 * Model biểu diễn một phần tử ứng dụng hiển thị trên Bảo Khố (Vault Screen).
 */
data class CanonicalVaultAppItem(
    val app: CanonicalVaultApp,
    val linkedTasks: List<CanonicalTask>,
    val lockResult: LockEvaluationResult
) {
    val packageName: String get() = app.packageName
    val appName: String get() = app.displayName
    val linkedTasksCount: Int get() = linkedTasks.size
    val isLocked: Boolean get() = lockResult.isLocked
    val isUnlocked: Boolean get() = lockResult.isUnlocked
}

/**
 * Use Case: Lấy toàn bộ ứng dụng trong Bảo Khố kèm thông tin liên kết và trạng thái đánh giá khóa.
 */
class GetCanonicalVaultAppsUseCase(
    private val vaultRepository: CanonicalVaultRepository,
    private val taskRepository: CanonicalTaskRepository,
    private val lockEvaluator: CanonicalLockEvaluator
) {
    suspend operator fun invoke(instant: Instant = Instant.now()): List<CanonicalVaultAppItem> {
        val apps = vaultRepository.getAllVaultApps()
        return apps.map { app ->
            val tasks = taskRepository.getTasksLinkedToApp(app.packageName)
            val result = lockEvaluator.evaluateApp(app.packageName, instant)
            CanonicalVaultAppItem(
                app = app,
                linkedTasks = tasks,
                lockResult = result
            )
        }
    }
}

/**
 * Use Case: Đổi tên nhiệm vụ tức thì (Instant Update, không confirmation).
 */
class RenameTaskUseCase(
    private val taskRepository: CanonicalTaskRepository
) {
    suspend operator fun invoke(taskId: String, newTitle: String) {
        taskRepository.renameTask(taskId, newTitle)
    }
}

/**
 * Model biểu diễn một phần tử nhiệm vụ hiển thị trên Sảnh Nhiệm Vụ (Mission Hall).
 */
data class MissionHallTaskItem(
    val task: CanonicalTask,
    val isCompleted: Boolean,
    val completedAt: Instant?,
    val linkedApps: List<CanonicalVaultApp>,
    val pendingNextCycleRewardPackageNames: List<String>? = null
)

/**
 * Use Case: Lấy danh sách nhiệm vụ của Sảnh Nhiệm Vụ trong chu kỳ hiện tại.
 */
class GetMissionHallTasksCanonicalUseCase(
    private val taskRepository: CanonicalTaskRepository,
    private val vaultRepository: CanonicalVaultRepository,
    private val cycleRepository: CanonicalCycleRepository
) {
    suspend operator fun invoke(instant: Instant = Instant.now()): List<MissionHallTaskItem> {
        val currentCycle = cycleRepository.getCurrentCycle(instant)
        val activeTasks = taskRepository.getAllActiveTasks()
        val cycleStates = taskRepository.getCycleStates(currentCycle.cycleId)
        val allVaultApps = vaultRepository.getAllVaultApps().associateBy { it.packageName }

        return activeTasks.map { task ->
            val state = cycleStates[task.id]
            val linkedPackageNames = taskRepository.getAppsLinkedToTask(task.id)
            val linkedVaultApps = linkedPackageNames.mapNotNull { pkg ->
                allVaultApps[pkg] ?: CanonicalVaultApp(pkg, pkg)
            }
            MissionHallTaskItem(
                task = task,
                isCompleted = state?.isCompleted == true,
                completedAt = state?.completedAt,
                linkedApps = linkedVaultApps,
                pendingNextCycleRewardPackageNames = task.pendingNextCycleRewards
            )
        }
    }
}

/**
 * Use Case: Cập nhật liên kết phần thưởng của nhiệm vụ.
 * Hỗ trợ áp dụng ngay lập tức (IMMEDIATE_CURRENT_CYCLE) hoặc đặt chế độ Chờ chu kỳ tiếp theo (PENDING_NEXT_CYCLE)
 * theo chuẩn SSOT với thao tác Atomic Mutation.
 */
class UpdateTaskRewardLinkageUseCase(
    private val taskRepository: CanonicalTaskRepository,
    private val lockEvaluator: CanonicalLockEvaluator
) {
    suspend fun execute(
        taskId: String,
        selectedPackageNames: List<String>,
        timing: com.example.selfdisciplinepoc01.domain.canonical.task.RewardMutationTiming = com.example.selfdisciplinepoc01.domain.canonical.task.RewardMutationTiming.IMMEDIATE_CURRENT_CYCLE,
        instant: Instant = Instant.now()
    ): com.example.selfdisciplinepoc01.domain.canonical.task.TaskMutationResult {
        val affectedApps = taskRepository.updateTaskRewardLinkageAtomic(taskId, selectedPackageNames, timing)
        val results = mutableMapOf<String, LockEvaluationResult>()
        for (pkg in affectedApps) {
            results[pkg] = lockEvaluator.evaluateApp(pkg, instant)
        }
        if (affectedApps.isNotEmpty()) {
            CanonicalMutationSyncManager.notifyMutationCommitted(affectedApps)
        }
        val task = taskRepository.getTask(taskId)
        return com.example.selfdisciplinepoc01.domain.canonical.task.TaskMutationResult(
            task = task,
            affectedAppsEvaluation = results,
            timing = timing
        )
    }

    suspend operator fun invoke(
        taskId: String,
        selectedPackageNames: List<String>,
        isPendingNextCycle: Boolean = false,
        instant: Instant = Instant.now()
    ): Map<String, LockEvaluationResult> {
        val timing = if (isPendingNextCycle) {
            com.example.selfdisciplinepoc01.domain.canonical.task.RewardMutationTiming.PENDING_NEXT_CYCLE
        } else {
            com.example.selfdisciplinepoc01.domain.canonical.task.RewardMutationTiming.IMMEDIATE_CURRENT_CYCLE
        }
        return execute(taskId, selectedPackageNames, timing, instant).affectedAppsEvaluation
    }

    suspend operator fun invoke(
        taskId: String,
        selectedPackageNames: List<String>,
        timing: com.example.selfdisciplinepoc01.domain.canonical.task.RewardMutationTiming,
        instant: Instant = Instant.now()
    ): Map<String, LockEvaluationResult> {
        return execute(taskId, selectedPackageNames, timing, instant).affectedAppsEvaluation
    }

    suspend fun cancelPending(taskId: String) {
        taskRepository.cancelPendingNextCycleRewards(taskId)
    }
}

