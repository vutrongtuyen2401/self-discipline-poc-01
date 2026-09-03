package com.example.selfdisciplinepoc01.policy

import com.example.selfdisciplinepoc01.target.model.TimeSchedule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class ScheduleEvaluatorTest {

    private val testZone: ZoneId = ZoneId.of("UTC")
    private val testDate: LocalDate = LocalDate.of(2026, 9, 3)

    private fun timeToMillis(hour: Int, minute: Int, second: Int = 0, millis: Int = 0): Long {
        val zdt = ZonedDateTime.of(
            testDate,
            LocalTime.of(hour, minute, second, millis * 1_000_000),
            testZone
        )
        return zdt.toInstant().toEpochMilli()
    }

    // 1. Schedule disabled => no restriction
    @Test
    fun schedule_whenDisabled_returnsFalse() {
        val schedule = TimeSchedule(
            enabled = false,
            startHour = 9,
            startMinute = 0,
            endHour = 17,
            endMinute = 0
        )
        val time1200 = timeToMillis(12, 0)
        assertFalse("Disabled schedule must never lock", ScheduleEvaluator.isWithinSchedule(schedule, time1200, testZone))
    }

    // 2. Schedule is null => returns false
    @Test
    fun schedule_whenNull_returnsFalse() {
        val time1200 = timeToMillis(12, 0)
        assertFalse(ScheduleEvaluator.isWithinSchedule(null, time1200, testZone))
    }

    // 3. Normal interval (09:00 -> 17:00): before start (08:59:59) => ALLOW
    @Test
    fun normalInterval_beforeStart_returnsFalse() {
        val schedule = TimeSchedule(enabled = true, startHour = 9, startMinute = 0, endHour = 17, endMinute = 0)
        val time0859 = timeToMillis(8, 59, 59, 999)
        assertFalse("08:59:59 should be ALLOWED", ScheduleEvaluator.isWithinSchedule(schedule, time0859, testZone))
    }

    // 4. Normal interval (09:00 -> 17:00): exact start (09:00:00) => LOCK
    @Test
    fun normalInterval_exactStart_returnsTrue() {
        val schedule = TimeSchedule(enabled = true, startHour = 9, startMinute = 0, endHour = 17, endMinute = 0)
        val time0900 = timeToMillis(9, 0, 0, 0)
        assertTrue("09:00:00 exact start should be LOCKED", ScheduleEvaluator.isWithinSchedule(schedule, time0900, testZone))
    }

    // 5. Normal interval (09:00 -> 17:00): inside (12:00:00) => LOCK
    @Test
    fun normalInterval_inside_returnsTrue() {
        val schedule = TimeSchedule(enabled = true, startHour = 9, startMinute = 0, endHour = 17, endMinute = 0)
        val time1200 = timeToMillis(12, 0, 0, 0)
        assertTrue("12:00:00 inside should be LOCKED", ScheduleEvaluator.isWithinSchedule(schedule, time1200, testZone))
    }

    // 6. Normal interval (09:00 -> 17:00): 16:59:59 => LOCK
    @Test
    fun normalInterval_lastSecondBeforeEnd_returnsTrue() {
        val schedule = TimeSchedule(enabled = true, startHour = 9, startMinute = 0, endHour = 17, endMinute = 0)
        val time1659 = timeToMillis(16, 59, 59, 999)
        assertTrue("16:59:59 before end should be LOCKED", ScheduleEvaluator.isWithinSchedule(schedule, time1659, testZone))
    }

    // 7. Normal interval (09:00 -> 17:00): exact end (17:00:00) => ALLOW
    @Test
    fun normalInterval_exactEnd_returnsFalse() {
        val schedule = TimeSchedule(enabled = true, startHour = 9, startMinute = 0, endHour = 17, endMinute = 0)
        val time1700 = timeToMillis(17, 0, 0, 0)
        assertFalse("17:00:00 exact end should be ALLOWED", ScheduleEvaluator.isWithinSchedule(schedule, time1700, testZone))
    }

    // 8. Normal interval (09:00 -> 17:00): after end (18:00:00) => ALLOW
    @Test
    fun normalInterval_afterEnd_returnsFalse() {
        val schedule = TimeSchedule(enabled = true, startHour = 9, startMinute = 0, endHour = 17, endMinute = 0)
        val time1800 = timeToMillis(18, 0, 0, 0)
        assertFalse("18:00:00 after end should be ALLOWED", ScheduleEvaluator.isWithinSchedule(schedule, time1800, testZone))
    }

    // 9. Cross-midnight interval (22:00 -> 07:00): 21:59:59 => ALLOW
    @Test
    fun crossMidnight_beforeStart_returnsFalse() {
        val schedule = TimeSchedule(enabled = true, startHour = 22, startMinute = 0, endHour = 7, endMinute = 0)
        val time2159 = timeToMillis(21, 59, 59, 999)
        assertFalse("21:59:59 before 22:00 should be ALLOWED", ScheduleEvaluator.isWithinSchedule(schedule, time2159, testZone))
    }

    // 10. Cross-midnight interval (22:00 -> 07:00): exact start 22:00:00 => LOCK
    @Test
    fun crossMidnight_exactStart_returnsTrue() {
        val schedule = TimeSchedule(enabled = true, startHour = 22, startMinute = 0, endHour = 7, endMinute = 0)
        val time2200 = timeToMillis(22, 0, 0, 0)
        assertTrue("22:00:00 exact start should be LOCKED", ScheduleEvaluator.isWithinSchedule(schedule, time2200, testZone))
    }

    // 11. Cross-midnight interval (22:00 -> 07:00): 23:59:59 => LOCK
    @Test
    fun crossMidnight_lateNight_returnsTrue() {
        val schedule = TimeSchedule(enabled = true, startHour = 22, startMinute = 0, endHour = 7, endMinute = 0)
        val time2359 = timeToMillis(23, 59, 59, 999)
        assertTrue("23:59:59 should be LOCKED", ScheduleEvaluator.isWithinSchedule(schedule, time2359, testZone))
    }

    // 12. Cross-midnight interval (22:00 -> 07:00): exact midnight 00:00:00 => LOCK
    @Test
    fun crossMidnight_exactMidnight_returnsTrue() {
        val schedule = TimeSchedule(enabled = true, startHour = 22, startMinute = 0, endHour = 7, endMinute = 0)
        val time0000 = timeToMillis(0, 0, 0, 0)
        assertTrue("00:00:00 exact midnight should be LOCKED", ScheduleEvaluator.isWithinSchedule(schedule, time0000, testZone))
    }

    // 13. Cross-midnight interval (22:00 -> 07:00): 06:59:59 => LOCK
    @Test
    fun crossMidnight_earlyMorning_returnsTrue() {
        val schedule = TimeSchedule(enabled = true, startHour = 22, startMinute = 0, endHour = 7, endMinute = 0)
        val time0659 = timeToMillis(6, 59, 59, 999)
        assertTrue("06:59:59 should be LOCKED", ScheduleEvaluator.isWithinSchedule(schedule, time0659, testZone))
    }

    // 14. Cross-midnight interval (22:00 -> 07:00): exact end 07:00:00 => ALLOW
    @Test
    fun crossMidnight_exactEnd_returnsFalse() {
        val schedule = TimeSchedule(enabled = true, startHour = 22, startMinute = 0, endHour = 7, endMinute = 0)
        val time0700 = timeToMillis(7, 0, 0, 0)
        assertFalse("07:00:00 exact end should be ALLOWED", ScheduleEvaluator.isWithinSchedule(schedule, time0700, testZone))
    }

    // 15. Cross-midnight interval (22:00 -> 07:00): 12:00:00 => ALLOW
    @Test
    fun crossMidnight_midday_returnsFalse() {
        val schedule = TimeSchedule(enabled = true, startHour = 22, startMinute = 0, endHour = 7, endMinute = 0)
        val time1200 = timeToMillis(12, 0, 0, 0)
        assertFalse("12:00:00 midday should be ALLOWED", ScheduleEvaluator.isWithinSchedule(schedule, time1200, testZone))
    }

    // 16. start == end all-day (10:00 -> 10:00): enabled => LOCK for all times
    @Test
    fun startEqualsEnd_allDayLock() {
        val schedule = TimeSchedule(enabled = true, startHour = 10, startMinute = 0, endHour = 10, endMinute = 0)
        assertTrue("Exact hour should be LOCKED", ScheduleEvaluator.isWithinSchedule(schedule, timeToMillis(10, 0), testZone))
        assertTrue("Midnight should be LOCKED", ScheduleEvaluator.isWithinSchedule(schedule, timeToMillis(0, 0), testZone))
        assertTrue("Any time should be LOCKED", ScheduleEvaluator.isWithinSchedule(schedule, timeToMillis(15, 30), testZone))
    }

    // 17. start == end but disabled => ALLOW
    @Test
    fun startEqualsEnd_whenDisabled_returnsFalse() {
        val schedule = TimeSchedule(enabled = false, startHour = 10, startMinute = 0, endHour = 10, endMinute = 0)
        assertFalse(ScheduleEvaluator.isWithinSchedule(schedule, timeToMillis(10, 0), testZone))
    }
}
