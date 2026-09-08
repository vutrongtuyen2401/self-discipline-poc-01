package com.example.selfdisciplinepoc01.domain.usecase

import com.example.selfdisciplinepoc01.data.repository.CoreDataRepository
import com.example.selfdisciplinepoc01.time.BusinessDayProvider
import com.example.selfdisciplinepoc01.time.Clock
import com.example.selfdisciplinepoc01.time.SystemClockImpl
import java.time.ZoneId

/**
 * UseCase for completing a Task bound to the canonical 04:00 daily cycle.
 *
 * Per Canonical Design V2 (Section 8):
 * - Resets occur at 04:00:00 local time.
 * - Tasks started before 04:00 belong to the previous cycle even if completed after 04:00.
 * - Operation is idempotent and does not mutate historical days.
 */
class CompleteTaskUseCase(
    private val repository: CoreDataRepository,
    private val businessDayProvider: BusinessDayProvider,
    private val clock: Clock = SystemClockImpl(),
    private val zoneIdProvider: () -> ZoneId = { ZoneId.systemDefault() }
) {
    suspend operator fun invoke(
        taskId: Long,
        completedWallTimeMillis: Long = clock.wallTimeMillis(),
        taskStartWallMillis: Long? = null,
        zoneId: ZoneId = zoneIdProvider()
    ): Result<Unit> {
        return try {
            val businessDate = if (taskStartWallMillis != null) {
                businessDayProvider.getTaskBusinessDate(taskStartWallMillis, zoneId).toString()
            } else {
                businessDayProvider.getBusinessDate(completedWallTimeMillis, zoneId).toString()
            }
            repository.setTaskCompletion(taskId, businessDate, isCompleted = true)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
