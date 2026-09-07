package com.example.selfdisciplinepoc01.domain.usecase

import com.example.selfdisciplinepoc01.data.repository.CoreDataRepository

/**
 * UseCase for creating a new Task in Mission Hall.
 *
 * Per Canonical Design V2 (Section 5):
 * - Minimalist creation: task name + confirmation.
 * - Whitespace is trimmed.
 * - Empty name is rejected.
 */
class CreateTaskUseCase(
    private val repository: CoreDataRepository
) {
    suspend operator fun invoke(name: String): Result<Long> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Tên nhiệm vụ không được để trống"))
        }
        return try {
            val id = repository.createTask(trimmed)
            Result.success(id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
