package com.example.selfdisciplinepoc01.domain.enforcement

import com.example.selfdisciplinepoc01.data.repository.CoreDataRepository

/**
 * Enforcement status regarding Task-based sealing and unlocking.
 *
 * Explicitly guards OPEN-01.
 */
enum class TaskAppEnforcementStatus {
    /**
     * OPEN-01 remains OPEN in Canonical Design V2 (Section 7).
     * The exact unlock formula (2/3 rounding, thresholds, edge cases) is NOT yet finalized.
     * Therefore, task-based unlocking/sealing enforcement remains disabled pending canonical resolution.
     */
    DISABLED_PENDING_OPEN_01,

    /**
     * Technical App Lock enforcement (ScheduleEvaluator, UsageTracker via DataStore)
     * continues to operate as the frozen technical foundation.
     */
    TECHNICAL_FOUNDATION_ACTIVE
}

/**
 * Boundary adapter between Vault/Task product domain and Technical App Lock enforcement.
 *
 * CRITICAL GOVERNANCE:
 * - Does NOT implement 2/3 unlock formula.
 * - Does NOT implement percentage thresholds.
 * - Does NOT calculate automatic unlocking based on task completion count.
 * - Prevents conflating Room Vault membership with DataStore target configuration.
 */
class TaskAppEnforcementAdapter(
    private val repository: CoreDataRepository
) {
    fun getEnforcementStatus(): TaskAppEnforcementStatus {
        return TaskAppEnforcementStatus.DISABLED_PENDING_OPEN_01
    }

    /**
     * Evaluates whether an app has task-based unlock approval.
     * Currently returns false because OPEN-01 is OPEN.
     */
    suspend fun isTaskBasedUnlockApproved(packageName: String): Boolean {
        // OPEN-01 remains OPEN. No invented formula.
        return false
    }
}
