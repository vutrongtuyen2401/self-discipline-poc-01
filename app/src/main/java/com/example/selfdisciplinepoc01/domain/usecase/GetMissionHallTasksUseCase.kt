package com.example.selfdisciplinepoc01.domain.usecase

import com.example.selfdisciplinepoc01.data.entity.TaskEntity
import com.example.selfdisciplinepoc01.data.repository.CoreDataRepository
import com.example.selfdisciplinepoc01.domain.model.SequentialTaskChain
import com.example.selfdisciplinepoc01.domain.model.Task
import com.example.selfdisciplinepoc01.time.BusinessDayProvider
import com.example.selfdisciplinepoc01.time.Clock
import com.example.selfdisciplinepoc01.time.SystemClockImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.ZoneId

/**
 * UseCase for querying and assembling the Sequential Task Chain in Mission Hall.
 *
 * Per Canonical Design V2 (Section 5):
 * - Displays incomplete tasks for the current business day.
 * - Completed tasks are excluded from the incomplete list.
 * - Automatically advances to the next incomplete task.
 * - Archived tasks are omitted and skipped in the sequence.
 */
class GetMissionHallTasksUseCase(
    private val repository: CoreDataRepository,
    private val businessDayProvider: BusinessDayProvider,
    private val clock: Clock = SystemClockImpl(),
    private val zoneIdProvider: () -> ZoneId = { ZoneId.systemDefault() }
) {

    fun observeSequentialChain(
        wallTimeMillis: Long = clock.wallTimeMillis(),
        zoneId: ZoneId = zoneIdProvider()
    ): Flow<SequentialTaskChain> {
        val businessDate = businessDayProvider.getBusinessDate(wallTimeMillis, zoneId).toString()

        return combine(
            repository.observeActiveTasks(),
            repository.observeCompletedTaskIdsForDate(businessDate)
        ) { activeEntities, completedIds ->
            assembleChain(activeEntities, completedIds.toSet())
        }
    }

    suspend fun getSequentialChain(
        wallTimeMillis: Long = clock.wallTimeMillis(),
        zoneId: ZoneId = zoneIdProvider()
    ): SequentialTaskChain {
        val businessDate = businessDayProvider.getBusinessDate(wallTimeMillis, zoneId).toString()
        val activeEntities = repository.getAllActiveTasks()
        val completedIds = repository.getCompletedTaskIdsForDate(businessDate).toSet()

        return assembleChain(activeEntities, completedIds)
    }

    private fun assembleChain(
        activeEntities: List<TaskEntity>,
        completedIds: Set<Long>
    ): SequentialTaskChain {
        val allActiveTasks = activeEntities.map { entity ->
            Task(
                id = entity.id,
                name = entity.name,
                createdAtWallMillis = entity.createdAtWallMillis,
                isArchived = entity.isArchived
            )
        }

        val incomplete = allActiveTasks.filter { it.id !in completedIds }
        val completed = allActiveTasks.filter { it.id in completedIds }
        val current = incomplete.firstOrNull()

        return SequentialTaskChain(
            currentTask = current,
            incompleteTasks = incomplete,
            completedTasksToday = completed,
            totalActiveTasks = allActiveTasks.size,
            completedCountToday = completed.size
        )
    }
}
