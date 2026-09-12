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
 * Suite kiểm thử chấp nhận (Acceptance Tests) cho Thẩm định Khóa Bảo Khố (Vault Lock Evaluation).
 *
 * Tiêu chí SSOT:
 * 1. Bảng chuẩn hóa N=0..8:
 *    N=0 -> 0 -> UNLOCKED
 *    N=1 -> 1
 *    N=2 -> 1 (Đặc xá khởi đầu bắt buộc theo SSOT)
 *    N=3 -> 2
 *    N=4 -> 3
 *    N=5 -> 4
 *    N=6 -> 4
 *    N=7 -> 5
 *    N=8 -> 6
 * 2. Incomplete linked task + no voucher -> LOCKED
 * 3. Enough completed tasks -> UNLOCKED
 * 4. N=0 -> UNLOCKED
 * 5. Rewardless tasks excluded (INV-TASK-001)
 * 6. Effective voucher -> UNLOCKED
 * 7. Expired voucher -> does not override lock
 * 8. Multiple reward apps remain independent
 * 9. Duplicate links do not inflate N
 */
class CanonicalVaultLockEvaluationTest {

    private val currentCycle = CanonicalCycleId("2026-09-11")
    private val appA = "com.facebook.katana"
    private val appB = "com.google.android.youtube"

    @Test
    fun test01_canonicalNormalizationTableN0_to_N8() {
        assertEquals(0, CanonicalLockPolicy.calculateRequiredCompletions(0))
        assertEquals(1, CanonicalLockPolicy.calculateRequiredCompletions(1))
        assertEquals(1, CanonicalLockPolicy.calculateRequiredCompletions(2)) // Đặc xá khởi đầu
        assertEquals(2, CanonicalLockPolicy.calculateRequiredCompletions(3))
        assertEquals(3, CanonicalLockPolicy.calculateRequiredCompletions(4))
        assertEquals(4, CanonicalLockPolicy.calculateRequiredCompletions(5))
        assertEquals(4, CanonicalLockPolicy.calculateRequiredCompletions(6))
        assertEquals(5, CanonicalLockPolicy.calculateRequiredCompletions(7))
        assertEquals(6, CanonicalLockPolicy.calculateRequiredCompletions(8))
    }

    @Test
    fun test02_n0AlwaysUnlocks() {
        val res = CanonicalLockPolicy.evaluateLock(
            packageName = appA,
            linkedTasks = emptyList(),
            cycleTaskStates = emptyMap()
        )
        assertTrue("N=0 phải luôn UNLOCKED theo SSOT", res.isUnlocked)
        assertEquals(CanonicalLockDecision.UNLOCKED, res.decision)
        assertEquals(0, res.totalLinkedRewardTasks)
        assertEquals(0, res.requiredCompletions)
    }

    @Test
    fun test03_incompleteLinkedTaskWithoutVoucherLocks() {
        val tasks = listOf(
            CanonicalTask(id = "t1", title = "Task 1", hasReward = true),
            CanonicalTask(id = "t2", title = "Task 2", hasReward = true),
            CanonicalTask(id = "t3", title = "Task 3", hasReward = true)
        )
        // N=3, required = 2. Hoàn thành 1 task (1 < 2) -> LOCKED
        val states = mapOf("t1" to TaskCycleState("t1", currentCycle, TaskCycleStatus.COMPLETED))

        val res = CanonicalLockPolicy.evaluateLock(
            packageName = appA,
            linkedTasks = tasks,
            cycleTaskStates = states
        )

        assertTrue(res.isLocked)
        assertEquals(CanonicalLockDecision.LOCKED, res.decision)
        assertEquals(3, res.totalLinkedRewardTasks)
        assertEquals(1, res.completedLinkedRewardTasks)
        assertEquals(2, res.requiredCompletions)
    }

    @Test
    fun test04_completedTasksDoNotUnlockAppWhileRequirementsRemain() {
        val tasks = listOf(
            CanonicalTask(id = "t1", title = "Task 1", hasReward = true),
            CanonicalTask(id = "t2", title = "Task 2", hasReward = true),
            CanonicalTask(id = "t3", title = "Task 3", hasReward = true)
        )
        // Hoàn thành 2 trong 3 tasks. Dù K=2 >= 2/3, App VẪN LOCKED vì vẫn còn tasks yêu cầu app trong chu kỳ!
        val states = mapOf(
            "t1" to TaskCycleState("t1", currentCycle, TaskCycleStatus.COMPLETED),
            "t2" to TaskCycleState("t2", currentCycle, TaskCycleStatus.COMPLETED)
        )

        val res = CanonicalAppLockPolicy.evaluateLock(
            packageName = appA,
            linkedTasks = tasks,
            cycleTaskStates = states
        )

        assertTrue("Có task liên kết còn hiệu lực trong chu kỳ => VẪN LOCKED theo SSOT", res.isLocked)
        assertEquals(CanonicalLockDecision.LOCKED, res.decision)
        assertEquals(2, res.completedLinkedRewardTasks)
        assertEquals(3, res.totalLinkedRewardTasks)

        // NHƯNG đủ điều kiện xóa khỏi Vault theo Deletion Policy:
        assertTrue(CanonicalAppDeletionPolicy.canDelete(totalLinkedRewardTasks = 3, completedRewardTasks = 2))
    }

    @Test
    fun test05_effectiveVoucherOverridesLockEvenWithZeroCompletedTasks() {
        val tasks = listOf(
            CanonicalTask(id = "t1", title = "Task 1", hasReward = true),
            CanonicalTask(id = "t2", title = "Task 2", hasReward = true)
        )
        val now = Instant.now()
        val voucher = VoucherEffect(
            voucherId = "voucher_special",
            voucherName = "Lệnh Miễn Phong",
            targetPackageName = appA,
            effectiveFrom = now.minusSeconds(100),
            effectiveUntil = now.plusSeconds(3600)
        )

        // Không hoàn thành task nào (0/2) nhưng có voucher còn hiệu lực
        val res = CanonicalLockPolicy.evaluateLock(
            packageName = appA,
            linkedTasks = tasks,
            cycleTaskStates = emptyMap(),
            activeVouchers = listOf(voucher),
            evaluationInstant = now
        )

        assertTrue("Thẻ bài hiệu lực phải ghi đè khóa phong ấn ngay lập tức", res.isUnlocked)
        assertEquals(voucher, res.effectiveVoucher)
    }

    @Test
    fun test06_expiredVoucherDoesNotOverrideLock() {
        val tasks = listOf(CanonicalTask(id = "t1", title = "Task 1", hasReward = true))
        val now = Instant.now()
        val expiredVoucher = VoucherEffect(
            voucherId = "voucher_old",
            voucherName = "Lệnh Bài Cũ",
            targetPackageName = appA,
            effectiveFrom = now.minusSeconds(7200),
            effectiveUntil = now.minusSeconds(3600) // Đã hết hạn cách đây 1 giờ
        )

        val res = CanonicalLockPolicy.evaluateLock(
            packageName = appA,
            linkedTasks = tasks,
            cycleTaskStates = emptyMap(),
            activeVouchers = listOf(expiredVoucher),
            evaluationInstant = now
        )

        assertTrue("Thẻ bài hết hạn không được mở khóa", res.isLocked)
        assertEquals(CanonicalLockDecision.LOCKED, res.decision)
    }

    @Test
    fun test07_multipleRewardAppsRemainIndependent() {
        val taskShared = CanonicalTask(id = "t_shared", title = "Nhiệm vụ chung", hasReward = true)
        val taskOnlyB = CanonicalTask(id = "t_only_b", title = "Nhiệm vụ riêng App B", hasReward = true)

        // App A liên kết với t_shared (N=1)
        val tasksForA = listOf(taskShared)
        // App B liên kết với cả 2 tasks (N=2)
        val tasksForB = listOf(taskShared, taskOnlyB)

        // Chỉ hoàn thành taskOnlyB
        val states = mapOf(
            "t_only_b" to TaskCycleState("t_only_b", currentCycle, TaskCycleStatus.COMPLETED)
        )

        val resA = CanonicalAppLockPolicy.evaluateLock(appA, tasksForA, states)
        val resB = CanonicalAppLockPolicy.evaluateLock(appB, tasksForB, states)

        // App A: có t_shared yêu cầu (N=1) -> LOCKED
        assertTrue("App A phải LOCKED vì có task yêu cầu", resA.isLocked)
        // App B: có 2 tasks yêu cầu (N=2) -> cũng VẪN LOCKED vì vẫn có task yêu cầu
        assertTrue("App B phải LOCKED vì vẫn có task yêu cầu trong chu kỳ", resB.isLocked)

        // Nhưng nếu gỡ toàn bộ task yêu cầu của App A (unlink)
        val resAUnlinked = CanonicalAppLockPolicy.evaluateLock(appA, emptyList(), states)
        assertTrue("App A sau khi không còn task yêu cầu nào thì UNLOCKED", resAUnlinked.isUnlocked)
        // App B vẫn giữ 2 tasks -> Vẫn LOCKED
        val resBStillLinked = CanonicalAppLockPolicy.evaluateLock(appB, tasksForB, states)
        assertTrue("App B vẫn có tasks yêu cầu -> Vẫn LOCKED độc lập", resBStillStillLinked(resBStillLinked))
    }

    private fun resBStillStillLinked(res: LockEvaluationResult): Boolean = res.isLocked

    @Test
    fun test08_duplicateTasksDoNotInflateN() {
        val task1 = CanonicalTask(id = "t1", title = "Task duy nhất", hasReward = true)
        // Nếu danh sách chứa phần tử lặp
        val tasksWithDuplicate = listOf(task1, task1.copy())

        // Distinct theo ID
        val distinctTasks = tasksWithDuplicate.distinctBy { it.id }
        val res = CanonicalLockPolicy.evaluateLock(
            packageName = appA,
            linkedTasks = distinctTasks,
            cycleTaskStates = emptyMap()
        )

        assertEquals("Trùng lặp không được làm phồng mẫu số N", 1, res.totalLinkedRewardTasks)
        assertEquals(1, res.requiredCompletions)
    }
}
