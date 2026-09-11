package com.example.selfdisciplinepoc01.domain.canonical.cycle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Suite kiểm thử chấp nhận (Acceptance Tests) cho Canonical Cycle Engine.
 *
 * Tiêu chí SSOT:
 * 1. 03:59:59 -> previous cycle
 * 2. 04:00:00 -> current/new cycle
 * 3. 04:00:00.000 boundary exactness
 * 4. same instant + same zone -> same CycleId
 * 5. offline catch-up does not replay old cycles
 * 6. timezone-aware calculation
 */
class CycleEngineTest {

    private val hcmZone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val utcZone = ZoneId.of("UTC")
    private val nyZone = ZoneId.of("America/New_York")

    @Test
    fun test01_boundary035959BelongsToPreviousCycle() {
        val instant035959 = ZonedDateTime.of(2026, 9, 11, 3, 59, 59, 999_000_000, hcmZone).toInstant()
        val cycleId = CycleEngine.getCurrentCycleId(instant035959, hcmZone)
        
        // Phải thuộc chu kỳ ngày hôm trước: 2026-09-10
        assertEquals("2026-09-10", cycleId.dateIdentifier)
    }

    @Test
    fun test02_boundary040000BelongsToNewCycle() {
        val instant040000 = ZonedDateTime.of(2026, 9, 11, 4, 0, 0, 0, hcmZone).toInstant()
        val cycleId = CycleEngine.getCurrentCycleId(instant040000, hcmZone)
        
        // Phải thuộc chu kỳ ngày hôm nay: 2026-09-11
        assertEquals("2026-09-11", cycleId.dateIdentifier)
    }

    @Test
    fun test03_boundaryExactnessToNanoseconds() {
        val instantJustBefore = ZonedDateTime.of(2026, 9, 11, 3, 59, 59, 999_999_999, hcmZone).toInstant()
        val instantExact = ZonedDateTime.of(2026, 9, 11, 4, 0, 0, 0, hcmZone).toInstant()

        val boundaryJustBefore = CycleEngine.getCurrentCycleBoundary(instantJustBefore, hcmZone)
        val boundaryExact = CycleEngine.getCurrentCycleBoundary(instantExact, hcmZone)

        assertEquals("2026-09-10", boundaryJustBefore.cycleId.dateIdentifier)
        assertEquals("2026-09-11", boundaryExact.cycleId.dateIdentifier)
        assertEquals(boundaryJustBefore.endInstant, boundaryExact.startInstant)

        // Ranh giới start là inclusive, end là exclusive
        assertTrue(boundaryExact.contains(boundaryExact.startInstant))
        assertFalse(boundaryExact.contains(boundaryExact.endInstant))
    }

    @Test
    fun test04_sameInstantAndZoneAlwaysResolvesToSameCycleId() {
        val instant = ZonedDateTime.of(2026, 9, 11, 14, 30, 0, 0, hcmZone).toInstant()

        val id1 = CycleEngine.getCurrentCycleId(instant, hcmZone)
        val id2 = CycleEngine.getCurrentCycleId(instant, hcmZone)
        val id3 = CycleEngine.getCurrentCycleId(instant, hcmZone)

        assertEquals(id1, id2)
        assertEquals(id2, id3)
        assertEquals("2026-09-11", id1.dateIdentifier)
    }

    @Test
    fun test05_offlineCatchUpDoesNotReplayOldCycles() {
        // Giả sử thiết bị offline từ 7 ngày trước (2026-09-04)
        val lastActive = ZonedDateTime.of(2026, 9, 4, 10, 0, 0, 0, hcmZone).toInstant()
        // Thiết bị mở lại vào ngày 2026-09-11 lúc 15:00
        val current = ZonedDateTime.of(2026, 9, 11, 15, 0, 0, 0, hcmZone).toInstant()

        val resolvedBoundary = CycleEngine.resolveCatchUpCycle(
            lastActiveInstant = lastActive,
            currentInstant = current,
            zoneId = hcmZone
        )

        // Trả về trực tiếp chu kỳ hiện tại 2026-09-11, không tạo chu kỳ trung gian
        assertEquals("2026-09-11", resolvedBoundary.cycleId.dateIdentifier)
        assertTrue(resolvedBoundary.contains(current))
        assertFalse(resolvedBoundary.contains(lastActive))
    }

    @Test
    fun test06_timezoneAwareCalculation() {
        // Cùng một instant thời gian tuyệt đối (UTC) nhưng ở 2 múi giờ khác nhau:
        // 2026-09-11 02:30:00 UTC
        val instantUtc = Instant.parse("2026-09-11T02:30:00Z")

        // Tại UTC: 02:30 < 04:00 -> Thuộc chu kỳ 2026-09-10
        val cycleUtc = CycleEngine.getCurrentCycleId(instantUtc, utcZone)
        assertEquals("2026-09-10", cycleUtc.dateIdentifier)

        // Tại HCM (UTC+7): Lúc đó là 09:30 AM ngày 11/09 -> Đã qua 04:00 -> Thuộc chu kỳ 2026-09-11
        val cycleHcm = CycleEngine.getCurrentCycleId(instantUtc, hcmZone)
        assertEquals("2026-09-11", cycleHcm.dateIdentifier)

        // Tại New York (UTC-4): Lúc đó là 22:30 ngày 10/09 -> Thuộc chu kỳ 2026-09-10
        val cycleNy = CycleEngine.getCurrentCycleId(instantUtc, nyZone)
        assertEquals("2026-09-10", cycleNy.dateIdentifier)
    }

    @Test
    fun test07_isSameCycleHelper() {
        val time1 = ZonedDateTime.of(2026, 9, 11, 5, 0, 0, 0, hcmZone).toInstant()
        val time2 = ZonedDateTime.of(2026, 9, 11, 23, 0, 0, 0, hcmZone).toInstant()
        val time3 = ZonedDateTime.of(2026, 9, 12, 3, 30, 0, 0, hcmZone).toInstant()
        val timeNextCycle = ZonedDateTime.of(2026, 9, 12, 4, 1, 0, 0, hcmZone).toInstant()

        // time1, time2, time3 đều thuộc chu kỳ 2026-09-11
        assertTrue(CycleEngine.isSameCycle(time1, time2, hcmZone))
        assertTrue(CycleEngine.isSameCycle(time2, time3, hcmZone))

        // timeNextCycle thuộc chu kỳ 2026-09-12
        assertFalse(CycleEngine.isSameCycle(time3, timeNextCycle, hcmZone))
    }
}
