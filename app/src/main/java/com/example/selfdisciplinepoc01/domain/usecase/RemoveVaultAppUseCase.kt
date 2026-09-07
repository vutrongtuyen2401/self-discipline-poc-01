package com.example.selfdisciplinepoc01.domain.usecase

import com.example.selfdisciplinepoc01.data.repository.CoreDataRepository

/**
 * UseCase to remove an application from the Vault (Bảo Khố).
 *
 * Per Canonical Design V2 (Section 6):
 * - Removing an app from Vault cascades to removing all its Task ↔ App linkages.
 * - Tasks remain intact.
 * - Daily task completion history remains intact.
 * - Other apps in the Vault remain unaffected.
 */
class RemoveVaultAppUseCase(
    private val repository: CoreDataRepository
) {
    suspend operator fun invoke(packageName: String): Result<Unit> {
        val trimmedPkg = packageName.trim()
        if (trimmedPkg.isBlank()) {
            return Result.failure(IllegalArgumentException("Package name không hợp lệ"))
        }
        return try {
            repository.removeVaultApp(trimmedPkg)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
