package com.example.selfdisciplinepoc01.data.canonical.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.selfdisciplinepoc01.data.canonical.entity.CanonicalTaskEntity
import com.example.selfdisciplinepoc01.data.canonical.entity.CanonicalVaultAppEntity
import com.example.selfdisciplinepoc01.data.canonical.entity.CanonicalVoucherEntity
import com.example.selfdisciplinepoc01.data.canonical.entity.TaskCycleStateEntity
import com.example.selfdisciplinepoc01.data.canonical.entity.TaskRewardLinkEntity

@Dao
interface CanonicalTaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTask(task: CanonicalTaskEntity)

    @Query("SELECT * FROM canonical_tasks WHERE id = :id")
    suspend fun getTaskById(id: String): CanonicalTaskEntity?

    @Query("SELECT * FROM canonical_tasks WHERE isArchived = 0 ORDER BY orderIndex ASC, createdAtEpochMillis ASC")
    suspend fun getAllActiveTasks(): List<CanonicalTaskEntity>

    @Query("UPDATE canonical_tasks SET isArchived = 1 WHERE id = :taskId")
    suspend fun archiveTask(taskId: String)

    @Query("UPDATE canonical_tasks SET title = :newTitle WHERE id = :taskId")
    suspend fun renameTask(taskId: String, newTitle: String)

    @Query("UPDATE canonical_tasks SET pendingNextCycleRewards = :pendingJson WHERE id = :taskId")
    suspend fun updatePendingRewards(taskId: String, pendingJson: String?)

    @Query("SELECT * FROM canonical_tasks WHERE pendingNextCycleRewards IS NOT NULL AND isArchived = 0")
    suspend fun getTasksWithPendingRewards(): List<CanonicalTaskEntity>

    @Query("DELETE FROM canonical_tasks WHERE id = :taskId")
    suspend fun deleteTaskById(taskId: String)
}

@Dao
interface TaskCycleStateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertState(state: TaskCycleStateEntity)

    @Query("SELECT * FROM canonical_task_cycle_states WHERE taskId = :taskId AND cycleId = :cycleId")
    suspend fun getState(taskId: String, cycleId: String): TaskCycleStateEntity?

    @Query("SELECT * FROM canonical_task_cycle_states WHERE cycleId = :cycleId")
    suspend fun getStatesForCycle(cycleId: String): List<TaskCycleStateEntity>

    @Query("SELECT * FROM canonical_task_cycle_states WHERE taskId = :taskId ORDER BY cycleId ASC")
    suspend fun getHistoryForTask(taskId: String): List<TaskCycleStateEntity>

    @Query("UPDATE canonical_task_cycle_states SET status = :status, completedAtEpochMillis = :completedAt WHERE taskId = :taskId AND cycleId = :cycleId")
    suspend fun updateStatus(taskId: String, cycleId: String, status: String, completedAt: Long?)
}

@Dao
interface CanonicalVaultDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertApp(app: CanonicalVaultAppEntity)

    @Query("SELECT * FROM canonical_vault_apps WHERE packageName = :packageName")
    suspend fun getApp(packageName: String): CanonicalVaultAppEntity?

    @Query("SELECT * FROM canonical_vault_apps ORDER BY addedAtEpochMillis ASC")
    suspend fun getAllApps(): List<CanonicalVaultAppEntity>

    @Query("DELETE FROM canonical_vault_apps WHERE packageName = :packageName")
    suspend fun deleteApp(packageName: String)
}

@Dao
interface TaskRewardLinkDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLink(link: TaskRewardLinkEntity)

    @Query("DELETE FROM canonical_task_reward_links WHERE taskId = :taskId AND packageName = :packageName")
    suspend fun deleteLink(taskId: String, packageName: String)

    @Query("DELETE FROM canonical_task_reward_links WHERE packageName = :packageName")
    suspend fun deleteByApp(packageName: String)

    @Query("DELETE FROM canonical_task_reward_links WHERE taskId = :taskId")
    suspend fun deleteByTask(taskId: String)

    @Query("SELECT * FROM canonical_task_reward_links WHERE packageName = :packageName")
    suspend fun getLinksForApp(packageName: String): List<TaskRewardLinkEntity>

    @Query("SELECT * FROM canonical_task_reward_links WHERE taskId = :taskId")
    suspend fun getLinksForTask(taskId: String): List<TaskRewardLinkEntity>

    @Query("SELECT * FROM canonical_task_reward_links")
    suspend fun getAllLinks(): List<TaskRewardLinkEntity>
}

@Dao
interface CanonicalVoucherDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVoucher(voucher: CanonicalVoucherEntity)

    @Query("SELECT * FROM canonical_vouchers WHERE effectiveFromEpochMillis <= :nowEpochMillis AND effectiveUntilEpochMillis > :nowEpochMillis")
    suspend fun getActiveVouchers(nowEpochMillis: Long): List<CanonicalVoucherEntity>

    @Query("DELETE FROM canonical_vouchers WHERE voucherId = :voucherId")
    suspend fun deleteVoucher(voucherId: String)
}
