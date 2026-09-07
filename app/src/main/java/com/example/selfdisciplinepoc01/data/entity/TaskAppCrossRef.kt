package com.example.selfdisciplinepoc01.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Proposed Cross-Reference Entity modeling the Many-to-Many relationship between
 * Tasks (Nhiệm Vụ) and Applications (Bảo Khố).
 *
 * Part of Core Data Architecture (Technical Foundation for Checkpoint CP6).
 * Note: Database schema is a proposal; OPEN-05 remains OPEN.
 *
 * Per Canonical Design V2 (Section 4, 5, 6):
 * - One task can link to multiple apps.
 * - One app can link to multiple tasks.
 * - If an app is linked to 2 tasks, they count as 2 distinct tasks, not merged.
 * - Foreign keys enforce referential integrity; removing an app from Vault
 *   cascades to removing its association in this table.
 */
@Entity(
    tableName = "task_app_cross_ref",
    primaryKeys = ["taskId", "appPackageName"],
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = AppEntity::class,
            parentColumns = ["packageName"],
            childColumns = ["appPackageName"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["taskId"]),
        Index(value = ["appPackageName"])
    ]
)
data class TaskAppCrossRef(
    val taskId: Long,
    val appPackageName: String,
    val linkedAtWallMillis: Long = System.currentTimeMillis()
)
