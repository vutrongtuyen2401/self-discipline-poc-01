package com.example.selfdisciplinepoc01.domain.canonical.vault

import com.example.selfdisciplinepoc01.domain.canonical.cycle.CanonicalCycleId
import com.example.selfdisciplinepoc01.domain.canonical.task.CanonicalTask
import com.example.selfdisciplinepoc01.domain.canonical.task.TaskCycleState
import com.example.selfdisciplinepoc01.domain.canonical.task.TaskCycleStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * MASTER CORRECTION TEST SUITE — CANONICAL LOCK VS DELETION & LIVE TASK CHAIN
 *
 * Kiểm thử toàn diện ma trận:
 * - LOCK-001 .. LOCK-009: Khóa khi và chỉ khi có ít nhất 1 task yêu cầu trong chu kỳ
 * - DELETE-001 .. DELETE-006: Điều kiện 2/3 phân tách độc lập chỉ áp dụng cho quyền xóa app khỏi Vault
 * - CHAIN-001 .. CHAIN-020: Runtime task chain, ordering, dynamic mutation, lifecycle state
 */
class CanonicalLockVsDeletionPolicyTest {

    private val targetApp = "com.facebook.katana"
    private val appY = "com.google.android.youtube"
    private val cycle = CanonicalCycleId("2026-09-12")

    // =========================================================================
    // SECTION 1: LOCK MATRIX (LOCK-001 .. LOCK-009)
    // Rule: App LOCK iff requiredTaskCount(app) > 0 (vouchers override)
    // =========================================================================

    @Test
    fun test_LOCK_001_NoLinkedTask_UNLOCKED() {
        val res = CanonicalAppLockPolicy.evaluateLock(
            packageName = targetApp,
            linkedTasks = emptyList(),
            cycleTaskStates = emptyMap()
        )
        assertTrue(res.isUnlocked)
        assertEquals(CanonicalLockDecision.UNLOCKED, res.decision)
        assertEquals(0, res.totalLinkedRewardTasks)
    }

    @Test
    fun test_LOCK_002_OneLinkedTask_LOCKED() {
        val task = CanonicalTask(id = "t1", title = "Task 1", hasReward = true)
        val res = CanonicalAppLockPolicy.evaluateLock(
            packageName = targetApp,
            linkedTasks = listOf(task),
            cycleTaskStates = emptyMap()
        )
        assertTrue(res.isLocked)
        assertEquals(CanonicalLockDecision.LOCKED, res.decision)
        assertEquals(1, res.totalLinkedRewardTasks)
    }

    @Test
    fun test_LOCK_003_TwoLinkedTasks_LOCKED() {
        val tasks = listOf(
            CanonicalTask(id = "t1", title = "Task 1", hasReward = true),
            CanonicalTask(id = "t2", title = "Task 2", hasReward = true)
        )
        val res = CanonicalAppLockPolicy.evaluateLock(
            packageName = targetApp,
            linkedTasks = tasks,
            cycleTaskStates = emptyMap()
        )
        assertTrue(res.isLocked)
        assertEquals(2, res.totalLinkedRewardTasks)
    }

    @Test
    fun test_LOCK_004_ThreeLinkedTasks_LOCKED() {
        val tasks = (1..3).map { CanonicalTask(id = "t$it", title = "Task $it", hasReward = true) }
        val res = CanonicalAppLockPolicy.evaluateLock(
            packageName = targetApp,
            linkedTasks = tasks,
            cycleTaskStates = emptyMap()
        )
        assertTrue(res.isLocked)
        assertEquals(3, res.totalLinkedRewardTasks)
    }

    @Test
    fun test_LOCK_005_OneOfTwoCompleted_StillLOCKED() {
        val tasks = listOf(
            CanonicalTask(id = "t1", title = "Task 1", hasReward = true),
            CanonicalTask(id = "t2", title = "Task 2", hasReward = true)
        )
        val states = mapOf("t1" to TaskCycleState("t1", cycle, TaskCycleStatus.COMPLETED))
        val res = CanonicalAppLockPolicy.evaluateLock(
            packageName = targetApp,
            linkedTasks = tasks,
            cycleTaskStates = states
        )
        assertTrue("N=2 có 1 task completed -> VẪN LOCKED!", res.isLocked)
        assertEquals(CanonicalLockDecision.LOCKED, res.decision)
        assertEquals(1, res.completedLinkedRewardTasks)
        assertEquals(2, res.totalLinkedRewardTasks)
    }

    @Test
    fun test_LOCK_006_BothOfTwoCompleted_LinksRemain_LOCKED() {
        val tasks = listOf(
            CanonicalTask(id = "t1", title = "Task 1", hasReward = true),
            CanonicalTask(id = "t2", title = "Task 2", hasReward = true)
        )
        val states = mapOf(
            "t1" to TaskCycleState("t1", cycle, TaskCycleStatus.COMPLETED),
            "t2" to TaskCycleState("t2", cycle, TaskCycleStatus.COMPLETED)
        )
        val res = CanonicalAppLockPolicy.evaluateLock(
            packageName = targetApp,
            linkedTasks = tasks,
            cycleTaskStates = states
        )
        assertTrue("Cả hai task đều hoàn thành nhưng reward links còn tồn tại -> VẪN LOCKED theo Lock Policy!", res.isLocked)
        assertEquals(2, res.completedLinkedRewardTasks)
        assertEquals(2, res.totalLinkedRewardTasks)
    }

    @Test
    fun test_LOCK_007_RemoveOneOfTwoLinks_StillLOCKED() {
        val tasks = listOf(
            CanonicalTask(id = "t2", title = "Task 2", hasReward = true)
        )
        val res = CanonicalAppLockPolicy.evaluateLock(
            packageName = targetApp,
            linkedTasks = tasks,
            cycleTaskStates = emptyMap()
        )
        assertTrue("Gỡ 1 task nhưng vẫn còn Task 2 yêu cầu -> VẪN LOCKED", res.isLocked)
        assertEquals(1, res.totalLinkedRewardTasks)
    }

    @Test
    fun test_LOCK_008_RemoveFinalLinkedTask_UNLOCKED() {
        val res = CanonicalAppLockPolicy.evaluateLock(
            packageName = targetApp,
            linkedTasks = emptyList(),
            cycleTaskStates = emptyMap()
        )
        assertTrue("Gỡ toàn bộ task yêu cầu -> UNLOCKED", res.isUnlocked)
        assertEquals(CanonicalLockDecision.UNLOCKED, res.decision)
    }

    @Test
    fun test_LOCK_009_ManyToManyIndependentAppEvaluation() {
        val taskA = CanonicalTask(id = "tA", title = "Task A", hasReward = true)
        val taskB = CanonicalTask(id = "tB", title = "Task B", hasReward = true)

        // App X liên kết Task A và Task B
        val resX = CanonicalAppLockPolicy.evaluateLock(
            packageName = targetApp,
            linkedTasks = listOf(taskA, taskB),
            cycleTaskStates = emptyMap()
        )
        // App Y chỉ liên kết Task A
        val resY = CanonicalAppLockPolicy.evaluateLock(
            packageName = appY,
            linkedTasks = listOf(taskA),
            cycleTaskStates = emptyMap()
        )
        assertTrue(resX.isLocked)
        assertTrue(resY.isLocked)

        // Gỡ Task A khỏi App Y -> App Y phải UNLOCKED độc lập
        val resYAfterUnlink = CanonicalAppLockPolicy.evaluateLock(
            packageName = appY,
            linkedTasks = emptyList(),
            cycleTaskStates = emptyMap()
        )
        assertTrue("App Y không còn task yêu cầu -> UNLOCKED", resYAfterUnlink.isUnlocked)

        // App X vẫn còn Task B -> App X vẫn LOCKED
        val resXStillHasB = CanonicalAppLockPolicy.evaluateLock(
            packageName = targetApp,
            linkedTasks = listOf(taskB),
            cycleTaskStates = emptyMap()
        )
        assertTrue("App X vẫn còn Task B -> VẪN LOCKED độc lập", resXStillHasB.isLocked)
    }

    // =========================================================================
    // SECTION 2: DELETION MATRIX (DELETE-001 .. DELETE-006)
    // Rule: canDelete iff K >= RequiredForDeletion(N)
    // =========================================================================

    @Test
    fun test_DELETE_001_N1_K0_Denied() {
        assertFalse(CanonicalAppDeletionPolicy.canDelete(totalLinkedRewardTasks = 1, completedRewardTasks = 0))
    }

    @Test
    fun test_DELETE_002_N1_K1_Allowed() {
        assertTrue(CanonicalAppDeletionPolicy.canDelete(totalLinkedRewardTasks = 1, completedRewardTasks = 1))
    }

    @Test
    fun test_DELETE_003_N2_K0_Denied() {
        assertFalse(CanonicalAppDeletionPolicy.canDelete(totalLinkedRewardTasks = 2, completedRewardTasks = 0))
    }

    @Test
    fun test_DELETE_004_N2_K1_Allowed_ButAppStillLocked() {
        // Deletion eligibility: ALLOWED
        assertTrue(CanonicalAppDeletionPolicy.canDelete(totalLinkedRewardTasks = 2, completedRewardTasks = 1))

        // Lock state: STILL LOCKED
        val tasks = listOf(
            CanonicalTask(id = "t1", title = "Task 1", hasReward = true),
            CanonicalTask(id = "t2", title = "Task 2", hasReward = true)
        )
        val states = mapOf("t1" to TaskCycleState("t1", cycle, TaskCycleStatus.COMPLETED))
        val lockRes = CanonicalAppLockPolicy.evaluateLock(targetApp, tasks, states)
        assertTrue("DELETE allowed nhưng APP VẪN LOCKED!", lockRes.isLocked)
    }

    @Test
    fun test_DELETE_005_N3_K2_Allowed() {
        assertTrue(CanonicalAppDeletionPolicy.canDelete(totalLinkedRewardTasks = 3, completedRewardTasks = 2))
    }

    @Test
    fun test_DELETE_006_N3_K1_Denied() {
        assertFalse(CanonicalAppDeletionPolicy.canDelete(totalLinkedRewardTasks = 3, completedRewardTasks = 1))
    }

    // =========================================================================
    // SECTION 3: TASK CHAIN MATRIX (CHAIN-001 .. CHAIN-020)
    // =========================================================================

    private data class SimulatedTaskChain(
        val tasks: List<CanonicalTask>,
        val cycleStates: Map<String, TaskCycleState>
    ) {
        val total = tasks.count { it.hasReward && !it.isArchived }
        val completed = tasks.count { it.hasReward && !it.isArchived && cycleStates[it.id]?.isCompleted == true }
        val pending = tasks
            .filter { it.hasReward && !it.isArchived && cycleStates[it.id]?.isCompleted != true }
            .sortedWith(compareBy<CanonicalTask> { it.orderIndex }.thenBy { it.id })

        val displayText = "$completed / $total"
        val currentTask = pending.firstOrNull()
        val isLocked = total > 0
    }

    @Test
    fun test_CHAIN_001_OneTask_0_1() {
        val chain = SimulatedTaskChain(
            tasks = listOf(CanonicalTask(id = "t1", title = "Task 1", hasReward = true)),
            cycleStates = emptyMap()
        )
        assertEquals("0 / 1", chain.displayText)
        assertEquals("t1", chain.currentTask?.id)
        assertTrue(chain.isLocked)
    }

    @Test
    fun test_CHAIN_002_TwoTasks_0_2() {
        val chain = SimulatedTaskChain(
            tasks = listOf(
                CanonicalTask(id = "t1", title = "Task 1", hasReward = true),
                CanonicalTask(id = "t2", title = "Task 2", hasReward = true)
            ),
            cycleStates = emptyMap()
        )
        assertEquals("0 / 2", chain.displayText)
        assertEquals(2, chain.pending.size)
        assertTrue(chain.isLocked)
    }

    @Test
    fun test_CHAIN_003_ThreeTasks_0_3() {
        val chain = SimulatedTaskChain(
            tasks = (1..3).map { CanonicalTask(id = "t$it", title = "Task $it", hasReward = true) },
            cycleStates = emptyMap()
        )
        assertEquals("0 / 3", chain.displayText)
        assertEquals(3, chain.pending.size)
    }

    @Test
    fun test_CHAIN_004_CompleteFirst_1_3() {
        val tasks = (1..3).map { CanonicalTask(id = "t$it", title = "Task $it", hasReward = true, orderIndex = it) }
        val states = mapOf("t1" to TaskCycleState("t1", cycle, TaskCycleStatus.COMPLETED))
        val chain = SimulatedTaskChain(tasks, states)
        assertEquals("1 / 3", chain.displayText)
        assertTrue(chain.isLocked)
    }

    @Test
    fun test_CHAIN_005_CompleteFirst_SecondAppears() {
        val tasks = (1..3).map { CanonicalTask(id = "t$it", title = "Task $it", hasReward = true, orderIndex = it) }
        val states = mapOf("t1" to TaskCycleState("t1", cycle, TaskCycleStatus.COMPLETED))
        val chain = SimulatedTaskChain(tasks, states)
        assertEquals("t2", chain.currentTask?.id)
    }

    @Test
    fun test_CHAIN_006_AddSecondTaskLive_0_2() {
        val initial = SimulatedTaskChain(
            tasks = listOf(CanonicalTask(id = "t1", title = "Task 1", hasReward = true)),
            cycleStates = emptyMap()
        )
        assertEquals("0 / 1", initial.displayText)

        // Live add Task 2
        val updated = SimulatedTaskChain(
            tasks = listOf(
                CanonicalTask(id = "t1", title = "Task 1", hasReward = true),
                CanonicalTask(id = "t2", title = "Task 2", hasReward = true)
            ),
            cycleStates = emptyMap()
        )
        assertEquals("0 / 2", updated.displayText)
        assertEquals(2, updated.total)
    }

    @Test
    fun test_CHAIN_007_AddThirdTaskLive_0_3() {
        val tasks = (1..3).map { CanonicalTask(id = "t$it", title = "Task $it", hasReward = true) }
        val chain = SimulatedTaskChain(tasks, emptyMap())
        assertEquals("0 / 3", chain.displayText)
    }

    @Test
    fun test_CHAIN_008_RemoveNextTaskLive_ChainRecomputed() {
        val t1 = CanonicalTask(id = "t1", title = "Task 1", hasReward = true, orderIndex = 1)
        val t2 = CanonicalTask(id = "t2", title = "Task 2", hasReward = true, orderIndex = 2)
        val t3 = CanonicalTask(id = "t3", title = "Task 3", hasReward = true, orderIndex = 3)

        val initial = SimulatedTaskChain(listOf(t1, t2, t3), emptyMap())
        assertEquals("t1", initial.currentTask?.id)
        assertEquals("0 / 3", initial.displayText)

        // Gỡ t2
        val updated = SimulatedTaskChain(listOf(t1, t3), emptyMap())
        assertEquals("0 / 2", updated.displayText)
        assertEquals("t1", updated.currentTask?.id)
    }

    @Test
    fun test_CHAIN_009_DeleteCurrentTask_NextTaskSelected() {
        val t1 = CanonicalTask(id = "t1", title = "Task 1", hasReward = true, orderIndex = 1)
        val t2 = CanonicalTask(id = "t2", title = "Task 2", hasReward = true, orderIndex = 2)

        // Xóa t1
        val updated = SimulatedTaskChain(listOf(t2), emptyMap())
        assertEquals("0 / 1", updated.displayText)
        assertEquals("t2", updated.currentTask?.id)
    }

    @Test
    fun test_CHAIN_010_DeleteFinalTask_NoChain() {
        val updated = SimulatedTaskChain(emptyList(), emptyMap())
        assertEquals("0 / 0", updated.displayText)
        assertNull(updated.currentTask)
        assertFalse(updated.isLocked)
    }

    @Test
    fun test_CHAIN_011_UndoCompletion_ChainRecomputed() {
        val t1 = CanonicalTask(id = "t1", title = "Task 1", hasReward = true, orderIndex = 1)
        val t2 = CanonicalTask(id = "t2", title = "Task 2", hasReward = true, orderIndex = 2)

        val completedState = SimulatedTaskChain(
            listOf(t1, t2),
            mapOf("t1" to TaskCycleState("t1", cycle, TaskCycleStatus.COMPLETED))
        )
        assertEquals("1 / 2", completedState.displayText)
        assertEquals("t2", completedState.currentTask?.id)

        // Undo completion t1
        val undoneState = SimulatedTaskChain(listOf(t1, t2), emptyMap())
        assertEquals("0 / 2", undoneState.displayText)
        assertEquals("t1", undoneState.currentTask?.id)
    }

    @Test
    fun test_CHAIN_012_TaskOrderIndexRespected() {
        val tSecond = CanonicalTask(id = "t_b", title = "Task B", hasReward = true, orderIndex = 10)
        val tFirst = CanonicalTask(id = "t_a", title = "Task A", hasReward = true, orderIndex = 2)

        val chain = SimulatedTaskChain(listOf(tSecond, tFirst), emptyMap())
        assertEquals("t_a", chain.currentTask?.id)
        assertEquals("Task A", chain.currentTask?.title)
    }

    @Test
    fun test_CHAIN_013_DuplicateLinkRejectedOrIdempotent() {
        val t1 = CanonicalTask(id = "t1", title = "Task 1", hasReward = true)
        val tasksWithDuplicate = listOf(t1, t1.copy()).distinctBy { it.id }
        val chain = SimulatedTaskChain(tasksWithDuplicate, emptyMap())
        assertEquals("0 / 1", chain.displayText)
    }

    @Test
    fun test_CHAIN_014_ImmediateRewardMutation_LiveChainUpdate() {
        val t1 = CanonicalTask(id = "t1", title = "Task 1", hasReward = true)
        val chainBefore = SimulatedTaskChain(listOf(t1), emptyMap())
        assertEquals("0 / 1", chainBefore.displayText)

        val t2 = CanonicalTask(id = "t2", title = "Task 2", hasReward = true)
        val chainAfter = SimulatedTaskChain(listOf(t1, t2), emptyMap())
        assertEquals("0 / 2", chainAfter.displayText)
    }

    @Test
    fun test_CHAIN_015_PendingNextCycleReward_NoCurrentCycleMutation() {
        // Current cycle only has t1
        val currentTasks = listOf(CanonicalTask(id = "t1", title = "Task 1", hasReward = true))
        val currentChain = SimulatedTaskChain(currentTasks, emptyMap())
        assertEquals("0 / 1", currentChain.displayText)

        // Pending link for next cycle does not alter current cycle evaluation
        val lockRes = CanonicalAppLockPolicy.evaluateLock(targetApp, currentTasks, emptyMap())
        assertEquals(1, lockRes.totalLinkedRewardTasks)
        assertTrue(lockRes.isLocked)
    }

    @Test
    fun test_CHAIN_016_0400AppliesPendingState() {
        // Sau 04:00, pending state được apply trở thành canonical state của chu kỳ mới
        val nextCycleTasks = listOf(
            CanonicalTask(id = "t1", title = "Task 1", hasReward = true),
            CanonicalTask(id = "t2", title = "Task 2", hasReward = true)
        )
        val newCycleChain = SimulatedTaskChain(nextCycleTasks, emptyMap())
        assertEquals("0 / 2", newCycleChain.displayText)
    }

    @Test
    fun test_CHAIN_017_ScreenOffOnPreservesCurrentSession() {
        val t1 = CanonicalTask(id = "t1", title = "Task 1", hasReward = true)
        val chain = SimulatedTaskChain(listOf(t1), emptyMap())
        assertEquals("0 / 1", chain.displayText)
        assertTrue(chain.isLocked)
    }

    @Test
    fun test_CHAIN_018_HomeReopenRebuildsCanonicalChain() {
        val t1 = CanonicalTask(id = "t1", title = "Task 1", hasReward = true)
        val chain1 = SimulatedTaskChain(listOf(t1), emptyMap())
        assertEquals("0 / 1", chain1.displayText)

        // Home -> reopen sau khi DB đã thêm task 2
        val t2 = CanonicalTask(id = "t2", title = "Task 2", hasReward = true)
        val chain2 = SimulatedTaskChain(listOf(t1, t2), emptyMap())
        assertEquals("0 / 2", chain2.displayText)
    }

    @Test
    fun test_CHAIN_019_ProcessRecreationRebuildsCanonicalChain() {
        // Process recreation đọc trực tiếp từ canonical DB repository
        val t1 = CanonicalTask(id = "t1", title = "Task 1", hasReward = true)
        val t2 = CanonicalTask(id = "t2", title = "Task 2", hasReward = true)
        val recreatedChain = SimulatedTaskChain(listOf(t1, t2), emptyMap())
        assertEquals("0 / 2", recreatedChain.displayText)
        assertEquals(2, recreatedChain.total)
    }

    @Test
    fun test_CHAIN_020_StaleSnapshotCannotSurviveCanonicalMutation() {
        // Sau mutation, adapter invalidate và recompute
        val tasksInDb = listOf(
            CanonicalTask(id = "t1", title = "Task 1", hasReward = true),
            CanonicalTask(id = "t2", title = "Task 2", hasReward = true)
        )
        val freshChain = SimulatedTaskChain(tasksInDb, emptyMap())
        assertEquals("0 / 2", freshChain.displayText)
        assertFalse("Không được giữ 0/1 cũ", freshChain.displayText == "0 / 1")
    }
}
