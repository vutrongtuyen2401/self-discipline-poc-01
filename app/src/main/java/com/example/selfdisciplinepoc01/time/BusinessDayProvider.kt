package com.example.selfdisciplinepoc01.time

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Contract for determining the canonical business day boundary.
 *
 * According to Canonical Design V2 (Section 8 - Daily Cycle & 04:00 Reset):
 * - The system resets at 04:00 local time every day.
 * - Calendar dates prior to 04:00 belong to the previous business cycle.
 * - Tasks or sessions started before 04:00 belong to the old cycle,
 *   even if completed after 04:00.
 * - Rolling 24-hour windows are strictly disallowed.
 */
interface BusinessDayProvider {
    val boundaryHour: Int
    val boundaryMinute: Int

    /**
     * Determines the business date for a given wall-clock timestamp and timezone.
     *
     * If local time is strictly before 04:00:00, returns calendar date minus 1 day.
     * If local time is at or after 04:00:00, returns calendar date.
     */
    fun getBusinessDate(wallTimeMillis: Long, zoneId: ZoneId): LocalDate

    /**
     * Returns the epoch milli representing the exact start of a business day (04:00:00).
     */
    fun getBusinessDayStartWall(businessDate: LocalDate, zoneId: ZoneId): Long

    /**
     * Returns the epoch milli representing the exact end of a business day (03:59:59.999 of next calendar day).
     */
    fun getBusinessDayEndWall(businessDate: LocalDate, zoneId: ZoneId): Long

    /**
     * Returns the next upcoming 04:00:00 boundary in epoch millis from [wallTimeMillis].
     */
    fun getNextBoundaryWall(wallTimeMillis: Long, zoneId: ZoneId): Long

    /**
     * Returns true if two wall-clock timestamps belong to the same business day cycle.
     */
    fun isSameBusinessDay(wallA: Long, wallB: Long, zoneId: ZoneId): Boolean

    /**
     * Resolves the cycle date for a task given its starting wall-clock timestamp.
     * Per Canonical Design V2 (Section 8): A task started before 04:00 belongs to the old cycle.
     */
    fun getTaskBusinessDate(startWallMillis: Long, zoneId: ZoneId): LocalDate
}

/**
 * Standard implementation of [BusinessDayProvider] adhering to 04:00 boundary.
 */
class BusinessDayProviderImpl(
    override val boundaryHour: Int = CANONICAL_BOUNDARY_HOUR,
    override val boundaryMinute: Int = CANONICAL_BOUNDARY_MINUTE
) : BusinessDayProvider {

    private val boundaryTime: LocalTime = LocalTime.of(boundaryHour, boundaryMinute, 0, 0)

    override fun getBusinessDate(wallTimeMillis: Long, zoneId: ZoneId): LocalDate {
        val zdt = Instant.ofEpochMilli(wallTimeMillis).atZone(zoneId)
        val localDate = zdt.toLocalDate()
        val localTime = zdt.toLocalTime()
        return if (localTime.isBefore(boundaryTime)) {
            localDate.minusDays(1)
        } else {
            localDate
        }
    }

    override fun getBusinessDayStartWall(businessDate: LocalDate, zoneId: ZoneId): Long {
        val startZdt = ZonedDateTime.of(businessDate, boundaryTime, zoneId)
        return startZdt.toInstant().toEpochMilli()
    }

    override fun getBusinessDayEndWall(businessDate: LocalDate, zoneId: ZoneId): Long {
        val nextCycleStartZdt = ZonedDateTime.of(businessDate.plusDays(1), boundaryTime, zoneId)
        return nextCycleStartZdt.toInstant().toEpochMilli() - 1L
    }

    override fun getNextBoundaryWall(wallTimeMillis: Long, zoneId: ZoneId): Long {
        val zdt = Instant.ofEpochMilli(wallTimeMillis).atZone(zoneId)
        val localDate = zdt.toLocalDate()
        val localTime = zdt.toLocalTime()
        val targetDate = if (localTime.isBefore(boundaryTime)) {
            localDate
        } else {
            localDate.plusDays(1)
        }
        return ZonedDateTime.of(targetDate, boundaryTime, zoneId).toInstant().toEpochMilli()
    }

    override fun isSameBusinessDay(wallA: Long, wallB: Long, zoneId: ZoneId): Boolean {
        val dateA = getBusinessDate(wallA, zoneId)
        val dateB = getBusinessDate(wallB, zoneId)
        return dateA == dateB
    }

    override fun getTaskBusinessDate(startWallMillis: Long, zoneId: ZoneId): LocalDate {
        return getBusinessDate(startWallMillis, zoneId)
    }

    companion object {
        const val CANONICAL_BOUNDARY_HOUR = 4
        const val CANONICAL_BOUNDARY_MINUTE = 0
    }
}
