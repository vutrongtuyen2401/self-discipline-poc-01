package com.example.selfdisciplinepoc01.domain.model

/**
 * Clean Domain Model for a Self-Discipline Task.
 *
 * Core Product Entity for Checkpoint CP3 (Mission Hall).
 *
 * Per Canonical Design V2 (Section 5):
 * - Minimalistic: task name + confirmation.
 * - Stable identity, created timestamp, archive flag.
 * - Sequential execution: completed tasks disappear from pending list.
 * - Archiving/deleting a task causes it to be skipped in the sequential chain.
 *
 * GOVERNANCE:
 * - NO unlock threshold (OPEN-01)
 * - NO point/reward formulas (OPEN-02)
 * - NO cultivation semantics
 */
data class Task(
    val id: Long = 0L,
    val name: String,
    val createdAtWallMillis: Long = System.currentTimeMillis(),
    val isArchived: Boolean = false
)

/**
 * Represents a task enriched with its completion status for a specific business day.
 */
data class TaskWithStatus(
    val task: Task,
    val isCompletedToday: Boolean = false,
    val completedAtWallMillis: Long? = null
)

/**
 * Encapsulates the deterministic sequential flow of tasks for a given business day.
 *
 * Per Canonical Design V2 (Section 5):
 * - Tasks are executed in sequence (ordered deterministically by id ASC).
 * - [currentTask]: The first active, non-archived task that is NOT yet completed today.
 * - [incompleteTasks]: All active tasks not yet completed today, in order.
 * - [completedTasksToday]: Tasks completed during the current business day.
 * - If a task is archived/deleted mid-chain, it is skipped and [currentTask] advances to next valid.
 */
data class SequentialTaskChain(
    val currentTask: Task?,
    val incompleteTasks: List<Task>,
    val completedTasksToday: List<Task>,
    val totalActiveTasks: Int,
    val completedCountToday: Int
) {
    val isAllCompleted: Boolean
        get() = totalActiveTasks > 0 && completedCountToday >= totalActiveTasks

    val isEmpty: Boolean
        get() = totalActiveTasks == 0
}
