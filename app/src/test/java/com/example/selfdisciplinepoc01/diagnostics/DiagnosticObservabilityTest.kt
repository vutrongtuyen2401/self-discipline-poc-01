package com.example.selfdisciplinepoc01.diagnostics

import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import com.example.selfdisciplinepoc01.LockReason
import com.example.selfdisciplinepoc01.overlay.BlockingShieldOverlay
import com.example.selfdisciplinepoc01.policy.PolicyDecision
import com.example.selfdisciplinepoc01.policy.PolicyEngine
import com.example.selfdisciplinepoc01.policy.ScheduleScheduler
import com.example.selfdisciplinepoc01.policy.ScheduleWatcherImpl
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
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/**
 * Deterministic Unit Test Suite for PHASE 11-B: PRODUCTION OBSERVABILITY & DIAGNOSTICS.
 *
 * Verifies:
 * - DiagnosticEvent structured metadata and trace formatting.
 * - NoOp logger and bounded in-memory ring buffer behaviors.
 * - ScheduleWatcher event emission and stale callback traces.
 * - UsageLimitWatcher event emission and stale callback traces.
 * - BlockingShieldOverlay lifecycle and failure traces.
 * - Lock decision trace (accepted, Rule A, Rule B, Rule C rejections).
 * - Service lifecycle trace.
 *
 * Guaranteed constraints:
 * - No wall-clock Thread.sleep().
 * - 100% deterministic and non-blocking.
 */
class DiagnosticObservabilityTest {

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

    private class FakeWindowManager : WindowManager {
        val addedViews = mutableListOf<View>()
        var addViewCallCount = 0
        var removeViewCallCount = 0
        var throwOnAddView: Throwable? = null
        var throwOnRemoveView: Throwable? = null

        @Deprecated("Deprecated in Java")
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

    @Test
    fun testDiagnosticEventEmission_storesMetadataAndFormatsTraceCorrectly() {
        val event = DiagnosticEvent(
            type = DiagnosticEventType.LOCK_LAUNCH_ACCEPTED,
            message = "Lock launch accepted for target",
            packageName = "com.android.chrome",
            sessionId = 42L,
            generation = 3L,
            lockReason = LockReason.SCHEDULE_DEADLINE,
            currentForegroundPackage = "com.google.android.youtube",
            elapsedTimestamp = 1200L,
            rejectionReason = null,
            details = mapOf("customKey" to "customValue")
        )

        val trace = event.formatTrace()
        assertTrue(trace.startsWith("[LOCK_LAUNCH_ACCEPTED] Lock launch accepted for target"))
        assertTrue(trace.contains("pkg='com.android.chrome'"))
        assertTrue(trace.contains("sessionId=42"))
        assertTrue(trace.contains("gen=3"))
        assertTrue(trace.contains("reason=SCHEDULE_DEADLINE"))
        assertTrue(trace.contains("fgPkg='com.google.android.youtube'"))
        assertTrue(trace.contains("elapsed=1200ms"))
        assertTrue(trace.contains("customKey=customValue"))
    }

    @Test
    fun testDiagnosticLoggerNoOp_doesNotThrowOrStore() {
        val noOp = NoOpDiagnosticLogger()
        val event = DiagnosticEvent(
            type = DiagnosticEventType.SERVICE_CREATED,
            message = "Test service created"
        )

        // Should execute cleanly without any exceptions
        noOp.debug(event)
        noOp.info(event)
        noOp.warn(event)
        noOp.error(event, RuntimeException("Test exception"))
    }

    @Test
    fun testBoundedRingBuffer_evictsOldestWhenCapacityReached() {
        val capacity = 5
        val boundedLogger = AndroidDiagnosticLogger(tag = "TestTag", bufferCapacity = capacity)

        for (i in 1..7) {
            boundedLogger.info(
                DiagnosticEvent(
                    type = DiagnosticEventType.POLICY_EVALUATION,
                    message = "Event number $i"
                )
            )
        }

        val events = boundedLogger.getRecentEvents()
        assertEquals(capacity, events.size)
        // Earliest events 1 and 2 should have been evicted
        assertEquals("Event number 3", events[0].message)
        assertEquals("Event number 7", events[4].message)
    }

    @Test
    fun testScheduleWatcher_emitsDeadlineScheduledAndActiveTrace() {
        val zone = ZoneId.of("UTC")
        val clock = TestClock.at(2026, 9, 4, 8, 50, zoneId = zone)

        val repo = FakeTargetRepository()
        val pkg = "com.android.chrome"
        repo.apps[pkg] = LockedApp(
            packageName = pkg,
            enabled = true,
            schedule = TimeSchedule(
                startHour = 9,
                startMinute = 0,
                endHour = 12,
                endMinute = 0,
                enabled = true
            )
        )
        val usageTracker = UsageTracker(clock = clock, zoneId = zone)
        val policyEngine = PolicyEngine(repo, usageTracker, clock = clock, zoneId = zone)
        val scheduler = TestScheduleScheduler()
        val inMemoryLogger = InMemoryDiagnosticLogger()

        var lockedCount = 0
        val watcher = ScheduleWatcherImpl(
            targetRepository = repo,
            policyEngine = policyEngine,
            clock = clock,
            zoneId = zone,
            scheduler = scheduler,
            logger = inMemoryLogger,
            onScheduleLocked = { lockedCount++ }
        )

        watcher.start()
        assertTrue(inMemoryLogger.hasEvent(DiagnosticEventType.SCHEDULE_WATCHER_STARTED))

        // Trigger foreground at 8:50 UTC -> Boundary at 9:00 UTC (10 min = 600,000ms delay)
        watcher.onForegroundChanged(pkg)
        assertNotNull(scheduler.scheduledRunnable)
        assertEquals(10 * 60 * 1000L, scheduler.scheduledDelayMillis)

        val scheduledEvent = inMemoryLogger.lastEventOfType(DiagnosticEventType.SCHEDULE_DEADLINE_SCHEDULED)
        assertNotNull(scheduledEvent)
        assertEquals(pkg, scheduledEvent?.packageName)
        assertEquals(10 * 60 * 1000L, scheduledEvent?.remainingMillis)

        // Fast forward clock by 10 minutes and fire callback
        clock.advanceBoth(10 * 60 * 1000L + 1000L)
        scheduler.trigger()

        assertEquals(1, lockedCount)
        assertTrue(inMemoryLogger.hasEvent(DiagnosticEventType.SCHEDULE_DEADLINE_CALLBACK))
        assertTrue(inMemoryLogger.hasEvent(DiagnosticEventType.SCHEDULE_ACTIVE))
    }

    @Test
    fun testScheduleWatcher_staleCallbackTrace_stoppedWatcher() {
        val zone = ZoneId.of("UTC")
        val clock = TestClock.at(2026, 9, 4, 8, 50, zoneId = zone)

        val repo = FakeTargetRepository()
        val pkg = "com.android.chrome"
        repo.apps[pkg] = LockedApp(
            packageName = pkg,
            enabled = true,
            schedule = TimeSchedule(
                startHour = 9,
                startMinute = 0,
                endHour = 12,
                endMinute = 0,
                enabled = true
            )
        )
        val usageTracker = UsageTracker(clock = clock, zoneId = zone)
        val policyEngine = PolicyEngine(repo, usageTracker, clock = clock, zoneId = zone)
        val scheduler = TestScheduleScheduler()
        val inMemoryLogger = InMemoryDiagnosticLogger()

        var lockedCount = 0
        val watcher = ScheduleWatcherImpl(
            targetRepository = repo,
            policyEngine = policyEngine,
            clock = clock,
            zoneId = zone,
            scheduler = scheduler,
            logger = inMemoryLogger,
            onScheduleLocked = { lockedCount++ }
        )

        watcher.start()
        watcher.onForegroundChanged(pkg)
        val staleRunnable = scheduler.scheduledRunnable
        assertNotNull(staleRunnable)

        // Stop watcher
        watcher.stop()
        assertTrue(inMemoryLogger.hasEvent(DiagnosticEventType.SCHEDULE_WATCHER_STOPPED))

        // Triggering stale runnable should be rejected
        staleRunnable?.run()
        assertEquals(0, lockedCount)

        val staleEvent = inMemoryLogger.lastEventOfType(DiagnosticEventType.SCHEDULE_STALE_CALLBACK)
        assertNotNull(staleEvent)
        assertEquals("WATCHER_STOPPED", staleEvent?.rejectionReason)
    }

    @Test
    fun testScheduleWatcher_staleCallbackTrace_generationMismatch() {
        val zone = ZoneId.of("UTC")
        val clock = TestClock.at(2026, 9, 4, 8, 50, zoneId = zone)

        val repo = FakeTargetRepository()
        val pkg = "com.android.chrome"
        repo.apps[pkg] = LockedApp(
            packageName = pkg,
            enabled = true,
            schedule = TimeSchedule(
                startHour = 9,
                startMinute = 0,
                endHour = 12,
                endMinute = 0,
                enabled = true
            )
        )
        val usageTracker = UsageTracker(clock = clock, zoneId = zone)
        val policyEngine = PolicyEngine(repo, usageTracker, clock = clock, zoneId = zone)
        val scheduler = TestScheduleScheduler()
        val inMemoryLogger = InMemoryDiagnosticLogger()

        var lockedCount = 0
        val watcher = ScheduleWatcherImpl(
            targetRepository = repo,
            policyEngine = policyEngine,
            clock = clock,
            zoneId = zone,
            scheduler = scheduler,
            logger = inMemoryLogger,
            onScheduleLocked = { lockedCount++ }
        )

        watcher.start()
        watcher.onForegroundChanged(pkg)
        val staleRunnable = scheduler.scheduledRunnable
        assertNotNull(staleRunnable)

        // Target switches to another app
        watcher.onForegroundChanged("com.other.app")

        // Firing old stale runnable
        staleRunnable?.run()
        assertEquals(0, lockedCount)

        val staleEvent = inMemoryLogger.lastEventOfType(DiagnosticEventType.SCHEDULE_STALE_CALLBACK)
        assertNotNull(staleEvent)
        assertTrue(staleEvent?.rejectionReason?.contains("GENERATION_MISMATCH") == true)
    }

    @Test
    fun testUsageLimitWatcher_emitsDeadlineScheduledAndLimitExhaustedTrace() {
        val zone = ZoneId.of("UTC")
        val clock = TestClock.at(2026, 9, 4, 10, 0, zoneId = zone)
        val repo = FakeTargetRepository()
        val pkg = "com.android.chrome"
        repo.apps[pkg] = LockedApp(
            packageName = pkg,
            enabled = true,
            timeLimit = TimeLimit(dailyLimitMinutes = 1, enabled = true)
        )
        val usageTracker = UsageTracker(clock = clock, zoneId = zone)
        val policyEngine = PolicyEngine(repo, usageTracker, clock = clock, zoneId = zone)
        val scheduler = TestLimitScheduler()
        val inMemoryLogger = InMemoryDiagnosticLogger()

        var limitCount = 0
        val watcher = UsageLimitWatcherImpl(
            targetRepository = repo,
            usageTracker = usageTracker,
            policyEngine = policyEngine,
            scheduler = scheduler,
            logger = inMemoryLogger,
            onLimitReached = { limitCount++ }
        )

        watcher.start()
        assertTrue(inMemoryLogger.hasEvent(DiagnosticEventType.USAGE_WATCHER_STARTED))

        // Target enters foreground with 60s remaining
        watcher.onForegroundChanged(pkg)
        assertEquals(60_000L, scheduler.scheduledDelayMillis)

        val scheduledEvent = inMemoryLogger.lastEventOfType(DiagnosticEventType.USAGE_DEADLINE_SCHEDULED)
        assertNotNull(scheduledEvent)
        assertEquals(60_000L, scheduledEvent?.remainingMillis)
        assertEquals(60_000L, scheduledEvent?.limitMillis)

        // Consume 60s usage
        usageTracker.startSession(pkg)
        clock.advanceElapsed(60_000L)
        usageTracker.stopSession(pkg)

        scheduler.trigger()

        assertEquals(1, limitCount)
        assertTrue(inMemoryLogger.hasEvent(DiagnosticEventType.USAGE_DEADLINE_CALLBACK))
        assertTrue(inMemoryLogger.hasEvent(DiagnosticEventType.DAILY_LIMIT_EXHAUSTED))
    }

    @Test
    fun testUsageLimitWatcher_staleCallbackTrace_packageMismatch() {
        val zone = ZoneId.of("UTC")
        val clock = TestClock.at(2026, 9, 4, 10, 0, zoneId = zone)
        val repo = FakeTargetRepository()
        val pkg = "com.android.chrome"
        repo.apps[pkg] = LockedApp(
            packageName = pkg,
            enabled = true,
            timeLimit = TimeLimit(dailyLimitMinutes = 1, enabled = true)
        )
        val usageTracker = UsageTracker(clock = clock, zoneId = zone)
        val policyEngine = PolicyEngine(repo, usageTracker, clock = clock, zoneId = zone)
        val scheduler = TestLimitScheduler()
        val inMemoryLogger = InMemoryDiagnosticLogger()

        var limitCount = 0
        val watcher = UsageLimitWatcherImpl(
            targetRepository = repo,
            usageTracker = usageTracker,
            policyEngine = policyEngine,
            scheduler = scheduler,
            logger = inMemoryLogger,
            onLimitReached = { limitCount++ }
        )

        watcher.start()
        watcher.onForegroundChanged(pkg)
        val staleRunnable = scheduler.scheduledRunnable
        assertNotNull(staleRunnable)

        // Switch to null
        watcher.onForegroundChanged(null)

        // Firing stale runnable
        staleRunnable?.run()
        assertEquals(0, limitCount)

        val staleEvent = inMemoryLogger.lastEventOfType(DiagnosticEventType.USAGE_STALE_CALLBACK)
        assertNotNull(staleEvent)
        assertTrue(
            staleEvent?.rejectionReason?.contains("GENERATION_MISMATCH") == true ||
            staleEvent?.rejectionReason?.contains("PACKAGE_MISMATCH") == true
        )
    }

    @Test
    fun testBlockingShieldOverlay_emitsLifecycleEvents() {
        val inMemoryLogger = InMemoryDiagnosticLogger()
        val fakeWm = FakeWindowManager()

        val shield = BlockingShieldOverlay(
            context = null,
            windowManager = fakeWm,
            logger = inMemoryLogger
        )
        shield.viewFactory = { View(null) }

        // 1. Show shield
        shield.show(100L, 200L, 1L, "com.android.chrome")
        assertTrue(inMemoryLogger.hasEvent(DiagnosticEventType.SHIELD_SHOW_REQUESTED))
        assertTrue(inMemoryLogger.hasEvent(DiagnosticEventType.SHIELD_SHOWN))
        assertEquals(1, fakeWm.addedViews.size)

        // 2. Hide shield
        shield.hide("test_hide")
        assertTrue(inMemoryLogger.hasEvent(DiagnosticEventType.SHIELD_HIDE))
        assertEquals(0, fakeWm.addedViews.size)

        // 3. Show again and test cleanup
        shield.show(300L, 400L, 2L, "com.android.chrome")
        assertEquals(1, fakeWm.addedViews.size)
        shield.cleanup()
        assertTrue(inMemoryLogger.hasEvent(DiagnosticEventType.SHIELD_CLEANUP))
        assertEquals(0, fakeWm.addedViews.size)
    }

    @Test
    fun testBlockingShieldOverlay_emitsFailureEventOnWindowManagerException() {
        val inMemoryLogger = InMemoryDiagnosticLogger()
        val fakeWm = FakeWindowManager().apply {
            throwOnAddView = WindowManager.BadTokenException("Bad token in unit test")
        }

        val shield = BlockingShieldOverlay(
            context = null,
            windowManager = fakeWm,
            logger = inMemoryLogger
        )
        shield.viewFactory = { View(null) }

        shield.show(100L, 200L, 1L, "com.android.chrome")
        assertTrue(inMemoryLogger.hasEvent(DiagnosticEventType.SHIELD_SHOW_REQUESTED))
        assertTrue(inMemoryLogger.hasEvent(DiagnosticEventType.SHIELD_SHOW_FAILED))
    }

    @Test
    fun testLockDecisionTrace_simulation_coversAllRuleBranches() {
        val logger = InMemoryDiagnosticLogger()

        fun simulateLaunch(
            pkg: String,
            reason: LockReason,
            now: Long,
            lastLaunchTimestamp: Long,
            prevPkg: String?,
            isLockScreenVisible: Boolean,
            isChromeLocked: Boolean,
            cooldownMs: Long = 1500L
        ): Boolean {
            val isNewTransition = (prevPkg != pkg)
            val elapsed = now - lastLaunchTimestamp

            logger.debug(
                DiagnosticEvent(
                    type = DiagnosticEventType.LOCK_DECISION,
                    message = "Evaluating lock launch for $pkg (reason=$reason)",
                    packageName = pkg,
                    lockReason = reason,
                    currentForegroundPackage = prevPkg,
                    elapsedTimestamp = elapsed
                )
            )

            // Rule A
            if (isLockScreenVisible) {
                logger.warn(
                    DiagnosticEvent(
                        type = DiagnosticEventType.LOCK_LAUNCH_REJECTED,
                        message = "Rule A rejected",
                        packageName = pkg,
                        lockReason = reason,
                        rejectionReason = "RULE_A_LOCKSCREEN_VISIBLE"
                    )
                )
                return false
            }

            // Rule B
            if (!isNewTransition && isChromeLocked) {
                logger.warn(
                    DiagnosticEvent(
                        type = DiagnosticEventType.LOCK_LAUNCH_REJECTED,
                        message = "Rule B rejected",
                        packageName = pkg,
                        lockReason = reason,
                        rejectionReason = "RULE_B_INTRA_SESSION_DUPLICATE"
                    )
                )
                return false
            }

            // Rule C
            if (!isNewTransition && elapsed < cooldownMs) {
                logger.warn(
                    DiagnosticEvent(
                        type = DiagnosticEventType.LOCK_LAUNCH_REJECTED,
                        message = "Rule C rejected",
                        packageName = pkg,
                        lockReason = reason,
                        rejectionReason = "RULE_C_COOLDOWN_ACTIVE",
                        elapsedTimestamp = elapsed
                    )
                )
                return false
            }

            logger.info(
                DiagnosticEvent(
                    type = DiagnosticEventType.LOCK_LAUNCH_ACCEPTED,
                    message = "Accepted launch",
                    packageName = pkg,
                    lockReason = reason
                )
            )
            logger.info(
                DiagnosticEvent(
                    type = DiagnosticEventType.LOCK_SESSION_STARTED,
                    message = "Session started",
                    packageName = pkg,
                    lockReason = reason
                )
            )
            return true
        }

        // 1. Accepted launch
        val accepted = simulateLaunch(
            pkg = "com.android.chrome",
            reason = LockReason.ACCESSIBILITY_EVENT,
            now = 1000L,
            lastLaunchTimestamp = 0L,
            prevPkg = null,
            isLockScreenVisible = false,
            isChromeLocked = false
        )
        assertTrue(accepted)
        assertTrue(logger.hasEvent(DiagnosticEventType.LOCK_LAUNCH_ACCEPTED))
        assertTrue(logger.hasEvent(DiagnosticEventType.LOCK_SESSION_STARTED))

        // 2. Rule A rejection
        val rejectedA = simulateLaunch(
            pkg = "com.android.chrome",
            reason = LockReason.DAILY_LIMIT,
            now = 1050L,
            lastLaunchTimestamp = 1000L,
            prevPkg = "com.android.chrome",
            isLockScreenVisible = true,
            isChromeLocked = true
        )
        assertFalse(rejectedA)
        val eventA = logger.getEventsOfType(DiagnosticEventType.LOCK_LAUNCH_REJECTED).last()
        assertEquals("RULE_A_LOCKSCREEN_VISIBLE", eventA.rejectionReason)

        // 3. Rule B rejection
        val rejectedB = simulateLaunch(
            pkg = "com.android.chrome",
            reason = LockReason.ACCESSIBILITY_EVENT,
            now = 3000L,
            lastLaunchTimestamp = 1000L,
            prevPkg = "com.android.chrome",
            isLockScreenVisible = false,
            isChromeLocked = true
        )
        assertFalse(rejectedB)
        val eventB = logger.getEventsOfType(DiagnosticEventType.LOCK_LAUNCH_REJECTED).last()
        assertEquals("RULE_B_INTRA_SESSION_DUPLICATE", eventB.rejectionReason)

        // 4. Rule C rejection
        val rejectedC = simulateLaunch(
            pkg = "com.android.chrome",
            reason = LockReason.SCHEDULE_DEADLINE,
            now = 1500L,
            lastLaunchTimestamp = 1000L,
            prevPkg = "com.android.chrome",
            isLockScreenVisible = false,
            isChromeLocked = false
        )
        assertFalse(rejectedC)
        val eventC = logger.getEventsOfType(DiagnosticEventType.LOCK_LAUNCH_REJECTED).last()
        assertEquals("RULE_C_COOLDOWN_ACTIVE", eventC.rejectionReason)
    }

    @Test
    fun testServiceLifecycleTrace_coversAllTransitions() {
        val logger = InMemoryDiagnosticLogger()

        logger.info(DiagnosticEvent(DiagnosticEventType.SERVICE_CREATED, "Service created"))
        logger.info(DiagnosticEvent(DiagnosticEventType.SERVICE_CONNECTED, "Service connected"))
        logger.warn(DiagnosticEvent(DiagnosticEventType.SERVICE_INTERRUPTED, "Service interrupted"))
        logger.info(DiagnosticEvent(DiagnosticEventType.SERVICE_DESTROYED, "Service destroyed"))

        assertEquals(4, logger.getEvents().size)
        assertTrue(logger.hasEvent(DiagnosticEventType.SERVICE_CREATED))
        assertTrue(logger.hasEvent(DiagnosticEventType.SERVICE_CONNECTED))
        assertTrue(logger.hasEvent(DiagnosticEventType.SERVICE_INTERRUPTED))
        assertTrue(logger.hasEvent(DiagnosticEventType.SERVICE_DESTROYED))
    }
}
