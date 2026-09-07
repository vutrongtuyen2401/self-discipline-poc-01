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
import com.example.selfdisciplinepoc01.time.TestClock
import com.example.selfdisciplinepoc01.usage.LimitScheduler
import com.example.selfdisciplinepoc01.usage.UsageLimitWatcherImpl
import com.example.selfdisciplinepoc01.usage.UsageTracker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap

/**
 * PHASE 11-A: Production Stress and Soak Test Suite.
 *
 * Deterministic stress tests executing >= 100 cycles per requirement:
 * 1. 100 rapid foreground transitions
 * 2. 100 watcher start/stop cycles
 * 3. 100 service lifecycle cycles
 * 4. 100 policy updates
 * 5. 100 stale callback attempts
 * 6. 100 shield add/remove cleanup cycles
 *
 * Zero Thread.sleep / zero polling used. Completely deterministic fake time/clock.
 */
class ProductionStressAndSoakTest {

    private class StressRepository : TargetRepository {
        val apps = ConcurrentHashMap<String, LockedApp>()

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

    private class StressScheduleScheduler : ScheduleScheduler {
        var scheduledRunnable: Runnable? = null
        var delayMillis: Long = -1L
        var scheduleCount = 0
        var cancelCount = 0

        override fun schedule(delayMillis: Long, action: Runnable) {
            scheduleCount++
            scheduledRunnable = action
            this.delayMillis = delayMillis
        }

        override fun cancel() {
            cancelCount++
            scheduledRunnable = null
            delayMillis = -1L
        }
    }

    private class StressLimitScheduler : LimitScheduler {
        var scheduledRunnable: Runnable? = null
        var delayMillis: Long = -1L
        var scheduleCount = 0
        var cancelCount = 0

        override fun schedule(delayMillis: Long, action: Runnable) {
            scheduleCount++
            scheduledRunnable = action
            this.delayMillis = delayMillis
        }

        override fun cancel() {
            cancelCount++
            scheduledRunnable = null
            delayMillis = -1L
        }
    }

    private class StressWindowManager : WindowManager {
        val addedViews = mutableListOf<View>()
        var addCount = 0
        var removeCount = 0
        var shouldThrowOnAdd = false
        var shouldThrowOnRemove = false

        @Suppress("DEPRECATION")
        override fun getDefaultDisplay() = throw UnsupportedOperationException()
        override fun removeViewImmediate(view: View?) = removeView(view)

        override fun addView(view: View?, params: ViewGroup.LayoutParams?) {
            addCount++
            if (shouldThrowOnAdd) throw IllegalStateException("Simulated addView failure")
            if (view != null) addedViews.add(view)
        }

        override fun updateViewLayout(view: View?, params: ViewGroup.LayoutParams?) {}

        override fun removeView(view: View?) {
            removeCount++
            if (shouldThrowOnRemove) throw IllegalStateException("Simulated removeView failure")
            if (view != null) {
                if (!addedViews.remove(view)) {
                    throw IllegalArgumentException("View not attached")
                }
            }
        }
    }

    private class StressLauncher {
        val launchHistory = mutableListOf<String>()

        fun launchLockSession(pkg: String, reason: LockReason, timestamp: Long): Boolean {
            launchHistory.add("$pkg:${reason.name}:$timestamp")
            return true
        }
    }

    // =========================================================================
    // 1. 100 Rapid Foreground Transitions
    // =========================================================================
    @Test
    fun test01_100RapidForegroundTransitions() {
        val clock = TestClock.at(2026, 9, 4, 10, 0, zoneId = ZoneId.of("UTC"))
        val repo = StressRepository()
        val pkgLocked = "com.stress.locked"
        val pkgFree = "com.stress.free"
        repo.apps[pkgLocked] = LockedApp(
            packageName = pkgLocked,
            enabled = true,
            schedule = TimeSchedule(startHour = 12, startMinute = 0, endHour = 14, endMinute = 0, enabled = true)
        )
        val usageTracker = UsageTracker(clock = clock, zoneId = ZoneId.of("UTC"))
        val policyEngine = PolicyEngine(repo, usageTracker, clock = clock, zoneId = ZoneId.of("UTC"))
        val scheduleScheduler = StressScheduleScheduler()
        val launcher = StressLauncher()

        val scheduleWatcher = ScheduleWatcherImpl(repo, policyEngine, clock = clock, zoneId = ZoneId.of("UTC"), scheduler = scheduleScheduler) { p ->
            launcher.launchLockSession(p, LockReason.SCHEDULE_DEADLINE, clock.elapsedRealtimeMillis())
        }

        // 100 rapid transitions alternating between locked app and free app
        for (i in 1..100) {
            val pkg = if (i % 2 == 1) pkgLocked else pkgFree
            scheduleWatcher.onForegroundChanged(pkg)
            usageTracker.startSession(pkg)
            clock.advanceBoth(100L) // 100ms
            usageTracker.stopSession()
        }

        // Verify scheduler remained stable and no premature launches occurred
        assertEquals(0, launcher.launchHistory.size)
        // Ensure final state matches the last transition (pkgFree: index 100 % 2 == 0)
        assertNull(scheduleScheduler.scheduledRunnable)
    }

    // =========================================================================
    // 2. 100 Watcher Start/Stop Cycles
    // =========================================================================
    @Test
    fun test02_100WatcherStartStopCycles() {
        val clock = TestClock.at(2026, 9, 4, 10, 0, zoneId = ZoneId.of("UTC"))
        val repo = StressRepository()
        val usageTracker = UsageTracker(clock = clock, zoneId = ZoneId.of("UTC"))
        val policyEngine = PolicyEngine(repo, usageTracker, clock = clock, zoneId = ZoneId.of("UTC"))
        val scheduleScheduler = StressScheduleScheduler()
        val limitScheduler = StressLimitScheduler()
        val launcher = StressLauncher()

        val scheduleWatcher = ScheduleWatcherImpl(repo, policyEngine, clock = clock, zoneId = ZoneId.of("UTC"), scheduler = scheduleScheduler) { p ->
            launcher.launchLockSession(p, LockReason.SCHEDULE_DEADLINE, clock.elapsedRealtimeMillis())
        }
        val limitWatcher = UsageLimitWatcherImpl(repo, usageTracker, policyEngine, limitScheduler) { p ->
            launcher.launchLockSession(p, LockReason.DAILY_LIMIT, clock.elapsedRealtimeMillis())
        }

        for (i in 1..100) {
            scheduleWatcher.start()
            limitWatcher.start()

            // Idempotent second start
            scheduleWatcher.start()
            limitWatcher.start()

            scheduleWatcher.stop()
            limitWatcher.stop()

            // Idempotent second stop
            scheduleWatcher.stop()
            limitWatcher.stop()
        }

        // All 100 cycles execute safely with zero errors and zero launches
        assertEquals(0, launcher.launchHistory.size)
    }

    // =========================================================================
    // 3. 100 Service Lifecycle Cycles
    // =========================================================================
    @Test
    fun test03_100ServiceLifecycleCycles() {
        val clock = TestClock.at(2026, 9, 4, 10, 0, zoneId = ZoneId.of("UTC"))
        val repo = StressRepository()
        val targetPkg = "com.stress.lifecycle"
        repo.apps[targetPkg] = LockedApp(
            packageName = targetPkg,
            enabled = true,
            timeLimit = TimeLimit(dailyLimitMinutes = 5, enabled = true)
        )

        for (i in 1..100) {
            // Simulate Service onCreate & onServiceConnected
            val usageTracker = UsageTracker(clock = clock, zoneId = ZoneId.of("UTC"))
            val policyEngine = PolicyEngine(repo, usageTracker, clock = clock, zoneId = ZoneId.of("UTC"))
            val limitScheduler = StressLimitScheduler()
            val launcher = StressLauncher()

            val limitWatcher = UsageLimitWatcherImpl(repo, usageTracker, policyEngine, limitScheduler) { p ->
                launcher.launchLockSession(p, LockReason.DAILY_LIMIT, clock.elapsedRealtimeMillis())
            }
            limitWatcher.start()

            // Target becomes foreground
            usageTracker.startSession(targetPkg)
            limitWatcher.onForegroundChanged(targetPkg)

            // Advance time a bit
            clock.advanceBoth(1000L)

            // Simulate Service onDestroy
            limitWatcher.stop()
            usageTracker.stopSession()

            // Invariant: stopped cleanly, no pending callbacks fired
            assertEquals(0, launcher.launchHistory.size)
        }
    }

    // =========================================================================
    // 4. 100 Policy Updates
    // =========================================================================
    @Test
    fun test04_100PolicyUpdates() {
        val clock = TestClock.at(2026, 9, 4, 11, 0, zoneId = ZoneId.of("UTC"))
        val repo = StressRepository()
        val pkg = "com.stress.policy"
        val initialApp = LockedApp(
            packageName = pkg,
            enabled = true,
            schedule = TimeSchedule(startHour = 12, startMinute = 0, endHour = 14, endMinute = 0, enabled = true)
        )
        repo.apps[pkg] = initialApp
        val usageTracker = UsageTracker(clock = clock, zoneId = ZoneId.of("UTC"))
        val policyEngine = PolicyEngine(repo, usageTracker, clock = clock, zoneId = ZoneId.of("UTC"))
        val scheduler = StressScheduleScheduler()
        val launcher = StressLauncher()

        val watcher = ScheduleWatcherImpl(repo, policyEngine, clock = clock, zoneId = ZoneId.of("UTC"), scheduler = scheduler) { p ->
            launcher.launchLockSession(p, LockReason.SCHEDULE_DEADLINE, clock.elapsedRealtimeMillis())
        }
        watcher.onForegroundChanged(pkg)
        assertNotNull(scheduler.scheduledRunnable)

        // Perform 100 successive policy updates with varying schedules
        for (i in 1..100) {
            val isEnabled = (i % 2 == 1)
            val updated = LockedApp(
                packageName = pkg,
                enabled = isEnabled,
                schedule = if (isEnabled) TimeSchedule(startHour = 13, startMinute = 0, endHour = 15, endMinute = 0, enabled = true) else null
            )
            repo.apps[pkg] = updated
            watcher.onPolicyUpdated(pkg)

            if (!isEnabled) {
                assertNull(scheduler.scheduledRunnable)
            } else {
                assertNotNull(scheduler.scheduledRunnable)
            }
        }

        // Never prematurely launched
        assertEquals(0, launcher.launchHistory.size)
    }

    // =========================================================================
    // 5. 100 Stale Callback Attempts
    // =========================================================================
    @Test
    fun test05_100StaleCallbackAttempts() {
        val clock = TestClock.at(2026, 9, 4, 11, 0, zoneId = ZoneId.of("UTC"))
        val repo = StressRepository()
        val pkg = "com.stress.stale"
        repo.apps[pkg] = LockedApp(
            packageName = pkg,
            enabled = true,
            schedule = TimeSchedule(startHour = 12, startMinute = 0, endHour = 13, endMinute = 0, enabled = true)
        )
        val usageTracker = UsageTracker(clock = clock, zoneId = ZoneId.of("UTC"))
        val policyEngine = PolicyEngine(repo, usageTracker, clock = clock, zoneId = ZoneId.of("UTC"))
        val scheduler = StressScheduleScheduler()
        val launcher = StressLauncher()

        val watcher = ScheduleWatcherImpl(repo, policyEngine, clock = clock, zoneId = ZoneId.of("UTC"), scheduler = scheduler) { p ->
            launcher.launchLockSession(p, LockReason.SCHEDULE_DEADLINE, clock.elapsedRealtimeMillis())
        }

        for (i in 1..100) {
            watcher.onForegroundChanged(pkg)
            val capturedRunnable = scheduler.scheduledRunnable

            // Invalidate generation by changing foreground or stopping
            watcher.onForegroundChanged("com.stress.other")

            // Attempt to invoke the old captured stale runnable
            capturedRunnable?.run()
        }

        // Invariant: All 100 stale callbacks dropped safely
        assertEquals(0, launcher.launchHistory.size)
    }

    // =========================================================================
    // 6. 100 Shield Add/Remove Cleanup Cycles
    // =========================================================================
    @Test
    fun test06_100ShieldAddRemoveCleanupCycles() {
        val wm = StressWindowManager()
        val overlay = BlockingShieldOverlay(context = null, windowManager = wm)
        // Set mock view factory to avoid Android View inflating without context
        overlay.viewFactory = {
            object : View(null) {
                override fun setBackgroundColor(color: Int) {}
                override fun setClickable(clickable: Boolean) {}
                override fun setFocusable(focusable: Boolean) {}
            }
        }

        for (i in 1..100) {
            overlay.show(t1 = 100L, t2 = 105L, sessionId = i.toLong(), targetPackage = "com.stress.shield")
            assertTrue(overlay.isShown())

            // Alternate between hide() and cleanup()
            if (i % 2 == 0) {
                overlay.hide()
            } else {
                overlay.cleanup()
            }

            assertFalse(overlay.isShown())
        }

        // Invariant: After 100 balanced cycles, no view is leaked
        assertEquals(0, wm.addedViews.size)
        assertEquals(100, wm.addCount)
        assertEquals(100, wm.removeCount)

        // Stress resilience: 100 cycles under WindowManager exceptions
        for (i in 1..100) {
            wm.shouldThrowOnAdd = (i % 2 == 1)
            wm.shouldThrowOnRemove = (i % 2 == 0)

            overlay.show(t1 = 100L, t2 = 105L, sessionId = (1000 + i).toLong(), targetPackage = "com.stress.shield.err")
            overlay.hide()
            assertFalse(overlay.isShown())
        }
    }

    // =========================================================================
    // 7. 500 Foreground Transitions Soak Test (Phase 12 Soak Target)
    // =========================================================================
    @Test
    fun test07_500ForegroundTransitionsSoak() {
        val clock = TestClock.at(2026, 9, 4, 10, 0, zoneId = ZoneId.of("UTC"))
        val repo = StressRepository()
        val pkg1 = "com.soak.target1"
        val pkg2 = "com.soak.target2"
        val pkgFree = "com.soak.free"

        repo.apps[pkg1] = LockedApp(
            packageName = pkg1,
            enabled = true,
            schedule = TimeSchedule(startHour = 14, startMinute = 0, endHour = 16, endMinute = 0, enabled = true)
        )
        repo.apps[pkg2] = LockedApp(
            packageName = pkg2,
            enabled = true,
            timeLimit = TimeLimit(dailyLimitMinutes = 60, enabled = true)
        )

        val usageTracker = UsageTracker(clock = clock, zoneId = ZoneId.of("UTC"))
        val policyEngine = PolicyEngine(repo, usageTracker, clock = clock, zoneId = ZoneId.of("UTC"))
        val scheduleScheduler = StressScheduleScheduler()
        val limitScheduler = StressLimitScheduler()
        val launcher = StressLauncher()

        val scheduleWatcher = ScheduleWatcherImpl(repo, policyEngine, clock = clock, zoneId = ZoneId.of("UTC"), scheduler = scheduleScheduler) { p ->
            launcher.launchLockSession(p, LockReason.SCHEDULE_DEADLINE, clock.elapsedRealtimeMillis())
        }
        val limitWatcher = UsageLimitWatcherImpl(repo, usageTracker, policyEngine, limitScheduler) { p ->
            launcher.launchLockSession(p, LockReason.DAILY_LIMIT, clock.elapsedRealtimeMillis())
        }

        scheduleWatcher.start()
        limitWatcher.start()

        val packages = listOf(pkg1, pkgFree, pkg2, pkgFree)
        // 500 rapid transitions
        for (i in 1..500) {
            val currentPkg = packages[i % packages.size]
            scheduleWatcher.onForegroundChanged(currentPkg)
            limitWatcher.onForegroundChanged(currentPkg)
            usageTracker.startSession(currentPkg)

            clock.advanceBoth(50L) // 50ms per step
            usageTracker.stopSession()
        }

        // Must maintain zero crashes, zero premature lock launches, stable scheduler state
        assertEquals(0, launcher.launchHistory.size)
        // Ensure trackers and watchers shut down cleanly
        scheduleWatcher.stop()
        limitWatcher.stop()
        assertNull(scheduleScheduler.scheduledRunnable)
        assertNull(limitScheduler.scheduledRunnable)
    }

    // =========================================================================
    // 8. 200 Lock Sessions & Handoff Cycles Soak (Phase 12 Soak Target)
    // =========================================================================
    @Test
    fun test08_200LockSessionsAndHandoffCyclesSoak() {
        val wm = StressWindowManager()
        val overlay = BlockingShieldOverlay(context = null, windowManager = wm)
        overlay.viewFactory = {
            object : View(null) {
                override fun setBackgroundColor(color: Int) {}
                override fun setClickable(clickable: Boolean) {}
                override fun setFocusable(focusable: Boolean) {}
            }
        }

        val launcher = StressLauncher()
        var currentSessionId = 0L

        for (i in 1..200) {
            val sessionId = ++currentSessionId
            val pkg = "com.soak.target"

            // 1. Lock launch accepted
            launcher.launchLockSession(pkg, LockReason.ACCESSIBILITY_EVENT, 1000L * i)

            // 2. Shield shown
            overlay.show(t1 = 1000L * i, t2 = 1000L * i + 5, sessionId = sessionId, targetPackage = pkg)
            assertTrue(overlay.isShown())

            // 3. LockScreen handoff -> Shield hidden
            overlay.hide()
            assertFalse(overlay.isShown())
        }

        // Invariant: Exactly 200 sessions launched, exactly 200 shields shown and hidden, 0 views leaked
        assertEquals(200, launcher.launchHistory.size)
        assertEquals(200, wm.addCount)
        assertEquals(200, wm.removeCount)
        assertEquals(0, wm.addedViews.size)
    }
}
