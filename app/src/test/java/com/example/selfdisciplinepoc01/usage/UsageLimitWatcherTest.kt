package com.example.selfdisciplinepoc01.usage

import com.example.selfdisciplinepoc01.policy.PolicyEngine
import com.example.selfdisciplinepoc01.target.model.LockedApp
import com.example.selfdisciplinepoc01.target.model.TimeLimit
import com.example.selfdisciplinepoc01.target.model.TimeSchedule
import com.example.selfdisciplinepoc01.target.repository.TargetRepository
import com.example.selfdisciplinepoc01.time.TestClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class UsageLimitWatcherTest {

    private val testZone = ZoneId.of("UTC")

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

    // 1. Target with no limit -> no callback scheduled
    @Test
    fun test1_targetWithNoLimit_noCallbackScheduled() {
        val repo = FakeTargetRepository()
        repo.apps["com.test.app"] = LockedApp("com.test.app", enabled = true, timeLimit = null)
        val clock = TestClock.at(2026, 9, 3, 10, 0, zoneId = testZone)
        val usageTracker = UsageTracker(clock = clock, zoneId = testZone)
        val policyEngine = PolicyEngine(repo, usageTracker, clock, testZone)
        val scheduler = TestLimitScheduler()

        var lockTriggeredPackage: String? = null
        val watcher = UsageLimitWatcherImpl(repo, usageTracker, policyEngine, scheduler) {
            lockTriggeredPackage = it
        }

        watcher.onForegroundChanged("com.test.app")
        assertNull("No callback should be scheduled when target has no limit", scheduler.scheduledRunnable)
        assertNull(lockTriggeredPackage)
    }

    // 2. Limit disabled -> no callback scheduled
    @Test
    fun test2_limitDisabled_noCallbackScheduled() {
        val repo = FakeTargetRepository()
        repo.apps["com.test.app"] = LockedApp(
            "com.test.app",
            enabled = true,
            timeLimit = TimeLimit(enabled = false, dailyLimitMinutes = 30)
        )
        val clock = TestClock.at(2026, 9, 3, 10, 0, zoneId = testZone)
        val usageTracker = UsageTracker(clock = clock, zoneId = testZone)
        val policyEngine = PolicyEngine(repo, usageTracker, clock, testZone)
        val scheduler = TestLimitScheduler()

        val watcher = UsageLimitWatcherImpl(repo, usageTracker, policyEngine, scheduler) {}
        watcher.onForegroundChanged("com.test.app")

        assertNull("Disabled limit must not schedule callback", scheduler.scheduledRunnable)
    }

    // 3. Usage below limit -> callback scheduled for exact remaining time
    @Test
    fun test3_usageBelowLimit_schedulesForRemainingTime() {
        val repo = FakeTargetRepository()
        // 30 minute limit = 1,800,000 ms
        repo.apps["com.test.app"] = LockedApp(
            "com.test.app",
            enabled = true,
            timeLimit = TimeLimit(enabled = true, dailyLimitMinutes = 30)
        )
        val clock = TestClock.at(2026, 9, 3, 10, 0, zoneId = testZone)
        val usageTracker = UsageTracker(clock = clock, zoneId = testZone)

        // Simulate 10 minutes already used (600,000 ms)
        usageTracker.startSession("com.test.app")
        clock.advanceBoth(600_000L)
        usageTracker.stopSession()

        val policyEngine = PolicyEngine(repo, usageTracker, clock, testZone)
        val scheduler = TestLimitScheduler()

        val watcher = UsageLimitWatcherImpl(repo, usageTracker, policyEngine, scheduler) {}

        // User opens app again: remaining time should be exactly 20 minutes (1,200,000 ms)
        usageTracker.startSession("com.test.app")
        watcher.onForegroundChanged("com.test.app")

        assertNotNull(scheduler.scheduledRunnable)
        assertEquals(1_200_000L, scheduler.scheduledDelayMillis)
    }

    // 4. Exact deadline reached -> PolicyEngine returns LOCK and calls lock callback
    @Test
    fun test4_exactDeadline_policyReturnsLock_triggersLockCallback() {
        val repo = FakeTargetRepository()
        // 10 second limit (10,000 ms)
        repo.apps["com.test.app"] = LockedApp(
            "com.test.app",
            enabled = true,
            timeLimit = TimeLimit(enabled = true, dailyLimitSeconds = 10)
        )
        val clock = TestClock.at(2026, 9, 3, 10, 0, zoneId = testZone)
        val usageTracker = UsageTracker(clock = clock, zoneId = testZone)
        val policyEngine = PolicyEngine(repo, usageTracker, clock, testZone)
        val scheduler = TestLimitScheduler()

        var lockedPkg: String? = null
        val watcher = UsageLimitWatcherImpl(repo, usageTracker, policyEngine, scheduler) {
            lockedPkg = it
        }

        usageTracker.startSession("com.test.app")
        watcher.onForegroundChanged("com.test.app")

        assertEquals(10_000L, scheduler.scheduledDelayMillis)

        // Advance monotonic time by 10,000 ms
        clock.advanceBoth(10_000L)

        // Trigger deadline runnable
        scheduler.trigger()

        assertEquals("com.test.app", lockedPkg)
    }

    // 5. Stale Callback Guard: foreground changed before callback fires -> ignored!
    @Test
    fun test5_staleCallback_foregroundChanged_ignored() {
        val repo = FakeTargetRepository()
        repo.apps["com.test.chrome"] = LockedApp(
            "com.test.chrome",
            enabled = true,
            timeLimit = TimeLimit(enabled = true, dailyLimitMinutes = 15)
        )
        val clock = TestClock.at(2026, 9, 3, 10, 0, zoneId = testZone)
        val usageTracker = UsageTracker(clock = clock, zoneId = testZone)
        val policyEngine = PolicyEngine(repo, usageTracker, clock, testZone)
        val scheduler = TestLimitScheduler()

        var lockedPkg: String? = null
        val watcher = UsageLimitWatcherImpl(repo, usageTracker, policyEngine, scheduler) {
            lockedPkg = it
        }

        // Chrome opens
        usageTracker.startSession("com.test.chrome")
        watcher.onForegroundChanged("com.test.chrome")
        val chromeRunnable = scheduler.scheduledRunnable
        assertNotNull(chromeRunnable)

        // User switches to Launcher before deadline fires
        usageTracker.stopSession("com.test.chrome")
        watcher.onForegroundChanged(null)

        // Stale Chrome callback fires late
        chromeRunnable?.run()

        assertNull("Stale callback must NOT trigger lock for old package", lockedPkg)
    }

    // 6. Callback fires after watcher.stop() -> ignored!
    @Test
    fun test6_callbackAfterStop_ignored() {
        val repo = FakeTargetRepository()
        repo.apps["com.test.app"] = LockedApp(
            "com.test.app",
            enabled = true,
            timeLimit = TimeLimit(enabled = true, dailyLimitMinutes = 15)
        )
        val clock = TestClock.at(2026, 9, 3, 10, 0, zoneId = testZone)
        val usageTracker = UsageTracker(clock = clock, zoneId = testZone)
        val policyEngine = PolicyEngine(repo, usageTracker, clock, testZone)
        val scheduler = TestLimitScheduler()

        var lockedPkg: String? = null
        val watcher = UsageLimitWatcherImpl(repo, usageTracker, policyEngine, scheduler) {
            lockedPkg = it
        }

        watcher.onForegroundChanged("com.test.app")
        val runnable = scheduler.scheduledRunnable
        assertNotNull(runnable)

        // Service destroys or stops watcher
        watcher.stop()

        // Delayed runnable fires
        runnable?.run()

        assertNull("Stopped watcher must ignore callbacks", lockedPkg)
    }

    // 7. Scheduler Jitter: ALLOW callback reschedules if usage is still below limit
    @Test
    fun test7_jitterEarlyCallback_reschedulesInsteadOfBlindLock() {
        val repo = FakeTargetRepository()
        // 10 second limit (10,000 ms)
        repo.apps["com.test.app"] = LockedApp(
            "com.test.app",
            enabled = true,
            timeLimit = TimeLimit(enabled = true, dailyLimitSeconds = 10)
        )
        val clock = TestClock.at(2026, 9, 3, 10, 0, zoneId = testZone)
        val usageTracker = UsageTracker(clock = clock, zoneId = testZone)
        val policyEngine = PolicyEngine(repo, usageTracker, clock, testZone)
        val scheduler = TestLimitScheduler()

        var lockedPkg: String? = null
        val watcher = UsageLimitWatcherImpl(repo, usageTracker, policyEngine, scheduler) {
            lockedPkg = it
        }

        usageTracker.startSession("com.test.app")
        watcher.onForegroundChanged("com.test.app")

        // Suppose scheduler fires slightly early at 9,950 ms (50 ms before actual deadline)
        clock.advanceBoth(9_950L)
        scheduler.trigger()

        // PolicyEngine returns ALLOW because 9,950ms < 10,000ms
        assertNull("Must not blindly lock when usage is still below limit", lockedPkg)

        // Watcher must have rescheduled for the remaining 50 ms!
        assertNotNull("Must reschedule for remaining jitter duration", scheduler.scheduledRunnable)
        assertEquals(50L, scheduler.scheduledDelayMillis)

        // Advance the final 50 ms
        clock.advanceBoth(50L)
        scheduler.trigger()

        assertEquals("com.test.app", lockedPkg)
    }

    // 8. Target disabled while callback pending -> ALLOW, no lock
    @Test
    fun test8_targetDisabledWhilePending_noLock() {
        val repo = FakeTargetRepository()
        repo.apps["com.test.app"] = LockedApp(
            "com.test.app",
            enabled = true,
            timeLimit = TimeLimit(enabled = true, dailyLimitSeconds = 10)
        )
        val clock = TestClock.at(2026, 9, 3, 10, 0, zoneId = testZone)
        val usageTracker = UsageTracker(clock = clock, zoneId = testZone)
        val policyEngine = PolicyEngine(repo, usageTracker, clock, testZone)
        val scheduler = TestLimitScheduler()

        var lockedPkg: String? = null
        val watcher = UsageLimitWatcherImpl(repo, usageTracker, policyEngine, scheduler) {
            lockedPkg = it
        }

        usageTracker.startSession("com.test.app")
        watcher.onForegroundChanged("com.test.app")

        // User disables target in settings while timer is pending
        repo.apps["com.test.app"] = repo.apps["com.test.app"]!!.copy(enabled = false)

        clock.advanceBoth(10_000L)
        scheduler.trigger()

        assertNull("Disabled target must be ALLOW, no lock", lockedPkg)
    }

    // 9. Schedule + Limit interaction: if schedule is already locking, limit watcher is superseded
    @Test
    fun test9_scheduleAndLimitInteraction() {
        val repo = FakeTargetRepository()
        repo.apps["com.test.app"] = LockedApp(
            "com.test.app",
            enabled = true,
            // Schedule active 09:00..17:00
            schedule = TimeSchedule(enabled = true, startHour = 9, startMinute = 0, endHour = 17, endMinute = 0),
            // Limit 30 min
            timeLimit = TimeLimit(enabled = true, dailyLimitMinutes = 30)
        )
        // 12:00 is inside schedule
        val clock = TestClock.at(2026, 9, 3, 12, 0, zoneId = testZone)
        val usageTracker = UsageTracker(clock = clock, zoneId = testZone)
        val policyEngine = PolicyEngine(repo, usageTracker, clock, testZone)
        val scheduler = TestLimitScheduler()

        var lockedPkg: String? = null
        val watcher = UsageLimitWatcherImpl(repo, usageTracker, policyEngine, scheduler) {
            lockedPkg = it
        }

        // Target opens inside active schedule: remaining calculation still exists, but PolicyEngine returns LOCK immediately
        watcher.onForegroundChanged("com.test.app")
        scheduler.trigger()

        assertEquals("com.test.app", lockedPkg)
    }

    // 10. Duplicate onForegroundChanged does not create duplicate active callbacks
    @Test
    fun test10_duplicateOnForegroundChanged_doesNotCreateDuplicateCallbacks() {
        val repo = FakeTargetRepository()
        repo.apps["com.test.app"] = LockedApp(
            "com.test.app",
            enabled = true,
            timeLimit = TimeLimit(enabled = true, dailyLimitMinutes = 20)
        )
        val clock = TestClock.at(2026, 9, 3, 10, 0, zoneId = testZone)
        val usageTracker = UsageTracker(clock = clock, zoneId = testZone)
        val policyEngine = PolicyEngine(repo, usageTracker, clock, testZone)
        val scheduler = TestLimitScheduler()

        val watcher = UsageLimitWatcherImpl(repo, usageTracker, policyEngine, scheduler) {}

        watcher.onForegroundChanged("com.test.app")
        val initialGen = watcher.getCurrentGeneration()
        val initialDelay = scheduler.scheduledDelayMillis

        // Repeated accessibility events for the same foreground package
        watcher.onForegroundChanged("com.test.app")
        watcher.onForegroundChanged("com.test.app")

        assertEquals("Generation must not advance on duplicate calls", initialGen, watcher.getCurrentGeneration())
        assertEquals("Scheduled delay must remain unchanged", initialDelay, scheduler.scheduledDelayMillis)
        assertEquals("No extra cancels on duplicate calls", 0, scheduler.cancelCallCount)
    }

    // 11. Screen OFF cancels watcher
    @Test
    fun test11_screenOff_cancelsWatcher() {
        val repo = FakeTargetRepository()
        repo.apps["com.test.app"] = LockedApp(
            "com.test.app",
            enabled = true,
            timeLimit = TimeLimit(enabled = true, dailyLimitMinutes = 20)
        )
        val clock = TestClock.at(2026, 9, 3, 10, 0, zoneId = testZone)
        val usageTracker = UsageTracker(clock = clock, zoneId = testZone)
        val policyEngine = PolicyEngine(repo, usageTracker, clock, testZone)
        val scheduler = TestLimitScheduler()

        val watcher = UsageLimitWatcherImpl(repo, usageTracker, policyEngine, scheduler) {}

        watcher.onForegroundChanged("com.test.app")
        assertNotNull(scheduler.scheduledRunnable)

        // Screen OFF triggers clear
        watcher.onForegroundChanged(null)
        assertNull("Scheduled runnable must be cleared on screen off", scheduler.scheduledRunnable)
        assertNull("Active package must be cleared on screen off", watcher.getActivePackage())
    }

    // 12. Screen ON does not blindly restart until foreground event
    @Test
    fun test12_screenOn_doesNotBlindlyRestart() {
        val repo = FakeTargetRepository()
        repo.apps["com.test.app"] = LockedApp(
            "com.test.app",
            enabled = true,
            timeLimit = TimeLimit(enabled = true, dailyLimitMinutes = 20)
        )
        val clock = TestClock.at(2026, 9, 3, 10, 0, zoneId = testZone)
        val usageTracker = UsageTracker(clock = clock, zoneId = testZone)
        val policyEngine = PolicyEngine(repo, usageTracker, clock, testZone)
        val scheduler = TestLimitScheduler()

        val watcher = UsageLimitWatcherImpl(repo, usageTracker, policyEngine, scheduler) {}

        watcher.onForegroundChanged("com.test.app")
        watcher.onForegroundChanged(null) // screen off

        // On screen on, watcher remains inactive until actual foreground event arrives
        assertNull(watcher.getActivePackage())
        assertNull(scheduler.scheduledRunnable)

        // When accessibility foreground confirms app
        watcher.onForegroundChanged("com.test.app")
        assertNotNull(scheduler.scheduledRunnable)
        assertEquals("com.test.app", watcher.getActivePackage())
    }

    // 13. Policy update invalidates and reschedules with new limit
    @Test
    fun test13_policyUpdated_invalidatesAndReschedules() {
        val repo = FakeTargetRepository()
        repo.apps["com.test.app"] = LockedApp(
            "com.test.app",
            enabled = true,
            timeLimit = TimeLimit(enabled = true, dailyLimitMinutes = 30)
        )
        val clock = TestClock.at(2026, 9, 3, 10, 0, zoneId = testZone)
        val usageTracker = UsageTracker(clock = clock, zoneId = testZone)
        val policyEngine = PolicyEngine(repo, usageTracker, clock, testZone)
        val scheduler = TestLimitScheduler()

        val watcher = UsageLimitWatcherImpl(repo, usageTracker, policyEngine, scheduler) {}

        watcher.onForegroundChanged("com.test.app")
        assertEquals(30 * 60_000L, scheduler.scheduledDelayMillis)
        val oldGen = watcher.getCurrentGeneration()

        // Policy updated to 10 minutes limit
        repo.apps["com.test.app"] = repo.apps["com.test.app"]!!.copy(
            timeLimit = TimeLimit(enabled = true, dailyLimitMinutes = 10)
        )
        watcher.onPolicyUpdated("com.test.app")

        assertTrue("Generation must advance on policy update", watcher.getCurrentGeneration() > oldGen)
        assertEquals(10 * 60_000L, scheduler.scheduledDelayMillis)
    }

    // 14. Wall-clock change does NOT corrupt monotonic deadline
    @Test
    fun test14_wallClockChange_doesNotCorruptDeadline() {
        val repo = FakeTargetRepository()
        repo.apps["com.test.app"] = LockedApp(
            "com.test.app",
            enabled = true,
            timeLimit = TimeLimit(enabled = true, dailyLimitSeconds = 30)
        )
        val clock = TestClock.at(2026, 9, 3, 10, 0, zoneId = testZone)
        val usageTracker = UsageTracker(clock = clock, zoneId = testZone)
        val policyEngine = PolicyEngine(repo, usageTracker, clock, testZone)
        val scheduler = TestLimitScheduler()

        var lockedPkg: String? = null
        val watcher = UsageLimitWatcherImpl(repo, usageTracker, policyEngine, scheduler) {
            lockedPkg = it
        }

        usageTracker.startSession("com.test.app")
        watcher.onForegroundChanged("com.test.app")

        // Advance 20 seconds elapsed time
        clock.advanceElapsed(20_000L)

        // User changes wall clock by 3 hours
        clock.setWallTime(TestClock.at(2026, 9, 3, 13, 0, zoneId = testZone).wallTimeMillis())

        // At 20s, limit is not yet reached (used 20s < 30s)
        scheduler.trigger()
        assertNull("Must not lock early despite wall clock jump", lockedPkg)

        // Rescheduled for remaining 10s
        assertEquals(10_000L, scheduler.scheduledDelayMillis)

        // Advance remaining 10s elapsed
        clock.advanceElapsed(10_000L)
        scheduler.trigger()

        assertEquals("com.test.app", lockedPkg)
    }

    // 15. Zero minute daily limit locks immediately
    @Test
    fun test15_zeroMinuteLimit_locksImmediately() {
        val repo = FakeTargetRepository()
        repo.apps["com.test.app"] = LockedApp(
            "com.test.app",
            enabled = true,
            timeLimit = TimeLimit(enabled = true, dailyLimitMinutes = 0)
        )
        val clock = TestClock.at(2026, 9, 3, 10, 0, zoneId = testZone)
        val usageTracker = UsageTracker(clock = clock, zoneId = testZone)
        val policyEngine = PolicyEngine(repo, usageTracker, clock, testZone)
        val scheduler = TestLimitScheduler()

        var lockedPkg: String? = null
        val watcher = UsageLimitWatcherImpl(repo, usageTracker, policyEngine, scheduler) {
            lockedPkg = it
        }

        usageTracker.startSession("com.test.app")
        watcher.onForegroundChanged("com.test.app")

        assertEquals(0L, scheduler.scheduledDelayMillis)
        scheduler.trigger()

        assertEquals("com.test.app", lockedPkg)
    }

    // 16. Lifecycle recovery: stop -> start -> does not blindly resume -> awaits foreground confirmation -> schedules new deadline
    @Test
    fun test16_stopThenStart_lifecycleRecovery() {
        val repo = FakeTargetRepository()
        repo.apps["com.test.app"] = LockedApp(
            "com.test.app",
            enabled = true,
            timeLimit = TimeLimit(enabled = true, dailyLimitSeconds = 30)
        )
        val clock = TestClock.at(2026, 9, 3, 10, 0, zoneId = testZone)
        val usageTracker = UsageTracker(clock = clock, zoneId = testZone)
        val policyEngine = PolicyEngine(repo, usageTracker, clock, testZone)
        val scheduler = TestLimitScheduler()

        var lockedPkg: String? = null
        val watcher = UsageLimitWatcherImpl(repo, usageTracker, policyEngine, scheduler) {
            lockedPkg = it
        }

        // App opens and watcher starts
        usageTracker.startSession("com.test.app")
        watcher.onForegroundChanged("com.test.app")
        assertNotNull(scheduler.scheduledRunnable)
        assertEquals(30_000L, scheduler.scheduledDelayMillis)

        // Service is interrupted -> watcher.stop()
        watcher.stop()
        assertNull("Stop must clear scheduled runnable", scheduler.scheduledRunnable)
        assertNull("Stop must clear active package", watcher.getActivePackage())

        // Service reconnects -> watcher.start()
        watcher.start()
        // MUST NOT blindly resume previous package or schedule a deadline without foreground confirmation!
        assertNull("Start must not blindly schedule deadline", scheduler.scheduledRunnable)
        assertNull("Start must not restore active package before event", watcher.getActivePackage())

        // Fresh Accessibility foreground event arrives
        watcher.onForegroundChanged("com.test.app")
        assertNotNull("Fresh foreground event must schedule deadline", scheduler.scheduledRunnable)
        assertEquals(30_000L, scheduler.scheduledDelayMillis)
        assertEquals("com.test.app", watcher.getActivePackage())

        // Advance 30s and trigger
        clock.advanceBoth(30_000L)
        scheduler.trigger()

        assertEquals("com.test.app", lockedPkg)
    }
}
