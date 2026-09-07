package com.example.selfdisciplinepoc01.domain.model

/**
 * Domain model for an application discovered from device PackageManager.
 */
data class DiscoveredApp(
    val packageName: String,
    val appName: String,
    val isAlreadyInVault: Boolean = false
)

/**
 * Domain model for an application managed inside Vault (Bảo Khố).
 *
 * Per Canonical Design V2 (Section 6):
 * - Identifiable by package name and friendly application name.
 * - Sealed/Disciplined when linked to tasks.
 * - NO lock icon on avatar; status denoted via color.
 */
data class VaultApp(
    val packageName: String,
    val appName: String,
    val isVaultManaged: Boolean = true,
    val createdAtWallMillis: Long = System.currentTimeMillis(),
    val linkedTasksCount: Int = 0
)
