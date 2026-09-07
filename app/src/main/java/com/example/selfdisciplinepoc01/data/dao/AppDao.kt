package com.example.selfdisciplinepoc01.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.selfdisciplinepoc01.data.entity.AppEntity
import kotlinx.coroutines.flow.Flow

/**
 * Proposed DAO for Vault Applications.
 *
 * Technical foundation for Checkpoint CP6.
 * Note: Database schema is a proposal; OPEN-05 remains OPEN.
 */
@Dao
interface AppDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertApp(app: AppEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertApps(apps: List<AppEntity>)

    @Update
    suspend fun updateApp(app: AppEntity)

    @Delete
    suspend fun deleteApp(app: AppEntity)

    @Query("DELETE FROM vault_apps WHERE packageName = :packageName")
    suspend fun deleteByPackageName(packageName: String)

    @Query("SELECT * FROM vault_apps WHERE packageName = :packageName")
    suspend fun getApp(packageName: String): AppEntity?

    @Query("SELECT * FROM vault_apps")
    suspend fun getAllApps(): List<AppEntity>

    @Query("SELECT * FROM vault_apps WHERE isVaultManaged = 1")
    suspend fun getAllVaultApps(): List<AppEntity>

    @Query("SELECT * FROM vault_apps WHERE isVaultManaged = 1")
    fun observeAllVaultApps(): Flow<List<AppEntity>>
}
