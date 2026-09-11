package com.example.selfdisciplinepoc01.target.repository

import com.example.selfdisciplinepoc01.target.model.LockedApp
import com.example.selfdisciplinepoc01.target.model.TimeLimit
import com.example.selfdisciplinepoc01.target.model.TimeSchedule
import kotlinx.coroutines.flow.Flow

/**
 * LEGACY COMPONENT (POC-01):
 * Repository quản lý cấu hình các ứng dụng bị khóa theo Target model cũ (schedule, time limit).
 *
 * CANONICAL ISOLATION (Phase 1A):
 * Không được phép làm authority cho Vault App trong Hệ thống Phong Ấn Dục Vọng.
 * CanonicalVaultRepository là hợp đồng chính thức cho Vault Apps.
 */
@Deprecated(
    message = "Legacy POC-01 TargetRepository. Superseded by CanonicalVaultRepository.",
    level = DeprecationLevel.WARNING
)
interface TargetRepository {
    /**
     * Emits the current reactive list of configured targets.
     */
    fun getLockedPackages(): Flow<List<LockedApp>>

    /**
     * Synchronously checks if [packageName] is currently configured and enabled.
     * Guaranteed sub-millisecond return via in-memory cache.
     */
    fun isLocked(packageName: String): Boolean

    /**
     * Synchronously returns the configured [LockedApp] for [packageName], or null if not registered.
     * Guaranteed sub-millisecond return via in-memory cache.
     */
    fun getTarget(packageName: String): LockedApp?

    /**
     * Adds a new package as a locked target.
     */
    suspend fun add(packageName: String)

    /**
     * Removes a target package.
     */
    suspend fun remove(packageName: String)

    /**
     * Enables or disables a target package.
     */
    suspend fun setEnabled(packageName: String, enabled: Boolean)

    /**
     * Updates policy configuration (schedule and/or daily limit) for [packageName].
     */
    suspend fun updatePolicy(
        packageName: String,
        schedule: TimeSchedule?,
        timeLimit: TimeLimit?
    )
}
