package com.example.selfdisciplinepoc01.data.repository

import com.example.selfdisciplinepoc01.data.entity.AppEntity
import com.example.selfdisciplinepoc01.data.entity.TaskAppCrossRef
import com.example.selfdisciplinepoc01.data.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

/**
 * Proposed Repository interface for Core Data operations.
 *
 * Provides a clean abstraction over Vault Apps, Mission Tasks, Many-to-Many associations,
 * and 04:00 Business Day task completions.
 *
 * PROPOSAL — OPEN-05 REMAINS OPEN.
 */
interface CoreDataRepository {

    // --- Vault Applications ---
    suspend fun addVaultApp(packageName: String, appName: String)
    suspend fun removeVaultApp(packageName: String)
    suspend fun getApp(packageName: String): AppEntity?
    suspend fun getAllVaultApps(): List<AppEntity>
    fun observeVaultApps(): Flow<List<AppEntity>>

    // --- Mission Tasks ---
    suspend fun createTask(name: String): Long
    suspend fun archiveTask(taskId: Long)
    suspend fun deleteTask(taskId: Long)
    suspend fun getTaskById(taskId: Long): TaskEntity?
    suspend fun getAllActiveTasks(): List<TaskEntity>
    fun observeActiveTasks(): Flow<List<TaskEntity>>

    // --- Task ↔ App Many-to-Many Relationships ---
    suspend fun linkTaskToApp(taskId: Long, packageName: String)
    suspend fun linkTaskToApps(taskId: Long, packageNames: List<String>)
    suspend fun unlinkTaskFromApp(taskId: Long, packageName: String)
    suspend fun syncTaskLinkedApps(taskId: Long, packageNames: List<String>)
    suspend fun getAppsForTask(taskId: Long): List<AppEntity>
    suspend fun getTasksForApp(packageName: String): List<TaskEntity>
    fun observeAppsForTask(taskId: Long): Flow<List<AppEntity>>
    fun observeTasksForApp(packageName: String): Flow<List<TaskEntity>>
    fun observeAllCrossRefs(): Flow<List<TaskAppCrossRef>>

    // --- Daily Task Completion (Cycle 04:00) ---
    suspend fun setTaskCompletion(taskId: Long, businessDate: String, isCompleted: Boolean)
    suspend fun isTaskCompletedOnDate(taskId: Long, businessDate: String): Boolean
    fun observeTaskCompletion(taskId: Long, businessDate: String): Flow<Boolean>
    suspend fun getCompletedTaskIdsForDate(businessDate: String): List<Long>
    fun observeCompletedTaskIdsForDate(businessDate: String): Flow<List<Long>>
    suspend fun getDailyCompletionsForDate(businessDate: String): List<com.example.selfdisciplinepoc01.data.entity.DailyTaskCompletionEntity>
    fun observeDailyCompletionsForDate(businessDate: String): Flow<List<com.example.selfdisciplinepoc01.data.entity.DailyTaskCompletionEntity>>
}

