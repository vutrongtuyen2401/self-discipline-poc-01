package com.example.selfdisciplinepoc01.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.selfdisciplinepoc01.data.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

/**
 * Proposed DAO for Mission Tasks.
 *
 * Technical foundation for Checkpoint CP3.
 * Note: Database schema is a proposal; OPEN-05 remains OPEN.
 */
@Dao
interface TaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<TaskEntity>): List<Long>

    @Update
    suspend fun updateTask(task: TaskEntity)

    @Delete
    suspend fun deleteTask(task: TaskEntity)

    @Query("DELETE FROM mission_tasks WHERE id = :id")
    suspend fun deleteTaskById(id: Long)

    @Query("SELECT * FROM mission_tasks WHERE id = :id")
    suspend fun getTaskById(id: Long): TaskEntity?

    @Query("SELECT * FROM mission_tasks WHERE isArchived = 0 ORDER BY id ASC")
    suspend fun getAllActiveTasks(): List<TaskEntity>

    @Query("SELECT * FROM mission_tasks WHERE isArchived = 0 ORDER BY id ASC")
    fun observeAllActiveTasks(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM mission_tasks ORDER BY id ASC")
    suspend fun getAllTasks(): List<TaskEntity>
}
