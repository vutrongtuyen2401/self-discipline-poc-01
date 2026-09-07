package com.example.selfdisciplinepoc01.policy

import com.example.selfdisciplinepoc01.target.model.LockedApp
import com.example.selfdisciplinepoc01.target.model.TimeLimit
import com.example.selfdisciplinepoc01.target.model.TimeSchedule
import com.example.selfdisciplinepoc01.target.repository.TargetRepository
import com.example.selfdisciplinepoc01.time.TestClock
import com.example.selfdisciplinepoc01.usage.UsageTracker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class ScheduleWatcherTest {

    private val testZone = ZoneId.of("UTC")

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

    // 1. Normal schedule: before start (08:30 for 09:00..17:00) -> scheduled for remaining 30 min
    @Test
    fun test01_normalSchedule_beforeStart() {
        val repo = FakeTargetRepository()
        repo.apps["com.test.app"] = LockedApp(
            "com.test.app",
            enabled = true,
            schedule = TimeSchedule(enabled = true, startHour = 9, startMinute = 0, endHour = 17, endMinute = 0)
        )
        val clock = TestClock.at(2026, 9, 3, 8, 30, zoneId = testZone)
        val usageTracker = UsageTracker(clock = clock, zoneId = testZone)
        val policyEngine = PolicyEngine(repo, usageTracker, clock, testZone)
        val scheduler = TestScheduleScheduler()

        var lockedPkg: String? = null
        val watcher = ScheduleWatcherImpl(repo, policyEngine, clock, testZone, scheduler) { lockedPkg = it }

        watcher.onForegroundChanged("com.test.app")

        assertNull("Should not lock immediately when before start", lockedPkg)
        assertNotNull(scheduler.scheduledRunnable)
        assertEquals(30 * 60_000L, scheduler.scheduledDelayMillis)
    }

    // 2. Normal schedule: at exact start (09:00:00 for 09:00..17:00) -> immediate lock
    @Test
    fun test02_normalSchedule_atStart() {
        val repo = FakeTargetRepository()
        repo.apps["com.test.app"] = LockedApp(
            "com.test.app",
            enabled = true,
            schedule = TimeSchedule(enabled = true, startHour = 9, startMinute = 0, endHour = 17, endMinute = 0)
        )
        val clock = TestClock.at(2026, 9, 3, 9, 0, zoneId = testZone)
        val usageTracker = UsageTracker(clock = clock, zoneId = testZone)
        val policyEngine = PolicyEngine(repo, usageTracker, clock, testZone)
        val scheduler = TestScheduleScheduler()

        var lockedPkg: String? = null
        val watcher = ScheduleWatcherImpl(repo, policyEngine, clock, testZone, scheduler) { lockedPkg = it }

        watcher.onForegroundChanged("com.test.app")

        assertEquals("com.test.app", lockedPkg)
        assertNull("Should not schedule future runnable when locking immediately", scheduler.scheduledRunnable)
    }

    // 3. Normal schedule: inside interval (12:00 for 09:00..17:00) -> immediate lock
    @Test
    fun test03_normalSchedule_insideInterval() {
        val repo = FakeTargetRepository()
        repo.apps["com.test.app"] = LockedApp(
            "com.test.app",
            enabled = true,
            schedule = TimeSchedule(enabled = true, startHour = 9, startMinute = 0, endHour = 17, endMinute = 0)
        )
        val clock = TestClock.at(2026, 9, 3, 12, 0, zoneId = testZone)
        val usageTracker = UsageTracker(clock = clock, zoneId = testZone)
        val policyEngine = PolicyEngine(repo, usageTracker, clock, testZone)
        val scheduler = TestScheduleScheduler()

        var lockedPkg: String? = null
        val watcher = ScheduleWatcherImpl(repo, policyEngine, clock, testZone, scheduler) { lockedPkg = it }

        watcher.onForegroundChanged("com.test.app")

        assertEquals("com.test.app", lockedPkg)
    }

    // 4. Normal schedule: exact end (17:00:00 for 09:00..17:00) -> ALLOW, scheduled for tomorrow 09:00
    @Test
    fun test04_normalSchedule_atEnd() {
        val repo = FakeTargetRepository()
        repo.apps["com.test.app"] = LockedApp(
            "com.test.app",
            enabled = true,
            schedule = TimeSchedule(enabled = true, startHour = 9, startMinute = 0, endHour = 17, endMinute = 0)
        )
        val clock = TestClock.at(2026, 9, 3, 17, 0, zoneId = testZone)
        val usageTracker = UsageTracker(clock = clock, zoneId = testZone)
        val policyEngine = PolicyEngine(repo, usageTracker, clock, testZone)
        val scheduler = TestScheduleScheduler()

        var lockedPkg: String? = null
        val watcher = ScheduleWatcherImpl(repo, policyEngine, clock, testZone, scheduler) { lockedPkg = it }

        watcher.onForegroundChanged("com.test.app")

        assertNull(lockedPkg)
        assertNotNull(scheduler.scheduledRunnable)
        // From 17:00 today to 09:00 tomorrow = 16 hours
        assertEquals(16 * 3600_000L, scheduler.scheduledDelayMillis)
    }

    // 5. Normal schedule: after end (18:00 for 09:00..17:00) -> ALLOW, scheduled for tomorrow 09:00
    @Test
    fun test05_normalSchedule_afterEnd() {
        val repo = FakeTargetRepository()
        repo.apps["com.test.app"] = LockedApp(
            "com.test.app",
            enabled = true,
            schedule = TimeSchedule(enabled = true, startHour = 9, startMinute = 0, endHour = 17, endMinute = 0)
        )
        val clock = TestClock.at(2026, 9, 3, 18, 0, zoneId = testZone)
        val usageTracker = UsageTracker(clock = clock, zoneId = testZone)
        val policyEngine = PolicyEngine(repo, usageTracker, clock, testZone)
        val scheduler = TestScheduleScheduler()

        var lockedPkg: String? = null
        val watcher = ScheduleWatcherImpl(repo, policyEngine, clock, testZone, scheduler) { lockedPkg = it }

        watcher.onForegroundChanged("com.test.app")

        assertNull(lockedPkg)
        assertNotNull(scheduler.scheduledRunnable)
        // From 18:00 today to 09:00 tomorrow = 15 hours
        assertEquals(15 * 3600_000L, scheduler.scheduledDelayMillis)
    }

    // 6. Cross-midnight: before start (14:00 for 22:00..07:00) -> ALLOW, scheduled for today 22:00
    @Test
    fun test06_crossMidnight_beforeStart() {
        val repo = FakeTargetRepository()
        repo.apps["com.test.app"] = LockedApp(
            "com.test.app",
            enabled = true,
            schedule = TimeSchedule(enabled = true, startHour = 22, startMinute = 0, endHour = 7, endMinute = 0)
        )
        val clock = TestClock.at(2026, 9, 3, 14, 0, zoneId = testZone)
        val usageTracker = UsageTracker(clock = clock, zoneId = testZone)
        val policyEngine = PolicyEngine(repo, usageTracker, clock, testZone)
        val scheduler = TestScheduleScheduler()

        var lockedPkg: String? = null
        val watcher = ScheduleWatcherImpl(repo, policyEngine, clock, testZone, scheduler) { lockedPkg = it }

        watcher.onForegroundChanged("com.test.app")

        assertNull(lockedPkg)
        assertNotNull(scheduler.scheduledRunnable)
        // From 14:00 to 22:00 = 8 hours
        assertEquals(8 * 3600_000L, scheduler.scheduledDelayMillis)
    }

    // 7. Cross-midnight: before midnight (23:30 for 22:00..07:00) -> immediate lock
    @Test
    fun test07_crossMidnight_beforeMidnight() {
        val repo = FakeTargetRepository()
        repo.apps["com.test.app"] = LockedApp(
            "com.test.app",
            enabled = true,
            schedule = TimeSchedule(enabled = true, startHour = 22, startMinute = 0, endHour = 7, endMinute = 0)
        )
        val clock = TestClock.at(2026, 9, 3, 23, 30, zoneId = testZone)
        val usageTracker = UsageTracker(clock = clock, zoneId = testZone)
        val policyEngine = PolicyEngine(repo, usageTracker, clock, testZone)
        val scheduler = TestScheduleScheduler()

        var lockedPkg: String? = null
        val watcher = ScheduleWatcherImpl(repo, policyEngine, clock, testZone, scheduler) { lockedPkg = it }

        watcher.onForegroundChanged("com.test.app")

        assertEquals("com.test.app", lockedPkg)
    }

    // 8. Cross-midnight: after midnight (03:00 for 22:00..07:00) -> immediate lock
    @Test
    fun test08_crossMidnight_afterMidnight() {
        val repo = FakeTargetRepository()
        repo.apps["com.test.app"] = LockedApp(
            "com.test.app",
            enabled = true,
            schedule = TimeSchedule(enabled = true, startHour = 22, startMinute = 0, endHour = 7, endMinute = 0)
        )
        val clock = TestClock.at(2026, 9, 3, 3, 0, zoneId = testZone)
        val usageTracker = UsageTracker(clock = clock, zoneId = testZone)
        val policyEngine = PolicyEngine(repo, usageTracker, clock, testZone)
        val scheduler = TestScheduleScheduler()

        var lockedPkg: String? = null
        val watcher = ScheduleWatcherImpl(repo, policyEngine, clock, testZone, scheduler) { lockedPkg = it }

        watcher.onForegroundChanged("com.test.app")

        assertEquals("com.test.app", lockedPkg)
    }

    // 9. Cross-midnight: at exact end (07:00:00 for 22:00..07:00) -> ALLOW, scheduled for today 22:00
    @Test
    fun test09_crossMidnight_atEnd() {
        val repo = FakeTargetRepository()
        repo.apps["com.test.app"] = LockedApp(
            "com.test.app",
            enabled = true,
            schedule = TimeSchedule(enabled = true, startHour = 22, startMinute = 0, endHour = 7, endMinute = 0)
        )
        val clock = TestClock.at(2026, 9, 3, 7, 0, zoneId = testZone)
        val usageTracker = UsageTracker(clock = clock, zoneId = testZone)
        val policyEngine = PolicyEngine(repo, usageTracker, clock, testZone)
        val scheduler = TestScheduleScheduler()

        var lockedPkg: String? = null
        val watcher = ScheduleWatcherImpl(repo, policyEngine, clock, testZone, scheduler) { lockedPkg = it }

        watcher.onForegroundChanged("com.test.app")

        assertNull(lockedPkg)
        assertNotNull(scheduler.scheduledRunnable)
        // From 07:00 to 22:00 = 15 hours
        assertEquals(15 * 3600_000L, scheduler.scheduledDelayMillis)
    }

    // 10. All-day schedule: start == end (10:00..10:00 enabled) -> immediate lock, no periodic callbacks
    @Test
    fun test10_allDay_immediateLock_noPeriodicCallbacks() {
        val repo = FakeTargetRepository()
        repo.apps["com.test.app"] = LockedApp(
            "com.test.app",
            enabled = true,
            schedule = TimeSchedule(enabled = true, startHour = 10, startMinute = 0, endHour = 10, endMinute = 0)
        )
        val clock = TestClock.at(2026, 9, 3, 14, 0, zoneId = testZone)
        val usageTracker = UsageTracker(clock = clock, zoneId = testZone)
        val policyEngine = PolicyEngine(repo, usageTracker, clock, testZone)
        val scheduler = TestScheduleScheduler()

        var lockedPkg: String? = null
        val watcher = ScheduleWatcherImpl(repo, policyEngine, clock, testZone, scheduler) { lockedPkg = it }

        watcher.onForegroundChanged("com.test.app")

        assertEquals("com.test.app", lockedPkg)
        assertNull("No periodic callbacks for all-day schedule", scheduler.scheduledRunnable)
    }

    // 11. Deadline scheduling: fires callback at exact deadline
    @Test
    fun test11_deadlineScheduling_firesAtDeadline() {
        val repo = FakeTargetRepository()
        repo.apps["com.test.app"] = LockedApp(
            "com.test.app",
            enabled = true,
            schedule = TimeSchedule(enabled = true, startHour = 9, startMinute = 0, endHour = 17, endMinute = 0)
        )
        // 08:50:00 -> 10 minutes (600,000 ms) until 09:00:00
        val clock = TestClock.at(2026, 9, 3, 8, 50, zoneId = testZone)
        val usageTracker = UsageTracker(clock = clock, zoneId = testZone)
        val policyEngine = PolicyEngine(repo, usageTracker, clock, testZone)
        val scheduler = TestScheduleScheduler()

        var lockedPkg: String? = null
        val watcher = ScheduleWatcherImpl(repo, policyEngine, clock, testZone, scheduler) { lockedPkg = it }

        watcher.onForegroundChanged("com.test.app")
        assertEquals(600_000L, scheduler.scheduledDelayMillis)

        // Advance time to 09:00:00
        clock.advanceBoth(600_000L)
        scheduler.trigger()

        assertEquals("com.test.app", lockedPkg)
    }

    // 12. No early lock: firing early does NOT trigger lock
    @Test
    fun test12_noEarlyLock() {
        val repo = FakeTargetRepository()
        repo.apps["com.test.app"] = LockedApp(
            "com.test.app",
            enabled = true,
            schedule = TimeSchedule(enabled = true, startHour = 9, startMinute = 0, endHour = 17, endMinute = 0)
        )
        // 08:50:00 -> scheduled for 600,000 ms
        val clock = TestClock.at(2026, 9, 3, 8, 50, zoneId = testZone)
        val usageTracker = UsageTracker(clock = clock, zoneId = testZone)
        val policyEngine = PolicyEngine(repo, usageTracker, clock, testZone)
        val scheduler = TestScheduleScheduler()

        var lockedPkg: String? = null
        val watcher = ScheduleWatcherImpl(repo, policyEngine, clock, testZone, scheduler) { lockedPkg = it }

        watcher.onForegroundChanged("com.test.app")

        // Advance time by only 5 minutes (08:55:00)
        clock.advanceBoth(300_000L)
        scheduler.trigger()

        assertNull("Must not lock early before start boundary", lockedPkg)
    }

    // 13. Early callback => re-evaluates and reschedules remaining delay
    @Test
    fun test13_earlyCallback_reschedulesRemainingDelay() {
        val repo = FakeTargetRepository()
        repo.apps["com.test.app"] = LockedApp(
            "com.test.app",
            enabled = true,
            schedule = TimeSchedule(enabled = true, startHour = 9, startMinute = 0, endHour = 17, endMinute = 0)
        )
        val clock = TestClock.at(2026, 9, 3, 8, 50, zoneId = testZone)
        val usageTracker = UsageTracker(clock = clock, zoneId = testZone)
        val policyEngine = PolicyEngine(repo, usageTracker, clock, testZone)
        val scheduler = TestScheduleScheduler()

        var lockedPkg: String? = null
        val watcher = ScheduleWatcherImpl(repo, policyEngine, clock, testZone, scheduler) { lockedPkg = it }

        watcher.onForegroundChanged("com.test.app")

        // Advance 5 minutes (fired early at 08:55)
        clock.advanceBoth(300_000L)
        scheduler.trigger()

        assertNull(lockedPkg)
        // Rescheduled for remaining 5 minutes (300,000 ms)
        assertEquals(300_000L, scheduler.scheduledDelayMillis)

        // Advance remaining 5 minutes to 09:00:00
        clock.advanceBoth(300_000L)
        scheduler.trigger()

        assertEquals("com.test.app", lockedPkg)
    }

    // 14. Generation stale callback: old callback is ignored
    @Test
    fun test14_generationStaleCallback_ignored() {
        val repo = FakeTargetRepository()
        repo.apps["com.test.app"] = LockedApp(
            "com.test.app",
            enabled = true,
            schedule = TimeSchedule(enabled = true, startHour = 9, startMinute = 0, endHour = 17, endMinute = 0)
        )
        val clock = TestClock.at(2026, 9, 3, 8, 50, zoneId = testZone)
        val usageTracker = UsageTracker(clock = clock, zoneId = testZone)
        val policyEngine = PolicyEngine(repo, usageTracker, clock, testZone)
        val scheduler = TestScheduleScheduler()

        var lockedPkg: String? = null
        val watcher = ScheduleWatcherImpl(repo, policyEngine, clock, testZone, scheduler) { lockedPkg = it }

        watcher.onForegroundChanged("com.test.app")
        val staleRunnable = scheduler.scheduledRunnable
        assertNotNull(staleRunnable)

        // User switches to launcher
        watcher.onForegroundChanged(null)

        // Stale runnable triggers later at 09:00
        clock.advanceBoth(600_000L)
        staleRunnable?.run()

        assertNull("Stale callback must be ignored", lockedPkg)
    }

    // 15. Foreground change invalidation: switching to another package invalidates deadline
    @Test
    fun test15_foregroundChangeInvalidation() {
        val repo = FakeTargetRepository()
        repo.apps["com.test.app1"] = LockedApp(
            "com.test.app1",
            enabled = true,
            schedule = TimeSchedule(enabled = true, startHour = 9, startMinute = 0, endHour = 17, endMinute = 0)
        )
        repo.apps["com.test.app2"] = LockedApp(
            "com.test.app2",
            enabled = true,
            schedule = TimeSchedule(enabled = true, startHour = 10, startMinute = 0, endHour = 17, endMinute = 0)
        )
        val clock = TestClock.at(2026, 9, 3, 8, 30, zoneId = testZone)
        val usageTracker = UsageTracker(clock = clock, zoneId = testZone)
        val policyEngine = PolicyEngine(repo, usageTracker, clock, testZone)
        val scheduler = TestScheduleScheduler()

        val watcher = ScheduleWatcherImpl(repo, policyEngine, clock, testZone, scheduler) {}

        watcher.onForegroundChanged("com.test.app1")
        assertEquals(30 * 60_000L, scheduler.scheduledDelayMillis)
        val oldGen = watcher.getCurrentGeneration()

        // User switches to app2
        watcher.onForegroundChanged("com.test.app2")
        assertTrue(watcher.getCurrentGeneration() > oldGen)
        assertEquals("com.test.app2", watcher.getActivePackage())
        // From 08:30 to 10:00 = 90 minutes
        assertEquals(90 * 60_000L, scheduler.scheduledDelayMillis)
    }

    // 16. Policy update invalidation: updating schedule recalculates and reschedules
    @Test
    fun test16_policyUpdateInvalidation() {
        val repo = FakeTargetRepository()
        repo.apps["com.test.app"] = LockedApp(
            "com.test.app",
            enabled = true,
            schedule = TimeSchedule(enabled = true, startHour = 9, startMinute = 0, endHour = 17, endMinute = 0)
        )
        val clock = TestClock.at(2026, 9, 3, 8, 30, zoneId = testZone)
        val usageTracker = UsageTracker(clock = clock, zoneId = testZone)
        val policyEngine = PolicyEngine(repo, usageTracker, clock, testZone)
        val scheduler = TestScheduleScheduler()

        val watcher = ScheduleWatcherImpl(repo, policyEngine, clock, testZone, scheduler) {}

        watcher.onForegroundChanged("com.test.app")
        assertEquals(30 * 60_000L, scheduler.scheduledDelayMillis)
        val oldGen = watcher.getCurrentGeneration()

        // Schedule changed to 11:00..17:00
        repo.apps["com.test.app"] = repo.apps["com.test.app"]!!.copy(
            schedule = TimeSchedule(enabled = true, startHour = 11, startMinute = 0, endHour = 17, endMinute = 0)
        )
        watcher.onPolicyUpdated("com.test.app")

        assertTrue(watcher.getCurrentGeneration() > oldGen)
        // From 08:30 to 11:00 = 150 minutes
        assertEquals(150 * 60_000L, scheduler.scheduledDelayMillis)
    }

    // 17. Stop/start lifecycle recovery: stop clears state, start does NOT blindly resume until fresh foreground event
    @Test
    fun test17_stopStartLifecycleRecovery() {
        val repo = FakeTargetRepository()
        repo.apps["com.test.app"] = LockedApp(
            "com.test.app",
            enabled = true,
            schedule = TimeSchedule(enabled = true, startHour = 9, startMinute = 0, endHour = 17, endMinute = 0)
        )
        val clock = TestClock.at(2026, 9, 3, 8, 50, zoneId = testZone)
        val usageTracker = UsageTracker(clock = clock, zoneId = testZone)
        val policyEngine = PolicyEngine(repo, usageTracker, clock, testZone)
        val scheduler = TestScheduleScheduler()

        var lockedPkg: String? = null
        val watcher = ScheduleWatcherImpl(repo, policyEngine, clock, testZone, scheduler) { lockedPkg = it }

        watcher.onForegroundChanged("com.test.app")
        assertNotNull(scheduler.scheduledRunnable)

        // Service interrupts
        watcher.stop()
        assertNull(scheduler.scheduledRunnable)
        assertNull(watcher.getActivePackage())

        // Service reconnects
        watcher.start()
        assertNull("Must not blindly resume without event", scheduler.scheduledRunnable)
        assertNull(watcher.getActivePackage())

        // Fresh event arrives
        watcher.onForegroundChanged("com.test.app")
        assertNotNull(scheduler.scheduledRunnable)
        assertEquals(600_000L, scheduler.scheduledDelayMillis)

        // Advance to 09:00
        clock.advanceBoth(600_000L)
        scheduler.trigger()
        assertEquals("com.test.app", lockedPkg)
    }

    // 18. Screen OFF cancellation: cancels pending deadline
    @Test
    fun test18_screenOff_cancelsDeadline() {
        val repo = FakeTargetRepository()
        repo.apps["com.test.app"] = LockedApp(
            "com.test.app",
            enabled = true,
            schedule = TimeSchedule(enabled = true, startHour = 9, startMinute = 0, endHour = 17, endMinute = 0)
        )
        val clock = TestClock.at(2026, 9, 3, 8, 50, zoneId = testZone)
        val usageTracker = UsageTracker(clock = clock, zoneId = testZone)
        val policyEngine = PolicyEngine(repo, usageTracker, clock, testZone)
        val scheduler = TestScheduleScheduler()

        val watcher = ScheduleWatcherImpl(repo, policyEngine, clock, testZone, scheduler) {}

        watcher.onForegroundChanged("com.test.app")
        assertNotNull(scheduler.scheduledRunnable)

        // Screen OFF
        watcher.onForegroundChanged(null)
        assertNull(scheduler.scheduledRunnable)
        assertNull(watcher.getActivePackage())
    }

    // 19. Screen ON requires fresh foreground event: does not resume blindly
    @Test
    fun test19_screenOn_requiresFreshForegroundEvent() {
        val repo = FakeTargetRepository()
        repo.apps["com.test.app"] = LockedApp(
            "com.test.app",
            enabled = true,
            schedule = TimeSchedule(enabled = true, startHour = 9, startMinute = 0, endHour = 17, endMinute = 0)
        )
        val clock = TestClock.at(2026, 9, 3, 8, 50, zoneId = testZone)
        val usageTracker = UsageTracker(clock = clock, zoneId = testZone)
        val policyEngine = PolicyEngine(repo, usageTracker, clock, testZone)
        val scheduler = TestScheduleScheduler()

        val watcher = ScheduleWatcherImpl(repo, policyEngine, clock, testZone, scheduler) {}

        watcher.onForegroundChanged("com.test.app")
        watcher.onForegroundChanged(null) // Screen OFF

        // Screen ON happens in system: watcher is NOT called, active package remains null
        assertNull(watcher.getActivePackage())
        assertNull(scheduler.scheduledRunnable)

        // Accessibility event arrives confirming target
        watcher.onForegroundChanged("com.test.app")
        assertEquals("com.test.app", watcher.getActivePackage())
        assertNotNull(scheduler.scheduledRunnable)
    }

    // 20. Duplicate foreground idempotency: duplicate calls do not create duplicate timers
    @Test
    fun test20_duplicateForegroundIdempotency() {
        val repo = FakeTargetRepository()
        repo.apps["com.test.app"] = LockedApp(
            "com.test.app",
            enabled = true,
            schedule = TimeSchedule(enabled = true, startHour = 9, startMinute = 0, endHour = 17, endMinute = 0)
        )
        val clock = TestClock.at(2026, 9, 3, 8, 50, zoneId = testZone)
        val usageTracker = UsageTracker(clock = clock, zoneId = testZone)
        val policyEngine = PolicyEngine(repo, usageTracker, clock, testZone)
        val scheduler = TestScheduleScheduler()

        val watcher = ScheduleWatcherImpl(repo, policyEngine, clock, testZone, scheduler) {}

        watcher.onForegroundChanged("com.test.app")
        val initialGen = watcher.getCurrentGeneration()
        val initialDelay = scheduler.scheduledDelayMillis

        // Repeated accessibility events
        watcher.onForegroundChanged("com.test.app")
        watcher.onForegroundChanged("com.test.app")

        assertEquals(initialGen, watcher.getCurrentGeneration())
        assertEquals(initialDelay, scheduler.scheduledDelayMillis)
        assertEquals(0, scheduler.cancelCallCount)
    }

    // 21. Disabled schedule: no watcher scheduled
    @Test
    fun test21_disabledSchedule_noWatcher() {
        val repo = FakeTargetRepository()
        repo.apps["com.test.app"] = LockedApp(
            "com.test.app",
            enabled = true,
            schedule = TimeSchedule(enabled = false, startHour = 9, startMinute = 0, endHour = 17, endMinute = 0)
        )
        val clock = TestClock.at(2026, 9, 3, 8, 50, zoneId = testZone)
        val usageTracker = UsageTracker(clock = clock, zoneId = testZone)
        val policyEngine = PolicyEngine(repo, usageTracker, clock, testZone)
        val scheduler = TestScheduleScheduler()

        val watcher = ScheduleWatcherImpl(repo, policyEngine, clock, testZone, scheduler) {}
        watcher.onForegroundChanged("com.test.app")

        assertNull("Disabled schedule must not schedule watcher", scheduler.scheduledRunnable)
    }

    // 22. Disabled target: no watcher scheduled
    @Test
    fun test22_disabledTarget_noWatcher() {
        val repo = FakeTargetRepository()
        repo.apps["com.test.app"] = LockedApp(
            "com.test.app",
            enabled = false,
            schedule = TimeSchedule(enabled = true, startHour = 9, startMinute = 0, endHour = 17, endMinute = 0)
        )
        val clock = TestClock.at(2026, 9, 3, 8, 50, zoneId = testZone)
        val usageTracker = UsageTracker(clock = clock, zoneId = testZone)
        val policyEngine = PolicyEngine(repo, usageTracker, clock, testZone)
        val scheduler = TestScheduleScheduler()

        val watcher = ScheduleWatcherImpl(repo, policyEngine, clock, testZone, scheduler) {}
        watcher.onForegroundChanged("com.test.app")

        assertNull("Disabled target must not schedule watcher", scheduler.scheduledRunnable)
    }

    // 23. No schedule: no watcher scheduled
    @Test
    fun test23_noSchedule_noWatcher() {
        val repo = FakeTargetRepository()
        repo.apps["com.test.app"] = LockedApp(
            "com.test.app",
            enabled = true,
            schedule = null
        )
        val clock = TestClock.at(2026, 9, 3, 8, 50, zoneId = testZone)
        val usageTracker = UsageTracker(clock = clock, zoneId = testZone)
        val policyEngine = PolicyEngine(repo, usageTracker, clock, testZone)
        val scheduler = TestScheduleScheduler()

        val watcher = ScheduleWatcherImpl(repo, policyEngine, clock, testZone, scheduler) {}
        watcher.onForegroundChanged("com.test.app")

        assertNull("Target without schedule must not schedule watcher", scheduler.scheduledRunnable)
    }

    // 24. Schedule + Daily Limit race: both trigger around same time, shared lock path protects against duplicate sessions
    @Test
    fun test24_scheduleAndDailyLimitRace_duplicateLockProtection() {
        val repo = FakeTargetRepository()
        // 10-minute limit (600,000 ms) and schedule starting at 09:00 (10 min away from 08:50)
        repo.apps["com.test.app"] = LockedApp(
            "com.test.app",
            enabled = true,
            schedule = TimeSchedule(enabled = true, startHour = 9, startMinute = 0, endHour = 17, endMinute = 0),
            timeLimit = TimeLimit(enabled = true, dailyLimitMinutes = 10)
        )
        val clock = TestClock.at(2026, 9, 3, 8, 50, zoneId = testZone)
        val usageTracker = UsageTracker(clock = clock, zoneId = testZone)
        val policyEngine = PolicyEngine(repo, usageTracker, clock, testZone)
        val scheduler = TestScheduleScheduler()

        var scheduleLockCount = 0
        val scheduleWatcher = ScheduleWatcherImpl(repo, policyEngine, clock, testZone, scheduler) {
            scheduleLockCount++
        }

        usageTracker.startSession("com.test.app")
        scheduleWatcher.onForegroundChanged("com.test.app")

        // Advance 10 minutes: both schedule and usage limit are reached at 09:00:00
        clock.advanceBoth(600_000L)
        scheduler.trigger()

        assertEquals("Schedule lock callback fired once", 1, scheduleLockCount)
    }

    // 25. Reason Ownership: ScheduleWatcher must NOT emit SCHEDULE_DEADLINE if locked for another reason
    @Test
    fun test25_reasonOwnership_doesNotEmitScheduleLockWhenScheduleInactive() {
        val repo = FakeTargetRepository()
        // Schedule is 09:00..17:00 (currently 08:50, INACTIVE)
        // But daily limit is 0 minutes (EXHAUSTED immediately)
        repo.apps["com.test.app"] = LockedApp(
            "com.test.app",
            enabled = true,
            schedule = TimeSchedule(enabled = true, startHour = 9, startMinute = 0, endHour = 17, endMinute = 0),
            timeLimit = TimeLimit(enabled = true, dailyLimitMinutes = 0)
        )
        val clock = TestClock.at(2026, 9, 3, 8, 50, zoneId = testZone)
        val usageTracker = UsageTracker(clock = clock, zoneId = testZone)
        val policyEngine = PolicyEngine(repo, usageTracker, clock, testZone)
        val scheduler = TestScheduleScheduler()

        var scheduleLockEmitted: String? = null
        val scheduleWatcher = ScheduleWatcherImpl(repo, policyEngine, clock, testZone, scheduler) {
            scheduleLockEmitted = it
        }

        // PolicyEngine returns LOCK (due to daily limit = 0), but isScheduleActive is FALSE
        scheduleWatcher.onForegroundChanged("com.test.app")

        assertNull("ScheduleWatcher must not claim ownership of daily limit lock", scheduleLockEmitted)
    }

    // 26. Schedule end / ALLOW transition: crossing schedule end transitions to ALLOW without spurious lock
    @Test
    fun test26_scheduleEnd_allowTransition_noSpuriousLock() {
        val repo = FakeTargetRepository()
        repo.apps["com.test.app"] = LockedApp(
            "com.test.app",
            enabled = true,
            schedule = TimeSchedule(enabled = true, startHour = 9, startMinute = 0, endHour = 17, endMinute = 0)
        )
        // 16:59:00 -> 1 minute before schedule ends
        val clock = TestClock.at(2026, 9, 3, 16, 59, zoneId = testZone)
        val usageTracker = UsageTracker(clock = clock, zoneId = testZone)
        val policyEngine = PolicyEngine(repo, usageTracker, clock, testZone)
        val scheduler = TestScheduleScheduler()

        var scheduleLockEmitted: String? = null
        val scheduleWatcher = ScheduleWatcherImpl(repo, policyEngine, clock, testZone, scheduler) {
            scheduleLockEmitted = it
        }

        // Inside schedule at 16:59:00 -> PolicyEngine is LOCK
        scheduleWatcher.onForegroundChanged("com.test.app")
        assertEquals("com.test.app", scheduleLockEmitted)

        // Reset emitted callback tracker
        scheduleLockEmitted = null

        // Advance 1 minute to 17:00:00 (schedule ends, transitions to ALLOW)
        clock.advanceBoth(60_000L)
        // Target app is re-evaluated at 17:00:00
        scheduleWatcher.onPolicyUpdated("com.test.app")

        // PolicyEngine is now ALLOW (outside schedule). Watcher schedules next boundary (tomorrow 09:00)
        assertNull("Must not lock when schedule has ended", scheduleLockEmitted)
        assertNotNull(scheduler.scheduledRunnable)
        assertEquals(16 * 3600_000L, scheduler.scheduledDelayMillis)
    }
}
