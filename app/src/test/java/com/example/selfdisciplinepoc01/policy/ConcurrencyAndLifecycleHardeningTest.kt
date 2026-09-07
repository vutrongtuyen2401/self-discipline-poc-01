package com.example.selfdisciplinepoc01.policy

import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import com.example.selfdisciplinepoc01.LockReason
import com.example.selfdisciplinepoc01.overlay.BlockingShieldOverlay
import com.example.selfdisciplinepoc01.target.model.LockedApp
import com.example.selfdisciplinepoc01.target.model.TimeLimit
import com.example.selfdisciplinepoc01.target.model.TimeSchedule
import com.example.selfdisciplinepoc01.target.repository.TargetRepository
import com.example.selfdisciplinepoc01.target.repository.TargetRepositoryImpl
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
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * PHASE 11-A: PRODUCTION HARDENING — CONCURRENCY & LIFECYCLE HARDENING TEST SUITE
 *
 * Verifies lifecycle, concurrency, cleanup, resource ownership, and process/service restart behavior:
 * - 15-scenario Concurrency / Race Matrix
 * - Watcher ownership & idempotency
 * - BlockingShieldOverlay resource safety & balanced view tree lifecycle
 * - Repository resilience (non-blocking in-memory reads, safe fallbacks)
 * - UsageTracker resource safety & clean segment boundaries
 *
 * Invariant: EXACTLY ONE valid lock entry may win. All later/stale contenders must be harmless.
 */
class ConcurrencyAndLifecycleHardeningTest {

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

    private class FakeWindowManager : WindowManager {
        val addedViews = mutableListOf<View>()
        var addViewCallCount = 0
        var removeViewCallCount = 0
        var throwOnAddView: Throwable? = null
        var throwOnRemoveView: Throwable? = null

        override fun getDefaultDisplay() = throw UnsupportedOperationException()
        override fun removeViewImmediate(view: View?) = removeView(view)

        override fun addView(view: View?, params: ViewGroup.LayoutParams?) {
            addViewCallCount++
            throwOnAddView?.let { throw it }
            if (view != null) addedViews.add(view)
        }

        override fun updateViewLayout(view: View?, params: ViewGroup.LayoutParams?) {}

        override fun removeView(view: View?) {
            removeViewCallCount++
            throwOnRemoveView?.let { throw it }
            if (view != null) {
                if (!addedViews.remove(view)) {
                    throw IllegalArgumentException("View not attached to window manager")
                }
            }
        }
    }

    // =========================================================================
    // 1. Accessibility event + Schedule deadline
    // =========================================================================
    @Test
    fun testRace01_accessibilityEventAndScheduleDeadline() {
        val clock = TestClock.at(2026, 9, 4, 13, 59, 59, zoneId = ZoneId.of("UTC"))
        val repo = FakeTargetRepository()
        val pkg = "com.test.race1"
        repo.apps[pkg] = LockedApp(
            packageName = pkg,
            enabled = true,
            schedule = TimeSchedule(startHour = 14, startMinute = 0, endHour = 16, endMinute = 0, enabled = true)
        )
        val usageTracker = UsageTracker(clock = clock, zoneId = ZoneId.of("UTC"))
        val policyEngine = PolicyEngine(repo, usageTracker, clock = clock, zoneId = ZoneId.of("UTC"))
        val scheduler = TestScheduleScheduler()
        val launcher = MockLockLauncher()

        val watcher = ScheduleWatcherImpl(repo, policyEngine, clock = clock, zoneId = ZoneId.of("UTC"), scheduler = scheduler) { p ->
            launcher.launchLockSession(p, LockReason.SCHEDULE_DEADLINE, clock.elapsedRealtimeMillis())
        }
        watcher.onForegroundChanged(pkg)

        // Advance to 14:00 (schedule active)
        clock.advanceBoth(1000L)

        // Accessibility event arrives first
        val eventDecision = policyEngine.evaluate(pkg)
        assertEquals(PolicyDecision.LOCK, eventDecision)
        val launched = launcher.launchLockSession(pkg, LockReason.ACCESSIBILITY_EVENT, clock.elapsedRealtimeMillis())
        assertTrue(launched)

        // Schedule deadline fires immediately after
        scheduler.trigger()

        // Invariant: Exactly one lock session launched, Reason is ACCESSIBILITY_EVENT
        assertEquals(1, launcher.launchHistory.size)
        assertEquals(LockReason.ACCESSIBILITY_EVENT, launcher.launchHistory[0].reason)
    }

    // =========================================================================
    // 2. Accessibility event + Daily Limit deadline
    // =========================================================================
    @Test
    fun testRace02_accessibilityEventAndDailyLimitDeadline() {
        val clock = TestClock.at(2026, 9, 4, 10, 0, zoneId = ZoneId.of("UTC"))
        val repo = FakeTargetRepository()
        val pkg = "com.test.race2"
        repo.apps[pkg] = LockedApp(
            packageName = pkg,
            enabled = true,
            timeLimit = TimeLimit(dailyLimitMinutes = 10, enabled = true)
        )
        val usageTracker = UsageTracker(clock = clock, zoneId = ZoneId.of("UTC"))
        val policyEngine = PolicyEngine(repo, usageTracker, clock = clock, zoneId = ZoneId.of("UTC"))
        val scheduler = TestLimitScheduler()
        val launcher = MockLockLauncher()

        val watcher = UsageLimitWatcherImpl(repo, usageTracker, policyEngine, scheduler) { p ->
            launcher.launchLockSession(p, LockReason.DAILY_LIMIT, clock.elapsedRealtimeMillis())
        }

        // Consume 10 minutes
        usageTracker.startSession(pkg)
        clock.advanceElapsed(600_000L)
        usageTracker.stopSession(pkg)

        watcher.onForegroundChanged(pkg)

        // Accessibility event detects limit reached
        val decision = policyEngine.evaluate(pkg)
        assertEquals(PolicyDecision.LOCK, decision)
        launcher.launchLockSession(pkg, LockReason.ACCESSIBILITY_EVENT, clock.elapsedRealtimeMillis())

        // Delayed limit watcher trigger executes
        scheduler.trigger()

        // Invariant: Exactly one lock session, Reason is ACCESSIBILITY_EVENT
        assertEquals(1, launcher.launchHistory.size)
        assertEquals(LockReason.ACCESSIBILITY_EVENT, launcher.launchHistory[0].reason)
    }

    // =========================================================================
    // 3. Schedule deadline + Daily Limit deadline (Simultaneous)
    // =========================================================================
    @Test
    fun testRace03_scheduleDeadlineAndDailyLimitDeadline() {
        val clock = TestClock.at(2026, 9, 4, 13, 50, zoneId = ZoneId.of("UTC"))
        val repo = FakeTargetRepository()
        val pkg = "com.test.race3"
        repo.apps[pkg] = LockedApp(
            packageName = pkg,
            enabled = true,
            schedule = TimeSchedule(startHour = 14, startMinute = 0, endHour = 16, endMinute = 0, enabled = true),
            timeLimit = TimeLimit(dailyLimitMinutes = 10, enabled = true)
        )
        val usageTracker = UsageTracker(clock = clock, zoneId = ZoneId.of("UTC"))
        val policyEngine = PolicyEngine(repo, usageTracker, clock = clock, zoneId = ZoneId.of("UTC"))
        val schedScheduler = TestScheduleScheduler()
        val limitScheduler = TestLimitScheduler()
        val launcher = MockLockLauncher()

        val sWatcher = ScheduleWatcherImpl(repo, policyEngine, clock = clock, zoneId = ZoneId.of("UTC"), scheduler = schedScheduler) { p ->
            launcher.launchLockSession(p, LockReason.SCHEDULE_DEADLINE, clock.elapsedRealtimeMillis())
        }
        val lWatcher = UsageLimitWatcherImpl(repo, usageTracker, policyEngine, limitScheduler) { p ->
            launcher.launchLockSession(p, LockReason.DAILY_LIMIT, clock.elapsedRealtimeMillis())
        }

        // Pre-consume 9 minutes
        usageTracker.startSession(pkg)
        clock.advanceBoth(540_000L) // 13:59
        usageTracker.stopSession(pkg)

        sWatcher.onForegroundChanged(pkg)
        lWatcher.onForegroundChanged(pkg)

        // 1 minute passes: 14:00 arrives (Schedule starts) and limit (10m) reached
        usageTracker.startSession(pkg)
        clock.advanceBoth(60_000L) // 14:00
        usageTracker.stopSession(pkg)

        // First trigger: Limit
        limitScheduler.trigger()
        assertEquals(1, launcher.launchHistory.size)
        assertEquals(LockReason.DAILY_LIMIT, launcher.launchHistory[0].reason)

        // Second trigger: Schedule
        schedScheduler.trigger()

        // Invariant: Exactly one lock session, Rule B prevents duplicate, DAILY_LIMIT preserved
        assertEquals(1, launcher.launchHistory.size)
        assertEquals(LockReason.DAILY_LIMIT, launcher.launchHistory[0].reason)
    }

    // =========================================================================
    // 4. Schedule deadline + foreground switch
    // =========================================================================
    @Test
    fun testRace04_scheduleDeadlineAndForegroundSwitch() {
        val clock = TestClock.at(2026, 9, 4, 13, 50, zoneId = ZoneId.of("UTC"))
        val repo = FakeTargetRepository()
        val pkgTarget = "com.test.race4"
        repo.apps[pkgTarget] = LockedApp(
            packageName = pkgTarget,
            enabled = true,
            schedule = TimeSchedule(startHour = 14, startMinute = 0, endHour = 16, endMinute = 0, enabled = true)
        )
        val usageTracker = UsageTracker(clock = clock, zoneId = ZoneId.of("UTC"))
        val policyEngine = PolicyEngine(repo, usageTracker, clock = clock, zoneId = ZoneId.of("UTC"))
        val scheduler = TestScheduleScheduler()
        val launcher = MockLockLauncher()

        val watcher = ScheduleWatcherImpl(repo, policyEngine, clock = clock, zoneId = ZoneId.of("UTC"), scheduler = scheduler) { p ->
            launcher.launchLockSession(p, LockReason.SCHEDULE_DEADLINE, clock.elapsedRealtimeMillis())
        }
        watcher.onForegroundChanged(pkgTarget)
        val pendingCallback = scheduler.scheduledRunnable

        // User switches to Home screen (non-target) at 13:55
        clock.advanceBoth(300_000L)
        watcher.onForegroundChanged(null)

        // Pending callback fires after switch
        clock.advanceBoth(300_000L) // 14:00
        pendingCallback?.run()

        // Invariant: Home screen never locked, 0 launches
        assertEquals(0, launcher.launchHistory.size)
    }

    // =========================================================================
    // 5. Daily Limit deadline + foreground switch
    // =========================================================================
    @Test
    fun testRace05_dailyLimitDeadlineAndForegroundSwitch() {
        val clock = TestClock.at(2026, 9, 4, 10, 0, zoneId = ZoneId.of("UTC"))
        val repo = FakeTargetRepository()
        val pkg = "com.test.race5"
        repo.apps[pkg] = LockedApp(
            packageName = pkg,
            enabled = true,
            timeLimit = TimeLimit(dailyLimitMinutes = 10, enabled = true)
        )
        val usageTracker = UsageTracker(clock = clock, zoneId = ZoneId.of("UTC"))
        val policyEngine = PolicyEngine(repo, usageTracker, clock = clock, zoneId = ZoneId.of("UTC"))
        val scheduler = TestLimitScheduler()
        val launcher = MockLockLauncher()

        val watcher = UsageLimitWatcherImpl(repo, usageTracker, policyEngine, scheduler) { p ->
            launcher.launchLockSession(p, LockReason.DAILY_LIMIT, clock.elapsedRealtimeMillis())
        }
        watcher.onForegroundChanged(pkg)
        val pendingCallback = scheduler.scheduledRunnable

        // User switches to Launcher before limit finishes
        watcher.onForegroundChanged(null)

        // Stale callback runs
        pendingCallback?.run()

        // Invariant: No lock emitted
        assertEquals(0, launcher.launchHistory.size)
    }

    // =========================================================================
    // 6. deadline + screen OFF
    // =========================================================================
    @Test
    fun testRace06_deadlineAndScreenOff() {
        val clock = TestClock.at(2026, 9, 4, 13, 50, zoneId = ZoneId.of("UTC"))
        val repo = FakeTargetRepository()
        val pkg = "com.test.race6"
        repo.apps[pkg] = LockedApp(
            packageName = pkg,
            enabled = true,
            schedule = TimeSchedule(startHour = 14, startMinute = 0, endHour = 16, endMinute = 0, enabled = true)
        )
        val usageTracker = UsageTracker(clock = clock, zoneId = ZoneId.of("UTC"))
        val policyEngine = PolicyEngine(repo, usageTracker, clock = clock, zoneId = ZoneId.of("UTC"))
        val scheduler = TestScheduleScheduler()
        val launcher = MockLockLauncher()

        val watcher = ScheduleWatcherImpl(repo, policyEngine, clock = clock, zoneId = ZoneId.of("UTC"), scheduler = scheduler) { p ->
            launcher.launchLockSession(p, LockReason.SCHEDULE_DEADLINE, clock.elapsedRealtimeMillis())
        }
        watcher.onForegroundChanged(pkg)

        // Screen turned off: AppDetector clears watcher and pauses usage
        usageTracker.onScreenOff()
        watcher.onForegroundChanged(null)

        // Clock advances across boundary while screen is OFF
        clock.advanceBoth(900_000L) // 14:05

        // Invariant: No lock launched while screen is OFF
        assertEquals(0, launcher.launchHistory.size)
    }

    // =========================================================================
    // 7. deadline + service destroy
    // =========================================================================
    @Test
    fun testRace07_deadlineAndServiceDestroy() {
        val clock = TestClock.at(2026, 9, 4, 13, 50, zoneId = ZoneId.of("UTC"))
        val repo = FakeTargetRepository()
        val pkg = "com.test.race7"
        repo.apps[pkg] = LockedApp(
            packageName = pkg,
            enabled = true,
            schedule = TimeSchedule(startHour = 14, startMinute = 0, endHour = 16, endMinute = 0, enabled = true)
        )
        val usageTracker = UsageTracker(clock = clock, zoneId = ZoneId.of("UTC"))
        val policyEngine = PolicyEngine(repo, usageTracker, clock = clock, zoneId = ZoneId.of("UTC"))
        val scheduler = TestScheduleScheduler()
        val launcher = MockLockLauncher()

        val watcher = ScheduleWatcherImpl(repo, policyEngine, clock = clock, zoneId = ZoneId.of("UTC"), scheduler = scheduler) { p ->
            launcher.launchLockSession(p, LockReason.SCHEDULE_DEADLINE, clock.elapsedRealtimeMillis())
        }
        watcher.onForegroundChanged(pkg)
        val pendingRunnable = scheduler.scheduledRunnable

        // Service destroyed
        watcher.stop()
        assertFalse(watcher.isWatcherRunning())

        // Callback executes after service destruction
        clock.advanceBoth(600_000L)
        pendingRunnable?.run()

        // Invariant: Ignored safely, 0 launches
        assertEquals(0, launcher.launchHistory.size)
    }

    // =========================================================================
    // 8. deadline + policy update
    // =========================================================================
    @Test
    fun testRace08_deadlineAndPolicyUpdate() {
        val clock = TestClock.at(2026, 9, 4, 13, 50, zoneId = ZoneId.of("UTC"))
        val repo = FakeTargetRepository()
        val pkg = "com.test.race8"
        repo.apps[pkg] = LockedApp(
            packageName = pkg,
            enabled = true,
            schedule = TimeSchedule(startHour = 14, startMinute = 0, endHour = 16, endMinute = 0, enabled = true)
        )
        val usageTracker = UsageTracker(clock = clock, zoneId = ZoneId.of("UTC"))
        val policyEngine = PolicyEngine(repo, usageTracker, clock = clock, zoneId = ZoneId.of("UTC"))
        val scheduler = TestScheduleScheduler()
        val launcher = MockLockLauncher()

        val watcher = ScheduleWatcherImpl(repo, policyEngine, clock = clock, zoneId = ZoneId.of("UTC"), scheduler = scheduler) { p ->
            launcher.launchLockSession(p, LockReason.SCHEDULE_DEADLINE, clock.elapsedRealtimeMillis())
        }
        watcher.onForegroundChanged(pkg)
        val oldCallback = scheduler.scheduledRunnable

        // Policy updated: schedule moved to 17:00 -> 19:00
        repo.apps[pkg] = repo.apps[pkg]!!.copy(
            schedule = TimeSchedule(startHour = 17, startMinute = 0, endHour = 19, endMinute = 0, enabled = true)
        )
        watcher.onPolicyUpdated(pkg)

        // Old callback runs at 14:00
        clock.advanceBoth(600_000L)
        oldCallback?.run()

        // Invariant: Old callback invalidated, no lock launched
        assertEquals(0, launcher.launchHistory.size)
    }

    // =========================================================================
    // 9. deadline + LockScreen already visible
    // =========================================================================
    @Test
    fun testRace09_deadlineAndLockScreenAlreadyVisible() {
        val clock = TestClock.at(2026, 9, 4, 14, 0, zoneId = ZoneId.of("UTC"))
        val repo = FakeTargetRepository()
        val pkg = "com.test.race9"
        val launcher = MockLockLauncher()
        launcher.isLockScreenVisible = true // LockScreen already visible

        val result = launcher.launchLockSession(pkg, LockReason.SCHEDULE_DEADLINE, clock.elapsedRealtimeMillis())

        // Invariant: Rule A blocks duplicate launch
        assertFalse(result)
        assertEquals(0, launcher.launchHistory.size)
    }

    // =========================================================================
    // 10. two callbacks targeting same package
    // =========================================================================
    @Test
    fun testRace10_twoCallbacksTargetingSamePackage() {
        val clock = TestClock.at(2026, 9, 4, 14, 0, zoneId = ZoneId.of("UTC"))
        val pkg = "com.test.race10"
        val launcher = MockLockLauncher()

        val launch1 = launcher.launchLockSession(pkg, LockReason.SCHEDULE_DEADLINE, clock.elapsedRealtimeMillis())
        assertTrue(launch1)

        // Second callback arrives 50ms later for the same package transition
        clock.advanceElapsed(50L)
        val launch2 = launcher.launchLockSession(pkg, LockReason.DAILY_LIMIT, clock.elapsedRealtimeMillis())

        // Invariant: Rule B / Rule C prevents duplicate
        assertFalse(launch2)
        assertEquals(1, launcher.launchHistory.size)
        assertEquals(LockReason.SCHEDULE_DEADLINE, launcher.launchHistory[0].reason)
    }

    // =========================================================================
    // 11. old callback targeting previous package
    // =========================================================================
    @Test
    fun testRace11_oldCallbackTargetingPreviousPackage() {
        val clock = TestClock.at(2026, 9, 4, 13, 50, zoneId = ZoneId.of("UTC"))
        val repo = FakeTargetRepository()
        val pkgA = "com.test.pkgA"
        val pkgB = "com.test.pkgB"
        repo.apps[pkgA] = LockedApp(pkgA, enabled = true, schedule = TimeSchedule(startHour = 14, startMinute = 0, endHour = 16, endMinute = 0, enabled = true))
        repo.apps[pkgB] = LockedApp(pkgB, enabled = true)
        val usageTracker = UsageTracker(clock = clock, zoneId = ZoneId.of("UTC"))
        val policyEngine = PolicyEngine(repo, usageTracker, clock = clock, zoneId = ZoneId.of("UTC"))
        val scheduler = TestScheduleScheduler()
        val launcher = MockLockLauncher()

        val watcher = ScheduleWatcherImpl(repo, policyEngine, clock = clock, zoneId = ZoneId.of("UTC"), scheduler = scheduler) { p ->
            launcher.launchLockSession(p, LockReason.SCHEDULE_DEADLINE, clock.elapsedRealtimeMillis())
        }
        watcher.onForegroundChanged(pkgA)
        val oldCallbackA = scheduler.scheduledRunnable

        // Switch to pkgB
        watcher.onForegroundChanged(pkgB)

        // Callback for pkgA triggers
        clock.advanceBoth(600_000L) // 14:00
        oldCallbackA?.run()

        // Invariant: Package mismatch guard drops pkgA callback, pkgB not locked
        assertEquals(0, launcher.launchHistory.size)
    }

    // =========================================================================
    // 12. callback after watcher stop
    // =========================================================================
    @Test
    fun testRace12_callbackAfterWatcherStop() {
        val clock = TestClock.at(2026, 9, 4, 13, 50, zoneId = ZoneId.of("UTC"))
        val repo = FakeTargetRepository()
        val pkg = "com.test.race12"
        repo.apps[pkg] = LockedApp(pkg, enabled = true, schedule = TimeSchedule(startHour = 14, startMinute = 0, endHour = 16, endMinute = 0, enabled = true))
        val usageTracker = UsageTracker(clock = clock, zoneId = ZoneId.of("UTC"))
        val policyEngine = PolicyEngine(repo, usageTracker, clock = clock, zoneId = ZoneId.of("UTC"))
        val scheduler = TestScheduleScheduler()
        val launcher = MockLockLauncher()

        val watcher = ScheduleWatcherImpl(repo, policyEngine, clock = clock, zoneId = ZoneId.of("UTC"), scheduler = scheduler) { p ->
            launcher.launchLockSession(p, LockReason.SCHEDULE_DEADLINE, clock.elapsedRealtimeMillis())
        }
        watcher.onForegroundChanged(pkg)
        val pending = scheduler.scheduledRunnable

        watcher.stop()
        pending?.run()

        assertEquals(0, launcher.launchHistory.size)
    }

    // =========================================================================
    // 13. callback after watcher restart
    // =========================================================================
    @Test
    fun testRace13_callbackAfterWatcherRestart() {
        val clock = TestClock.at(2026, 9, 4, 13, 50, zoneId = ZoneId.of("UTC"))
        val repo = FakeTargetRepository()
        val pkg = "com.test.race13"
        repo.apps[pkg] = LockedApp(pkg, enabled = true, schedule = TimeSchedule(startHour = 14, startMinute = 0, endHour = 16, endMinute = 0, enabled = true))
        val usageTracker = UsageTracker(clock = clock, zoneId = ZoneId.of("UTC"))
        val policyEngine = PolicyEngine(repo, usageTracker, clock = clock, zoneId = ZoneId.of("UTC"))
        val scheduler = TestScheduleScheduler()
        val launcher = MockLockLauncher()

        val watcher = ScheduleWatcherImpl(repo, policyEngine, clock = clock, zoneId = ZoneId.of("UTC"), scheduler = scheduler) { p ->
            launcher.launchLockSession(p, LockReason.SCHEDULE_DEADLINE, clock.elapsedRealtimeMillis())
        }
        watcher.onForegroundChanged(pkg)
        val generation1Callback = scheduler.scheduledRunnable

        // Watcher restarted (e.g. service reconnected)
        watcher.start()

        // Old callback from generation 1 fires
        clock.advanceBoth(600_000L) // 14:00
        generation1Callback?.run()

        // Invariant: Generation mismatch drops stale callback
        assertEquals(0, launcher.launchHistory.size)
    }

    // =========================================================================
    // 14. startActivity failure + shield cleanup
    // =========================================================================
    @Test
    fun testRace14_startActivityFailureAndShieldCleanup() {
        val fakeWm = FakeWindowManager()
        val shield = BlockingShieldOverlay(null, fakeWm)
        shield.viewFactory = { View(null) }

        // Show shield
        shield.show(100L, 200L, 1L, "com.test.failure")
        assertTrue(shield.isShown())
        assertEquals(1, fakeWm.addedViews.size)

        // Simulate startActivity exception -> cleanup called
        shield.hide("startActivity_exception")
        assertFalse(shield.isShown())
        assertEquals(0, fakeWm.addedViews.size)
    }

    // =========================================================================
    // 15. rapid service reconnect
    // =========================================================================
    @Test
    fun testRace15_rapidServiceReconnect() {
        val clock = TestClock.at(2026, 9, 4, 10, 0, zoneId = ZoneId.of("UTC"))
        val repo = FakeTargetRepository()
        val usageTracker = UsageTracker(clock = clock, zoneId = ZoneId.of("UTC"))
        val policyEngine = PolicyEngine(repo, usageTracker, clock = clock, zoneId = ZoneId.of("UTC"))
        val scheduler = TestScheduleScheduler()
        val launcher = MockLockLauncher()

        val watcher = ScheduleWatcherImpl(repo, policyEngine, clock = clock, zoneId = ZoneId.of("UTC"), scheduler = scheduler) { p ->
            launcher.launchLockSession(p, LockReason.SCHEDULE_DEADLINE, clock.elapsedRealtimeMillis())
        }

        // Rapid start / stop / start sequence
        for (i in 1..10) {
            watcher.start()
            watcher.stop()
        }
        watcher.start()

        // Invariant: Watcher is cleanly running, no dangling callbacks, 0 launches
        assertTrue(watcher.isWatcherRunning())
        assertNull(watcher.getActivePackage())
        assertEquals(0, launcher.launchHistory.size)
    }

    // =========================================================================
    // Watcher Ownership & Idempotency
    // =========================================================================
    @Test
    fun testWatcher_idempotentStartAndStop() {
        val clock = TestClock.at(2026, 9, 4, 10, 0, zoneId = ZoneId.of("UTC"))
        val repo = FakeTargetRepository()
        val usageTracker = UsageTracker(clock = clock, zoneId = ZoneId.of("UTC"))
        val policyEngine = PolicyEngine(repo, usageTracker, clock = clock, zoneId = ZoneId.of("UTC"))
        val scheduler = TestScheduleScheduler()

        val watcher = ScheduleWatcherImpl(repo, policyEngine, clock = clock, zoneId = ZoneId.of("UTC"), scheduler = scheduler) {}

        // Calling stop() repeatedly when already stopped is a safe no-op
        watcher.stop()
        watcher.stop()
        watcher.stop()
        assertFalse(watcher.isWatcherRunning())

        // Calling start() after stop() restores watcher cleanly
        watcher.start()
        assertTrue(watcher.isWatcherRunning())
        assertNull(watcher.getActivePackage())
    }

    // =========================================================================
    // BlockingShieldOverlay Resource Safety (Tolerates double remove & exceptions)
    // =========================================================================
    @Test
    fun testShieldOverlay_toleratesDoubleRemoveAndExceptions() {
        val fakeWm = FakeWindowManager()
        val shield = BlockingShieldOverlay(null, fakeWm)
        shield.viewFactory = { View(null) }

        // hide() when never shown -> no exception
        shield.hide("never_shown")
        assertFalse(shield.isShown())

        // show() then double hide()
        shield.show(1L, 2L, 1L, "com.test.pkg")
        assertTrue(shield.isShown())
        shield.hide("first_hide")
        assertFalse(shield.isShown())
        shield.hide("second_hide") // double hide
        assertFalse(shield.isShown())

        // cleanup() when already hidden -> no exception
        shield.cleanup()
        assertFalse(shield.isShown())
    }

    @Test
    fun testShieldOverlay_toleratesWindowManagerExceptions() {
        val fakeWm = FakeWindowManager()
        fakeWm.throwOnAddView = WindowManager.BadTokenException("Simulated BadTokenException")
        val shield = BlockingShieldOverlay(null, fakeWm)
        shield.viewFactory = { View(null) }

        // addView throws BadTokenException -> handled gracefully
        shield.show(1L, 2L, 1L, "com.test.pkg")
        assertFalse(shield.isShown())

        // removeView throws exception -> handled gracefully
        fakeWm.throwOnAddView = null
        fakeWm.throwOnRemoveView = RuntimeException("Simulated removeView crash")
        shield.show(1L, 2L, 1L, "com.test.pkg")
        assertTrue(shield.isShown())
        shield.hide("hide_with_crash")
        assertFalse(shield.isShown())
    }

    // =========================================================================
    // Repository Resilience: Non-blocking in-memory reads & fallback
    // =========================================================================
    @Test
    fun testRepository_safeFallbackOnCorruptedJson() {
        val corruptedJson = "{ invalid json string [["
        val result = TargetRepositoryImpl.deserialize(corruptedJson)
        // Must fallback safely to default apps without throwing exception
        assertNotNull(result)
        assertEquals(1, result.size)
        assertEquals(TargetRepositoryImpl.DEFAULT_TARGET_PACKAGE, result[0].packageName)
    }

    @Test
    fun testRepository_nullJsonReturnsDefaultApps() {
        val result = TargetRepositoryImpl.deserialize(null)
        assertNotNull(result)
        assertEquals(1, result.size)
        assertEquals(TargetRepositoryImpl.DEFAULT_TARGET_PACKAGE, result[0].packageName)
    }

    // =========================================================================
    // UsageTracker: Idempotent start/stop & Screen state
    // =========================================================================
    @Test
    fun testUsageTracker_idempotentStartAndSafeRepeatedStop() {
        val clock = TestClock.at(2026, 9, 4, 10, 0, zoneId = ZoneId.of("UTC"))
        clock.setElapsedRealtime(1000L)
        val tracker = UsageTracker(clock = clock, zoneId = ZoneId.of("UTC"))
        val pkg = "com.test.usage.idempotent"

        // Repeated startSession calls for same package are idempotent
        tracker.startSession(pkg)
        tracker.startSession(pkg)
        assertTrue(tracker.isSessionActive(pkg))

        // Advance elapsed
        clock.advanceElapsed(5000L)

        // Stop session
        tracker.stopSession(pkg)
        assertFalse(tracker.isSessionActive(pkg))
        assertEquals(5000L, tracker.getTodayUsage(pkg))

        // Repeated stopSession calls are safe no-ops
        tracker.stopSession(pkg)
        tracker.stopSession(null)
        assertEquals(5000L, tracker.getTodayUsage(pkg))
    }
}
