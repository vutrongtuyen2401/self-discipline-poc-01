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
 * Use Case: Tạo nhiệm vụ mới trong Sảnh Nhiệm Vụ.
 */
class CreateTaskUseCase(
    private val taskRepository: CanonicalTaskRepository
) {
    suspend operator fun invoke(
        id: String,
        title: String,
        description: String = "",
        orderIndex: Int = 0,
        hasReward: Boolean = true,
        linkedAppPackageNames: List<String> = emptyList()
    ): CanonicalTask {
        val task = CanonicalTask(
            id = id,
            title = title,
            description = description,
            orderIndex = orderIndex,
            hasReward = hasReward,
            createdAt = Instant.now()
        )
        taskRepository.saveTask(task)
        for (pkg in linkedAppPackageNames) {
            taskRepository.linkTaskToApp(id, pkg)
        }
        return task
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
 * Hỗ trợ áp dụng ngay lập tức hoặc đặt chế độ Chờ chu kỳ tiếp theo (Pending Next Cycle).
 */
class UpdateTaskRewardLinkageUseCase(
    private val taskRepository: CanonicalTaskRepository,
    private val lockEvaluator: CanonicalLockEvaluator
) {
    suspend operator fun invoke(
        taskId: String,
        selectedPackageNames: List<String>,
        isPendingNextCycle: Boolean = false,
        instant: Instant = Instant.now()
    ): Map<String, LockEvaluationResult> {
        if (isPendingNextCycle) {
            taskRepository.setPendingNextCycleRewards(taskId, selectedPackageNames)
            return emptyMap()
        } else {
            val oldApps = taskRepository.getAppsLinkedToTask(taskId)
            for (pkg in oldApps) {
                taskRepository.unlinkTaskFromApp(taskId, pkg)
            }
            for (pkg in selectedPackageNames) {
                taskRepository.linkTaskToApp(taskId, pkg)
            }
            val task = taskRepository.getTask(taskId)
            if (task != null) {
                taskRepository.saveTask(task.copy(hasReward = selectedPackageNames.isNotEmpty()))
            }
            val affected = (oldApps + selectedPackageNames).distinct()
            val results = mutableMapOf<String, LockEvaluationResult>()
            for (pkg in affected) {
                results[pkg] = lockEvaluator.evaluateApp(pkg, instant)
            }
            return results
        }
    }

    suspend fun cancelPending(taskId: String) {
        taskRepository.cancelPendingNextCycleRewards(taskId)
    }
}

