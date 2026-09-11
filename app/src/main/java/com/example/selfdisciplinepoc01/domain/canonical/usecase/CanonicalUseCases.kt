package com.example.selfdisciplinepoc01.domain.canonical.usecase

import com.example.selfdisciplinepoc01.domain.canonical.cycle.CanonicalCycleId
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
