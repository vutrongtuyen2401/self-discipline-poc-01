package com.example.selfdisciplinepoc01.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.selfdisciplinepoc01.data.entity.AppEntity
import com.example.selfdisciplinepoc01.data.entity.TaskAppCrossRef
import com.example.selfdisciplinepoc01.data.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

/**
 * Proposed DAO for Many-to-Many associations between Tasks and Vault Apps.
 *
 * Technical foundation for Checkpoint CP6.
 * Note: Database schema is a proposal; OPEN-05 remains OPEN.
 */
@Dao
interface TaskAppCrossRefDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCrossRef(crossRef: TaskAppCrossRef)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCrossRefs(crossRefs: List<TaskAppCrossRef>)

    @Delete
    suspend fun deleteCrossRef(crossRef: TaskAppCrossRef)

    @Query("DELETE FROM task_app_cross_ref WHERE taskId = :taskId AND appPackageName = :packageName")
    suspend fun deleteRelation(taskId: Long, packageName: String)

    @Query("DELETE FROM task_app_cross_ref WHERE taskId = :taskId")
    suspend fun deleteByTaskId(taskId: Long)

    @Query("DELETE FROM task_app_cross_ref WHERE appPackageName = :packageName")
    suspend fun deleteByAppPackageName(packageName: String)

    @Query("SELECT * FROM task_app_cross_ref")
    suspend fun getAllCrossRefs(): List<TaskAppCrossRef>

    @Query("SELECT * FROM task_app_cross_ref")
    fun observeAllCrossRefs(): Flow<List<TaskAppCrossRef>>

    @Query("SELECT * FROM task_app_cross_ref WHERE taskId = :taskId")
    suspend fun getCrossRefsForTask(taskId: Long): List<TaskAppCrossRef>

    @Query("SELECT * FROM task_app_cross_ref WHERE appPackageName = :packageName")
    suspend fun getCrossRefsForApp(packageName: String): List<TaskAppCrossRef>

    @Query("""
        SELECT a.* FROM vault_apps a
        INNER JOIN task_app_cross_ref r ON a.packageName = r.appPackageName
        WHERE r.taskId = :taskId
    """)
    suspend fun getAppsForTask(taskId: Long): List<AppEntity>

    @Query("""
        SELECT a.* FROM vault_apps a
        INNER JOIN task_app_cross_ref r ON a.packageName = r.appPackageName
        WHERE r.taskId = :taskId
    """)
    fun observeAppsForTask(taskId: Long): Flow<List<AppEntity>>

    @Query("""
        SELECT t.* FROM mission_tasks t
        INNER JOIN task_app_cross_ref r ON t.id = r.taskId
        WHERE r.appPackageName = :packageName
    """)
    suspend fun getTasksForApp(packageName: String): List<TaskEntity>

    @Query("""
        SELECT t.* FROM mission_tasks t
        INNER JOIN task_app_cross_ref r ON t.id = r.taskId
        WHERE r.appPackageName = :packageName
    """)
    fun observeTasksForApp(packageName: String): Flow<List<TaskEntity>>
}
