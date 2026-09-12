package com.example.selfdisciplinepoc01.data.canonical.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.selfdisciplinepoc01.domain.canonical.cycle.CanonicalCycleId
import com.example.selfdisciplinepoc01.domain.canonical.task.CanonicalTask
import com.example.selfdisciplinepoc01.domain.canonical.task.TaskCycleState
import com.example.selfdisciplinepoc01.domain.canonical.task.TaskCycleStatus
import com.example.selfdisciplinepoc01.domain.canonical.vault.CanonicalVaultApp
import com.example.selfdisciplinepoc01.domain.canonical.vault.VoucherEffect
import java.time.Instant

/**
 * Entity lưu trữ định nghĩa nhiệm vụ bền vững (Persistent Task Definition).
 */
@Entity(tableName = "canonical_tasks")
data class CanonicalTaskEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val description: String = "",
    val orderIndex: Int = 0,
    val hasReward: Boolean = true,
    val createdAtEpochMillis: Long = System.currentTimeMillis(),
    val isArchived: Boolean = false,
    val pendingNextCycleRewards: String? = null
) {
    fun toDomain(): CanonicalTask {
        return CanonicalTask(
            id = id,
            title = title,
            description = description,
            orderIndex = orderIndex,
            hasReward = hasReward,
            createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
            isArchived = isArchived,
            pendingNextCycleRewards = pendingNextCycleRewards?.let { str ->
                if (str.isBlank()) emptyList() else str.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            }
        )
    }

    companion object {
        fun fromDomain(task: CanonicalTask): CanonicalTaskEntity {
            return CanonicalTaskEntity(
                id = task.id,
                title = task.title,
                description = task.description,
                orderIndex = task.orderIndex,
                hasReward = task.hasReward,
                createdAtEpochMillis = task.createdAt.toEpochMilli(),
                isArchived = task.isArchived,
                pendingNextCycleRewards = task.pendingNextCycleRewards?.joinToString(",")
            )
        }
    }
}

/**
 * Entity lưu trạng thái nhiệm vụ theo từng chu kỳ (Cycle-Scoped State).
 * Đảm bảo mỗi chu kỳ có một bản ghi trạng thái riêng biệt, không ghi đè mất lịch sử.
 */
@Entity(
    tableName = "canonical_task_cycle_states",
    primaryKeys = ["taskId", "cycleId"],
    indices = [
        Index(value = ["cycleId"]),
        Index(value = ["taskId"])
    ]
)
data class TaskCycleStateEntity(
    val taskId: String,
    val cycleId: String, // YYYY-MM-DD
    val status: String,   // PENDING, COMPLETED, FAILED
    val completedAtEpochMillis: Long? = null
) {
    fun toDomain(): TaskCycleState {
        return TaskCycleState(
            taskId = taskId,
            cycleId = CanonicalCycleId(cycleId),
            status = TaskCycleStatus.valueOf(status),
            completedAt = completedAtEpochMillis?.let { Instant.ofEpochMilli(it) }
        )
    }

    companion object {
        fun fromDomain(state: TaskCycleState): TaskCycleStateEntity {
            return TaskCycleStateEntity(
                taskId = state.taskId,
                cycleId = state.cycleId.dateIdentifier,
                status = state.status.name,
                completedAtEpochMillis = state.completedAt?.toEpochMilli()
            )
        }
    }
}

/**
 * Entity ứng dụng được quản lý trong Kho Phong Ấn (Vault App).
 */
@Entity(tableName = "canonical_vault_apps")
data class CanonicalVaultAppEntity(
    @PrimaryKey
    val packageName: String,
    val displayName: String,
    val addedAtEpochMillis: Long = System.currentTimeMillis()
) {
    fun toDomain(): CanonicalVaultApp {
        return CanonicalVaultApp(
            packageName = packageName,
            displayName = displayName,
            addedAt = Instant.ofEpochMilli(addedAtEpochMillis)
        )
    }

    companion object {
        fun fromDomain(app: CanonicalVaultApp): CanonicalVaultAppEntity {
            return CanonicalVaultAppEntity(
                packageName = app.packageName,
                displayName = app.displayName,
                addedAtEpochMillis = app.addedAt.toEpochMilli()
            )
        }
    }
}

/**
 * Entity liên kết nhiều-nhiều giữa Nhiệm vụ và Ứng dụng thưởng (Task ↔ Vault App).
 */
@Entity(
    tableName = "canonical_task_reward_links",
    primaryKeys = ["taskId", "packageName"],
    indices = [
        Index(value = ["taskId"]),
        Index(value = ["packageName"])
    ]
)
data class TaskRewardLinkEntity(
    val taskId: String,
    val packageName: String,
    val linkedAtEpochMillis: Long = System.currentTimeMillis()
)

/**
 * Entity lưu trữ Thẻ bài / Voucher có hiệu lực phong ấn/giải phong ấn.
 */
@Entity(tableName = "canonical_vouchers")
data class CanonicalVoucherEntity(
    @PrimaryKey
    val voucherId: String,
    val voucherName: String,
    val targetPackageName: String? = null,
    val effectiveFromEpochMillis: Long,
    val effectiveUntilEpochMillis: Long
) {
    fun toDomain(): VoucherEffect {
        return VoucherEffect(
            voucherId = voucherId,
            voucherName = voucherName,
            targetPackageName = targetPackageName,
            effectiveFrom = Instant.ofEpochMilli(effectiveFromEpochMillis),
            effectiveUntil = Instant.ofEpochMilli(effectiveUntilEpochMillis)
        )
    }

    companion object {
        fun fromDomain(voucher: VoucherEffect): CanonicalVoucherEntity {
            return CanonicalVoucherEntity(
                voucherId = voucher.voucherId,
                voucherName = voucher.voucherName,
                targetPackageName = voucher.targetPackageName,
                effectiveFromEpochMillis = voucher.effectiveFrom.toEpochMilli(),
                effectiveUntilEpochMillis = voucher.effectiveUntil.toEpochMilli()
            )
        }
    }
}
