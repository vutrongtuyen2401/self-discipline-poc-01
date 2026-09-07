package com.example.selfdisciplinepoc01.domain.usecase

import com.example.selfdisciplinepoc01.data.repository.CoreDataRepository
import com.example.selfdisciplinepoc01.domain.model.VaultApp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * UseCase to get the list of Vault applications linked to a specific Task.
 *
 * Per Canonical Design V2 (Section 5):
 * - One task can link to multiple apps.
 * - Selection must only come from current Vault apps.
 */
class GetTaskLinkedAppsUseCase(
    private val repository: CoreDataRepository
) {
    suspend operator fun invoke(taskId: Long): List<VaultApp> {
        val appEntities = repository.getAppsForTask(taskId)
        return appEntities.map { entity ->
            VaultApp(
                packageName = entity.packageName,
                appName = entity.appName,
                isVaultManaged = entity.isVaultManaged,
                createdAtWallMillis = entity.createdAtWallMillis
            )
        }
    }

    fun observe(taskId: Long): Flow<List<VaultApp>> {
        return repository.observeAppsForTask(taskId).map { entities ->
            entities.map { entity ->
                VaultApp(
                    packageName = entity.packageName,
                    appName = entity.appName,
                    isVaultManaged = entity.isVaultManaged,
                    createdAtWallMillis = entity.createdAtWallMillis
                )
            }
        }
    }
}
