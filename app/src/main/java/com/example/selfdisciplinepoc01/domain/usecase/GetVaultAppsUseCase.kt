package com.example.selfdisciplinepoc01.domain.usecase

import com.example.selfdisciplinepoc01.data.repository.CoreDataRepository
import com.example.selfdisciplinepoc01.domain.model.VaultApp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * UseCase to query and observe Vault Applications.
 */
class GetVaultAppsUseCase(
    private val repository: CoreDataRepository
) {
    suspend fun getAllVaultApps(): List<VaultApp> {
        val entities = repository.getAllVaultApps()
        return entities.map { entity ->
            val tasks = repository.getTasksForApp(entity.packageName)
            VaultApp(
                packageName = entity.packageName,
                appName = entity.appName,
                isVaultManaged = entity.isVaultManaged,
                createdAtWallMillis = entity.createdAtWallMillis,
                linkedTasksCount = tasks.size
            )
        }
    }

    fun observeVaultApps(): Flow<List<VaultApp>> {
        return repository.observeVaultApps().map { entities ->
            entities.map { entity ->
                val tasks = repository.getTasksForApp(entity.packageName)
                VaultApp(
                    packageName = entity.packageName,
                    appName = entity.appName,
                    isVaultManaged = entity.isVaultManaged,
                    createdAtWallMillis = entity.createdAtWallMillis,
                    linkedTasksCount = tasks.size
                )
            }
        }
    }
}
