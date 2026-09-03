package com.example.selfdisciplinepoc01.policy

import com.example.selfdisciplinepoc01.target.model.TimeSchedule
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * Pure, deterministic schedule evaluator.
 * Evaluates whether a given wall-clock timestamp falls within a TimeSchedule window.
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
}
