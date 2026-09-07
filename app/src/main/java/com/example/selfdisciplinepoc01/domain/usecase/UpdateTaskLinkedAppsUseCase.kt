package com.example.selfdisciplinepoc01.domain.usecase

import com.example.selfdisciplinepoc01.data.repository.CoreDataRepository

/**
 * UseCase to update the many-to-many linkages between a Task and Vault Apps.
 *
 * Per Canonical Design V2 (Section 5 & 6):
 * - Only apps currently present in the Vault can be linked.
 * - Updating links overwrites the associations for this task with the selected set.
 * - Does NOT affect other tasks or other apps.
 */
class UpdateTaskLinkedAppsUseCase(
    private val repository: CoreDataRepository
) {
    suspend operator fun invoke(taskId: Long, selectedPackageNames: List<String>): Result<Unit> {
        return try {
            // Strict canonical validation: only allow apps currently existing in Vault
            val currentVaultPackages = repository.getAllVaultApps().map { it.packageName }.toSet()
            val validPackages = selectedPackageNames.filter { it in currentVaultPackages }.distinct()

            repository.syncTaskLinkedApps(taskId, validPackages)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
