package com.example.selfdisciplinepoc01.usage

import com.example.selfdisciplinepoc01.time.TestClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class UsageTrackerTest {

    private val testZone = ZoneId.of("UTC")

    // 1. Zero usage initially
    @Test
    fun test1_zeroUsageInitially() {
        val clock = TestClock.at(2026, 9, 3, 10, 0, zoneId = testZone)
        val tracker = UsageTracker(clock = clock, zoneId = testZone)

        assertEquals(0L, tracker.getTodayUsage("com.android.chrome"))
        assertFalse(tracker.isSessionActive("com.android.chrome"))
        assertNull(tracker.getActivePackage())
    }

    // 2. Start session and measure ongoing usage
    @Test
    fun test2_startSession_measuresOngoingUsage() {
        val clock = TestClock.at(2026, 9, 3, 10, 0, zoneId = testZone)
        val tracker = UsageTracker(clock = clock, zoneId = testZone)

        tracker.startSession("com.android.chrome")
        assertTrue(tracker.isSessionActive("com.android.chrome"))
        assertEquals("com.android.chrome", tracker.getActivePackage())

        // Advance 30 seconds
        clock.advanceBoth(30_000L)
        assertEquals(30_000L, tracker.getTodayUsage("com.android.chrome"))
    }

    // 3. Repeated startSession is idempotent (does NOT reset start timestamp)
    @Test
    fun test3_repeatedStartSession_isIdempotent() {
        val clock = TestClock.at(2026, 9, 3, 10, 0, zoneId = testZone)
        val tracker = UsageTracker(clock = clock, zoneId = testZone)

        tracker.startSession("com.android.chrome")

        // 10 seconds pass
        clock.advanceBoth(10_000L)

        // AccessibilityEvent fires again for the same package
        tracker.startSession("com.android.chrome")
        tracker.startSession("com.android.chrome")

        // Another 20 seconds pass
        clock.advanceBoth(20_000L)

        // Total should be 30 seconds, not 20 seconds!
        assertEquals(30_000L, tracker.getTodayUsage("com.android.chrome"))
    }

    // 4. Stop session records duration and stops active tracking
    @Test
    fun test4_stopSession_recordsDuration() {
        val clock = TestClock.at(2026, 9, 3, 10, 0, zoneId = testZone)
        val tracker = UsageTracker(clock = clock, zoneId = testZone)

        tracker.startSession("com.android.chrome")
        clock.advanceBoth(45_000L)

        tracker.stopSession("com.android.chrome")
        assertFalse(tracker.isSessionActive("com.android.chrome"))
        assertNull(tracker.getActivePackage())

        // Time stops accumulating
        clock.advanceBoth(60_000L)
        assertEquals(45_000L, tracker.getTodayUsage("com.android.chrome"))
    }

    // 5. Multiple sessions accumulate in the same day
    @Test
    fun test5_multipleSessionsAccumulate() {
        val clock = TestClock.at(2026, 9, 3, 10, 0, zoneId = testZone)
        val tracker = UsageTracker(clock = clock, zoneId = testZone)

        // Session 1: 40 seconds
        tracker.startSession("com.android.chrome")
        clock.advanceBoth(40_000L)
        tracker.stopSession()

        // Idle 100 seconds
        clock.advanceBoth(100_000L)

        // Session 2: 20 seconds
        tracker.startSession("com.android.chrome")
        clock.advanceBoth(20_000L)
        tracker.stopSession()

        assertEquals(60_000L, tracker.getTodayUsage("com.android.chrome"))
    }

    // 6. Transition to different package closes first session and starts second
    @Test
    fun test6_differentPackageTransition_closesFirstAndStartsSecond() {
        val clock = TestClock.at(2026, 9, 3, 10, 0, zoneId = testZone)
        val tracker = UsageTracker(clock = clock, zoneId = testZone)

        tracker.startSession("com.android.chrome")
        clock.advanceBoth(50_000L)

        // Switch to calculator
        tracker.startSession("com.android.bbkcalculator")
        assertEquals("com.android.bbkcalculator", tracker.getActivePackage())
        assertFalse(tracker.isSessionActive("com.android.chrome"))

        // Another 30 seconds pass
        clock.advanceBoth(30_000L)

        assertEquals(50_000L, tracker.getTodayUsage("com.android.chrome"))
        assertEquals(30_000L, tracker.getTodayUsage("com.android.bbkcalculator"))
    }

    // 7. Screen OFF closes active segment
    @Test
    fun test7_screenOff_closesActiveSegment() {
        val clock = TestClock.at(2026, 9, 3, 10, 0, zoneId = testZone)
        val tracker = UsageTracker(clock = clock, zoneId = testZone)

        tracker.startSession("com.android.chrome")
        clock.advanceBoth(25_000L)

        tracker.onScreenOff()
        assertFalse(tracker.isSessionActive("com.android.chrome"))
        assertNull(tracker.getActivePackage())

        // 10 minutes of screen off sleep time passes
        clock.advanceBoth(600_000L)

        // Usage must NOT have increased while screen was off
        assertEquals(25_000L, tracker.getTodayUsage("com.android.chrome"))
    }

    // 8. Screen ON does not blindly restart session
    @Test
    fun test8_screenOn_doesNotBlindlyRestart() {
        val clock = TestClock.at(2026, 9, 3, 10, 0, zoneId = testZone)
        val tracker = UsageTracker(clock = clock, zoneId = testZone)

        tracker.startSession("com.android.chrome")
        clock.advanceBoth(25_000L)
        tracker.onScreenOff()

        tracker.onScreenOn()
        assertNull("Screen on must not blindly resume active session", tracker.getActivePackage())
        assertEquals(25_000L, tracker.getTodayUsage("com.android.chrome"))
    }

    // 9. Daily cycle 04:00 split: session crossing 04:00 boundary splits duration between business days
    @Test
    fun test9_dailyCycle0400Split_splitsBetweenDays() {
        // Start session at 03:55:00 on Sep 4 (5 minutes before 04:00 boundary, belongs to Sep 3 business day)
        val clock = TestClock.at(2026, 9, 4, 3, 55, 0, zoneId = testZone)
        val tracker = UsageTracker(clock = clock, zoneId = testZone)

        tracker.startSession("com.android.chrome")

        // Advance 10 minutes (600,000 ms) to 04:05:00 on Sep 4 (crosses 04:00 into Sep 4 business day)
        clock.advanceBoth(600_000L)

        // Stop session at 04:05
        tracker.stopSession("com.android.chrome")

        // Check usage on Sep 4 business day (today according to 04:00 cycle): exactly 5 minutes (300,000 ms)
        assertEquals(300_000L, tracker.getTodayUsage("com.android.chrome"))

        // Check usage for Sep 3 business day by setting clock back before 04:00 (e.g. 03:50 on Sep 4)
        clock.setWallTime(TestClock.at(2026, 9, 4, 3, 50, zoneId = testZone).wallTimeMillis())
        assertEquals(300_000L, tracker.getTodayUsage("com.android.chrome"))
    }

    // 10. Wall-clock change does NOT corrupt monotonic duration
    @Test
    fun test10_wallClockChange_doesNotCorruptDuration() {
        val clock = TestClock.at(2026, 9, 3, 10, 0, 0, zoneId = testZone)
        val tracker = UsageTracker(clock = clock, zoneId = testZone)

        tracker.startSession("com.android.chrome")

        // Monotonic elapsed advances by 40 seconds (40,000 ms)
        clock.advanceElapsed(40_000L)

        // User changes wall clock to 2 hours ahead (12:00:00) or NTP sync jumps
        clock.setWallTime(TestClock.at(2026, 9, 3, 12, 0, 0, zoneId = testZone).wallTimeMillis())

        tracker.stopSession("com.android.chrome")

        // Usage must strictly be 40 seconds, NOT 2 hours!
        assertEquals(40_000L, tracker.getTodayUsage("com.android.chrome"))
    }

    // 11. Serialization and restoration
    @Test
    fun test11_serializeAndReload_restoresAggregates() {
        val clock = TestClock.at(2026, 9, 3, 10, 0, zoneId = testZone)
        val tracker1 = UsageTracker(clock = clock, zoneId = testZone)

        tracker1.startSession("com.android.chrome")
        clock.advanceBoth(75_000L)
        tracker1.stopSession()

        val json = tracker1.serializeToJson()

        // Recreate new tracker instance simulating app restart
        val tracker2 = UsageTracker(clock = clock, zoneId = testZone)
        tracker2.loadFromJson(json)

        assertEquals(75_000L, tracker2.getTodayUsage("com.android.chrome"))
    }
}
