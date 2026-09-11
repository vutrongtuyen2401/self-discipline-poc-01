package com.example.selfdisciplinepoc01.domain.canonical.cycle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Unit Test cho Cycle Foundation theo MASTER SSOT.
 *
 * Kiểm tra Invariant 1:
 * - Điểm mốc chuyển giao chu kỳ là đúng 04:00 sáng giờ địa phương.
 * - Thời điểm 03:59:59 thuộc chu kỳ ngày hôm trước.
 * - Thời điểm 04:00:00 thuộc chu kỳ ngày hôm nay.
 */
class CanonicalCycleTest {

    private val zoneId = ZoneId.of("Asia/Ho_Chi_Minh")

    @Test
    fun test01_cycleIdentityAround0400Boundary() {
        // 1. Thời điểm 03:59:59 ngày 2026-09-11
        val timeBefore0400 = ZonedDateTime.of(2026, 9, 11, 3, 59, 59, 999_000_000, zoneId).toInstant()
        val boundaryBefore = CanonicalCycleBoundary.fromInstant(timeBefore0400, zoneId)
        
        // Phải thuộc chu kỳ ngày hôm trước: 2026-09-10
        assertEquals("2026-09-10", boundaryBefore.cycleId.dateIdentifier)
        assertTrue("Boundary phải bao chứa thời điểm 03:59:59", boundaryBefore.contains(timeBefore0400))

        // 2. Đúng 04:00:00 ngày 2026-09-11
        val timeAt0400 = ZonedDateTime.of(2026, 9, 11, 4, 0, 0, 0, zoneId).toInstant()
        val boundaryAt = CanonicalCycleBoundary.fromInstant(timeAt0400, zoneId)
        
        // Phải thuộc chu kỳ ngày hôm nay: 2026-09-11
        assertEquals("2026-09-11", boundaryAt.cycleId.dateIdentifier)
        assertTrue("Boundary phải bao chứa thời điểm 04:00:00", boundaryAt.contains(timeAt0400))

        // 3. Thời điểm 04:00:01 ngày 2026-09-11
        val timeAfter0400 = ZonedDateTime.of(2026, 9, 11, 4, 0, 1, 0, zoneId).toInstant()
        val boundaryAfter = CanonicalCycleBoundary.fromInstant(timeAfter0400, zoneId)
        assertEquals("2026-09-11", boundaryAfter.cycleId.dateIdentifier)
        assertTrue(boundaryAfter.contains(timeAfter0400))

        // 4. Kiểm tra boundary start và end
        val expectedStart = ZonedDateTime.of(2026, 9, 11, 4, 0, 0, 0, zoneId).toInstant()
        val expectedEnd = ZonedDateTime.of(2026, 9, 12, 4, 0, 0, 0, zoneId).toInstant()
        assertEquals(expectedStart, boundaryAt.startInstant)
        assertEquals(expectedEnd, boundaryAt.endInstant)
        assertFalse("End instant là độc quyền (exclusive)", boundaryAt.contains(expectedEnd))
    }

    @Test
    fun test02_cycleIdValidation() {
        val validId = CanonicalCycleId("2026-09-11")
        assertEquals("2026-09-11", validId.dateIdentifier)

        val fromLocalDate = CanonicalCycleId.of(LocalDate.of(2026, 9, 11))
        assertEquals("2026-09-11", fromLocalDate.dateIdentifier)
    }

    @Test(expected = IllegalArgumentException::class)
    fun test03_invalidCycleIdThrows() {
        CanonicalCycleId("invalid-date")
    }
}
