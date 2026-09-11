package com.example.selfdisciplinepoc01.domain.canonical.task

import com.example.selfdisciplinepoc01.domain.canonical.cycle.CanonicalCycleId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Unit Test cho Task Foundation theo MASTER SSOT.
 *
 * Kiểm tra Invariant 2:
 * - Định nghĩa nhiệm vụ (Persistent Task Definition) tách biệt khỏi trạng thái chu kỳ (Cycle-Scoped State).
 * - Trạng thái hoàn thành ở chu kỳ cũ không bị ghi đè hay làm mất khi sang chu kỳ mới.
 * - Hỗ trợ bảo toàn lịch sử chu kỳ.
 */
class CanonicalTaskTest {

    @Test
    fun test01_persistentTaskDefinitionSeparation() {
        val task = CanonicalTask(
            id = "task_morning_exercise",
            title = "Chạy bộ 30 phút buổi sáng",
            description = "Tập thể dục nâng cao thể lực",
            orderIndex = 1,
            hasReward = true
        )

        assertEquals("task_morning_exercise", task.id)
        assertEquals("Chạy bộ 30 phút buổi sáng", task.title)
        assertTrue(task.hasReward)
        assertFalse(task.isArchived)
    }

    @Test
    fun test02_currentCycleVsHistoricalTaskState() {
        val taskId = "task_reading_book"
        val cycleYesterday = CanonicalCycleId("2026-09-10")
        val cycleToday = CanonicalCycleId("2026-09-11")

        // Chu kỳ hôm qua: Task đã hoàn thành
        val stateYesterday = TaskCycleState(
            taskId = taskId,
            cycleId = cycleYesterday,
            status = TaskCycleStatus.COMPLETED,
            completedAt = Instant.parse("2026-09-10T14:30:00Z")
        )

        // Chu kỳ hôm nay: Task chưa hoàn thành (PENDING)
        val stateToday = TaskCycleState(
            taskId = taskId,
            cycleId = cycleToday,
            status = TaskCycleStatus.PENDING,
            completedAt = null
        )

        // Kiểm tra tính độc lập và bảo toàn lịch sử
        assertTrue("Trạng thái chu kỳ hôm qua phải là COMPLETED", stateYesterday.isCompleted)
        assertFalse("Trạng thái chu kỳ hôm nay phải là PENDING", stateToday.isCompleted)
        assertNotEquals("Hai trạng thái thuộc về 2 chu kỳ khác nhau", stateYesterday.cycleId, stateToday.cycleId)
        assertEquals(stateYesterday.taskId, stateToday.taskId)
    }

    @Test
    fun test03_taskRewardLinkProperties() {
        val link = TaskRewardLink(
            taskId = "task_code_review",
            appPackageName = "com.facebook.katana"
        )
        assertEquals("task_code_review", link.taskId)
        assertEquals("com.facebook.katana", link.appPackageName)
    }
}
