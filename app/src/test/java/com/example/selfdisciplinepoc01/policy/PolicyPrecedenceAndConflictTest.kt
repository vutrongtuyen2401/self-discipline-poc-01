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
import java.time.ZoneId

/**
 * PHASE 10-B: Policy Conflict & Precedence Hardening Test Suite.
 *
 * Verifies deterministic interaction, conflict resolution, and reason ownership
 * between Schedule policy and Daily Limit according to the formal precedence rules:
 * 1. target disabled => ALLOW
 * 2. schedule active => schedule policy is restrictive
 * 3. daily limit exhausted => restrictive
 * 4. otherwise => ALLOW
 */
class PolicyPrecedenceAndConflictTest {

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

    /**
     * Shared lock launcher recorder modeling the exact Frozen Core State Machine rules
     * of launchLockSession() in AppDetectorAccessibilityService:
     * - Rule A: isLockScreenVisible guard
     * - Rule B: intra-session duplicate guard
     * - Rule C: COOLDOWN_MS guard
     */
    private class MockLockLauncher {
        val launchHistory = mutableListOf<LaunchRecord>()
        var isLockScreenVisible = false
        var isChromeLockedForCurrentTransition = false
        var lastForegroundPackage: String? = null
        var lastLockLaunchTimestamp = 0L
        var currentSessionId = 0L
        val cooldownMs = 1500L

        data class LaunchRecord(
            val sessionId: Long,
            val packageName: String,
            val reason: LockReason,
            val timestamp: Long
        )

        fun launch(packageName: String, reason: LockReason, now: Long): Boolean {
            val prevPkg = lastForegroundPackage
            val isNewTransition = (prevPkg != packageName)
            val elapsedSinceLastLaunch = now - lastLockLaunchTimestamp

            // Rule A: isLockScreenVisible
            if (isLockScreenVisible) return false

            // Rule B: intra-session duplicate
            if (!isNewTransition && isChromeLockedForCurrentTransition) return false

            // Rule C: cooldown check
            if (!isNewTransition && (elapsedSinceLastLaunch < cooldownMs)) return false

            // Eligible
            isChromeLockedForCurrentTransition = true
            lastForegroundPackage = packageName
            lastLockLaunchTimestamp = now
            currentSessionId++
            launchHistory.add(LaunchRecord(currentSessionId, packageName, reason, now))
            return true
        }

        fun onLockScreenResumed() {
            isLockScreenVisible = true
        }

        fun onLockScreenExited() {
            isLockScreenVisible = false
            isChromeLockedForCurrentTransition = false
            lastLockLaunchTimestamp = 0L
            lastForegroundPackage = "com.example.selfdisciplinepoc01"
        }
    }

    // -------------------------------------------------------------------------
    // 1. Schedule-only lock
    // -------------------------------------------------------------------------
    @Test
    fun test01_scheduleOnly_locksDueToSchedule() {
        val repo = FakeTargetRepository()
        repo.apps["com.test.app"] = LockedApp(
            "com.test.app",
            enabled = true,
            schedule = TimeSchedule(enabled = true, startHour = 9, startMinute = 0, endHour = 17, endMinute = 0),
            timeLimit = null
        )
        val clock = TestClock.at(2026, 9, 3, 10, 0, zoneId = testZone)
        val usageTracker = UsageTracker(clock = clock, zoneId = testZone)
        val policyEngine = PolicyEngine(repo, usageTracker, clock, testZone)

        assertEquals(PolicyDecision.LOCK, policyEngine.evaluate("com.test.app"))
        assertTrue(ScheduleEvaluator.isWithinSchedule(repo.apps["com.test.app"]!!.schedule, clock.wallTimeMillis(), testZone))
    }

    // -------------------------------------------------------------------------
    // 2. Limit-only lock
    // -------------------------------------------------------------------------
    @Test
    fun test02_limitOnly_locksDueToLimit() {
        val repo = FakeTargetRepository()
        repo.apps["com.test.app"] = LockedApp(
            "com.test.app",
            enabled = true,
            schedule = null,
            timeLimit = TimeLimit(enabled = true, dailyLimitMinutes = 10)
        )
        val clock = TestClock.at(2026, 9, 3, 10, 0, zoneId = testZone)
        val usageTracker = UsageTracker(clock = clock, zoneId = testZone)
        val policyEngine = PolicyEngine(repo, usageTracker, clock, testZone)

        // Usage not reached -> ALLOW
        assertEquals(PolicyDecision.ALLOW, policyEngine.evaluate("com.test.app"))

        // Track usage to reach limit
        usageTracker.startSession("com.test.app")
        clock.advanceBoth(10 * 60 * 1000L)
        usageTracker.stopSession("com.test.app")

        assertEquals(PolicyDecision.LOCK, policyEngine.evaluate("com.test.app"))
    }

    // -------------------------------------------------------------------------
    // 3. Schedule inactive + limit exhausted => DAILY_LIMIT
    // -------------------------------------------------------------------------
    @Test
    fun test03_scheduleInactive_limitExhausted_reasonIsDailyLimit() {
        val repo = FakeTargetRepository()
        repo.apps["com.test.app"] = LockedApp(
            "com.test.app",
            enabled = true,
            schedule = TimeSchedule(enabled = true, startHour = 14, startMinute = 0, endHour = 17, endMinute = 0),
            timeLimit = TimeLimit(enabled = true, dailyLimitMinutes = 10)
        )
        // 10:00 is outside schedule (inactive)
        val clock = TestClock.at(2026, 9, 3, 10, 0, zoneId = testZone)
        val usageTracker = UsageTracker(clock = clock, zoneId = testZone)
        val policyEngine = PolicyEngine(repo, usageTracker, clock, testZone)
        val scheduleScheduler = TestScheduleScheduler()
        val limitScheduler = TestLimitScheduler()
        val launcher = MockLockLauncher()

        val scheduleWatcher = ScheduleWatcherImpl(repo, policyEngine, clock, testZone, scheduleScheduler) { pkg ->
            launcher.launch(pkg, LockReason.SCHEDULE_DEADLINE, clock.elapsedRealtimeMillis())
        }
        val limitWatcher = UsageLimitWatcherImpl(repo, usageTracker, policyEngine, limitScheduler) { pkg ->
            launcher.launch(pkg, LockReason.DAILY_LIMIT, clock.elapsedRealtimeMillis())
        }

        // Exhaust daily limit
        usageTracker.startSession("com.test.app")
        clock.advanceBoth(10 * 60 * 1000L)
        usageTracker.stopSession("com.test.app")

        // Trigger limit watcher
        limitWatcher.onForegroundChanged("com.test.app")
        limitScheduler.trigger()

        assertEquals(1, launcher.launchHistory.size)
        assertEquals(LockReason.DAILY_LIMIT, launcher.launchHistory[0].reason)

        // If schedule watcher callback fires: sees schedule is inactive, preserves ownership, does NOT re-lock!
        scheduleWatcher.onForegroundChanged("com.test.app")
        scheduleScheduler.trigger()
        assertEquals(1, launcher.launchHistory.size)
    }

    // -------------------------------------------------------------------------
    // 4. Schedule active + limit exhausted => first trigger owns reason, no duplicate
    // -------------------------------------------------------------------------
    @Test
    fun test04_scheduleActive_limitExhausted_firstTriggerOwnsReason() {
        val repo = FakeTargetRepository()
        repo.apps["com.test.app"] = LockedApp(
            "com.test.app",
            enabled = true,
            schedule = TimeSchedule(enabled = true, startHour = 9, startMinute = 0, endHour = 17, endMinute = 0),
            timeLimit = TimeLimit(enabled = true, dailyLimitMinutes = 10)
        )
        // 10:00 is inside schedule
        val clock = TestClock.at(2026, 9, 3, 10, 0, zoneId = testZone)
        val usageTracker = UsageTracker(clock = clock, zoneId = testZone)
        val policyEngine = PolicyEngine(repo, usageTracker, clock, testZone)
        val launcher = MockLockLauncher()

        // ScheduleWatcher triggers first
        val scheduleWatcher = ScheduleWatcherImpl(repo, policyEngine, clock, testZone, TestScheduleScheduler()) { pkg ->
            launcher.launch(pkg, LockReason.SCHEDULE_DEADLINE, clock.elapsedRealtimeMillis())
        }
        val limitWatcher = UsageLimitWatcherImpl(repo, usageTracker, policyEngine, TestLimitScheduler()) { pkg ->
            launcher.launch(pkg, LockReason.DAILY_LIMIT, clock.elapsedRealtimeMillis())
        }

        // Usage already exhausted
        usageTracker.startSession("com.test.app")
        clock.advanceBoth(10 * 60 * 1000L)
        usageTracker.stopSession("com.test.app")

        scheduleWatcher.onForegroundChanged("com.test.app")
        assertEquals(1, launcher.launchHistory.size)
        assertEquals(LockReason.SCHEDULE_DEADLINE, launcher.launchHistory[0].reason)

        // Daily limit watcher also triggers -> Rule B suppresses duplicate session
        limitWatcher.onForegroundChanged("com.test.app")
        assertEquals(1, launcher.launchHistory.size)
    }

    // -------------------------------------------------------------------------
    // 5. Schedule ends but limit remains exhausted => remain LOCKED
    // -------------------------------------------------------------------------
    @Test
    fun test05_scheduleEnds_limitRemainsExhausted_remainsLocked() {
        val repo = FakeTargetRepository()
        repo.apps["com.test.app"] = LockedApp(
            "com.test.app",
            enabled = true,
            schedule = TimeSchedule(enabled = true, startHour = 9, startMinute = 0, endHour = 17, endMinute = 0),
            timeLimit = TimeLimit(enabled = true, dailyLimitMinutes = 10)
        )
        // 16:50 is inside schedule
        val clock = TestClock.at(2026, 9, 3, 16, 50, zoneId = testZone)
        val usageTracker = UsageTracker(clock = clock, zoneId = testZone)
        val policyEngine = PolicyEngine(repo, usageTracker, clock, testZone)
        val scheduleScheduler = TestScheduleScheduler()
        var scheduleLockEmitted = false

        // Usage is already exhausted
        usageTracker.startSession("com.test.app")
        clock.advanceBoth(15 * 60 * 1000L) // 15 min used > 10 min limit
        usageTracker.stopSession("com.test.app")

        // Time advances past schedule end boundary to 17:05 (schedule ended)
        clock.advanceBoth(15 * 60 * 1000L)

        // Schedule is now inactive
        assertFalse(ScheduleEvaluator.isWithinSchedule(repo.apps["com.test.app"]!!.schedule, clock.wallTimeMillis(), testZone))

        // But PolicyEngine STILL returns LOCK because daily limit is exhausted!
        assertEquals(PolicyDecision.LOCK, policyEngine.evaluate("com.test.app"))

        // When ScheduleWatcher evaluates, isScheduleActive is false -> does NOT emit schedule lock and does NOT unlock
        val scheduleWatcher = ScheduleWatcherImpl(repo, policyEngine, clock, testZone, scheduleScheduler) {
            scheduleLockEmitted = true
        }
        scheduleWatcher.onForegroundChanged("com.test.app")
        scheduleScheduler.trigger()

        assertFalse("ScheduleWatcher must not emit schedule lock when schedule has ended", scheduleLockEmitted)
        assertEquals(PolicyDecision.LOCK, policyEngine.evaluate("com.test.app"))
    }

    // -------------------------------------------------------------------------
    // 6. Limit becomes exhausted while schedule inactive => DAILY_LIMIT
    // -------------------------------------------------------------------------
    @Test
    fun test06_limitExhausted_whileScheduleInactive_emitsDailyLimit() {
        val repo = FakeTargetRepository()
        repo.apps["com.test.app"] = LockedApp(
            "com.test.app",
            enabled = true,
            schedule = TimeSchedule(enabled = true, startHour = 18, startMinute = 0, endHour = 22, endMinute = 0),
            timeLimit = TimeLimit(enabled = true, dailyLimitMinutes = 15)
        )
        // 10:00 (schedule inactive)
        val clock = TestClock.at(2026, 9, 3, 10, 0, zoneId = testZone)
        val usageTracker = UsageTracker(clock = clock, zoneId = testZone)
        val policyEngine = PolicyEngine(repo, usageTracker, clock, testZone)
        val limitScheduler = TestLimitScheduler()
        val launcher = MockLockLauncher()

        val limitWatcher = UsageLimitWatcherImpl(repo, usageTracker, policyEngine, limitScheduler) { pkg ->
            launcher.launch(pkg, LockReason.DAILY_LIMIT, clock.elapsedRealtimeMillis())
        }

        limitWatcher.onForegroundChanged("com.test.app")
        assertEquals(15 * 60 * 1000L, limitScheduler.scheduledDelayMillis)

        // Usage accumulates to 15 min
        usageTracker.startSession("com.test.app")
        clock.advanceBoth(15 * 60 * 1000L)
        limitScheduler.trigger()

        assertEquals(1, launcher.launchHistory.size)
        assertEquals(LockReason.DAILY_LIMIT, launcher.launchHistory[0].reason)
    }

    // -------------------------------------------------------------------------
    // 7. Limit becomes exhausted while schedule active => exactly one LockSession
    // -------------------------------------------------------------------------
    @Test
    fun test07_limitExhausted_whileScheduleActive_exactlyOneLockSession() {
        val repo = FakeTargetRepository()
        repo.apps["com.test.app"] = LockedApp(
            "com.test.app",
            enabled = true,
            schedule = TimeSchedule(enabled = true, startHour = 9, startMinute = 0, endHour = 17, endMinute = 0),
            timeLimit = TimeLimit(enabled = true, dailyLimitMinutes = 10)
        )
        val clock = TestClock.at(2026, 9, 3, 10, 0, zoneId = testZone)
        val usageTracker = UsageTracker(clock = clock, zoneId = testZone)
        val policyEngine = PolicyEngine(repo, usageTracker, clock, testZone)
        val launcher = MockLockLauncher()

        // 10:00 is inside schedule: target already locked on entry
        launcher.launch("com.test.app", LockReason.SCHEDULE_DEADLINE, clock.elapsedRealtimeMillis())
        assertEquals(1, launcher.launchHistory.size)

        // Daily limit deadline fires later
        val limitWatcher = UsageLimitWatcherImpl(repo, usageTracker, policyEngine, TestLimitScheduler()) { pkg ->
            launcher.launch(pkg, LockReason.DAILY_LIMIT, clock.elapsedRealtimeMillis())
        }
        usageTracker.startSession("com.test.app")
        clock.advanceBoth(10 * 60 * 1000L)
        usageTracker.stopSession("com.test.app")

        limitWatcher.onForegroundChanged("com.test.app")
        // Rule B prevents second launch
        assertEquals(1, launcher.launchHistory.size)
    }

    // -------------------------------------------------------------------------
    // 8. Simultaneous schedule/limit trigger => exactly one launchLockSession()
    // -------------------------------------------------------------------------
    @Test
    fun test08_simultaneousScheduleAndLimitTrigger_exactlyOneLaunchLockSession() {
        val repo = FakeTargetRepository()
        repo.apps["com.test.app"] = LockedApp(
            "com.test.app",
            enabled = true,
            schedule = TimeSchedule(enabled = true, startHour = 10, startMinute = 0, endHour = 17, endMinute = 0),
            timeLimit = TimeLimit(enabled = true, dailyLimitMinutes = 10)
        )
        val clock = TestClock.at(2026, 9, 3, 10, 0, zoneId = testZone)
        val usageTracker = UsageTracker(clock = clock, zoneId = testZone)
        val policyEngine = PolicyEngine(repo, usageTracker, clock, testZone)
        val launcher = MockLockLauncher()

        // Usage reached 10m exactly at 10:00:00.000 when schedule starts
        usageTracker.startSession("com.test.app")
        clock.advanceBoth(10 * 60 * 1000L)
        usageTracker.stopSession("com.test.app")

        val scheduleWatcher = ScheduleWatcherImpl(repo, policyEngine, clock, testZone, TestScheduleScheduler()) { pkg ->
            launcher.launch(pkg, LockReason.SCHEDULE_DEADLINE, clock.elapsedRealtimeMillis())
        }
        val limitWatcher = UsageLimitWatcherImpl(repo, usageTracker, policyEngine, TestLimitScheduler()) { pkg ->
            launcher.launch(pkg, LockReason.DAILY_LIMIT, clock.elapsedRealtimeMillis())
        }

        // Both watchers trigger simultaneously
        scheduleWatcher.onForegroundChanged("com.test.app")
        limitWatcher.onForegroundChanged("com.test.app")

        assertEquals(1, launcher.launchHistory.size)
        assertEquals(LockReason.SCHEDULE_DEADLINE, launcher.launchHistory[0].reason)
    }

    // -------------------------------------------------------------------------
    // 9. Schedule update active -> inactive while limit exhausted => remain LOCKED
    // -------------------------------------------------------------------------
    @Test
    fun test09_scheduleUpdate_activeToInactive_whileLimitExhausted_remainsLocked() {
        val repo = FakeTargetRepository()
        repo.apps["com.test.app"] = LockedApp(
            "com.test.app",
            enabled = true,
            schedule = TimeSchedule(enabled = true, startHour = 9, startMinute = 0, endHour = 17, endMinute = 0),
            timeLimit = TimeLimit(enabled = true, dailyLimitMinutes = 10)
        )
        val clock = TestClock.at(2026, 9, 3, 10, 0, zoneId = testZone)
        val usageTracker = UsageTracker(clock = clock, zoneId = testZone)
        val policyEngine = PolicyEngine(repo, usageTracker, clock, testZone)

        // Exhaust limit
        usageTracker.startSession("com.test.app")
        clock.advanceBoth(10 * 60 * 1000L)
        usageTracker.stopSession("com.test.app")

        assertEquals(PolicyDecision.LOCK, policyEngine.evaluate("com.test.app"))

        // Update schedule to inactive (e.g. 18:00..22:00)
        repo.apps["com.test.app"] = repo.apps["com.test.app"]!!.copy(
            schedule = TimeSchedule(enabled = true, startHour = 18, startMinute = 0, endHour = 22, endMinute = 0)
        )

        // PolicyEngine STILL evaluates LOCK because limit remains exhausted
        assertEquals(PolicyDecision.LOCK, policyEngine.evaluate("com.test.app"))
    }

    // -------------------------------------------------------------------------
    // 10. Limit update exhausted -> available while schedule active => remain LOCKED
    // -------------------------------------------------------------------------
    @Test
    fun test10_limitUpdate_exhaustedToAvailable_whileScheduleActive_remainsLocked() {
        val repo = FakeTargetRepository()
        repo.apps["com.test.app"] = LockedApp(
            "com.test.app",
            enabled = true,
            schedule = TimeSchedule(enabled = true, startHour = 9, startMinute = 0, endHour = 17, endMinute = 0),
            timeLimit = TimeLimit(enabled = true, dailyLimitMinutes = 10)
        )
        val clock = TestClock.at(2026, 9, 3, 10, 0, zoneId = testZone)
        val usageTracker = UsageTracker(clock = clock, zoneId = testZone)
        val policyEngine = PolicyEngine(repo, usageTracker, clock, testZone)

        // Exhaust limit
        usageTracker.startSession("com.test.app")
        clock.advanceBoth(10 * 60 * 1000L)
        usageTracker.stopSession("com.test.app")

        assertEquals(PolicyDecision.LOCK, policyEngine.evaluate("com.test.app"))

        // Update limit to 60 min (usage is 10 min, so limit is now available)
        repo.apps["com.test.app"] = repo.apps["com.test.app"]!!.copy(
            timeLimit = TimeLimit(enabled = true, dailyLimitMinutes = 60)
        )

        // PolicyEngine STILL evaluates LOCK because schedule is active
        assertEquals(PolicyDecision.LOCK, policyEngine.evaluate("com.test.app"))
    }

    // -------------------------------------------------------------------------
    // 11. Foreground switch during race cancels both watchers
    // -------------------------------------------------------------------------
    @Test
    fun test11_foregroundSwitchDuringRace_cancelsBothWatchers() {
        val repo = FakeTargetRepository()
        repo.apps["com.test.app"] = LockedApp(
            "com.test.app",
            enabled = true,
            schedule = TimeSchedule(enabled = true, startHour = 11, startMinute = 0, endHour = 17, endMinute = 0),
            timeLimit = TimeLimit(enabled = true, dailyLimitMinutes = 30)
        )
        val clock = TestClock.at(2026, 9, 3, 10, 0, zoneId = testZone)
        val usageTracker = UsageTracker(clock = clock, zoneId = testZone)
        val policyEngine = PolicyEngine(repo, usageTracker, clock, testZone)
        val scheduleScheduler = TestScheduleScheduler()
        val limitScheduler = TestLimitScheduler()
        var lockCount = 0

        val scheduleWatcher = ScheduleWatcherImpl(repo, policyEngine, clock, testZone, scheduleScheduler) { lockCount++ }
        val limitWatcher = UsageLimitWatcherImpl(repo, usageTracker, policyEngine, limitScheduler) { lockCount++ }

        scheduleWatcher.onForegroundChanged("com.test.app")
        limitWatcher.onForegroundChanged("com.test.app")
        assertNotNull(scheduleScheduler.scheduledDelayMillis)
        assertNotNull(limitScheduler.scheduledDelayMillis)

        // User switches to Home (non-target)
        scheduleWatcher.onForegroundChanged(null)
        limitWatcher.onForegroundChanged(null)

        assertNull(scheduleScheduler.scheduledDelayMillis)
        assertNull(limitScheduler.scheduledDelayMillis)
        assertNull(scheduleWatcher.getActivePackage())
        assertNull(limitWatcher.getActivePackage())

        // In-flight callbacks ignored
        scheduleScheduler.trigger()
        limitScheduler.trigger()
        assertEquals(0, lockCount)
    }

    // -------------------------------------------------------------------------
    // 12. Screen OFF during race pauses both watchers
    // -------------------------------------------------------------------------
    @Test
    fun test12_screenOffDuringRace_pausesBothWatchers() {
        val repo = FakeTargetRepository()
        repo.apps["com.test.app"] = LockedApp(
            "com.test.app",
            enabled = true,
            schedule = TimeSchedule(enabled = true, startHour = 11, startMinute = 0, endHour = 17, endMinute = 0),
            timeLimit = TimeLimit(enabled = true, dailyLimitMinutes = 30)
        )
        val clock = TestClock.at(2026, 9, 3, 10, 0, zoneId = testZone)
        val usageTracker = UsageTracker(clock = clock, zoneId = testZone)
        val policyEngine = PolicyEngine(repo, usageTracker, clock, testZone)
        val scheduleScheduler = TestScheduleScheduler()
        val limitScheduler = TestLimitScheduler()

        val scheduleWatcher = ScheduleWatcherImpl(repo, policyEngine, clock, testZone, scheduleScheduler) {}
        val limitWatcher = UsageLimitWatcherImpl(repo, usageTracker, policyEngine, limitScheduler) {}

        scheduleWatcher.onForegroundChanged("com.test.app")
        limitWatcher.onForegroundChanged("com.test.app")

        // Screen OFF: AppDetectorAccessibilityService calls onForegroundChanged(null) on both
        usageTracker.onScreenOff()
        scheduleWatcher.onForegroundChanged(null)
        limitWatcher.onForegroundChanged(null)

        assertNull(scheduleScheduler.scheduledDelayMillis)
        assertNull(limitScheduler.scheduledDelayMillis)
        assertNull(usageTracker.getActiveUsagePackage())
    }

    // -------------------------------------------------------------------------
    // 13. Service stop during race stops both watchers
    // -------------------------------------------------------------------------
    @Test
    fun test13_serviceStopDuringRace_stopsBothWatchers() {
        val repo = FakeTargetRepository()
        repo.apps["com.test.app"] = LockedApp(
            "com.test.app",
            enabled = true,
            schedule = TimeSchedule(enabled = true, startHour = 11, startMinute = 0, endHour = 17, endMinute = 0),
            timeLimit = TimeLimit(enabled = true, dailyLimitMinutes = 30)
        )
        val clock = TestClock.at(2026, 9, 3, 10, 0, zoneId = testZone)
        val usageTracker = UsageTracker(clock = clock, zoneId = testZone)
        val policyEngine = PolicyEngine(repo, usageTracker, clock, testZone)
        val scheduleScheduler = TestScheduleScheduler()
        val limitScheduler = TestLimitScheduler()

        val scheduleWatcher = ScheduleWatcherImpl(repo, policyEngine, clock, testZone, scheduleScheduler) {}
        val limitWatcher = UsageLimitWatcherImpl(repo, usageTracker, policyEngine, limitScheduler) {}

        scheduleWatcher.onForegroundChanged("com.test.app")
        limitWatcher.onForegroundChanged("com.test.app")

        scheduleWatcher.stop()
        limitWatcher.stop()

        assertFalse(scheduleWatcher.isWatcherRunning())
        assertNull(scheduleScheduler.scheduledDelayMillis)
        assertNull(limitScheduler.scheduledDelayMillis)
    }

    // -------------------------------------------------------------------------
    // 14. Stale ScheduleWatcher callback ignored
    // -------------------------------------------------------------------------
    @Test
    fun test14_staleScheduleWatcherCallback_ignored() {
        val repo = FakeTargetRepository()
        repo.apps["com.test.app"] = LockedApp(
            "com.test.app",
            enabled = true,
            schedule = TimeSchedule(enabled = true, startHour = 11, startMinute = 0, endHour = 17, endMinute = 0)
        )
        val clock = TestClock.at(2026, 9, 3, 10, 0, zoneId = testZone)
        val usageTracker = UsageTracker(clock = clock, zoneId = testZone)
        val policyEngine = PolicyEngine(repo, usageTracker, clock, testZone)
        val scheduleScheduler = TestScheduleScheduler()
        var lockCount = 0

        val scheduleWatcher = ScheduleWatcherImpl(repo, policyEngine, clock, testZone, scheduleScheduler) { lockCount++ }
        scheduleWatcher.onForegroundChanged("com.test.app")

        val capturedAction = scheduleScheduler.scheduledRunnable
        assertNotNull(capturedAction)

        // Invalidate generation by changing foreground
        scheduleWatcher.onForegroundChanged(null)

        // Advance time into active schedule and trigger old callback
        clock.advanceBoth(2 * 60 * 60 * 1000L) // 12:00
        capturedAction?.run()

        assertEquals(0, lockCount)
    }

    // -------------------------------------------------------------------------
    // 15. Stale UsageLimitWatcher callback ignored
    // -------------------------------------------------------------------------
    @Test
    fun test15_staleUsageLimitWatcherCallback_ignored() {
        val repo = FakeTargetRepository()
        repo.apps["com.test.app"] = LockedApp(
            "com.test.app",
            enabled = true,
            timeLimit = TimeLimit(enabled = true, dailyLimitMinutes = 10)
        )
        val clock = TestClock.at(2026, 9, 3, 10, 0, zoneId = testZone)
        val usageTracker = UsageTracker(clock = clock, zoneId = testZone)
        val policyEngine = PolicyEngine(repo, usageTracker, clock, testZone)
        val limitScheduler = TestLimitScheduler()
        var lockCount = 0

        val limitWatcher = UsageLimitWatcherImpl(repo, usageTracker, policyEngine, limitScheduler) { lockCount++ }
        limitWatcher.onForegroundChanged("com.test.app")

        val capturedRunnable = limitScheduler.scheduledRunnable
        assertNotNull(capturedRunnable)

        // Invalidate generation
        limitWatcher.onForegroundChanged(null)

        // Advance usage and trigger old runnable
        usageTracker.startSession("com.test.app")
        clock.advanceBoth(15 * 60 * 1000L)
        usageTracker.stopSession("com.test.app")
        capturedRunnable?.run()

        assertEquals(0, lockCount)
    }

    // -------------------------------------------------------------------------
    // 16. Duplicate launch protection
    // -------------------------------------------------------------------------
    @Test
    fun test16_duplicateLaunchProtection() {
        val launcher = MockLockLauncher()

        // 1st launch succeeds
        val res1 = launcher.launch("com.test.app", LockReason.SCHEDULE_DEADLINE, 1000L)
        assertTrue(res1)
        assertEquals(1, launcher.launchHistory.size)

        // 2nd rapid launch for same session is rejected by Rule B
        val res2 = launcher.launch("com.test.app", LockReason.DAILY_LIMIT, 1010L)
        assertFalse(res2)
        assertEquals(1, launcher.launchHistory.size)

        // 3rd launch when LockScreen is visible is rejected by Rule A
        launcher.onLockScreenResumed()
        val res3 = launcher.launch("com.test.app", LockReason.ACCESSIBILITY_EVENT, 2000L)
        assertFalse(res3)
        assertEquals(1, launcher.launchHistory.size)
    }

    // -------------------------------------------------------------------------
    // 17. LockReason ownership
    // -------------------------------------------------------------------------
    @Test
    fun test17_lockReasonOwnership_scheduleCannotEmitDailyLimit_andViceVersa() {
        val repo = FakeTargetRepository()
        repo.apps["com.test.app"] = LockedApp(
            "com.test.app",
            enabled = true,
            schedule = TimeSchedule(enabled = true, startHour = 9, startMinute = 0, endHour = 17, endMinute = 0),
            timeLimit = TimeLimit(enabled = true, dailyLimitMinutes = 60)
        )
        val clock = TestClock.at(2026, 9, 3, 10, 0, zoneId = testZone)
        val usageTracker = UsageTracker(clock = clock, zoneId = testZone)
        val policyEngine = PolicyEngine(repo, usageTracker, clock, testZone)
        val limitScheduler = TestLimitScheduler()
        var limitLockEmitted = false

        val limitWatcher = UsageLimitWatcherImpl(repo, usageTracker, policyEngine, limitScheduler) {
            limitLockEmitted = true
        }

        // Usage is 0/60m (not exhausted). Schedule is active (10:00).
        // UsageLimitWatcher must NOT emit DAILY_LIMIT even though PolicyEngine evaluates LOCK!
        limitWatcher.onForegroundChanged("com.test.app")
        limitScheduler.trigger()

        assertFalse("UsageLimitWatcher must preserve ownership and NOT emit DAILY_LIMIT when limit is not exhausted", limitLockEmitted)
    }

    // -------------------------------------------------------------------------
    // 18. Target disabled => always ALLOW
    // -------------------------------------------------------------------------
    @Test
    fun test18_targetDisabled_alwaysAllow() {
        val repo = FakeTargetRepository()
        repo.apps["com.test.app"] = LockedApp(
            "com.test.app",
            enabled = false, // Disabled
            schedule = TimeSchedule(enabled = true, startHour = 9, startMinute = 0, endHour = 17, endMinute = 0),
            timeLimit = TimeLimit(enabled = true, dailyLimitMinutes = 10)
        )
        val clock = TestClock.at(2026, 9, 3, 10, 0, zoneId = testZone)
        val usageTracker = UsageTracker(clock = clock, zoneId = testZone)
        val policyEngine = PolicyEngine(repo, usageTracker, clock, testZone)

        // Even with active schedule and 100m usage, disabled target is ALWAYS ALLOW
        usageTracker.startSession("com.test.app")
        clock.advanceBoth(100 * 60 * 1000L)
        usageTracker.stopSession("com.test.app")

        assertEquals(PolicyDecision.ALLOW, policyEngine.evaluate("com.test.app"))
    }

    // -------------------------------------------------------------------------
    // 19. Schedule disabled => only limit evaluated
    // -------------------------------------------------------------------------
    @Test
    fun test19_scheduleDisabled_onlyLimitEvaluated() {
        val repo = FakeTargetRepository()
        repo.apps["com.test.app"] = LockedApp(
            "com.test.app",
            enabled = true,
            schedule = TimeSchedule(enabled = false, startHour = 9, startMinute = 0, endHour = 17, endMinute = 0),
            timeLimit = TimeLimit(enabled = true, dailyLimitMinutes = 20)
        )
        val clock = TestClock.at(2026, 9, 3, 10, 0, zoneId = testZone)
        val usageTracker = UsageTracker(clock = clock, zoneId = testZone)
        val policyEngine = PolicyEngine(repo, usageTracker, clock, testZone)

        // 10:00 is within schedule hours, but schedule is disabled => ALLOW
        assertEquals(PolicyDecision.ALLOW, policyEngine.evaluate("com.test.app"))

        // Exceed daily limit => LOCK
        usageTracker.startSession("com.test.app")
        clock.advanceBoth(20 * 60 * 1000L)
        usageTracker.stopSession("com.test.app")

        assertEquals(PolicyDecision.LOCK, policyEngine.evaluate("com.test.app"))
    }

    // -------------------------------------------------------------------------
    // 20. Daily limit disabled => only schedule evaluated
    // -------------------------------------------------------------------------
    @Test
    fun test20_dailyLimitDisabled_onlyScheduleEvaluated() {
        val repo = FakeTargetRepository()
        repo.apps["com.test.app"] = LockedApp(
            "com.test.app",
            enabled = true,
            schedule = TimeSchedule(enabled = true, startHour = 18, startMinute = 0, endHour = 22, endMinute = 0),
            timeLimit = TimeLimit(enabled = false, dailyLimitMinutes = 5) // Disabled limit
        )
        val clock = TestClock.at(2026, 9, 3, 10, 0, zoneId = testZone)
        val usageTracker = UsageTracker(clock = clock, zoneId = testZone)
        val policyEngine = PolicyEngine(repo, usageTracker, clock, testZone)

        // 10:00 outside schedule, 50m usage tracked -> limit is disabled, so ALLOW
        usageTracker.startSession("com.test.app")
        clock.advanceBoth(50 * 60 * 1000L)
        usageTracker.stopSession("com.test.app")

        assertEquals(PolicyDecision.ALLOW, policyEngine.evaluate("com.test.app"))

        // Advance into schedule (19:00) -> LOCK
        clock.advanceBoth(9 * 60 * 60 * 1000L)
        assertEquals(PolicyDecision.LOCK, policyEngine.evaluate("com.test.app"))
    }
}
