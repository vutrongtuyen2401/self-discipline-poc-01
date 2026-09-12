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
 * Unit Test Suite cho CanonicalLockPolicy theo MASTER SSOT - HỆ THỐNG PHONG ẤN DỤC VỌNG.
 *
 * Kiểm tra các Invariant SSOT bắt buộc:
 * 3. Rewardless task exclusion from lock denominator (INV-TASK-001)
 * 4. N=1 -> required 1
 * 5. N=2 -> required 1 (Đặc xá khởi đầu bắt buộc theo SSOT)
 * 6. N=3 -> required 2
 * 7. N=4 -> required 3
 * 8. N=5 -> required 4
 * 9. N=6 -> required 4
 * 10. New app without linked reward task => unlocked (N=0 => UNLOCKED)
 * 11. App lock requires incomplete current-cycle linked reward task AND no effective voucher
 */
class CanonicalLockPolicyTest {

    private val currentCycle = CanonicalCycleId("2026-09-11")
    private val testAppPkg = "com.facebook.katana"

    // ==================================================
    // BẢNG CHUẨN HÓA SSOT (N=0..6 và N>6)
    // ==================================================

    @Test
    fun test01_canonicalTableRequirements_N1_to_N6() {
        // N=0 -> 0
        assertEquals(0, CanonicalLockPolicy.calculateRequiredCompletions(0))

        // Invariant 4: N=1 -> required 1
        assertEquals(1, CanonicalLockPolicy.calculateRequiredCompletions(1))

        // Invariant 5: N=2 -> required 1 (Đặc xá khởi đầu theo MASTER SSOT)
        assertEquals(1, CanonicalLockPolicy.calculateRequiredCompletions(2))

        // Invariant 6: N=3 -> required 2
        assertEquals(2, CanonicalLockPolicy.calculateRequiredCompletions(3))

        // Invariant 7: N=4 -> required 3
        assertEquals(3, CanonicalLockPolicy.calculateRequiredCompletions(4))

        // Invariant 8: N=5 -> required 4
        assertEquals(4, CanonicalLockPolicy.calculateRequiredCompletions(5))

        // Invariant 9: N=6 -> required 4
        assertEquals(4, CanonicalLockPolicy.calculateRequiredCompletions(6))

        // N=7 -> ceil(2*7/3) = ceil(4.66) = 5
        assertEquals(5, CanonicalLockPolicy.calculateRequiredCompletions(7))

        // N=8 -> ceil(2*8/3) = ceil(5.33) = 6
        assertEquals(6, CanonicalLockPolicy.calculateRequiredCompletions(8))
    }

    // ==================================================
    // INVARIANT 10: NEW APP WITHOUT LINKED REWARD TASK => UNLOCKED (N=0)
    // ==================================================

    @Test
    fun test02_newAppWithoutLinkedRewardTasksIsUnlocked() {
        val evalResult = CanonicalLockPolicy.evaluateLock(
            packageName = testAppPkg,
            linkedTasks = emptyList(),
            cycleTaskStates = emptyMap()
        )

        assertTrue("App không có linked task (N=0) phải ở trạng thái UNLOCKED", evalResult.isUnlocked)
        assertEquals(CanonicalLockDecision.UNLOCKED, evalResult.decision)
        assertEquals(0, evalResult.totalLinkedRewardTasks)
        assertEquals(0, evalResult.requiredCompletions)
    }

    // ==================================================
    // INVARIANT 3: REWARDLESS TASK EXCLUSION (INV-TASK-001)
    // ==================================================

    @Test
    fun test03_rewardlessTaskExclusionFromDenominator() {
        // Có 1 task có thưởng và 2 task không có thưởng (rewardless)
        val taskWithReward = CanonicalTask(id = "task_reward_1", title = "Task có thưởng", hasReward = true)
        val taskRewardless1 = CanonicalTask(id = "task_no_reward_1", title = "Cốt truyện", hasReward = false)
        val taskRewardless2 = CanonicalTask(id = "task_no_reward_2", title = "Thử thách", hasReward = false)

        val linkedTasks = listOf(taskWithReward, taskRewardless1, taskRewardless2)

        // Khi chưa hoàn thành task nào: N chỉ tính task có thưởng (N=1), required=1
        val evalResultPending = CanonicalLockPolicy.evaluateLock(
            packageName = testAppPkg,
            linkedTasks = linkedTasks,
            cycleTaskStates = mapOf(
                "task_reward_1" to TaskCycleState("task_reward_1", currentCycle, TaskCycleStatus.PENDING),
                "task_no_reward_1" to TaskCycleState("task_no_reward_1", currentCycle, TaskCycleStatus.COMPLETED)
            )
        )

        assertEquals("Mẫu số N phải loại trừ các task rewardless", 1, evalResultPending.totalLinkedRewardTasks)
        assertEquals(1, evalResultPending.requiredCompletions)
        assertTrue("Chưa hoàn thành task có thưởng thì phải LOCKED", evalResultPending.isLocked)

        // Khi hoàn thành task có thưởng: completed = 1 nhưng reward link vẫn tồn tại -> VẪN LOCKED
        val evalResultCompleted = CanonicalAppLockPolicy.evaluateLock(
            packageName = testAppPkg,
            linkedTasks = linkedTasks,
            cycleTaskStates = mapOf(
                "task_reward_1" to TaskCycleState("task_reward_1", currentCycle, TaskCycleStatus.COMPLETED)
            )
        )

        assertTrue("Hoàn thành task nhưng reward link vẫn còn hiệu lực => VẪN LOCKED", evalResultCompleted.isLocked)
        assertEquals(1, evalResultCompleted.completedLinkedRewardTasks)
        assertEquals(1, evalResultCompleted.totalLinkedRewardTasks)

        // NHƯNG đủ điều kiện xóa khỏi Vault theo Deletion Policy:
        assertTrue(CanonicalAppDeletionPolicy.canDelete(totalLinkedRewardTasks = 1, completedRewardTasks = 1))
    }

    // ==================================================
    // DELETION POLICY INVARIANT: ĐẶC XÁ N=2 -> REQUIRED FOR DELETION = 1
    // ==================================================

    @Test
    fun test04_amnestyForN2RequiresOnlyOneTaskForDeletionWhileAppStillLocked() {
        val task1 = CanonicalTask(id = "t1", title = "Nhiệm vụ 1", hasReward = true)
        val task2 = CanonicalTask(id = "t2", title = "Nhiệm vụ 2", hasReward = true)
        val tasks = listOf(task1, task2)

        // Chưa làm nhiệm vụ nào (0/2) => LOCKED và KHÔNG được xóa
        val res0 = CanonicalAppLockPolicy.evaluateLock(
            packageName = testAppPkg,
            linkedTasks = tasks,
            cycleTaskStates = emptyMap()
        )
        assertTrue(res0.isLocked)
        assertFalse(CanonicalAppDeletionPolicy.canDelete(totalLinkedRewardTasks = 2, completedRewardTasks = 0))

        // Hoàn thành 1 trong 2 nhiệm vụ (1/2) => ĐƯỢC PHÉP XÓA nhưng APP VẪN LOCKED!
        val res1 = CanonicalAppLockPolicy.evaluateLock(
            packageName = testAppPkg,
            linkedTasks = tasks,
            cycleTaskStates = mapOf(
                "t1" to TaskCycleState("t1", currentCycle, TaskCycleStatus.COMPLETED)
            )
        )
        assertTrue("N=2 có K=1 thì APP VẪN LOCKED theo Lock Policy", res1.isLocked)
        assertEquals(1, res1.completedLinkedRewardTasks)
        assertEquals(2, res1.totalLinkedRewardTasks)

        // ĐẶC XÁ 2/3 CHO PHÉP XÓA APP:
        assertTrue("N=2 có K=1 thì ĐỦ ĐIỀU KIỆN XÓA APP theo Deletion Policy", CanonicalAppDeletionPolicy.canDelete(totalLinkedRewardTasks = 2, completedRewardTasks = 1))
    }

    // ==================================================
    // INVARIANT 11: APP LOCKED IFF AT LEAST ONE INCOMPLETE LINKED REWARD TASK AND NO EFFECTIVE VOUCHER
    // ==================================================

    @Test
    fun test05_appLockedWithIncompleteTasksAndNoVoucher() {
        val task1 = CanonicalTask(id = "t1", title = "Task 1", hasReward = true)
        val task2 = CanonicalTask(id = "t2", title = "Task 2", hasReward = true)
        val task3 = CanonicalTask(id = "t3", title = "Task 3", hasReward = true)
        val tasks = listOf(task1, task2, task3)

        // N=3, required = 2. Mới hoàn thành 1 task => còn incomplete task => LOCKED
        val resIncomplete = CanonicalLockPolicy.evaluateLock(
            packageName = testAppPkg,
            linkedTasks = tasks,
            cycleTaskStates = mapOf(
                "t1" to TaskCycleState("t1", currentCycle, TaskCycleStatus.COMPLETED)
            ),
            activeVouchers = emptyList()
        )
        assertTrue(resIncomplete.isLocked)
        assertEquals(CanonicalLockDecision.LOCKED, resIncomplete.decision)
        assertEquals(3, resIncomplete.totalLinkedRewardTasks)
        assertEquals(1, resIncomplete.completedLinkedRewardTasks)
        assertEquals(2, resIncomplete.requiredCompletions)
    }

    @Test
    fun test06_effectiveVoucherOverridesLockEvenWithIncompleteTasks() {
        val task1 = CanonicalTask(id = "t1", title = "Task 1", hasReward = true)
        val tasks = listOf(task1)

        val now = Instant.now()
        val activeVoucher = VoucherEffect(
            voucherId = "voucher_gold_pass",
            voucherName = "Thẻ Bài Miễn Trừ",
            targetPackageName = testAppPkg,
            effectiveFrom = now.minusSeconds(60),
            effectiveUntil = now.plusSeconds(3600)
        )

        // Dù task1 chưa hoàn thành (0/1), nhưng có voucher còn hiệu lực => UNLOCKED
        val resWithVoucher = CanonicalLockPolicy.evaluateLock(
            packageName = testAppPkg,
            linkedTasks = tasks,
            cycleTaskStates = emptyMap(),
            activeVouchers = listOf(activeVoucher),
            evaluationInstant = now
        )

        assertTrue("Voucher còn hiệu lực phải mở khóa ngay lập tức", resWithVoucher.isUnlocked)
        assertEquals(CanonicalLockDecision.UNLOCKED, resWithVoucher.decision)
        assertEquals(activeVoucher, resWithVoucher.effectiveVoucher)
    }

    @Test
    fun test07_expiredVoucherDoesNotUnlock() {
        val task1 = CanonicalTask(id = "t1", title = "Task 1", hasReward = true)
        val tasks = listOf(task1)

        val now = Instant.now()
        val expiredVoucher = VoucherEffect(
            voucherId = "voucher_expired",
            voucherName = "Thẻ Bài Hết Hạn",
            targetPackageName = testAppPkg,
            effectiveFrom = now.minusSeconds(7200),
            effectiveUntil = now.minusSeconds(3600) // Đã hết hạn cách đây 1 giờ
        )

        val resExpired = CanonicalLockPolicy.evaluateLock(
            packageName = testAppPkg,
            linkedTasks = tasks,
            cycleTaskStates = emptyMap(),
            activeVouchers = listOf(expiredVoucher),
            evaluationInstant = now
        )

        assertTrue("Voucher hết hạn không được mở khóa", resExpired.isLocked)
    }
}
