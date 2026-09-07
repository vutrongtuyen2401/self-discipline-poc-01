package com.example.selfdisciplinepoc01.domain.usecase

import com.example.selfdisciplinepoc01.data.repository.CoreDataRepository

/**
 * UseCase for archiving/soft-deleting a Task.
 *
 * Per Canonical Design V2 (Section 5):
 * - A task archived/deleted while a sequential chain is active is skipped.
 * - The sequential chain automatically advances to the next valid task.
 */
class ArchiveTaskUseCase(
    private val repository: CoreDataRepository
) {
    suspend operator fun invoke(taskId: Long): Result<Unit> {
        return try {
            repository.archiveTask(taskId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
