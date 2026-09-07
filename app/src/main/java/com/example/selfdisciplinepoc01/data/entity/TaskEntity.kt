package com.example.selfdisciplinepoc01.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Proposed Entity for self-discipline tasks created in Mission Hall (Nhiệm Vụ Đường).
 *
 * Part of Core Data Architecture (Technical Foundation for Checkpoint CP3).
 * Note: Database schema is a proposal; OPEN-05 remains OPEN.
 *
 * Per Canonical Design V2 (Section 5):
 * - Form creation is minimalistic: Task Name + Confirmation.
 * - Tasks can be linked to one or multiple Vault applications as unlock rewards.
 * - Tasks are sequential; completed tasks disappear from pending list.
 */
@Entity(tableName = "mission_tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val createdAtWallMillis: Long = System.currentTimeMillis(),
    val isArchived: Boolean = false
)
