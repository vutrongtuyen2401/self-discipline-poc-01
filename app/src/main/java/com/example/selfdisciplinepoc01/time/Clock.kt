package com.example.selfdisciplinepoc01.time

import android.os.SystemClock

/**
 * Abstraction for time sources to ensure deterministic unit testing
 * and separation of concerns between wall-clock (calendar/schedule)
 * and monotonic elapsed time (duration accounting).
 */
interface Clock {
    /**
     * Wall-clock time in milliseconds since Unix epoch.
     * Used exclusively for:
     * - Determining local calendar day (LocalDate / YYYY-MM-DD)
     * - Evaluating time-of-day schedules (HH:mm)
     * - Identifying midnight rollover boundaries
     */
    fun wallTimeMillis(): Long

    /**
     * Monotonic elapsed realtime in milliseconds, including time spent in deep sleep.
     * Used exclusively for:
     * - Measuring actual application usage duration
     *
     * Guarantee: Cannot jump backward or forward due to NTP updates or user clock manipulation.
     */
    fun elapsedRealtimeMillis(): Long
}

class SystemClockImpl : Clock {
    override fun wallTimeMillis(): Long = System.currentTimeMillis()
    override fun elapsedRealtimeMillis(): Long = SystemClock.elapsedRealtime()
}
