package com.example.selfdisciplinepoc01.domain.canonical.repository

import com.example.selfdisciplinepoc01.domain.canonical.cycle.CanonicalCycleBoundary
import com.example.selfdisciplinepoc01.domain.canonical.cycle.CanonicalCycleId
import com.example.selfdisciplinepoc01.domain.canonical.task.CanonicalTask
import com.example.selfdisciplinepoc01.domain.canonical.task.TaskCycleState
import com.example.selfdisciplinepoc01.domain.canonical.vault.CanonicalVaultApp
import com.example.selfdisciplinepoc01.domain.canonical.vault.LockEvaluationResult
import com.example.selfdisciplinepoc01.domain.canonical.vault.VoucherEffect
import java.time.Instant

/**
 * Hợp đồng truy xuất và quản lý Chu kỳ (Cycle Repository Contract).
 */
interface CanonicalCycleRepository {
    fun getCurrentCycle(instant: Instant = Instant.now()): CanonicalCycleBoundary
    fun getCycleBoundary(cycleId: CanonicalCycleId): CanonicalCycleBoundary
}

/**
 * Hợp đồng quản lý Nhiệm vụ chuẩn (Canonical Task Repository Contract).
 * Đảm bảo phân tách rõ ràng giữa định nghĩa nhiệm vụ bền vững (persistent task)
 * và trạng thái theo từng chu kỳ (cycle-scoped state).
 */
interface CanonicalTaskRepository {
    suspend fun getTask(taskId: String): CanonicalTask?
    suspend fun getAllActiveTasks(): List<CanonicalTask>
    suspend fun getTasksLinkedToApp(packageName: String): List<CanonicalTask>
    suspend fun getAppsLinkedToTask(taskId: String): List<String>
    
    suspend fun getCycleState(taskId: String, cycleId: CanonicalCycleId): TaskCycleState?
    suspend fun getCycleStates(cycleId: CanonicalCycleId): Map<String, TaskCycleState>
    
    suspend fun saveTask(task: CanonicalTask)
    suspend fun saveCycleState(state: TaskCycleState)
    
    /**
     * Hoàn thành nhiệm vụ trong chu kỳ cụ thể (mặc định chu kỳ hiện tại).
     * Chỉ tác động đến đúng chu kỳ này, không ảnh hưởng các chu kỳ khác.
     */
    suspend fun completeTask(
        taskId: String,
        cycleId: CanonicalCycleId,
        completedAt: Instant = Instant.now()
    )

    /**
     * Hoàn tác trạng thái hoàn thành của nhiệm vụ trong chu kỳ cụ thể.
     * Chỉ tác động đến đúng chu kỳ này.
     */
    suspend fun undoTaskCompletion(
        taskId: String,
        cycleId: CanonicalCycleId
    )

    /**
     * Xóa nhiệm vụ (Archive Task):
     * - Đánh dấu isArchived = true để nhiệm vụ không còn tham gia chu kỳ hiện tại / tương lai.
     * - Xóa các TaskRewardLink của nhiệm vụ này.
     * - TUYỆT ĐỐI BẢO TOÀN toàn bộ lịch sử TaskCycleState của nhiệm vụ trong quá khứ.
     */
    suspend fun archiveTask(taskId: String)

    /**
     * Lấy toàn bộ lịch sử thực thi của nhiệm vụ qua tất cả các chu kỳ.
     */
    suspend fun getTaskCycleHistory(taskId: String): List<TaskCycleState>
    
    suspend fun linkTaskToApp(taskId: String, packageName: String)
    suspend fun unlinkTaskFromApp(taskId: String, packageName: String)
    
    /**
     * Xóa toàn bộ liên kết phần thưởng của một Vault App khi app bị xóa khỏi Vault.
     * Lưu ý: Bản thân các CanonicalTask vẫn được giữ nguyên vẹn.
     */
    suspend fun removeAllLinksForApp(packageName: String)
}

/**
 * Hợp đồng quản lý Kho Phong Ấn (Canonical Vault Repository Contract).
 */
interface CanonicalVaultRepository {
    suspend fun getAllVaultApps(): List<CanonicalVaultApp>
    suspend fun getVaultApp(packageName: String): CanonicalVaultApp?
    suspend fun addVaultApp(app: CanonicalVaultApp)
    suspend fun removeVaultApp(packageName: String)
    
    suspend fun getActiveVouchers(instant: Instant = Instant.now()): List<VoucherEffect>
}

/**
 * Cổng đánh giá khóa tối cao chuẩn (Canonical Lock Evaluator Contract).
 * Nguồn chân lý duy nhất cho quyết định khóa/mở khóa của Vault.
 */
interface CanonicalLockEvaluator {
    suspend fun evaluateApp(
        packageName: String,
        evaluationInstant: Instant = Instant.now()
    ): LockEvaluationResult
}
