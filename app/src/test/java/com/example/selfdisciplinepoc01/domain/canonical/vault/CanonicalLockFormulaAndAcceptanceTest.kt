package com.example.selfdisciplinepoc01.domain.canonical.vault

import com.example.selfdisciplinepoc01.domain.canonical.cycle.CanonicalCycleId
import com.example.selfdisciplinepoc01.domain.canonical.task.CanonicalTask
import com.example.selfdisciplinepoc01.domain.canonical.task.TaskCycleState
import com.example.selfdisciplinepoc01.domain.canonical.task.TaskCycleStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Unit Test Suite kiểm thử chính xác công thức Required(N) và các trường hợp Lock/Unlock Acceptance
 * theo MASTER SSOT - HỆ THỐNG PHONG ẤN DỤC VỌNG phục vụ nghiệm thu PHASE 2A-P0.2-CORRECTION.
 *
 * SSOT QUY ĐỊNH:
 * - N = 0 -> Required = 0 -> UNLOCKED
 * - N = 1 -> Required = 1
 * - N = 2 -> Required = 1 (Đặc xá khởi đầu)
 * - N = 3 -> Required = 2
 * - N = 4 -> Required = 3
 * - N = 5 -> Required = 4
 * - N = 6 -> Required = 4
 * - N > 6 -> ceil(2N / 3)
 *
 * Khóa (LOCKED) khi và chỉ khi: K < Required(N) VÀ không có voucher hữu hiệu.
 * Mở khóa (UNLOCKED) khi: K >= Required(N) HOẶC có voucher hữu hiệu.
 */
class CanonicalLockFormulaAndAcceptanceTest {

    private val testPackage = "com.android.chrome"
    private val cycle = CanonicalCycleId("2026-09-13")

    // =========================================================================
    // PHẦN 1: FORMULA TESTS (FORMULA-0 .. FORMULA-9)
    // =========================================================================

    @Test
    fun test_FORMULA_0() {
        assertEquals(0, CanonicalLockPolicy.calculateRequiredCompletions(0))
    }

    @Test
    fun test_FORMULA_1() {
        assertEquals(1, CanonicalLockPolicy.calculateRequiredCompletions(1))
    }

    @Test
    fun test_FORMULA_2() {
        // Special case: Đặc xá khởi đầu theo SSOT
        assertEquals(1, CanonicalLockPolicy.calculateRequiredCompletions(2))
    }

    @Test
    fun test_FORMULA_3() {
        assertEquals(2, CanonicalLockPolicy.calculateRequiredCompletions(3))
    }

    @Test
    fun test_FORMULA_4() {
        assertEquals(3, CanonicalLockPolicy.calculateRequiredCompletions(4))
    }

    @Test
    fun test_FORMULA_5() {
        assertEquals(4, CanonicalLockPolicy.calculateRequiredCompletions(5))
    }

    @Test
    fun test_FORMULA_6() {
        assertEquals(4, CanonicalLockPolicy.calculateRequiredCompletions(6))
    }

    @Test
    fun test_FORMULA_7() {
        // ceil(2 * 7 / 3) = ceil(14 / 3) = ceil(4.666...) = 5
        assertEquals(5, CanonicalLockPolicy.calculateRequiredCompletions(7))
    }

    @Test
    fun test_FORMULA_8() {
        // ceil(2 * 8 / 3) = ceil(16 / 3) = ceil(5.333...) = 6
        assertEquals(6, CanonicalLockPolicy.calculateRequiredCompletions(8))
    }

    @Test
    fun test_FORMULA_9() {
        // ceil(2 * 9 / 3) = ceil(18 / 3) = 6
        assertEquals(6, CanonicalLockPolicy.calculateRequiredCompletions(9))
    }

    // =========================================================================
    // PHẦN 2: ACCEPTANCE TESTS (LOCK-0 .. LOCK-4, LOCK-V, LOCK-R)
    // =========================================================================

    /** Case A (LOCK-0): N=0, K=0 -> UNLOCKED */
    @Test
    fun test_LOCK_0_CaseA_N0_K0() {
        val result = CanonicalLockPolicy.evaluateLock(
            packageName = testPackage,
            linkedTasks = emptyList(),
            cycleTaskStates = emptyMap()
        )
        assertTrue(result.isUnlocked)
        assertEquals(CanonicalLockDecision.UNLOCKED, result.decision)
        assertEquals(0, result.totalLinkedRewardTasks)
        assertEquals(0, result.requiredCompletions)
    }

    /** Case B (LOCK-1): N=1, K=0 -> LOCKED */
    @Test
    fun test_LOCK_1_CaseB_N1_K0() {
        val tasks = listOf(CanonicalTask(id = "t1", title = "Task 1", hasReward = true))
        val result = CanonicalAppLockPolicy.evaluateLock(
            packageName = testPackage,
            linkedTasks = tasks,
            cycleTaskStates = emptyMap() // K = 0
        )
        assertTrue(result.isLocked)
        assertEquals(CanonicalLockDecision.LOCKED, result.decision)
        assertEquals(1, result.totalLinkedRewardTasks)
        assertEquals(0, result.completedLinkedRewardTasks)
    }

    /** Case C: N=1, K=1 -> STILL LOCKED theo SSOT (Task link vẫn tồn tại trong chu kỳ) */
    @Test
    fun test_CaseC_N1_K1_StillLocked() {
        val tasks = listOf(CanonicalTask(id = "t1", title = "Task 1", hasReward = true))
        val states = mapOf("t1" to TaskCycleState("t1", cycle, TaskCycleStatus.COMPLETED))
        val result = CanonicalAppLockPolicy.evaluateLock(
            packageName = testPackage,
            linkedTasks = tasks,
            cycleTaskStates = states // K = 1
        )
        assertTrue("N=1 có K=1 nhưng reward link vẫn tồn tại -> VẪN LOCKED theo Lock Policy", result.isLocked)
        assertEquals(CanonicalLockDecision.LOCKED, result.decision)
        assertEquals(1, result.totalLinkedRewardTasks)
        assertEquals(1, result.completedLinkedRewardTasks)
    }

    /** Case D (LOCK-2): N=2, K=0 -> LOCKED */
    @Test
    fun test_LOCK_2_CaseD_N2_K0() {
        val tasks = listOf(
            CanonicalTask(id = "t1", title = "Task 1", hasReward = true),
            CanonicalTask(id = "t2", title = "Task 2", hasReward = true)
        )
        val result = CanonicalAppLockPolicy.evaluateLock(
            packageName = testPackage,
            linkedTasks = tasks,
            cycleTaskStates = emptyMap() // K = 0
        )
        assertTrue(result.isLocked)
        assertEquals(CanonicalLockDecision.LOCKED, result.decision)
        assertEquals(2, result.totalLinkedRewardTasks)
        assertEquals(0, result.completedLinkedRewardTasks)
    }

    /** Case E: N=2, K=1 -> STILL LOCKED (App vẫn LOCKED dù K=1) */
    @Test
    fun test_CaseE_N2_K1_StillLocked() {
        val tasks = listOf(
            CanonicalTask(id = "t1", title = "Task 1", hasReward = true),
            CanonicalTask(id = "t2", title = "Task 2", hasReward = true)
        )
        val states = mapOf("t1" to TaskCycleState("t1", cycle, TaskCycleStatus.COMPLETED))
        val result = CanonicalAppLockPolicy.evaluateLock(
            packageName = testPackage,
            linkedTasks = tasks,
            cycleTaskStates = states // K = 1
        )
        assertTrue("N=2 có K=1 -> VẪN LOCKED vì còn task yêu cầu", result.isLocked)
        assertEquals(CanonicalLockDecision.LOCKED, result.decision)
        assertEquals(2, result.totalLinkedRewardTasks)
        assertEquals(1, result.completedLinkedRewardTasks)
    }

    /** Case F (LOCK-3): N=3, K=1 -> LOCKED */
    @Test
    fun test_LOCK_3_CaseF_N3_K1() {
        val tasks = listOf(
            CanonicalTask(id = "t1", title = "Task 1", hasReward = true),
            CanonicalTask(id = "t2", title = "Task 2", hasReward = true),
            CanonicalTask(id = "t3", title = "Task 3", hasReward = true)
        )
        val states = mapOf("t1" to TaskCycleState("t1", cycle, TaskCycleStatus.COMPLETED))
        val result = CanonicalAppLockPolicy.evaluateLock(
            packageName = testPackage,
            linkedTasks = tasks,
            cycleTaskStates = states // K = 1
        )
        assertTrue(result.isLocked)
        assertEquals(CanonicalLockDecision.LOCKED, result.decision)
        assertEquals(3, result.totalLinkedRewardTasks)
        assertEquals(1, result.completedLinkedRewardTasks)
    }

    /** Case G: N=3, K=2 -> STILL LOCKED */
    @Test
    fun test_CaseG_N3_K2_StillLocked() {
        val tasks = listOf(
            CanonicalTask(id = "t1", title = "Task 1", hasReward = true),
            CanonicalTask(id = "t2", title = "Task 2", hasReward = true),
            CanonicalTask(id = "t3", title = "Task 3", hasReward = true)
        )
        val states = mapOf(
            "t1" to TaskCycleState("t1", cycle, TaskCycleStatus.COMPLETED),
            "t2" to TaskCycleState("t2", cycle, TaskCycleStatus.COMPLETED)
        )
        val result = CanonicalAppLockPolicy.evaluateLock(
            packageName = testPackage,
            linkedTasks = tasks,
            cycleTaskStates = states // K = 2
        )
        assertTrue("N=3 có K=2 -> VẪN LOCKED vì còn task yêu cầu", result.isLocked)
        assertEquals(CanonicalLockDecision.LOCKED, result.decision)
        assertEquals(3, result.totalLinkedRewardTasks)
        assertEquals(2, result.completedLinkedRewardTasks)
    }

    /** Case H (LOCK-4): N=4, K=2 -> LOCKED */
    @Test
    fun test_LOCK_4_CaseH_N4_K2() {
        val tasks = (1..4).map { CanonicalTask(id = "t$it", title = "Task $it", hasReward = true) }
        val states = mapOf(
            "t1" to TaskCycleState("t1", cycle, TaskCycleStatus.COMPLETED),
            "t2" to TaskCycleState("t2", cycle, TaskCycleStatus.COMPLETED)
        )
        val result = CanonicalAppLockPolicy.evaluateLock(
            packageName = testPackage,
            linkedTasks = tasks,
            cycleTaskStates = states // K = 2
        )
        assertTrue(result.isLocked)
        assertEquals(CanonicalLockDecision.LOCKED, result.decision)
        assertEquals(4, result.totalLinkedRewardTasks)
        assertEquals(2, result.completedLinkedRewardTasks)
    }

    /** Case I: N=4, K=3 -> STILL LOCKED */
    @Test
    fun test_CaseI_N4_K3_StillLocked() {
        val tasks = (1..4).map { CanonicalTask(id = "t$it", title = "Task $it", hasReward = true) }
        val states = (1..3).associate { "t$it" to TaskCycleState("t$it", cycle, TaskCycleStatus.COMPLETED) }
        val result = CanonicalAppLockPolicy.evaluateLock(
            packageName = testPackage,
            linkedTasks = tasks,
            cycleTaskStates = states // K = 3
        )
        assertTrue("N=4 có K=3 -> VẪN LOCKED vì còn task yêu cầu", result.isLocked)
        assertEquals(CanonicalLockDecision.LOCKED, result.decision)
        assertEquals(4, result.totalLinkedRewardTasks)
        assertEquals(3, result.completedLinkedRewardTasks)
    }

    /** Case J (LOCK-V): Effective voucher exists + insufficient K -> UNLOCKED */
    @Test
    fun test_LOCK_V_CaseJ_EffectiveVoucherOverrides() {
        val tasks = (1..3).map { CanonicalTask(id = "t$it", title = "Task $it", hasReward = true) }
        val now = Instant.now()
        val activeVoucher = VoucherEffect(
            voucherId = "voucher_pass_01",
            voucherName = "Kim Bài Miễn Tử",
            targetPackageName = testPackage,
            effectiveFrom = now.minusSeconds(60),
            effectiveUntil = now.plusSeconds(3600)
        )
        // K = 0 < Required(3) = 2, nhưng có voucher hợp lệ
        val result = CanonicalLockPolicy.evaluateLock(
            packageName = testPackage,
            linkedTasks = tasks,
            cycleTaskStates = emptyMap(),
            activeVouchers = listOf(activeVoucher),
            evaluationInstant = now
        )
        assertTrue("Voucher còn hiệu lực phải ghi đè trạng thái khóa", result.isUnlocked)
        assertEquals(CanonicalLockDecision.UNLOCKED, result.decision)
        assertEquals(activeVoucher, result.effectiveVoucher)
    }

    /** Case K (LOCK-R): Rewardless tasks only -> N=0 -> UNLOCKED */
    @Test
    fun test_LOCK_R_CaseK_RewardlessTasksOnly() {
        val tasks = listOf(
            CanonicalTask(id = "t_story", title = "Nhiệm vụ cốt truyện", hasReward = false),
            CanonicalTask(id = "t_challenge", title = "Nhiệm vụ thử thách", hasReward = false)
        )
        val result = CanonicalLockPolicy.evaluateLock(
            packageName = testPackage,
            linkedTasks = tasks,
            cycleTaskStates = emptyMap()
        )
        assertTrue("Chỉ có rewardless tasks thì mẫu số N=0 -> UNLOCKED", result.isUnlocked)
        assertEquals(CanonicalLockDecision.UNLOCKED, result.decision)
        assertEquals(0, result.totalLinkedRewardTasks)
        assertEquals(0, result.requiredCompletions)
    }

    // =========================================================================
    // PHẦN 3: DELETION ACCEPTANCE TESTS (K >= RequiredForDeletion(N))
    // =========================================================================

    @Test
    fun test_DELETE_N1_K0_Denied() {
        assertFalse(CanonicalAppDeletionPolicy.canDelete(totalLinkedRewardTasks = 1, completedRewardTasks = 0))
    }

    @Test
    fun test_DELETE_N1_K1_Allowed() {
        assertTrue(CanonicalAppDeletionPolicy.canDelete(totalLinkedRewardTasks = 1, completedRewardTasks = 1))
    }

    @Test
    fun test_DELETE_N2_K0_Denied() {
        assertFalse(CanonicalAppDeletionPolicy.canDelete(totalLinkedRewardTasks = 2, completedRewardTasks = 0))
    }

    @Test
    fun test_DELETE_N2_K1_Allowed_WhileAppStillLocked() {
        // N=2, K=1: Được phép xóa khỏi Vault
        assertTrue("N=2 có K=1 được phép xóa app", CanonicalAppDeletionPolicy.canDelete(totalLinkedRewardTasks = 2, completedRewardTasks = 1))

        // NHƯNG App vẫn LOCKED theo Lock Policy nếu reward link còn tồn tại!
        val tasks = listOf(
            CanonicalTask(id = "t1", title = "Task 1", hasReward = true),
            CanonicalTask(id = "t2", title = "Task 2", hasReward = true)
        )
        val states = mapOf("t1" to TaskCycleState("t1", cycle, TaskCycleStatus.COMPLETED))
        val lockResult = CanonicalAppLockPolicy.evaluateLock(testPackage, tasks, states)
        assertTrue("N=2 K=1 thì APP VẪN LOCKED!", lockResult.isLocked)
    }

    @Test
    fun test_DELETE_N3_K1_Denied() {
        assertFalse(CanonicalAppDeletionPolicy.canDelete(totalLinkedRewardTasks = 3, completedRewardTasks = 1))
    }

    @Test
    fun test_DELETE_N3_K2_Allowed() {
        assertTrue(CanonicalAppDeletionPolicy.canDelete(totalLinkedRewardTasks = 3, completedRewardTasks = 2))
    }
}
