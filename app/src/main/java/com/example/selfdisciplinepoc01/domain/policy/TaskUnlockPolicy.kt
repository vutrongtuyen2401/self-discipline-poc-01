package com.example.selfdisciplinepoc01.domain.policy

import com.example.selfdisciplinepoc01.domain.enforcement.BusinessUnlockDecision

/**
 * Result of evaluating task-based business unlock policy for a Vault application.
 */
data class BusinessUnlockResult(
    val decision: BusinessUnlockDecision,
    val requiredCompletedTasks: Int,
    val isUnlocked: Boolean
)

/**
 * LEGACY COMPONENT — NON-CANONICAL RUNTIME:
 * Chứa các sai lệch đã được ghi nhận so với MASTER SSOT:
 * - N=0 bị gán LOCK (SSOT yêu cầu UNLOCKED).
 * - N=2 tính ra 2 required (SSOT yêu cầu 1 do chính sách đặc xá khởi đầu).
 *
 * CANONICAL MIGRATION:
 * Thành phần này KHÔNG thuộc canonical runtime path của Hệ Thống Phong Ấn Dục Vọng.
 * CanonicalLockPolicy / CanonicalLockEvaluator là nguồn chân lý thẩm quyền duy nhất.
 */
@Deprecated(
    message = "Legacy POC-01 TaskUnlockPolicy containing SSOT discrepancies. Do not use for Canonical Core. Superseded by CanonicalLockPolicy.",
    level = DeprecationLevel.WARNING
)
object TaskUnlockPolicy {

    /**
     * Calculates the required number of completed tasks to unlock an application.
     * Uses pure integer arithmetic: (2 * N + 2) / 3.
     *
     * Exact verification table:
     * - N = 0  -> 0 (does not unlock, requires N > 0)
     * - N = 1  -> (2*1 + 2)/3 = 1
     * - N = 2  -> (2*2 + 2)/3 = 2
     * - N = 3  -> (2*3 + 2)/3 = 2
     * - N = 4  -> (2*4 + 2)/3 = 3
     * - N = 5  -> (2*5 + 2)/3 = 4
     * - N = 6  -> (2*6 + 2)/3 = 4
     * - N = 7  -> (2*7 + 2)/3 = 5
     * - N = 8  -> (2*8 + 2)/3 = 6
     * - N = 9  -> (2*9 + 2)/3 = 6
     * - N = 10 -> (2*10 + 2)/3 = 7
     */
    fun calculateRequiredTasks(n: Int): Int {
        if (n <= 0) return 0
        return (2 * n + 2) / 3
    }

    /**
     * Evaluates the business unlock decision given the active and completed task counts.
     */
    fun evaluate(activeTaskCount: Int, completedTaskCount: Int): BusinessUnlockResult {
        if (activeTaskCount <= 0) {
            return BusinessUnlockResult(
                decision = BusinessUnlockDecision.NO_LINKED_TASKS,
                requiredCompletedTasks = 0,
                isUnlocked = false
            )
        }

        val required = calculateRequiredTasks(activeTaskCount)
        val isUnlocked = (completedTaskCount >= required)

        val decision = if (isUnlocked) {
            BusinessUnlockDecision.UNLOCKED
        } else {
            BusinessUnlockDecision.INSUFFICIENT_COMPLETION
        }

        return BusinessUnlockResult(
            decision = decision,
            requiredCompletedTasks = required,
            isUnlocked = isUnlocked
        )
    }
}
