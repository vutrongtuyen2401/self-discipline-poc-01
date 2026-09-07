package com.example.selfdisciplinepoc01.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.selfdisciplinepoc01.data.entity.DailyTaskCompletionEntity
import kotlinx.coroutines.flow.Flow

/**
 * Proposed DAO for recording daily task completion states bound to 04:00 Business Days.
 *
 * Technical foundation for Checkpoint CP3 & CP5.
 * Note: Database schema is a proposal; OPEN-05 remains OPEN.
 */
@Dao
interface DailyTaskCompletionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setCompletion(completion: DailyTaskCompletionEntity)

    @Query("SELECT * FROM daily_task_completions WHERE taskId = :taskId AND businessDate = :businessDate")
    suspend fun getCompletion(taskId: Long, businessDate: String): DailyTaskCompletionEntity?

    @Query("SELECT * FROM daily_task_completions WHERE taskId = :taskId AND businessDate = :businessDate")
    fun observeCompletion(taskId: Long, businessDate: String): Flow<DailyTaskCompletionEntity?>

    @Query("SELECT * FROM daily_task_completions WHERE businessDate = :businessDate")
    suspend fun getCompletionsForDate(businessDate: String): List<DailyTaskCompletionEntity>

    @Query("SELECT * FROM daily_task_completions WHERE businessDate = :businessDate")
    fun observeCompletionsForDate(businessDate: String): Flow<List<DailyTaskCompletionEntity>>

    @Query("DELETE FROM daily_task_completions WHERE taskId = :taskId AND businessDate = :businessDate")
    suspend fun deleteCompletion(taskId: Long, businessDate: String)

    @Query("DELETE FROM daily_task_completions WHERE taskId = :taskId")
    suspend fun deleteCompletionsForTask(taskId: Long)
}
