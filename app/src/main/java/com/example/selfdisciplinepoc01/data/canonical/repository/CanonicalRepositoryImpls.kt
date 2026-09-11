package com.example.selfdisciplinepoc01.data.canonical.repository

import androidx.room.withTransaction
import com.example.selfdisciplinepoc01.data.canonical.entity.CanonicalTaskEntity
import com.example.selfdisciplinepoc01.data.canonical.entity.CanonicalVaultAppEntity
import com.example.selfdisciplinepoc01.data.canonical.entity.CanonicalVoucherEntity
import com.example.selfdisciplinepoc01.data.canonical.entity.TaskCycleStateEntity
import com.example.selfdisciplinepoc01.data.canonical.entity.TaskRewardLinkEntity
import com.example.selfdisciplinepoc01.data.database.AppDatabase
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
import com.example.selfdisciplinepoc01.domain.canonical.vault.CanonicalLockDecision
import com.example.selfdisciplinepoc01.domain.canonical.vault.CanonicalLockPolicy
import com.example.selfdisciplinepoc01.domain.canonical.vault.CanonicalVaultApp
import com.example.selfdisciplinepoc01.domain.canonical.vault.LockEvaluationResult
import com.example.selfdisciplinepoc01.domain.canonical.vault.VoucherEffect
import java.time.Instant
import java.time.ZoneId

/**
 * Hiện thực hóa CanonicalCycleRepository dựa trên CycleEngine chuẩn 04:00.
 */
class CanonicalCycleRepositoryImpl(
    private val zoneIdProvider: () -> ZoneId = { ZoneId.systemDefault() }
) : CanonicalCycleRepository {

    override fun getCurrentCycle(instant: Instant): CanonicalCycleBoundary {
        return CycleEngine.getCurrentCycleBoundary(instant, zoneIdProvider())
    }

    override fun getCycleBoundary(cycleId: CanonicalCycleId): CanonicalCycleBoundary {
        return CycleEngine.resolveCycleBoundary(cycleId, zoneIdProvider())
    }
}

/**
 * Hiện thực hóa CanonicalTaskRepository dựa trên Room Persistence.
 * Sử dụng atomic transactions bảo đảm toàn vẹn dữ liệu và bảo toàn lịch sử.
 */
class CanonicalTaskRepositoryImpl(
    private val database: AppDatabase
) : CanonicalTaskRepository {

    private val taskDao = database.canonicalTaskDao()
    private val stateDao = database.taskCycleStateDao()
    private val linkDao = database.taskRewardLinkDao()

    override suspend fun getTask(taskId: String): CanonicalTask? {
        return taskDao.getTaskById(taskId)?.toDomain()
    }

    override suspend fun getAllActiveTasks(): List<CanonicalTask> {
        return taskDao.getAllActiveTasks().map { it.toDomain() }
    }

    override suspend fun getTasksLinkedToApp(packageName: String): List<CanonicalTask> {
        val links = linkDao.getLinksForApp(packageName)
        val tasks = mutableListOf<CanonicalTask>()
        for (link in links) {
            val task = taskDao.getTaskById(link.taskId)
            if (task != null && !task.isArchived) {
                tasks.add(task.toDomain())
            }
        }
        return tasks
    }

    override suspend fun getAppsLinkedToTask(taskId: String): List<String> {
        return linkDao.getLinksForTask(taskId).map { it.packageName }
    }

    override suspend fun getCycleState(taskId: String, cycleId: CanonicalCycleId): TaskCycleState? {
        return stateDao.getState(taskId, cycleId.dateIdentifier)?.toDomain()
    }

    override suspend fun getCycleStates(cycleId: CanonicalCycleId): Map<String, TaskCycleState> {
        return stateDao.getStatesForCycle(cycleId.dateIdentifier)
            .map { it.toDomain() }
            .associateBy { it.taskId }
    }

    override suspend fun saveTask(task: CanonicalTask) {
        taskDao.upsertTask(CanonicalTaskEntity.fromDomain(task))
    }

    override suspend fun saveCycleState(state: TaskCycleState) {
        stateDao.upsertState(TaskCycleStateEntity.fromDomain(state))
    }

    override suspend fun completeTask(
        taskId: String,
        cycleId: CanonicalCycleId,
        completedAt: Instant
    ) {
        val state = TaskCycleState(
            taskId = taskId,
            cycleId = cycleId,
            status = TaskCycleStatus.COMPLETED,
            completedAt = completedAt
        )
        saveCycleState(state)
    }

    override suspend fun undoTaskCompletion(taskId: String, cycleId: CanonicalCycleId) {
        val state = TaskCycleState(
            taskId = taskId,
            cycleId = cycleId,
            status = TaskCycleStatus.PENDING,
            completedAt = null
        )
        saveCycleState(state)
    }

    override suspend fun archiveTask(taskId: String) {
        database.withTransaction {
            // 1. Đánh dấu isArchived = true
            taskDao.archiveTask(taskId)
            // 2. Xóa liên kết phần thưởng với các app
            linkDao.deleteByTask(taskId)
            // LƯU Ý BẢO TOÀN LỊCH SỬ: Không xóa TaskCycleState cũ!
        }
    }

    override suspend fun getTaskCycleHistory(taskId: String): List<TaskCycleState> {
        return stateDao.getHistoryForTask(taskId).map { it.toDomain() }
    }

    override suspend fun linkTaskToApp(taskId: String, packageName: String) {
        linkDao.insertLink(
            TaskRewardLinkEntity(
                taskId = taskId,
                packageName = packageName,
                linkedAtEpochMillis = System.currentTimeMillis()
            )
        )
    }

    override suspend fun unlinkTaskFromApp(taskId: String, packageName: String) {
        linkDao.deleteLink(taskId, packageName)
    }

    override suspend fun removeAllLinksForApp(packageName: String) {
        linkDao.deleteByApp(packageName)
    }
}

/**
 * Hiện thực hóa CanonicalVaultRepository dựa trên Room Persistence.
 */
class CanonicalVaultRepositoryImpl(
    private val database: AppDatabase
) : CanonicalVaultRepository {

    private val vaultDao = database.canonicalVaultDao()
    private val voucherDao = database.canonicalVoucherDao()

    override suspend fun getAllVaultApps(): List<CanonicalVaultApp> {
        return vaultDao.getAllApps().map { it.toDomain() }
    }

    override suspend fun getVaultApp(packageName: String): CanonicalVaultApp? {
        return vaultDao.getApp(packageName)?.toDomain()
    }

    override suspend fun addVaultApp(app: CanonicalVaultApp) {
        vaultDao.upsertApp(CanonicalVaultAppEntity.fromDomain(app))
    }

    override suspend fun removeVaultApp(packageName: String) {
        vaultDao.deleteApp(packageName)
    }

    override suspend fun getActiveVouchers(instant: Instant): List<VoucherEffect> {
        return voucherDao.getActiveVouchers(instant.toEpochMilli()).map { it.toDomain() }
    }

    suspend fun saveVoucher(voucher: VoucherEffect) {
        voucherDao.upsertVoucher(CanonicalVoucherEntity.fromDomain(voucher))
    }
}

/**
 * Hiện thực hóa CanonicalLockEvaluator (Nguồn chân lý thẩm định khóa phong ấn).
 */
class CanonicalLockEvaluatorImpl(
    private val taskRepository: CanonicalTaskRepository,
    private val vaultRepository: CanonicalVaultRepository,
    private val cycleRepository: CanonicalCycleRepository
) : CanonicalLockEvaluator {

    override suspend fun evaluateApp(
        packageName: String,
        evaluationInstant: Instant
    ): LockEvaluationResult {
        // 1. Nếu ứng dụng không nằm trong Vault -> Luôn mở khóa
        val vaultApp = vaultRepository.getVaultApp(packageName)
        if (vaultApp == null) {
            return LockEvaluationResult(
                packageName = packageName,
                decision = CanonicalLockDecision.UNLOCKED,
                reason = "UNLOCKED_NOT_IN_VAULT: Ứng dụng không thuộc danh sách phong ấn của Bảo Khố",
                totalLinkedRewardTasks = 0,
                completedLinkedRewardTasks = 0,
                requiredCompletions = 0,
                effectiveVoucher = null
            )
        }

        // 2. Xác định chu kỳ hiện tại
        val cycleBoundary = cycleRepository.getCurrentCycle(evaluationInstant)
        val cycleId = cycleBoundary.cycleId

        // 3. Lấy danh sách nhiệm vụ được liên kết với ứng dụng này
        val linkedTasks = taskRepository.getTasksLinkedToApp(packageName)

        // 4. Lấy trạng thái của các nhiệm vụ trong chu kỳ hiện tại
        val cycleStates = taskRepository.getCycleStates(cycleId)

        // 5. Lấy các Thẻ bài (Vouchers) còn hiệu lực tại thời điểm đánh giá
        val activeVouchers = vaultRepository.getActiveVouchers(evaluationInstant)

        // 6. Đánh giá trạng thái khóa qua CanonicalLockPolicy chuẩn SSOT
        return CanonicalLockPolicy.evaluateLock(
            packageName = packageName,
            linkedTasks = linkedTasks,
            cycleTaskStates = cycleStates,
            activeVouchers = activeVouchers,
            evaluationInstant = evaluationInstant
        )
    }
}
