package com.example.selfdisciplinepoc01.domain.policy

import com.example.selfdisciplinepoc01.domain.enforcement.BusinessUnlockDecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.ceil

/**
 * Unit Test Suite for TaskUnlockPolicy (Product Decision OPEN-01).
 *
 * Requirements:
 * 1. Integer arithmetic formula: required = (2 * N + 2) / 3
 * 2. Strict equivalence with ceil(2 * N / 3) for all N >= 1, without floating point operations.
 * 3. Exact Canonical table verification for N = 0..10.
 * 4. Exhaustive tests for N = 0..35.
 * 5. Completion edge cases: 0%, right below threshold, exact threshold, 100%, and completed > N.
 * 6. Correct state mapping: NO_LINKED_TASKS, INSUFFICIENT_COMPLETION, UNLOCKED.
 */
class TaskUnlockPolicyTest {

    // ==================================================
    // 1. OFFICIAL CANONICAL VERIFICATION TABLE (N = 0..10)
    // ==================================================

    @Test
    fun testOfficialCanonicalTable_N0_to_N10() {
        // N = 0 -> required = 0, but unlock is false because N > 0 is mandatory
        assertEquals(0, TaskUnlockPolicy.calculateRequiredTasks(0))
        val res0 = TaskUnlockPolicy.evaluate(activeTaskCount = 0, completedTaskCount = 0)
        assertFalse("N=0 must never unlock", res0.isUnlocked)
        assertEquals(BusinessUnlockDecision.NO_LINKED_TASKS, res0.decision)
        assertEquals(0, res0.requiredCompletedTasks)

        // N = 1 -> required = 1
        assertEquals(1, TaskUnlockPolicy.calculateRequiredTasks(1))
        // N = 2 -> required = 2
        assertEquals(2, TaskUnlockPolicy.calculateRequiredTasks(2))
        // N = 3 -> required = 2
        assertEquals(2, TaskUnlockPolicy.calculateRequiredTasks(3))
        // N = 4 -> required = 3
        assertEquals(3, TaskUnlockPolicy.calculateRequiredTasks(4))
        // N = 5 -> required = 4
        assertEquals(4, TaskUnlockPolicy.calculateRequiredTasks(5))
        // N = 6 -> required = 4
        assertEquals(4, TaskUnlockPolicy.calculateRequiredTasks(6))
        // N = 7 -> required = 5
        assertEquals(5, TaskUnlockPolicy.calculateRequiredTasks(7))
        // N = 8 -> required = 6
        assertEquals(6, TaskUnlockPolicy.calculateRequiredTasks(8))
        // N = 9 -> required = 6
        assertEquals(6, TaskUnlockPolicy.calculateRequiredTasks(9))
        // N = 10 -> required = 7
        assertEquals(7, TaskUnlockPolicy.calculateRequiredTasks(10))
    }

    // ==================================================
    // 2. EXHAUSTIVE MATHEMATICAL PROOF (N = 0..35)
    // ==================================================

    @Test
    fun testExhaustiveIntegerVsCeilEquivalence_N0_to_N35() {
        for (n in 1..35) {
            val integerCalc = TaskUnlockPolicy.calculateRequiredTasks(n)
            val expectedCeil = ceil(2.0 * n / 3.0).toInt()
            assertEquals("Integer formula mismatch for N=$n", expectedCeil, integerCalc)
        }
    }

    // ==================================================
    // 3. COMPLETION EDGE CASES & STATE EVALUATION
    // ==================================================

    @Test
    fun testCompletionStates_forN3() {
        // N=3 -> required = 2
        // 0/3 (0%) -> LOCK
        val res0 = TaskUnlockPolicy.evaluate(activeTaskCount = 3, completedTaskCount = 0)
        assertFalse(res0.isUnlocked)
        assertEquals(BusinessUnlockDecision.INSUFFICIENT_COMPLETION, res0.decision)
        assertEquals(2, res0.requiredCompletedTasks)

        // 1/3 (Right below threshold: 1 < 2) -> LOCK
        val res1 = TaskUnlockPolicy.evaluate(activeTaskCount = 3, completedTaskCount = 1)
        assertFalse(res1.isUnlocked)
        assertEquals(BusinessUnlockDecision.INSUFFICIENT_COMPLETION, res1.decision)

        // 2/3 (Exact threshold: 2 >= 2) -> UNLOCK
        val res2 = TaskUnlockPolicy.evaluate(activeTaskCount = 3, completedTaskCount = 2)
        assertTrue(res2.isUnlocked)
        assertEquals(BusinessUnlockDecision.UNLOCKED, res2.decision)

        // 3/3 (100%: 3 >= 2) -> UNLOCK
        val res3 = TaskUnlockPolicy.evaluate(activeTaskCount = 3, completedTaskCount = 3)
        assertTrue(res3.isUnlocked)
        assertEquals(BusinessUnlockDecision.UNLOCKED, res3.decision)
    }

    @Test
    fun testCompletionStates_forN4() {
        // N=4 -> required = 3
        // 2/4 (Right below threshold) -> LOCK
        val res2 = TaskUnlockPolicy.evaluate(activeTaskCount = 4, completedTaskCount = 2)
        assertFalse(res2.isUnlocked)
        assertEquals(BusinessUnlockDecision.INSUFFICIENT_COMPLETION, res2.decision)

        // 3/4 (Exact threshold) -> UNLOCK
        val res3 = TaskUnlockPolicy.evaluate(activeTaskCount = 4, completedTaskCount = 3)
        assertTrue(res3.isUnlocked)
        assertEquals(BusinessUnlockDecision.UNLOCKED, res3.decision)
    }

    @Test
    fun testCompletionStates_forN5() {
        // N=5 -> required = 4
        // 3/5 (Right below threshold) -> LOCK
        val res3 = TaskUnlockPolicy.evaluate(activeTaskCount = 5, completedTaskCount = 3)
        assertFalse(res3.isUnlocked)
        assertEquals(BusinessUnlockDecision.INSUFFICIENT_COMPLETION, res3.decision)

        // 4/5 (Exact threshold) -> UNLOCK
        val res4 = TaskUnlockPolicy.evaluate(activeTaskCount = 5, completedTaskCount = 4)
        assertTrue(res4.isUnlocked)
        assertEquals(BusinessUnlockDecision.UNLOCKED, res4.decision)
    }

    // ==================================================
    // 4. BOUNDARY & NEGATIVE INPUT ROBUSTNESS
    // ==================================================

    @Test
    fun testBoundaryAndNegativeInputs() {
        // Negative active tasks
        assertEquals(0, TaskUnlockPolicy.calculateRequiredTasks(-5))
        val resNegativeN = TaskUnlockPolicy.evaluate(activeTaskCount = -1, completedTaskCount = 2)
        assertFalse(resNegativeN.isUnlocked)
        assertEquals(BusinessUnlockDecision.NO_LINKED_TASKS, resNegativeN.decision)

        // Negative completed tasks treated as 0
        val resNegativeCompleted = TaskUnlockPolicy.evaluate(activeTaskCount = 3, completedTaskCount = -1)
        assertFalse(resNegativeCompleted.isUnlocked)
        assertEquals(BusinessUnlockDecision.INSUFFICIENT_COMPLETION, resNegativeCompleted.decision)

        // Completed count exceeds active count (e.g., race or unlinked completion)
        val resOverflow = TaskUnlockPolicy.evaluate(activeTaskCount = 3, completedTaskCount = 5)
        assertTrue(resOverflow.isUnlocked)
        assertEquals(BusinessUnlockDecision.UNLOCKED, resOverflow.decision)
    }
}
