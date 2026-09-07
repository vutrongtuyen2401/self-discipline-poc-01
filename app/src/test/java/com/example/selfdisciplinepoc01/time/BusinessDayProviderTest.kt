package com.example.selfdisciplinepoc01.time

import com.example.selfdisciplinepoc01.usage.UsageTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Mandatory Unit Test Suite for Daily Cycle 04:00 Alignment
 *
 * Implements the full test matrix required by Phase 17 and Canonical Design V2 (Section 8):
 * 1. 03:59:59 (belongs to previous business day)
 * 2. 04:00:00 (belongs to current business day)
 * 3. 04:00:01 (belongs to current business day)
 * 4. 00:00:00 (midnight belongs to previous business day cycle)
 * 5. 23:59:59 (belongs to current business day)
 * 6. Crossing midnight without crossing 04:00 (23:30 -> 01:30 belongs to same business day)
 * 7. Crossing 04:00 (03:45 -> 04:15 splits duration between business days)
 * 8. Task started at 03:59 and completed at 04:01 belongs to old cycle
 * 9. Process restart around 04:00 preserves accounting
 * 10. Timezone changes dynamically adapt business date
 * 11. Wall clock forward jump resilience
 * 12. Wall clock backward jump resilience
 */
class BusinessDayProviderTest {

    private val testZone = ZoneId.of("Asia/Ho_Chi_Minh") // UTC+7
    private val provider = BusinessDayProviderImpl(boundaryHour = 4, boundaryMinute = 0)

    private fun toWallMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int): Long {
        return ZonedDateTime.of(year, month, day, hour, minute, second, 0, testZone).toInstant().toEpochMilli()
    }

    // 1. 03:59:59 -> belongs to previous calendar day
    @Test
    fun test01_at035959_belongsToPreviousBusinessDay() {
        val wall = toWallMillis(2026, 9, 7, 3, 59, 59)
        val businessDate = provider.getBusinessDate(wall, testZone)
        assertEquals(LocalDate.of(2026, 9, 6), businessDate)
    }

    // 2. 04:00:00 -> exact boundary belongs to current calendar day
    @Test
    fun test02_at040000_belongsToCurrentBusinessDay() {
        val wall = toWallMillis(2026, 9, 7, 4, 0, 0)
        val businessDate = provider.getBusinessDate(wall, testZone)
        assertEquals(LocalDate.of(2026, 9, 7), businessDate)
    }

    // 3. 04:00:01 -> belongs to current calendar day
    @Test
    fun test03_at040001_belongsToCurrentBusinessDay() {
        val wall = toWallMillis(2026, 9, 7, 4, 0, 1)
        val businessDate = provider.getBusinessDate(wall, testZone)
        assertEquals(LocalDate.of(2026, 9, 7), businessDate)
    }

    // 4. 00:00:00 midnight -> belongs to previous calendar day (part of 04:00 cycle)
    @Test
    fun test04_at000000_belongsToPreviousBusinessDay() {
        val wall = toWallMillis(2026, 9, 7, 0, 0, 0)
        val businessDate = provider.getBusinessDate(wall, testZone)
        assertEquals(LocalDate.of(2026, 9, 6), businessDate)
    }

    // 5. 23:59:59 -> belongs to current calendar day
    @Test
    fun test05_at235959_belongsToCurrentBusinessDay() {
        val wall = toWallMillis(2026, 9, 7, 23, 59, 59)
        val businessDate = provider.getBusinessDate(wall, testZone)
        assertEquals(LocalDate.of(2026, 9, 7), businessDate)
    }

    // 6. Crossing midnight without crossing 04:00 (23:30 Sep 6 -> 01:30 Sep 7)
    @Test
    fun test06_crossingMidnightWithoutCrossing0400_isSameBusinessDay() {
        val wallBeforeMidnight = toWallMillis(2026, 9, 6, 23, 30, 0)
        val wallAfterMidnight = toWallMillis(2026, 9, 7, 1, 30, 0)

        assertTrue(provider.isSameBusinessDay(wallBeforeMidnight, wallAfterMidnight, testZone))
        assertEquals(LocalDate.of(2026, 9, 6), provider.getBusinessDate(wallBeforeMidnight, testZone))
        assertEquals(LocalDate.of(2026, 9, 6), provider.getBusinessDate(wallAfterMidnight, testZone))

        // In UsageTracker: duration should not be split across business days
        val clock = FakeClock(initialWall = wallBeforeMidnight, initialElapsed = 10_000L)
        val tracker = UsageTracker(clock = clock, zoneIdProvider = { testZone }, businessDayProvider = provider)

        // 2 hours = 7,200,000 ms
        val duration = 7_200_000L
        tracker.recordUsageSegment("com.test.game", wallBeforeMidnight, wallAfterMidnight, duration)

        // Query today's usage at 01:30 (still business day Sep 6)
        clock.setWallTime(wallAfterMidnight)
        assertEquals(duration, tracker.getTodayUsage("com.test.game"))
    }

    // 7. Crossing 04:00 boundary (03:45 -> 04:15) -> splits duration proportionally
    @Test
    fun test07_crossing0400_splitsDurationProportionally() {
        // Start 15 minutes before 04:00 (03:45 Sep 7 -> business day Sep 6)
        val startWall = toWallMillis(2026, 9, 7, 3, 45, 0)
        // End 15 minutes after 04:00 (04:15 Sep 7 -> business day Sep 7)
        val endWall = toWallMillis(2026, 9, 7, 4, 15, 0)
        val totalDuration = 30 * 60 * 1000L // 30 minutes = 1,800,000 ms

        assertFalse(provider.isSameBusinessDay(startWall, endWall, testZone))
        assertEquals(LocalDate.of(2026, 9, 6), provider.getBusinessDate(startWall, testZone))
        assertEquals(LocalDate.of(2026, 9, 7), provider.getBusinessDate(endWall, testZone))

        val clock = FakeClock(initialWall = startWall, initialElapsed = 100_000L)
        val tracker = UsageTracker(clock = clock, zoneIdProvider = { testZone }, businessDayProvider = provider)

        tracker.recordUsageSegment("com.test.app", startWall, endWall, totalDuration)

        // Query usage when clock is still at 03:45 (Sep 6 cycle) -> should be 15 mins
        clock.setWallTime(startWall)
        val usageDay6 = tracker.getTodayUsage("com.test.app")
        assertEquals(15 * 60 * 1000L, usageDay6)

        // Query usage when clock advances to 04:15 (Sep 7 cycle) -> should be 15 mins
        clock.setWallTime(endWall)
        val usageDay7 = tracker.getTodayUsage("com.test.app")
        assertEquals(15 * 60 * 1000L, usageDay7)
    }

    // 8. Task started at 03:59 and completed at 04:01 belongs to old cycle
    @Test
    fun test08_taskStartedBefore0400_belongsToOldCycle() {
        val taskStartWall = toWallMillis(2026, 9, 7, 3, 59, 0)
        val taskCompleteWall = toWallMillis(2026, 9, 7, 4, 1, 0)

        // Canonical Design Mục 8: Task bắt đầu trước 04:00 thuộc chu kỳ cũ, kể cả khi hoàn thành sau 04:00
        val taskCycle = provider.getTaskBusinessDate(taskStartWall, testZone)
        assertEquals(LocalDate.of(2026, 9, 6), taskCycle)

        // System date after 04:00 has moved to new cycle
        val currentCycle = provider.getBusinessDate(taskCompleteWall, testZone)
        assertEquals(LocalDate.of(2026, 9, 7), currentCycle)

        // Since task belonged to old cycle, in the new cycle the task will be reset
        assertFalse(taskCycle == currentCycle)
    }

    // 9. Process restart around 04:00 preserves accounting
    @Test
    fun test09_processRestartAround0400_preservesAccounting() {
        val wallBefore = toWallMillis(2026, 9, 7, 3, 50, 0)
        val clock = FakeClock(initialWall = wallBefore, initialElapsed = 1_000L)
        val tracker1 = UsageTracker(clock = clock, zoneIdProvider = { testZone }, businessDayProvider = provider)

        tracker1.recordUsageSegment("com.test.restart", wallBefore, wallBefore + 5 * 60 * 1000L, 5 * 60 * 1000L)
        val serializedJson = tracker1.serializeToJson()

        // Advance clock past 04:00 (e.g. 04:05)
        val wallAfter = toWallMillis(2026, 9, 7, 4, 5, 0)
        val clock2 = FakeClock(initialWall = wallAfter, initialElapsed = 50_000L)
        val tracker2 = UsageTracker(clock = clock2, zoneIdProvider = { testZone }, businessDayProvider = provider)

        // Hydrate from serialized data
        tracker2.loadFromJson(serializedJson)

        // In new cycle, today's usage for com.test.restart should be 0ms
        assertEquals(0L, tracker2.getTodayUsage("com.test.restart"))

        // Record 3 minutes in new cycle
        tracker2.recordUsageSegment("com.test.restart", wallAfter, wallAfter + 3 * 60 * 1000L, 3 * 60 * 1000L)
        assertEquals(3 * 60 * 1000L, tracker2.getTodayUsage("com.test.restart"))
    }

    // 10. Timezone change dynamically adapts business date
    @Test
    fun test10_timezoneChange_dynamicallyAdapts() {
        // Epoch milli representing 2026-09-07T03:30:00 UTC (10:30 AM in UTC+7, but 03:30 AM in UTC)
        val wallInstant = ZonedDateTime.of(2026, 9, 7, 3, 30, 0, 0, ZoneId.of("UTC")).toInstant().toEpochMilli()

        // In UTC (03:30 AM < 04:00 AM) -> belongs to Sep 6
        assertEquals(LocalDate.of(2026, 9, 6), provider.getBusinessDate(wallInstant, ZoneId.of("UTC")))

        // In UTC+7 (10:30 AM >= 04:00 AM) -> belongs to Sep 7
        assertEquals(LocalDate.of(2026, 9, 7), provider.getBusinessDate(wallInstant, ZoneId.of("Asia/Ho_Chi_Minh")))
    }

    // 11. Wall clock forward jump resilience
    @Test
    fun test11_wallClockForwardJump_resilience() {
        val wallInitial = toWallMillis(2026, 9, 7, 2, 0, 0) // Sep 6 cycle
        val clock = FakeClock(initialWall = wallInitial, initialElapsed = 10_000L)
        val tracker = UsageTracker(clock = clock, zoneIdProvider = { testZone }, businessDayProvider = provider)

        tracker.startSession("com.test.jump")

        // Monotonic elapsed advances by 10s, but user jumps wall clock forward by 3 hours to 05:00
        val wallJumped = toWallMillis(2026, 9, 7, 5, 0, 0)
        clock.setWallTime(wallJumped)
        clock.setElapsedRealtime(20_000L) // only 10s elapsed!

        tracker.stopSession("com.test.jump")

        // Active duration is monotonically 10 seconds.
        // Wall interval: 2:00 -> 5:00 (3 hours). Boundary was at 4:00.
        // Before 4:00: 2 hours (2/3 of wall interval). After 4:00: 1 hour (1/3 of wall interval).
        // Sep 7 cycle usage: ~1/3 of 10s = 3333ms.
        val todayUsage = tracker.getTodayUsage("com.test.jump")
        assertTrue("Duration should be split proportionally without overflow", todayUsage in 3000L..4000L)
    }

    // 12. Wall clock backward jump resilience
    @Test
    fun test12_wallClockBackwardJump_resilience() {
        val wallInitial = toWallMillis(2026, 9, 7, 5, 0, 0)
        val clock = FakeClock(initialWall = wallInitial, initialElapsed = 10_000L)
        val tracker = UsageTracker(clock = clock, zoneIdProvider = { testZone }, businessDayProvider = provider)

        tracker.startSession("com.test.backward")

        // User rolls clock back 2 hours to 03:00, monotonic elapsed advances by 5s
        val wallBackward = toWallMillis(2026, 9, 7, 3, 0, 0)
        clock.setWallTime(wallBackward)
        clock.setElapsedRealtime(15_000L)

        tracker.stopSession("com.test.backward")

        // Clock moved backward! Monotonic elapsed (5,000ms) is assigned to start business day (Sep 7).
        // Check today's usage when clock is back at Sep 7 cycle
        clock.setWallTime(toWallMillis(2026, 9, 7, 6, 0, 0))
        assertEquals(5_000L, tracker.getTodayUsage("com.test.backward"))
    }

    private class FakeClock(
        private var initialWall: Long,
        private var initialElapsed: Long
    ) : Clock {
        override fun wallTimeMillis(): Long = initialWall
        override fun elapsedRealtimeMillis(): Long = initialElapsed

        fun setWallTime(time: Long) { initialWall = time }
        fun setElapsedRealtime(time: Long) { initialElapsed = time }
    }
}
