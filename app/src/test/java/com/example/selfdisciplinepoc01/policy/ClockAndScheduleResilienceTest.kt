package com.example.selfdisciplinepoc01.policy

import com.example.selfdisciplinepoc01.LockReason
import com.example.selfdisciplinepoc01.target.model.LockedApp
import com.example.selfdisciplinepoc01.target.model.TimeLimit
import com.example.selfdisciplinepoc01.target.model.TimeSchedule
import com.example.selfdisciplinepoc01.target.repository.TargetRepository
import com.example.selfdisciplinepoc01.time.TestClock
import com.example.selfdisciplinepoc01.usage.LimitScheduler
import com.example.selfdisciplinepoc01.usage.UsageLimitWatcherImpl
import com.example.selfdisciplinepoc01.usage.UsageTracker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * PHASE 10-C: CLOCK / TIME RESILIENCE HARDENING TEST SUITE
 *
 * Verifies resilient, deterministic behavior against:
 * - Wall-clock changes (forward / backward / large jumps)
 * - Timezone changes and DST transitions
 * - Midnight rollover (usage splitting & monotonic elapsed realtime)
 * - Stale deadlines and callbacks across service restarts
 * - Screen OFF / ON across midnight
 * - Deadline collision preservation under clock jumps
 *
 * Scenarios covered:
 * A. schedule boundary after wall-clock forward jump
 * B. schedule boundary after wall-clock backward jump
 * C. early callback re-evaluation
 * D. late callback re-evaluation
 * E. timezone-local schedule interpretation
 * F. cross-midnight schedule
 * G. midnight usage split
 * H. service restart near midnight
 * I. service restart after wall-clock change
 * J. screen OFF across midnight
 * K. screen ON waits for fresh foreground event
 * L. stale callback after restart
 * M. schedule + limit collision after clock jump
 * N. target switch after clock jump
 * O. policy update after clock jump
 */
class ClockAndScheduleResilienceTest {

    private class TestScheduleScheduler : ScheduleScheduler {
        var scheduledDelayMillis: Long? = null
        var scheduledRunnable: Runnable? = null
        var cancelCallCount: Int = 0

        override fun schedule(delayMillis: Long, action: Runnable) {
            cancel()
            scheduledDelayMillis = delayMillis
            scheduledRunnable = action
        }

        override fun cancel() {
            if (scheduledRunnable != null) {
                cancelCallCount++
            }
            scheduledDelayMillis = null
            scheduledRunnable = null
        }

        fun trigger() {
            val a = scheduledRunnable
            scheduledRunnable = null
            scheduledDelayMillis = null
            a?.run()
        }
    }

    private class TestLimitScheduler : LimitScheduler {
        var scheduledDelayMillis: Long? = null
        var scheduledRunnable: Runnable? = null
        var cancelCallCount: Int = 0

        override fun schedule(delayMillis: Long, action: Runnable) {
            cancel()
            scheduledDelayMillis = delayMillis
            scheduledRunnable = action
        }

        override fun cancel() {
            if (scheduledRunnable != null) {
                cancelCallCount++
            }
            scheduledDelayMillis = null
            scheduledRunnable = null
        }

        fun trigger() {
            val r = scheduledRunnable
            scheduledRunnable = null
            scheduledDelayMillis = null
            r?.run()
        }
    }

    private class FakeTargetRepository(val apps: MutableMap<String, LockedApp> = mutableMapOf()) : TargetRepository {
        override fun getLockedPackages(): Flow<List<LockedApp>> = flowOf(apps.values.toList())
        override fun isLocked(packageName: String): Boolean = apps[packageName]?.enabled == true
        override fun getTarget(packageName: String): LockedApp? = apps[packageName]
        override suspend fun add(packageName: String) { apps[packageName] = LockedApp(packageName, true) }
        override suspend fun remove(packageName: String) { apps.remove(packageName) }
        override suspend fun setEnabled(packageName: String, enabled: Boolean) {
            apps[packageName] = (apps[packageName] ?: LockedApp(packageName)).copy(enabled = enabled)
        }
        override suspend fun updatePolicy(packageName: String, schedule: TimeSchedule?, timeLimit: TimeLimit?) {
            apps[packageName] = (apps[packageName] ?: LockedApp(packageName)).copy(schedule = schedule, timeLimit = timeLimit)
        }
    }

    private class MockLockLauncher {
        val launchHistory = mutableListOf<LaunchRecord>()
        var isLockScreenVisible = false
        var isChromeLockedForCurrentTransition = false
        var lastForegroundPackage: String? = null
        var lastLockLaunchTimestamp = 0L
        var currentSessionId = 0L
        val cooldownMs = 1500L

        data class LaunchRecord(
            val packageName: String,
            val reason: LockReason,
            val sessionId: Long,
            val timestamp: Long
        )

        fun launchLockSession(
            packageName: String,
            reason: LockReason,
            now: Long
        ): Boolean {
            val prevPkg = lastForegroundPackage
            val isNewTransition = (prevPkg != packageName)
            val elapsedSinceLastLaunch = now - lastLockLaunchTimestamp

            // Rule A: isLockScreenVisible guard
            if (isLockScreenVisible) return false

            // Rule B: intra-session duplicate guard
            if (!isNewTransition && isChromeLockedForCurrentTransition) return false

            // Rule C: COOLDOWN_MS guard
            if (!isNewTransition && (elapsedSinceLastLaunch < cooldownMs)) return false

            // Launch successful
            isChromeLockedForCurrentTransition = true
            lastForegroundPackage = packageName
            lastLockLaunchTimestamp = now
            currentSessionId++
            val session = currentSessionId

            launchHistory.add(LaunchRecord(packageName, reason, session, now))
            return true
        }
    }

    // =========================================================================
    // A. schedule boundary after wall-clock forward jump
    // =========================================================================
    @Test
    fun testA_scheduleBoundaryAfterWallClockForwardJump() {
        val clock = TestClock.at(2026, 9, 4, 13, 0, zoneId = ZoneId.of("UTC"))
        val repo = FakeTargetRepository()
        val pkg = "com.test.forward"
        // Schedule: 14:00 -> 16:00
        repo.apps[pkg] = LockedApp(
            packageName = pkg,
            enabled = true,
            schedule = TimeSchedule(startHour = 14, startMinute = 0, endHour = 16, endMinute = 0, enabled = true)
        )
        val usageTracker = UsageTracker(clock = clock, zoneId = ZoneId.of("UTC"))
        val policyEngine = PolicyEngine(repo, usageTracker, clock = clock, zoneId = ZoneId.of("UTC"))
        val scheduler = TestScheduleScheduler()
        val lockLauncher = MockLockLauncher()

        val watcher = ScheduleWatcherImpl(
            targetRepository = repo,
            policyEngine = policyEngine,
            clock = clock,
            zoneId = ZoneId.of("UTC"),
            scheduler = scheduler,
            onScheduleLocked = { p -> lockLauncher.launchLockSession(p, LockReason.SCHEDULE_DEADLINE, clock.elapsedRealtimeMillis()) }
        )

        watcher.onForegroundChanged(pkg)
        // Next boundary is 14:00 (1 hour delay)
        assertEquals(3600_000L, scheduler.scheduledDelayMillis)

        // Wall clock jumps forward to 14:15 (past schedule start)
        clock.setWallTime(ZonedDateTime.of(LocalDate.of(2026, 9, 4), LocalTime.of(14, 15), ZoneId.of("UTC")).toInstant().toEpochMilli())
        clock.advanceElapsed(15_000L)

        // Callback fires
        scheduler.trigger()

        // Confirmed LOCK due to schedule: lock session launched immediately with SCHEDULE_DEADLINE
        assertEquals(1, lockLauncher.launchHistory.size)
        assertEquals(LockReason.SCHEDULE_DEADLINE, lockLauncher.launchHistory[0].reason)
        assertEquals(pkg, lockLauncher.launchHistory[0].packageName)
    }

    // =========================================================================
    // B. schedule boundary after wall-clock backward jump
    // =========================================================================
    @Test
    fun testB_scheduleBoundaryAfterWallClockBackwardJump() {
        val clock = TestClock.at(2026, 9, 4, 13, 50, zoneId = ZoneId.of("UTC"))
        val repo = FakeTargetRepository()
        val pkg = "com.test.backward"
        // Schedule: 14:00 -> 16:00
        repo.apps[pkg] = LockedApp(
            packageName = pkg,
            enabled = true,
            schedule = TimeSchedule(startHour = 14, startMinute = 0, endHour = 16, endMinute = 0, enabled = true)
        )
        val usageTracker = UsageTracker(clock = clock, zoneId = ZoneId.of("UTC"))
        val policyEngine = PolicyEngine(repo, usageTracker, clock = clock, zoneId = ZoneId.of("UTC"))
        val scheduler = TestScheduleScheduler()
        val lockLauncher = MockLockLauncher()

        val watcher = ScheduleWatcherImpl(
            targetRepository = repo,
            policyEngine = policyEngine,
            clock = clock,
            zoneId = ZoneId.of("UTC"),
            scheduler = scheduler,
            onScheduleLocked = { p -> lockLauncher.launchLockSession(p, LockReason.SCHEDULE_DEADLINE, clock.elapsedRealtimeMillis()) }
        )

        watcher.onForegroundChanged(pkg)
        // 10 minutes delay to 14:00
        assertEquals(600_000L, scheduler.scheduledDelayMillis)

        // Wall clock moved backward to 13:10!
        clock.setWallTime(ZonedDateTime.of(LocalDate.of(2026, 9, 4), LocalTime.of(13, 10), ZoneId.of("UTC")).toInstant().toEpochMilli())

        // Callback fires
        scheduler.trigger()

        // Must NOT lock because schedule is not active!
        assertEquals(0, lockLauncher.launchHistory.size)
        // Must reschedule against newly calculated boundary (14:00 is 50 minutes away = 3,000,000ms)
        assertEquals(3000_000L, scheduler.scheduledDelayMillis)
    }

    // =========================================================================
    // C. early callback re-evaluation
    // =========================================================================
    @Test
    fun testC_earlyCallbackReEvaluation() {
        val clock = TestClock.at(2026, 9, 4, 13, 50, zoneId = ZoneId.of("UTC"))
        val repo = FakeTargetRepository()
        val pkg = "com.test.early"
        // Schedule: 14:00 -> 16:00
        repo.apps[pkg] = LockedApp(
            packageName = pkg,
            enabled = true,
            schedule = TimeSchedule(startHour = 14, startMinute = 0, endHour = 16, endMinute = 0, enabled = true)
        )
        val usageTracker = UsageTracker(clock = clock, zoneId = ZoneId.of("UTC"))
        val policyEngine = PolicyEngine(repo, usageTracker, clock = clock, zoneId = ZoneId.of("UTC"))
        val scheduler = TestScheduleScheduler()
        val lockLauncher = MockLockLauncher()

        val watcher = ScheduleWatcherImpl(
            targetRepository = repo,
            policyEngine = policyEngine,
            clock = clock,
            zoneId = ZoneId.of("UTC"),
            scheduler = scheduler,
            onScheduleLocked = { p -> lockLauncher.launchLockSession(p, LockReason.SCHEDULE_DEADLINE, clock.elapsedRealtimeMillis()) }
        )

        watcher.onForegroundChanged(pkg)
        assertEquals(600_000L, scheduler.scheduledDelayMillis)

        // OS timer jitter: callback fires 2 minutes EARLY at 13:58
        clock.setWallTime(ZonedDateTime.of(LocalDate.of(2026, 9, 4), LocalTime.of(13, 58), ZoneId.of("UTC")).toInstant().toEpochMilli())
        scheduler.trigger()

        // Did not lock early!
        assertEquals(0, lockLauncher.launchHistory.size)
        // Rescheduled for remaining 2 minutes (120,000ms)
        assertEquals(120_000L, scheduler.scheduledDelayMillis)

        // Advance to 14:00 and trigger rescheduled callback
        clock.setWallTime(ZonedDateTime.of(LocalDate.of(2026, 9, 4), LocalTime.of(14, 0), ZoneId.of("UTC")).toInstant().toEpochMilli())
        scheduler.trigger()

        // Now locks on time!
        assertEquals(1, lockLauncher.launchHistory.size)
        assertEquals(LockReason.SCHEDULE_DEADLINE, lockLauncher.launchHistory[0].reason)
    }

    // =========================================================================
    // D. late callback re-evaluation
    // =========================================================================
    @Test
    fun testD_lateCallbackReEvaluation_withinWindow() {
        val clock = TestClock.at(2026, 9, 4, 13, 50, zoneId = ZoneId.of("UTC"))
        val repo = FakeTargetRepository()
        val pkg = "com.test.late.in"
        // Schedule: 14:00 -> 16:00
        repo.apps[pkg] = LockedApp(
            packageName = pkg,
            enabled = true,
            schedule = TimeSchedule(startHour = 14, startMinute = 0, endHour = 16, endMinute = 0, enabled = true)
        )
        val usageTracker = UsageTracker(clock = clock, zoneId = ZoneId.of("UTC"))
        val policyEngine = PolicyEngine(repo, usageTracker, clock = clock, zoneId = ZoneId.of("UTC"))
        val scheduler = TestScheduleScheduler()
        val lockLauncher = MockLockLauncher()

        val watcher = ScheduleWatcherImpl(
            targetRepository = repo,
            policyEngine = policyEngine,
            clock = clock,
            zoneId = ZoneId.of("UTC"),
            scheduler = scheduler,
            onScheduleLocked = { p -> lockLauncher.launchLockSession(p, LockReason.SCHEDULE_DEADLINE, clock.elapsedRealtimeMillis()) }
        )

        watcher.onForegroundChanged(pkg)

        // Device slept: callback fires late at 14:05 (within 14:00 - 16:00)
        clock.setWallTime(ZonedDateTime.of(LocalDate.of(2026, 9, 4), LocalTime.of(14, 5), ZoneId.of("UTC")).toInstant().toEpochMilli())
        scheduler.trigger()

        // Handle immediately based on current policy -> LOCK
        assertEquals(1, lockLauncher.launchHistory.size)
        assertEquals(LockReason.SCHEDULE_DEADLINE, lockLauncher.launchHistory[0].reason)
    }

    @Test
    fun testD_lateCallbackReEvaluation_pastWindow() {
        val clock = TestClock.at(2026, 9, 4, 13, 50, zoneId = ZoneId.of("UTC"))
        val repo = FakeTargetRepository()
        val pkg = "com.test.late.out"
        // Schedule: 14:00 -> 16:00
        repo.apps[pkg] = LockedApp(
            packageName = pkg,
            enabled = true,
            schedule = TimeSchedule(startHour = 14, startMinute = 0, endHour = 16, endMinute = 0, enabled = true)
        )
        val usageTracker = UsageTracker(clock = clock, zoneId = ZoneId.of("UTC"))
        val policyEngine = PolicyEngine(repo, usageTracker, clock = clock, zoneId = ZoneId.of("UTC"))
        val scheduler = TestScheduleScheduler()
        val lockLauncher = MockLockLauncher()

        val watcher = ScheduleWatcherImpl(
            targetRepository = repo,
            policyEngine = policyEngine,
            clock = clock,
            zoneId = ZoneId.of("UTC"),
            scheduler = scheduler,
            onScheduleLocked = { p -> lockLauncher.launchLockSession(p, LockReason.SCHEDULE_DEADLINE, clock.elapsedRealtimeMillis()) }
        )

        watcher.onForegroundChanged(pkg)

        // Large wall clock jump / late firing to 16:30 (past schedule end)
        clock.setWallTime(ZonedDateTime.of(LocalDate.of(2026, 9, 4), LocalTime.of(16, 30), ZoneId.of("UTC")).toInstant().toEpochMilli())
        scheduler.trigger()

        // Window ended: do NOT emit schedule lock!
        assertEquals(0, lockLauncher.launchHistory.size)
        // Must calculate and reschedule for tomorrow 14:00 (21.5 hours away = 77,400,000ms)
        assertEquals(77400_000L, scheduler.scheduledDelayMillis)
    }

    // =========================================================================
    // E. timezone-local schedule interpretation
    // =========================================================================
    @Test
    fun testE_timezoneLocalScheduleInterpretation() {
        // Schedule: 09:00 -> 17:00
        val schedule = TimeSchedule(startHour = 9, startMinute = 0, endHour = 17, endMinute = 0, enabled = true)

        // 09:30 UTC
        val nowEpoch = ZonedDateTime.of(LocalDate.of(2026, 9, 4), LocalTime.of(9, 30), ZoneId.of("UTC")).toInstant().toEpochMilli()

        // In UTC (09:30): Active
        assertTrue(ScheduleEvaluator.isWithinSchedule(schedule, nowEpoch, ZoneId.of("UTC")))

        // In Asia/Bangkok (UTC+7, 16:30): Active
        assertTrue(ScheduleEvaluator.isWithinSchedule(schedule, nowEpoch, ZoneId.of("Asia/Bangkok")))

        // In America/New_York (UTC-4 in Sep, 05:30): Inactive
        assertFalse(ScheduleEvaluator.isWithinSchedule(schedule, nowEpoch, ZoneId.of("America/New_York")))

        // In Asia/Tokyo (UTC+9, 18:30): Inactive
        assertFalse(ScheduleEvaluator.isWithinSchedule(schedule, nowEpoch, ZoneId.of("Asia/Tokyo")))
    }

    @Test
    fun testE_timezoneChangeBetweenSchedulingAndCallback() {
        var currentZone = ZoneId.of("Asia/Bangkok") // UTC+7
        val clock = TestClock.at(2026, 9, 4, 8, 30, zoneId = currentZone)
        val repo = FakeTargetRepository()
        val pkg = "com.test.tz.change"
        // Schedule: 09:00 -> 17:00
        repo.apps[pkg] = LockedApp(
            packageName = pkg,
            enabled = true,
            schedule = TimeSchedule(startHour = 9, startMinute = 0, endHour = 17, endMinute = 0, enabled = true)
        )
        val usageTracker = UsageTracker(clock = clock, zoneIdProvider = { currentZone })
        val policyEngine = PolicyEngine(repo, usageTracker, clock = clock, zoneIdProvider = { currentZone })
        val scheduler = TestScheduleScheduler()
        val lockLauncher = MockLockLauncher()

        val watcher = ScheduleWatcherImpl(
            targetRepository = repo,
            policyEngine = policyEngine,
            clock = clock,
            zoneIdProvider = { currentZone },
            scheduler = scheduler,
            onScheduleLocked = { p -> lockLauncher.launchLockSession(p, LockReason.SCHEDULE_DEADLINE, clock.elapsedRealtimeMillis()) }
        )

        watcher.onForegroundChanged(pkg)
        // In Bangkok: 08:30 -> 09:00 = 30 minutes (1,800,000ms)
        assertEquals(1800_000L, scheduler.scheduledDelayMillis)

        // Timezone changes to UTC (Europe/London in winter or UTC)
        // 08:30 Bangkok was 01:30 UTC. 30 minutes later = 02:00 UTC.
        currentZone = ZoneId.of("UTC")
        clock.advanceBoth(1800_000L) // wall time becomes 02:00 UTC

        // Callback fires
        scheduler.trigger()

        // In UTC, 02:00 is NOT within 09:00 -> 17:00. Must NOT lock!
        assertEquals(0, lockLauncher.launchHistory.size)
        // Must reschedule for 09:00 UTC (7 hours away = 25,200,000ms)
        assertEquals(25200_000L, scheduler.scheduledDelayMillis)
    }

    @Test
    fun testE_dstTransitionPreservesScheduleInterpretation() {
        val nyZone = ZoneId.of("America/New_York")
        // DST switch in 2026: March 8, 2026 (clocks forward 02:00 -> 03:00)
        val schedule = TimeSchedule(startHour = 9, startMinute = 0, endHour = 17, endMinute = 0, enabled = true)

        val beforeDst = ZonedDateTime.of(LocalDate.of(2026, 3, 7), LocalTime.of(10, 0), nyZone).toInstant().toEpochMilli()
        val afterDst = ZonedDateTime.of(LocalDate.of(2026, 3, 9), LocalTime.of(10, 0), nyZone).toInstant().toEpochMilli()

        assertTrue(ScheduleEvaluator.isWithinSchedule(schedule, beforeDst, nyZone))
        assertTrue(ScheduleEvaluator.isWithinSchedule(schedule, afterDst, nyZone))

        val boundaryBefore = ScheduleEvaluator.getNextBoundaryMillis(schedule, beforeDst, nyZone)
        val boundaryAfter = ScheduleEvaluator.getNextBoundaryMillis(schedule, afterDst, nyZone)
        assertNotNull(boundaryBefore)
        assertNotNull(boundaryAfter)
    }

    // =========================================================================
    // F. cross-midnight schedule
    // =========================================================================
    @Test
    fun testF_crossMidnightSchedule() {
        val schedule = TimeSchedule(startHour = 22, startMinute = 0, endHour = 7, endMinute = 0, enabled = true)
        val zone = ZoneId.of("UTC")

        // 21:50 -> Inactive (next is 22:00 today)
        val t2150 = ZonedDateTime.of(LocalDate.of(2026, 9, 4), LocalTime.of(21, 50), zone).toInstant().toEpochMilli()
        assertFalse(ScheduleEvaluator.isWithinSchedule(schedule, t2150, zone))
        val b2150 = ScheduleEvaluator.getNextBoundaryMillis(schedule, t2150, zone)
        val expectedB2150 = ZonedDateTime.of(LocalDate.of(2026, 9, 4), LocalTime.of(22, 0), zone).toInstant().toEpochMilli()
        assertEquals(expectedB2150, b2150)

        // 22:10 -> Active (next is 07:00 tomorrow)
        val t2210 = ZonedDateTime.of(LocalDate.of(2026, 9, 4), LocalTime.of(22, 10), zone).toInstant().toEpochMilli()
        assertTrue(ScheduleEvaluator.isWithinSchedule(schedule, t2210, zone))
        val b2210 = ScheduleEvaluator.getNextBoundaryMillis(schedule, t2210, zone)
        val expectedB2210 = ZonedDateTime.of(LocalDate.of(2026, 9, 5), LocalTime.of(7, 0), zone).toInstant().toEpochMilli()
        assertEquals(expectedB2210, b2210)

        // 02:00 -> Active (next is 07:00 today)
        val t0200 = ZonedDateTime.of(LocalDate.of(2026, 9, 5), LocalTime.of(2, 0), zone).toInstant().toEpochMilli()
        assertTrue(ScheduleEvaluator.isWithinSchedule(schedule, t0200, zone))
        val b0200 = ScheduleEvaluator.getNextBoundaryMillis(schedule, t0200, zone)
        val expectedB0200 = ZonedDateTime.of(LocalDate.of(2026, 9, 5), LocalTime.of(7, 0), zone).toInstant().toEpochMilli()
        assertEquals(expectedB0200, b0200)

        // 07:05 -> Inactive (next is 22:00 today)
        val t0705 = ZonedDateTime.of(LocalDate.of(2026, 9, 5), LocalTime.of(7, 5), zone).toInstant().toEpochMilli()
        assertFalse(ScheduleEvaluator.isWithinSchedule(schedule, t0705, zone))
        val b0705 = ScheduleEvaluator.getNextBoundaryMillis(schedule, t0705, zone)
        val expectedB0705 = ZonedDateTime.of(LocalDate.of(2026, 9, 5), LocalTime.of(22, 0), zone).toInstant().toEpochMilli()
        assertEquals(expectedB0705, b0705)
    }

    // =========================================================================
    // G. midnight usage split
    // =========================================================================
    @Test
    fun testG_dailyCycle0400UsageSplit() {
        val zone = ZoneId.of("UTC")
        val clock = TestClock.at(2026, 9, 5, 3, 55, zoneId = zone)
        clock.setElapsedRealtime(100_000L)
        val usageTracker = UsageTracker(clock = clock, zoneId = zone)
        val pkg = "com.test.rollover.usage"

        // Session starts at 03:55 (5 minutes before 04:00 boundary)
        usageTracker.startSession(pkg)

        // Advance 10 minutes (crosses 04:00 boundary to 04:05 on 2026-09-05)
        clock.advanceBoth(600_000L) // 10 minutes = 600,000ms

        // Query today's usage while active:
        // Current cycle is 2026-09-05. Elapsed duration is 10 min, portion after 04:00 is 5 min (300,000ms)
        val activeTodayUsage = usageTracker.getTodayUsage(pkg)
        assertEquals(300_000L, activeTodayUsage)

        // Stop session at 04:05
        usageTracker.stopSession(pkg)

        // Query today's usage (2026-09-05 cycle) after stop: exactly 5 minutes (300,000ms)
        assertEquals(300_000L, usageTracker.getTodayUsage(pkg))

        // Check previous cycle's usage (2026-09-04 cycle) by stepping clock back to before 04:00 (e.g. 03:50 on 2026-09-05)
        clock.setWallTime(ZonedDateTime.of(LocalDate.of(2026, 9, 5), LocalTime.of(3, 50), zone).toInstant().toEpochMilli())
        assertEquals(300_000L, usageTracker.getTodayUsage(pkg))

        // Total usage between two cycles equals total monotonic elapsed duration (600,000ms)
        val day1Key = UsageTracker.makeKey(LocalDate.of(2026, 9, 4), pkg)
        val day2Key = UsageTracker.makeKey(LocalDate.of(2026, 9, 5), pkg)
        // Verify JSON serialization contains exact split
        val json = usageTracker.serializeToJson()
        assertTrue(json.contains(day1Key))
        assertTrue(json.contains(day2Key))
    }

    // =========================================================================
    // H. service restart near midnight
    // =========================================================================
    @Test
    fun testH_serviceRestartNearMidnight() {
        val zone = ZoneId.of("UTC")
        val clock = TestClock.at(2026, 9, 4, 23, 59, 50, zoneId = zone)
        val repo = FakeTargetRepository()
        val pkg = "com.test.restart.midnight"
        // 10 minutes daily limit
        repo.apps[pkg] = LockedApp(
            packageName = pkg,
            enabled = true,
            timeLimit = TimeLimit(dailyLimitMinutes = 10, enabled = true)
        )
        val usageTracker = UsageTracker(clock = clock, zoneId = zone)
        val policyEngine = PolicyEngine(repo, usageTracker, clock = clock, zoneId = zone)
        val limitScheduler = TestLimitScheduler()
        val lockLauncher = MockLockLauncher()

        val watcher = UsageLimitWatcherImpl(
            targetRepository = repo,
            usageTracker = usageTracker,
            policyEngine = policyEngine,
            scheduler = limitScheduler,
            onLimitReached = { p -> lockLauncher.launchLockSession(p, LockReason.DAILY_LIMIT, clock.elapsedRealtimeMillis()) }
        )

        // Service starts near midnight
        watcher.start()
        watcher.onForegroundChanged(pkg)

        // Cross midnight by 15 seconds (to 00:00:05 next day)
        clock.advanceBoth(15_000L)

        // Trigger limit deadline if any was fired
        limitScheduler.trigger()

        // Today is a fresh day: usage is 0, must NOT lock!
        assertEquals(0, lockLauncher.launchHistory.size)
    }

    // =========================================================================
    // I. service restart after wall-clock change
    // =========================================================================
    @Test
    fun testI_serviceRestartAfterWallClockChange() {
        val zone = ZoneId.of("UTC")
        val clock = TestClock.at(2026, 9, 4, 10, 0, zoneId = zone)
        val repo = FakeTargetRepository()
        val pkg = "com.test.restart.clockjump"
        // Schedule: 14:00 -> 16:00
        repo.apps[pkg] = LockedApp(
            packageName = pkg,
            enabled = true,
            schedule = TimeSchedule(startHour = 14, startMinute = 0, endHour = 16, endMinute = 0, enabled = true)
        )
        val usageTracker = UsageTracker(clock = clock, zoneId = zone)
        val policyEngine = PolicyEngine(repo, usageTracker, clock = clock, zoneId = zone)
        val scheduleScheduler = TestScheduleScheduler()
        val lockLauncher = MockLockLauncher()

        val watcher = ScheduleWatcherImpl(
            targetRepository = repo,
            policyEngine = policyEngine,
            clock = clock,
            zoneId = zone,
            scheduler = scheduleScheduler,
            onScheduleLocked = { p -> lockLauncher.launchLockSession(p, LockReason.SCHEDULE_DEADLINE, clock.elapsedRealtimeMillis()) }
        )

        // Service stopped, wall clock jumped from 10:00 to 14:30
        clock.setWallTime(ZonedDateTime.of(LocalDate.of(2026, 9, 4), LocalTime.of(14, 30), zone).toInstant().toEpochMilli())

        // Service restarts
        watcher.start()

        // Foreground event arrives
        watcher.onForegroundChanged(pkg)

        // Evaluates against fresh wall clock (14:30 is within 14:00-16:00) -> immediate lock!
        assertEquals(1, lockLauncher.launchHistory.size)
        assertEquals(LockReason.SCHEDULE_DEADLINE, lockLauncher.launchHistory[0].reason)
    }

    // =========================================================================
    // J. screen OFF across midnight
    // =========================================================================
    @Test
    fun testJ_screenOffAcrossMidnight() {
        val zone = ZoneId.of("UTC")
        val clock = TestClock.at(2026, 9, 4, 23, 50, zoneId = zone)
        val usageTracker = UsageTracker(clock = clock, zoneId = zone)
        val pkg = "com.test.screenoff"

        // Target used for 5 minutes before screen turns off
        usageTracker.startSession(pkg)
        clock.advanceBoth(300_000L) // 23:55
        usageTracker.onScreenOff()

        // Screen remains OFF across midnight until 08:00 next day (8 hours 5 minutes = 29,100,000ms)
        clock.advanceBoth(29100_000L)

        // No usage should be accumulated while screen is OFF!
        assertEquals(0L, usageTracker.getTodayUsage(pkg))

        // Yesterday's usage should be exactly 5 minutes (300,000ms)
        clock.setWallTime(ZonedDateTime.of(LocalDate.of(2026, 9, 4), LocalTime.of(23, 59), zone).toInstant().toEpochMilli())
        assertEquals(300_000L, usageTracker.getTodayUsage(pkg))
    }

    // =========================================================================
    // K. screen ON waits for fresh foreground event
    // =========================================================================
    @Test
    fun testK_screenOnWaitsForFreshForegroundEvent() {
        val zone = ZoneId.of("UTC")
        val clock = TestClock.at(2026, 9, 4, 23, 50, zoneId = zone)
        val usageTracker = UsageTracker(clock = clock, zoneId = zone)
        val pkg = "com.test.screenon"

        usageTracker.startSession(pkg)
        usageTracker.onScreenOff()
        assertFalse(usageTracker.isSessionActive(pkg))

        // Screen turned on
        usageTracker.onScreenOn()

        // MUST NOT blindly restart session for previous package!
        assertFalse(usageTracker.isSessionActive(pkg))
        assertNull(usageTracker.getActivePackage())

        // Only starts after explicit foreground event
        usageTracker.startSession(pkg)
        assertTrue(usageTracker.isSessionActive(pkg))
    }

    // =========================================================================
    // L. stale callback after restart
    // =========================================================================
    @Test
    fun testL_staleCallbackAfterRestart() {
        val clock = TestClock.at(2026, 9, 4, 13, 50, zoneId = ZoneId.of("UTC"))
        val repo = FakeTargetRepository()
        val pkg = "com.test.stale"
        repo.apps[pkg] = LockedApp(
            packageName = pkg,
            enabled = true,
            schedule = TimeSchedule(startHour = 14, startMinute = 0, endHour = 16, endMinute = 0, enabled = true)
        )
        val usageTracker = UsageTracker(clock = clock, zoneId = ZoneId.of("UTC"))
        val policyEngine = PolicyEngine(repo, usageTracker, clock = clock, zoneId = ZoneId.of("UTC"))
        val scheduler = TestScheduleScheduler()
        val lockLauncher = MockLockLauncher()

        val watcher = ScheduleWatcherImpl(
            targetRepository = repo,
            policyEngine = policyEngine,
            clock = clock,
            zoneId = ZoneId.of("UTC"),
            scheduler = scheduler,
            onScheduleLocked = { p -> lockLauncher.launchLockSession(p, LockReason.SCHEDULE_DEADLINE, clock.elapsedRealtimeMillis()) }
        )

        watcher.onForegroundChanged(pkg)
        val capturedRunnable = scheduler.scheduledRunnable
        assertNotNull(capturedRunnable)

        // Service restarts (generation incremented)
        watcher.start()

        // Advance to 14:00
        clock.setWallTime(ZonedDateTime.of(LocalDate.of(2026, 9, 4), LocalTime.of(14, 0), ZoneId.of("UTC")).toInstant().toEpochMilli())

        // Stale runnable from previous generation fires
        capturedRunnable?.run()

        // Stale guard MUST ignore callback from previous generation
        assertEquals(0, lockLauncher.launchHistory.size)
    }

    // =========================================================================
    // M. schedule + limit collision after clock jump
    // =========================================================================
    @Test
    fun testM_scheduleAndLimitCollisionAfterClockJump() {
        val clock = TestClock.at(2026, 9, 4, 13, 55, zoneId = ZoneId.of("UTC"))
        val repo = FakeTargetRepository()
        val pkg = "com.test.collision.jump"
        // Schedule: 14:00 -> 16:00
        // Daily Limit: 10 minutes
        repo.apps[pkg] = LockedApp(
            packageName = pkg,
            enabled = true,
            schedule = TimeSchedule(startHour = 14, startMinute = 0, endHour = 16, endMinute = 0, enabled = true),
            timeLimit = TimeLimit(dailyLimitMinutes = 10, enabled = true)
        )
        val usageTracker = UsageTracker(clock = clock, zoneId = ZoneId.of("UTC"))
        val policyEngine = PolicyEngine(repo, usageTracker, clock = clock, zoneId = ZoneId.of("UTC"))
        val scheduleScheduler = TestScheduleScheduler()
        val limitScheduler = TestLimitScheduler()
        val lockLauncher = MockLockLauncher()

        val scheduleWatcher = ScheduleWatcherImpl(
            targetRepository = repo,
            policyEngine = policyEngine,
            clock = clock,
            zoneId = ZoneId.of("UTC"),
            scheduler = scheduleScheduler,
            onScheduleLocked = { p -> lockLauncher.launchLockSession(p, LockReason.SCHEDULE_DEADLINE, clock.elapsedRealtimeMillis()) }
        )

        val limitWatcher = UsageLimitWatcherImpl(
            targetRepository = repo,
            usageTracker = usageTracker,
            policyEngine = policyEngine,
            scheduler = limitScheduler,
            onLimitReached = { p -> lockLauncher.launchLockSession(p, LockReason.DAILY_LIMIT, clock.elapsedRealtimeMillis()) }
        )

        // Pre-consume 9 minutes (1 minute remaining)
        usageTracker.startSession(pkg)
        clock.advanceBoth(540_000L) // 9 minutes
        usageTracker.stopSession(pkg)

        scheduleWatcher.onForegroundChanged(pkg)
        limitWatcher.onForegroundChanged(pkg)

        // Wall clock jumps to 14:05 AND 1 minute elapsed passes
        clock.setWallTime(ZonedDateTime.of(LocalDate.of(2026, 9, 4), LocalTime.of(14, 5), ZoneId.of("UTC")).toInstant().toEpochMilli())
        clock.advanceElapsed(60_000L)
        // Record 1 minute so limit is reached
        usageTracker.startSession(pkg)
        clock.advanceElapsed(60_000L)
        usageTracker.stopSession(pkg)

        // First trigger fires (Schedule)
        scheduleScheduler.trigger()
        assertEquals(1, lockLauncher.launchHistory.size)
        assertEquals(LockReason.SCHEDULE_DEADLINE, lockLauncher.launchHistory[0].reason)

        // Second trigger fires (Limit)
        limitScheduler.trigger()

        // Rule B intra-session duplicate guard prevents duplicate launch
        assertEquals(1, lockLauncher.launchHistory.size)
        // First reason is preserved
        assertEquals(LockReason.SCHEDULE_DEADLINE, lockLauncher.launchHistory[0].reason)
    }

    // =========================================================================
    // N. target switch after clock jump
    // =========================================================================
    @Test
    fun testN_targetSwitchAfterClockJump() {
        val clock = TestClock.at(2026, 9, 4, 13, 50, zoneId = ZoneId.of("UTC"))
        val repo = FakeTargetRepository()
        val pkgA = "com.test.pkg.a"
        val pkgB = "com.test.pkg.b"
        repo.apps[pkgA] = LockedApp(
            packageName = pkgA,
            enabled = true,
            schedule = TimeSchedule(startHour = 14, startMinute = 0, endHour = 16, endMinute = 0, enabled = true)
        )
        val usageTracker = UsageTracker(clock = clock, zoneId = ZoneId.of("UTC"))
        val policyEngine = PolicyEngine(repo, usageTracker, clock = clock, zoneId = ZoneId.of("UTC"))
        val scheduler = TestScheduleScheduler()
        val lockLauncher = MockLockLauncher()

        val watcher = ScheduleWatcherImpl(
            targetRepository = repo,
            policyEngine = policyEngine,
            clock = clock,
            zoneId = ZoneId.of("UTC"),
            scheduler = scheduler,
            onScheduleLocked = { p -> lockLauncher.launchLockSession(p, LockReason.SCHEDULE_DEADLINE, clock.elapsedRealtimeMillis()) }
        )

        watcher.onForegroundChanged(pkgA)
        val capturedRunnableA = scheduler.scheduledRunnable

        // User switches to pkgB and clock jumps to 14:05
        clock.setWallTime(ZonedDateTime.of(LocalDate.of(2026, 9, 4), LocalTime.of(14, 5), ZoneId.of("UTC")).toInstant().toEpochMilli())
        watcher.onForegroundChanged(pkgB)

        // Old runnable for pkgA executes
        capturedRunnableA?.run()

        // Must NOT lock because activePackage is pkgB, not pkgA
        assertEquals(0, lockLauncher.launchHistory.size)
    }

    // =========================================================================
    // O. policy update after clock jump
    // =========================================================================
    @Test
    fun testO_policyUpdateAfterClockJump() {
        val clock = TestClock.at(2026, 9, 4, 13, 50, zoneId = ZoneId.of("UTC"))
        val repo = FakeTargetRepository()
        val pkg = "com.test.policy.update"
        repo.apps[pkg] = LockedApp(
            packageName = pkg,
            enabled = true,
            schedule = TimeSchedule(startHour = 14, startMinute = 0, endHour = 16, endMinute = 0, enabled = true)
        )
        val usageTracker = UsageTracker(clock = clock, zoneId = ZoneId.of("UTC"))
        val policyEngine = PolicyEngine(repo, usageTracker, clock = clock, zoneId = ZoneId.of("UTC"))
        val scheduler = TestScheduleScheduler()
        val lockLauncher = MockLockLauncher()

        val watcher = ScheduleWatcherImpl(
            targetRepository = repo,
            policyEngine = policyEngine,
            clock = clock,
            zoneId = ZoneId.of("UTC"),
            scheduler = scheduler,
            onScheduleLocked = { p -> lockLauncher.launchLockSession(p, LockReason.SCHEDULE_DEADLINE, clock.elapsedRealtimeMillis()) }
        )

        watcher.onForegroundChanged(pkg)

        // Wall clock jumps to 14:05
        clock.setWallTime(ZonedDateTime.of(LocalDate.of(2026, 9, 4), LocalTime.of(14, 5), ZoneId.of("UTC")).toInstant().toEpochMilli())

        // User disables schedule in policy
        repo.apps[pkg] = repo.apps[pkg]!!.copy(
            schedule = repo.apps[pkg]!!.schedule?.copy(enabled = false)
        )
        watcher.onPolicyUpdated(pkg)

        // Schedule is disabled: scheduler has no pending callbacks and no lock is emitted
        assertNull(scheduler.scheduledRunnable)
        assertEquals(0, lockLauncher.launchHistory.size)
    }
}
