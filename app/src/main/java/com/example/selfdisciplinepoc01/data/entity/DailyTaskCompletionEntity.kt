package com.example.selfdisciplinepoc01.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Proposed Entity recording daily task completion states bound to a specific Business Day.
 *
 * Part of Core Data Architecture (Technical Foundation for Checkpoint CP3 & CP5).
 * Note: Database schema is a proposal; OPEN-05 remains OPEN.
 *
 * Per Canonical Design V2 (Section 5 & 8):
 * - Daily cycle resets at 04:00 local time.
 * - Tasks reset to uncompleted status each cycle.
 * - Storing completion by (taskId, businessDate) guarantees natural daily resets
 *   without mutating the static definition of the task.
 */
@Entity(
    tableName = "daily_task_completions",
    primaryKeys = ["taskId", "businessDate"],
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["businessDate"]),
        Index(value = ["taskId"])
    ]
)
data class DailyTaskCompletionEntity(
    val taskId: Long,
    val businessDate: String, // format: "yyyy-MM-dd" calculated via BusinessDayProvider
    val isCompleted: Boolean = true,
    val completedAtWallMillis: Long = System.currentTimeMillis()
)
