package com.example.selfdisciplinepoc01.domain.usecase

import com.example.selfdisciplinepoc01.data.repository.CoreDataRepository

/**
 * UseCase to register an application into the Vault (Bảo Khố).
 *
 * Per Canonical Design V2 (Section 6):
 * - Idempotent: package name is primary identity.
 * - Restores app to unsealed status prior to user attaching new tasks.
 */
class AddVaultAppUseCase(
    private val repository: CoreDataRepository
) {
    suspend operator fun invoke(packageName: String, appName: String): Result<Unit> {
        val trimmedPkg = packageName.trim()
        val trimmedName = appName.trim()
        if (trimmedPkg.isBlank()) {
            return Result.failure(IllegalArgumentException("Package name không được để trống"))
        }
        val finalName = if (trimmedName.isBlank()) trimmedPkg else trimmedName
        return try {
            repository.addVaultApp(trimmedPkg, finalName)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
