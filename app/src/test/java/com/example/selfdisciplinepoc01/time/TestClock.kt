package com.example.selfdisciplinepoc01.time

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Deterministic clock for unit testing.
 * Allows independent advancement of wall-clock time and monotonic elapsed time.
 */
class TestClock(
    private var currentWallMillis: Long = 0L,
    private var currentElapsedMillis: Long = 0L
) : Clock {

    override fun wallTimeMillis(): Long = currentWallMillis

    override fun elapsedRealtimeMillis(): Long = currentElapsedMillis

    fun setWallTime(millis: Long) {
        currentWallMillis = millis
    }

    fun setElapsedRealtime(millis: Long) {
        currentElapsedMillis = millis
    }

    fun advanceElapsed(millis: Long) {
        currentElapsedMillis += millis
    }

    fun advanceBoth(millis: Long) {
        currentWallMillis += millis
        currentElapsedMillis += millis
    }

    companion object {
        fun at(
            year: Int,
            month: Int,
            day: Int,
            hour: Int,
            minute: Int,
            second: Int = 0,
            millisecond: Int = 0,
            zoneId: ZoneId = ZoneId.of("UTC")
        ): TestClock {
            val zdt = ZonedDateTime.of(
                LocalDate.of(year, month, day),
                LocalTime.of(hour, minute, second, millisecond * 1_000_000),
                zoneId
            )
            val wall = zdt.toInstant().toEpochMilli()
            return TestClock(currentWallMillis = wall, currentElapsedMillis = 100_000L)
        }
    }
}
