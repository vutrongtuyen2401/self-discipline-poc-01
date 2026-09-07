package com.example.selfdisciplinepoc01.data.repository

import com.example.selfdisciplinepoc01.data.database.AppDatabase
import com.example.selfdisciplinepoc01.data.entity.AppEntity
import com.example.selfdisciplinepoc01.data.entity.DailyTaskCompletionEntity
import com.example.selfdisciplinepoc01.data.entity.TaskAppCrossRef
import com.example.selfdisciplinepoc01.data.entity.TaskEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Implementation of [CoreDataRepository] backed by proposed Room Database.
 *
 * PROPOSAL — OPEN-05 REMAINS OPEN.
 */
class CoreDataRepositoryImpl(
    private val database: AppDatabase
) : CoreDataRepository {

    private val appDao = database.appDao()
    private val taskDao = database.taskDao()
    private val crossRefDao = database.taskAppCrossRefDao()
    private val completionDao = database.dailyTaskCompletionDao()

    override suspend fun addVaultApp(packageName: String, appName: String) {
        val app = AppEntity(
            packageName = packageName,
            appName = appName,
            isVaultManaged = true
        )
        appDao.upsertApp(app)
    }

    override suspend fun removeVaultApp(packageName: String) {
        crossRefDao.deleteByAppPackageName(packageName)
        appDao.deleteByPackageName(packageName)
    }

    override suspend fun getApp(packageName: String): AppEntity? {
        return appDao.getApp(packageName)
    }

    override suspend fun getAllVaultApps(): List<AppEntity> {
        return appDao.getAllVaultApps()
    }

    override fun observeVaultApps(): Flow<List<AppEntity>> {
        return appDao.observeAllVaultApps()
    }

    override suspend fun createTask(name: String): Long {
        val task = TaskEntity(
            name = name,
            createdAtWallMillis = System.currentTimeMillis()
        )
        return taskDao.insertTask(task)
    }

    override suspend fun archiveTask(taskId: Long) {
        val task = taskDao.getTaskById(taskId) ?: return
        taskDao.updateTask(task.copy(isArchived = true))
    }

    override suspend fun deleteTask(taskId: Long) {
        completionDao.deleteCompletionsForTask(taskId)
        crossRefDao.deleteByTaskId(taskId)
        taskDao.deleteTaskById(taskId)
    }

    override suspend fun getTaskById(taskId: Long): TaskEntity? {
        return taskDao.getTaskById(taskId)
    }

    override suspend fun getAllActiveTasks(): List<TaskEntity> {
        return taskDao.getAllActiveTasks()
    }

    override fun observeActiveTasks(): Flow<List<TaskEntity>> {
        return taskDao.observeAllActiveTasks()
    }

    override suspend fun linkTaskToApp(taskId: Long, packageName: String) {
        crossRefDao.insertCrossRef(
            TaskAppCrossRef(
                taskId = taskId,
                appPackageName = packageName,
                linkedAtWallMillis = System.currentTimeMillis()
            )
        )
    }

    override suspend fun linkTaskToApps(taskId: Long, packageNames: List<String>) {
        val list = packageNames.map { pkg ->
            TaskAppCrossRef(
                taskId = taskId,
                appPackageName = pkg,
                linkedAtWallMillis = System.currentTimeMillis()
            )
        }
        crossRefDao.insertCrossRefs(list)
    }

    override suspend fun unlinkTaskFromApp(taskId: Long, packageName: String) {
        crossRefDao.deleteRelation(taskId, packageName)
    }

    override suspend fun syncTaskLinkedApps(taskId: Long, packageNames: List<String>) {
        crossRefDao.deleteByTaskId(taskId)
        if (packageNames.isNotEmpty()) {
            val list = packageNames.map { pkg ->
                TaskAppCrossRef(
                    taskId = taskId,
                    appPackageName = pkg,
                    linkedAtWallMillis = System.currentTimeMillis()
                )
            }
            crossRefDao.insertCrossRefs(list)
        }
    }

    override suspend fun getAppsForTask(taskId: Long): List<AppEntity> {
        return crossRefDao.getAppsForTask(taskId)
    }

    override suspend fun getTasksForApp(packageName: String): List<TaskEntity> {
        return crossRefDao.getTasksForApp(packageName)
    }

    override fun observeAppsForTask(taskId: Long): Flow<List<AppEntity>> {
        return crossRefDao.observeAppsForTask(taskId)
    }

    override fun observeTasksForApp(packageName: String): Flow<List<TaskEntity>> {
        return crossRefDao.observeTasksForApp(packageName)
    }

    override suspend fun setTaskCompletion(taskId: Long, businessDate: String, isCompleted: Boolean) {
        if (isCompleted) {
            completionDao.setCompletion(
                DailyTaskCompletionEntity(
                    taskId = taskId,
                    businessDate = businessDate,
                    isCompleted = true,
                    completedAtWallMillis = System.currentTimeMillis()
                )
            )
        } else {
            completionDao.deleteCompletion(taskId, businessDate)
        }
    }

    override suspend fun isTaskCompletedOnDate(taskId: Long, businessDate: String): Boolean {
        val entry = completionDao.getCompletion(taskId, businessDate)
        return entry?.isCompleted == true
    }

    override fun observeTaskCompletion(taskId: Long, businessDate: String): Flow<Boolean> {
        return completionDao.observeCompletion(taskId, businessDate).map { it?.isCompleted == true }
    }

    override suspend fun getCompletedTaskIdsForDate(businessDate: String): List<Long> {
        return completionDao.getCompletionsForDate(businessDate)
            .filter { it.isCompleted }
            .map { it.taskId }
    }

    override fun observeCompletedTaskIdsForDate(businessDate: String): Flow<List<Long>> {
        return completionDao.observeCompletionsForDate(businessDate)
            .map { list -> list.filter { it.isCompleted }.map { it.taskId } }
    }

    override suspend fun getDailyCompletionsForDate(businessDate: String): List<DailyTaskCompletionEntity> {
        return completionDao.getCompletionsForDate(businessDate)
    }

    override fun observeDailyCompletionsForDate(businessDate: String): Flow<List<DailyTaskCompletionEntity>> {
        return completionDao.observeCompletionsForDate(businessDate)
    }
}
