package com.example.selfdisciplinepoc01.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Proposed Entity for applications managed within the Vault (Bảo Khố).
 *
 * Part of Core Data Architecture (Technical Foundation for Checkpoint CP6).
 * Note: Database schema is a proposal; OPEN-05 remains OPEN.
 *
 * Per Canonical Design V2 (Section 6):
 * - Vault is where apps under self-discipline control are registered.
 * - When an app is removed from Vault, all task associations are removed.
 * - Re-adding an app restores it to unsealed state before new tasks are attached.
 */
@Entity(tableName = "vault_apps")
data class AppEntity(
    @PrimaryKey
    val packageName: String,
    val appName: String,
    val isVaultManaged: Boolean = true,
    val createdAtWallMillis: Long = System.currentTimeMillis()
)
