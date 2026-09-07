package com.example.selfdisciplinepoc01.policy

import com.example.selfdisciplinepoc01.target.model.TimeSchedule
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

import java.time.ZonedDateTime

/**
 * Pure, deterministic schedule evaluator.
 * Evaluates whether a given wall-clock timestamp falls within a TimeSchedule window,
 * and computes the next schedule transition boundary timestamp.
 */
object ScheduleEvaluator {

    /**
     * Evaluates if [wallTimeMillis] falls within [schedule].
     *
     * Rules:
     * - schedule == null or !schedule.enabled => false (no schedule restriction)
     * - Normal interval (start < end): LOCK when start <= now < end
     * - Cross-midnight interval (start > end): LOCK when now >= start OR now < end
     * - All-day interval (start == end): LOCK when enabled == true
     */
    fun isWithinSchedule(
        schedule: TimeSchedule?,
        wallTimeMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Boolean {
        if (schedule == null || !schedule.enabled) {
            return false
        }

        val nowTime = Instant.ofEpochMilli(wallTimeMillis)
            .atZone(zoneId)
            .toLocalTime()

        val startTime = LocalTime.of(schedule.startHour, schedule.startMinute, 0, 0)
        val endTime = LocalTime.of(schedule.endHour, schedule.endMinute, 0, 0)

        return when {
            // start == end: all-day lock when enabled
            startTime == endTime -> true

            // Normal interval within the same calendar day (e.g. 09:00 -> 17:00)
            startTime.isBefore(endTime) -> {
                (!nowTime.isBefore(startTime)) && nowTime.isBefore(endTime)
            }

            // Cross-midnight interval (e.g. 22:00 -> 07:00)
            else -> {
                (!nowTime.isBefore(startTime)) || nowTime.isBefore(endTime)
            }
        }
    }

    /**
     * Calculates the next boundary epoch timestamp (in wall-clock milliseconds)
     * where the schedule transitions between ALLOW and LOCK (or LOCK and ALLOW).
     *
     * Returns null if:
     * - schedule is null or disabled
     * - schedule is all-day (start == end), which is continuously active with no transition boundaries.
     */
    fun getNextBoundaryMillis(
        schedule: TimeSchedule?,
        wallTimeMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Long? {
        if (schedule == null || !schedule.enabled) {
            return null
        }

        val startTime = LocalTime.of(schedule.startHour, schedule.startMinute, 0, 0)
        val endTime = LocalTime.of(schedule.endHour, schedule.endMinute, 0, 0)

        // All-day schedule (start == end): continuously locked, no boundary transitions
        if (startTime == endTime) {
            return null
        }

        val nowZdt = Instant.ofEpochMilli(wallTimeMillis).atZone(zoneId)
        val nowDate = nowZdt.toLocalDate()
        val nowTime = nowZdt.toLocalTime()

        val nextZdt = if (startTime.isBefore(endTime)) {
            // Normal schedule within same calendar day (e.g. 09:00 -> 17:00)
            when {
                nowTime.isBefore(startTime) -> {
                    // Before start (e.g. 08:30) -> next boundary is start today
                    ZonedDateTime.of(nowDate, startTime, zoneId)
                }
                nowTime.isBefore(endTime) -> {
                    // Inside schedule (e.g. 12:00) -> next boundary is end today
                    ZonedDateTime.of(nowDate, endTime, zoneId)
                }
                else -> {
                    // After end (e.g. 17:00 or 18:00) -> next boundary is start tomorrow
                    ZonedDateTime.of(nowDate.plusDays(1), startTime, zoneId)
                }
            }
        } else {
            // Cross-midnight schedule (e.g. 22:00 -> 07:00)
            when {
                // Outside interval: between end and start (e.g. 07:00 <= now < 22:00)
                (!nowTime.isBefore(endTime)) && nowTime.isBefore(startTime) -> {
                    // Next boundary is start today
                    ZonedDateTime.of(nowDate, startTime, zoneId)
                }
                // Inside interval: evening portion (now >= 22:00)
                !nowTime.isBefore(startTime) -> {
                    // Next boundary is end tomorrow morning (e.g. 07:00 next day)
                    ZonedDateTime.of(nowDate.plusDays(1), endTime, zoneId)
                }
                // Inside interval: morning portion (now < 07:00)
                else -> {
                    // Next boundary is end today morning (e.g. 07:00 today)
                    ZonedDateTime.of(nowDate, endTime, zoneId)
                }
            }
        }

        return nextZdt.toInstant().toEpochMilli()
    }
}
