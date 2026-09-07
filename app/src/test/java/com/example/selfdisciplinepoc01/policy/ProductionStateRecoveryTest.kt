package com.example.selfdisciplinepoc01.policy

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.example.selfdisciplinepoc01.target.model.LockedApp
import com.example.selfdisciplinepoc01.target.model.TimeLimit
import com.example.selfdisciplinepoc01.target.model.TimeSchedule
import com.example.selfdisciplinepoc01.target.repository.TargetRepositoryImpl
import com.example.selfdisciplinepoc01.time.TestClock
import com.example.selfdisciplinepoc01.usage.LimitScheduler
import com.example.selfdisciplinepoc01.usage.UsageLimitWatcherImpl
import com.example.selfdisciplinepoc01.usage.UsageTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
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
 * PHASE 11-C: PRODUCTION STATE PERSISTENCE & RECOVERY AUDIT TEST SUITE
 *
 * Verifies that configuration, usage accounting, watcher reconstruction,
 * and process/service recovery remain deterministic after:
 * - process death / cold start
 * - service reconnect
 * - screen OFF/ON
 * - midnight rollover
 * - timezone change / DST transition
 * - wall-clock forward / backward jump
 * - policy update
 * - target removal / re-addition
 * - pending deadline cancellation / reconstruction
 * - malformed / corrupted persisted state
 *
 * All tests use deterministic fake Clocks / Schedulers / DataStores.
 * Strictly NO Thread.sleep, NO polling, NO busy loops.
 */
class ProductionStateRecoveryTest {

    // --- TEST DOUBLES ---

    private class FakePreferencesDataStore(
        initialPreferences: Preferences = emptyPreferences()
    ) : DataStore<Preferences> {
        private val state = MutableStateFlow(initialPreferences)
        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val current = state.value
            val updated = transform(current)
            state.value = updated
            return updated
        }

        fun currentPreferences(): Preferences = state.value
    }

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

    // --- REPOSITORY PERSISTENCE & RECOVERY TESTS ---

    @Test
    fun test01_policySurvivesRestart_targetAndEnabledStateRestored() = runBlocking {
        val dataStore = FakePreferencesDataStore()
        val scope1 = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repo1 = TargetRepositoryImpl(dataStore, scope1)

        val targetPkg = "com.test.productive"
        repo1.add(targetPkg)
        assertTrue(repo1.isLocked(targetPkg))

        // Simulate Process Death & Cold Start: Instantiate completely new Repository
        val scope2 = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repo2 = TargetRepositoryImpl(dataStore, scope2)

        assertTrue("Target must survive process restart", repo2.isLocked(targetPkg))
        assertNotNull(repo2.getTarget(targetPkg))
        assertTrue(repo2.getTarget(targetPkg)!!.enabled)
    }

    @Test
    fun test02_disabledTargetSurvivesRestart_isNotLocked() = runBlocking {
        val dataStore = FakePreferencesDataStore()
        val scope1 = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repo1 = TargetRepositoryImpl(dataStore, scope1)

        val targetPkg = "com.test.disabled"
        repo1.add(targetPkg)
        repo1.setEnabled(targetPkg, false)
        assertFalse(repo1.isLocked(targetPkg))

        // Simulate Process Death & Restart
        val scope2 = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repo2 = TargetRepositoryImpl(dataStore, scope2)

        assertFalse("Disabled target must remain disabled after restart", repo2.isLocked(targetPkg))
        val target = repo2.getTarget(targetPkg)
        assertNotNull(target)
        assertFalse(target!!.enabled)
    }

    @Test
    fun test03_scheduleSurvivesRestart_exactHoursAndMinutesRestored() = runBlocking {
        val dataStore = FakePreferencesDataStore()
        val scope1 = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repo1 = TargetRepositoryImpl(dataStore, scope1)

        val targetPkg = "com.test.scheduled"
        repo1.add(targetPkg)
        val customSchedule = TimeSchedule(
            enabled = true,
            startHour = 14,
            startMinute = 30,
            endHour = 18,
            endMinute = 45
        )
        repo1.updatePolicy(targetPkg, schedule = customSchedule, timeLimit = null)

        // Process Death & Restart
        val scope2 = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repo2 = TargetRepositoryImpl(dataStore, scope2)

        val recoveredApp = repo2.getTarget(targetPkg)
        assertNotNull(recoveredApp)
        assertNotNull(recoveredApp!!.schedule)
        val s = recoveredApp.schedule!!
        assertTrue(s.enabled)
        assertEquals(14, s.startHour)
        assertEquals(30, s.startMinute)
        assertEquals(18, s.endHour)
        assertEquals(45, s.endMinute)
    }

    @Test
    fun test04_dailyLimitSurvivesRestart_minutesAndSecondsRestored() = runBlocking {
        val dataStore = FakePreferencesDataStore()
        val scope1 = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repo1 = TargetRepositoryImpl(dataStore, scope1)

        val targetPkg = "com.test.limited"
        repo1.add(targetPkg)
        val customLimit = TimeLimit(
            enabled = true,
            dailyLimitMinutes = 45,
            dailyLimitSeconds = 25
        )
        repo1.updatePolicy(targetPkg, schedule = null, timeLimit = customLimit)

        // Process Death & Restart
        val scope2 = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repo2 = TargetRepositoryImpl(dataStore, scope2)

        val recoveredApp = repo2.getTarget(targetPkg)
        assertNotNull(recoveredApp)
        assertNotNull(recoveredApp!!.timeLimit)
        val l = recoveredApp.timeLimit!!
        assertTrue(l.enabled)
        assertEquals(45, l.dailyLimitMinutes)
        assertEquals(25, l.dailyLimitSeconds)
        assertEquals(25_000L, l.limitMillis)
    }

    @Test
    fun test05_targetRemovalSurvivesRestart_notResurrected() = runBlocking {
        val dataStore = FakePreferencesDataStore()
        val scope1 = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repo1 = TargetRepositoryImpl(dataStore, scope1)

        val targetPkg = "com.test.removed"
        repo1.add(targetPkg)
        assertTrue(repo1.isLocked(targetPkg))

        repo1.remove(targetPkg)
        assertFalse(repo1.isLocked(targetPkg))
        assertNull(repo1.getTarget(targetPkg))

        // Process Death & Restart
        val scope2 = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repo2 = TargetRepositoryImpl(dataStore, scope2)

        assertFalse("Removed target must not be resurrected after restart", repo2.isLocked(targetPkg))
        assertNull(repo2.getTarget(targetPkg))
    }

    @Test
    fun test06_targetReAdditionDoesNotResurrectStalePolicy() = runBlocking {
        val dataStore = FakePreferencesDataStore()
        val scope1 = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repo1 = TargetRepositoryImpl(dataStore, scope1)

        val targetPkg = "com.test.recycle"
        repo1.add(targetPkg)
        repo1.updatePolicy(
            targetPkg,
            schedule = TimeSchedule(enabled = true, startHour = 10, startMinute = 0, endHour = 12, endMinute = 0),
            timeLimit = TimeLimit(enabled = true, dailyLimitMinutes = 15)
        )
        assertNotNull(repo1.getTarget(targetPkg)?.schedule)

        // Remove and restart
        repo1.remove(targetPkg)

        val scope2 = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val repo2 = TargetRepositoryImpl(dataStore, scope2)
        assertNull(repo2.getTarget(targetPkg))

        // Re-add target: should be clean default, NOT resurrecting old schedule / limit
        repo2.add(targetPkg)
        val freshApp = repo2.getTarget(targetPkg)
        assertNotNull(freshApp)
        assertTrue(freshApp!!.enabled)
        assertNull("Re-added target must not resurrect stale schedule", freshApp.schedule)
        assertNull("Re-added target must not resurrect stale time limit", freshApp.timeLimit)
    }

    // --- MALFORMED & CORRUPTED PERSISTED STATE FALLBACK ---

    @Test
    fun test07_malformedJsonSyntaxFallback_returnsSafeDefault() {
        val malformedJson = "{not_a_valid_json: true, unterminated"
        val result = TargetRepositoryImpl.deserialize(malformedJson)

        assertEquals(1, result.size)
        assertEquals(TargetRepositoryImpl.DEFAULT_TARGET_PACKAGE, result[0].packageName)
        assertTrue(result[0].enabled)
    }

    @Test
    fun test08_invalidScheduleHoursFallback_returnsSafeDefault() {
        // startHour 99 is impossible -> TimeSchedule constructor throws IllegalArgumentException
        val invalidScheduleJson = """
            [
                {
                    "packageName": "com.test.invalid",
                    "enabled": true,
                    "schedule": {
                        "enabled": true,
                        "startHour": 99,
                        "startMinute": 0,
                        "endHour": 17,
                        "endMinute": 0
                    }
                }
            ]
        """.trimIndent()

        val result = TargetRepositoryImpl.deserialize(invalidScheduleJson)
        // Safe fallback guaranteed
        assertEquals(1, result.size)
        assertEquals(TargetRepositoryImpl.DEFAULT_TARGET_PACKAGE, result[0].packageName)
    }

    @Test
    fun test09_invalidDailyLimitMinutesFallback_returnsSafeDefault() {
        // dailyLimitMinutes -10 is impossible -> TimeLimit constructor throws IllegalArgumentException
        val invalidLimitJson = """
            [
                {
                    "packageName": "com.test.negative",
                    "enabled": true,
                    "timeLimit": {
                        "enabled": true,
                        "dailyLimitMinutes": -10
                    }
                }
            ]
        """.trimIndent()

        val result = TargetRepositoryImpl.deserialize(invalidLimitJson)
        assertEquals(1, result.size)
        assertEquals(TargetRepositoryImpl.DEFAULT_TARGET_PACKAGE, result[0].packageName)
    }

    // --- USAGE RECOVERY, CALENDAR-DAY SEPARATION & CLOCK AUDIT ---

    @Test
    fun test10_usageDaySeparation_onlyTodayCounted() {
        val zone = ZoneId.of("UTC")
        val clock = TestClock.at(2026, 9, 4, 10, 0, 0, 0, zone)
        val tracker = UsageTracker(clock = clock, zoneId = zone)

        val pkg = "com.test.usage"
        // Record 10,000ms yesterday
        val yesterdayWall = clock.wallTimeMillis() - 86_400_000L
        tracker.recordUsageSegment(pkg, yesterdayWall, yesterdayWall + 10_000L, 10_000L)

        // Record 5,000ms today
        val todayWall = clock.wallTimeMillis()
        tracker.recordUsageSegment(pkg, todayWall, todayWall + 5_000L, 5_000L)

        // Serialize and simulate reload / process restart
        val serialized = tracker.serializeToJson()
        val tracker2 = UsageTracker(clock = clock, zoneId = zone)
        tracker2.loadFromJson(serialized)

        assertEquals("Only today usage must be counted", 5_000L, tracker2.getTodayUsage(pkg))
    }

    @Test
    fun test11_dailyCycle0400Rollover_durationAllocatedProportionally() {
        val zone = ZoneId.of("UTC")
        // Start 2 seconds before 04:00 boundary (03:59:58 on 2026-09-04)
        val clock = TestClock.at(2026, 9, 4, 3, 59, 58, 0, zone)
        val tracker = UsageTracker(clock = clock, zoneId = zone)

        val pkg = "com.test.rollover"
        tracker.startSession(pkg)

        // Advance 4 seconds (2s before 04:00, 2s after 04:00)
        clock.advanceBoth(4_000L)
        tracker.stopSession()

        // 2026-09-03 cycle should receive 2,000ms, 2026-09-04 cycle should receive 2,000ms
        val serialized = tracker.serializeToJson()

        // New tracker running on next cycle (2026-09-04 04:00:02)
        val trackerNextDay = UsageTracker(clock = clock, zoneId = zone)
        trackerNextDay.loadFromJson(serialized)

        assertEquals(2_000L, trackerNextDay.getTodayUsage(pkg))
    }

    @Test
    fun test12_timezoneChange_recomputesLocalCalendarDay() {
        // Case A: Timezone change within same calendar day preserves today usage
        var currentZone = ZoneId.of("UTC")
        val clock = TestClock.at(2026, 9, 4, 10, 0, 0, 0, currentZone) // 10:00 UTC
        val tracker = UsageTracker(clock = clock, zoneIdProvider = { currentZone })

        val pkg = "com.test.tz"
        tracker.recordUsageSegment(pkg, clock.wallTimeMillis(), clock.wallTimeMillis() + 10_000L, 10_000L)
        assertEquals(10_000L, tracker.getTodayUsage(pkg))

        // Switch timezone to UTC+7 (17:00 on 2026-09-04, same calendar day)
        currentZone = ZoneId.of("Asia/Bangkok") // UTC+7
        assertEquals("Same local day preserves today usage", 10_000L, tracker.getTodayUsage(pkg))

        // Case B: Timezone change crossing into tomorrow starts clean day for today
        // 23:30 UTC on 2026-09-04 -> 06:30 on 2026-09-05 in UTC+7
        val clockMidnight = TestClock.at(2026, 9, 4, 23, 30, 0, 0, ZoneId.of("UTC"))
        var dynamicZone = ZoneId.of("UTC")
        val trackerMidnight = UsageTracker(clock = clockMidnight, zoneIdProvider = { dynamicZone })
        trackerMidnight.recordUsageSegment(pkg, clockMidnight.wallTimeMillis(), clockMidnight.wallTimeMillis() + 5_000L, 5_000L)
        assertEquals(5_000L, trackerMidnight.getTodayUsage(pkg))

        // Switch to UTC+7: Local day is now 2026-09-05
        dynamicZone = ZoneId.of("Asia/Bangkok")
        assertEquals("New calendar day in new timezone starts with 0 today usage", 0L, trackerMidnight.getTodayUsage(pkg))

        // New usage in new timezone accumulates into new day
        trackerMidnight.recordUsageSegment(pkg, clockMidnight.wallTimeMillis(), clockMidnight.wallTimeMillis() + 3_000L, 3_000L)
        assertEquals(3_000L, trackerMidnight.getTodayUsage(pkg))
    }

    @Test
    fun test13_daylightSavingTimeTransition_elapsedRealtimeIsAuthoritative() {
        val zone = ZoneId.of("America/New_York")
        // Clock starts before DST jump
        val clock = TestClock(currentWallMillis = 100_000_000L, currentElapsedMillis = 10_000L)
        val tracker = UsageTracker(clock = clock, zoneId = zone)

        val pkg = "com.test.dst"
        tracker.startSession(pkg)

        // Wall clock jumps 1 hour (3,600,000ms) forward due to DST, but elapsed only moves 30,000ms
        clock.setWallTime(clock.wallTimeMillis() + 3_600_000L)
        clock.advanceElapsed(30_000L)
        tracker.stopSession()

        // Invariant: Duration is strictly monotonic elapsedRealtime (30,000ms), NOT 1 hour + 30s
        assertEquals(30_000L, tracker.getTodayUsage(pkg))
    }

    @Test
    fun test14_wallClockForwardJump_doesNotFabricateUsage() {
        val zone = ZoneId.of("UTC")
        val clock = TestClock.at(2026, 9, 4, 12, 0, 0, 0, zone)
        val tracker = UsageTracker(clock = clock, zoneId = zone)

        val pkg = "com.test.jump"
        tracker.startSession(pkg)

        // Wall clock forward jump of 5 days (432,000,000ms), but actual elapsed is only 5,000ms
        clock.setWallTime(clock.wallTimeMillis() + 432_000_000L)
        clock.advanceElapsed(5_000L)
        tracker.stopSession()

        // Usage must NOT be 5 days, total duration recorded across days is exactly 5,000ms
        val serialized = tracker.serializeToJson()
        val trackerAfter = UsageTracker(clock = clock, zoneId = zone)
        trackerAfter.loadFromJson(serialized)

        // The current day receives its proportional share of 5,000ms, not hundreds of hours
        assertTrue(trackerAfter.getTodayUsage(pkg) in 0L..5_000L)
    }

    @Test
    fun test15_wallClockBackwardJump_doesNotCreateNegativeUsage() {
        val zone = ZoneId.of("UTC")
        val clock = TestClock.at(2026, 9, 4, 15, 0, 0, 0, zone)
        val tracker = UsageTracker(clock = clock, zoneId = zone)

        val pkg = "com.test.backjump"
        tracker.startSession(pkg)

        // User rolls clock back 2 hours (-7,200,000ms), but elapsed moves forward 8,000ms
        clock.setWallTime(clock.wallTimeMillis() - 7_200_000L)
        clock.advanceElapsed(8_000L)
        tracker.stopSession()

        // Monotonic duration is strictly authoritative: 8,000ms assigned to startDate
        assertTrue(tracker.getTodayUsage(pkg) >= 0L)
        assertEquals(8_000L, tracker.getTodayUsage(pkg))
    }

    // --- WATCHER RECONSTRUCTION & PENDING DEADLINE SEMANTICS ---

    @Test
    fun test16_restartWithPendingScheduleDeadline_reconstructedOnFreshForeground() {
        val zone = ZoneId.of("UTC")
        // Target is locked between 14:00 and 18:00
        val targetApp = LockedApp(
            packageName = "com.test.schedule.recovery",
            enabled = true,
            schedule = TimeSchedule(enabled = true, startHour = 14, startMinute = 0, endHour = 18, endMinute = 0)
        )
        val fakeRepo = FakeTargetRepository(mutableMapOf(targetApp.packageName to targetApp))
        val fakeUsage = UsageTracker(TestClock.at(2026, 9, 4, 13, 50, 0, 0, zone), zoneId = zone)
        val clock = TestClock.at(2026, 9, 4, 13, 50, 0, 0, zone) // 13:50 (10 minutes before lock)
        val policyEngine = PolicyEngine(fakeRepo, fakeUsage, clock, zone)

        val scheduler = TestScheduleScheduler()
        var lockCallbackFired = false
        val watcher = ScheduleWatcherImpl(
            targetRepository = fakeRepo,
            policyEngine = policyEngine,
            clock = clock,
            zoneId = zone,
            scheduler = scheduler,
            onScheduleLocked = { lockCallbackFired = true }
        )

        // Watcher starts after process restart
        watcher.start()
        assertNull("Pending runnable must not exist before fresh foreground evidence", scheduler.scheduledRunnable)

        // Fresh foreground event arrives at 13:50
        watcher.onForegroundChanged(targetApp.packageName)
        assertNotNull("Deadline must be reconstructed", scheduler.scheduledRunnable)
        assertEquals(10 * 60 * 1000L, scheduler.scheduledDelayMillis) // 10 minutes remaining

        // Advance 10 minutes to 14:00 and trigger callback
        clock.advanceBoth(10 * 60 * 1000L)
        scheduler.trigger()

        assertTrue("Schedule lock must fire at boundary", lockCallbackFired)
    }

    @Test
    fun test17_restartWithPendingUsageDeadline_reconstructedOnFreshForeground() {
        val zone = ZoneId.of("UTC")
        val clock = TestClock.at(2026, 9, 4, 10, 0, 0, 0, zone)
        // Daily limit 60 seconds (60,000ms), 40,000ms already consumed before restart
        val targetApp = LockedApp(
            packageName = "com.test.limit.recovery",
            enabled = true,
            timeLimit = TimeLimit(enabled = true, dailyLimitSeconds = 60)
        )
        val fakeRepo = FakeTargetRepository(mutableMapOf(targetApp.packageName to targetApp))
        val fakeUsage = UsageTracker(clock, zoneId = zone)
        fakeUsage.recordUsageSegment(targetApp.packageName, clock.wallTimeMillis() - 100_000L, clock.wallTimeMillis() - 60_000L, 40_000L)
        assertEquals(40_000L, fakeUsage.getTodayUsage(targetApp.packageName))

        val policyEngine = PolicyEngine(fakeRepo, fakeUsage, clock, zone)
        val scheduler = TestLimitScheduler()
        var lockCallbackFired = false
        val watcher = UsageLimitWatcherImpl(
            targetRepository = fakeRepo,
            usageTracker = fakeUsage,
            policyEngine = policyEngine,
            scheduler = scheduler,
            onLimitReached = { lockCallbackFired = true }
        )

        // Service restart
        watcher.start()
        assertNull("Usage deadline must not be scheduled without foreground evidence", scheduler.scheduledRunnable)

        // Fresh foreground event arrives
        fakeUsage.startSession(targetApp.packageName)
        watcher.onForegroundChanged(targetApp.packageName)

        assertNotNull("Usage deadline must be reconstructed", scheduler.scheduledRunnable)
        assertEquals(20_000L, scheduler.scheduledDelayMillis) // 60s - 40s = 20s remaining

        // Advance 20 seconds
        clock.advanceBoth(20_000L)
        scheduler.trigger()

        assertTrue("Usage lock must fire when remaining deadline expires", lockCallbackFired)
    }

    @Test
    fun test18_restartAfterDeadline_locksImmediatelyOnFreshForeground() {
        val zone = ZoneId.of("UTC")
        // Process died and restarted at 14:15 (already inside 14:00-18:00 lock window)
        val clock = TestClock.at(2026, 9, 4, 14, 15, 0, 0, zone)
        val targetApp = LockedApp(
            packageName = "com.test.after.deadline",
            enabled = true,
            schedule = TimeSchedule(enabled = true, startHour = 14, startMinute = 0, endHour = 18, endMinute = 0)
        )
        val fakeRepo = FakeTargetRepository(mutableMapOf(targetApp.packageName to targetApp))
        val fakeUsage = UsageTracker(clock, zoneId = zone)
        val policyEngine = PolicyEngine(fakeRepo, fakeUsage, clock, zone)

        val scheduler = TestScheduleScheduler()
        var lockFired = false
        val watcher = ScheduleWatcherImpl(
            targetRepository = fakeRepo,
            policyEngine = policyEngine,
            clock = clock,
            zoneId = zone,
            scheduler = scheduler,
            onScheduleLocked = { lockFired = true }
        )

        watcher.start()
        assertFalse("No blind lock before foreground event", lockFired)

        // Fresh foreground event arrives
        watcher.onForegroundChanged(targetApp.packageName)

        // Because it is already in schedule, it must lock immediately without scheduling a future timer
        assertTrue("Must lock immediately if deadline has already passed", lockFired)
        assertNull("No future timer should be pending", scheduler.scheduledRunnable)
    }

    @Test
    fun test19_staleCallbackFromOldGenerationRejectedAfterRestart() {
        val zone = ZoneId.of("UTC")
        val clock = TestClock.at(2026, 9, 4, 10, 0, 0, 0, zone)
        val targetApp = LockedApp(
            packageName = "com.test.stale.gen",
            enabled = true,
            schedule = TimeSchedule(enabled = true, startHour = 12, startMinute = 0, endHour = 14, endMinute = 0)
        )
        val fakeRepo = FakeTargetRepository(mutableMapOf(targetApp.packageName to targetApp))
        val fakeUsage = UsageTracker(clock, zoneId = zone)
        val policyEngine = PolicyEngine(fakeRepo, fakeUsage, clock, zone)

        val scheduler = TestScheduleScheduler()
        var lockCount = 0
        val watcher = ScheduleWatcherImpl(
            targetRepository = fakeRepo,
            policyEngine = policyEngine,
            clock = clock,
            zoneId = zone,
            scheduler = scheduler,
            onScheduleLocked = { lockCount++ }
        )

        watcher.start()
        watcher.onForegroundChanged(targetApp.packageName)
        val oldRunnable = scheduler.scheduledRunnable
        assertNotNull(oldRunnable)

        // Service reconnect / restart: increments generation and resets active package
        watcher.start()

        // Old runnable fires belatedly from old generation
        oldRunnable!!.run()

        assertEquals("Stale callback from previous generation must be rejected", 0, lockCount)
    }

    @Test
    fun test20_targetSwitchDuringRecovery_cancelsOldDeadline() {
        val zone = ZoneId.of("UTC")
        val clock = TestClock.at(2026, 9, 4, 10, 0, 0, 0, zone)
        val appA = LockedApp("com.app.a", enabled = true, schedule = TimeSchedule(enabled = true, startHour = 11, startMinute = 0, endHour = 12, endMinute = 0))
        val appB = LockedApp("com.app.b", enabled = true, schedule = TimeSchedule(enabled = true, startHour = 16, startMinute = 0, endHour = 17, endMinute = 0))
        val fakeRepo = FakeTargetRepository(mutableMapOf(appA.packageName to appA, appB.packageName to appB))
        val fakeUsage = UsageTracker(clock, zoneId = zone)
        val policyEngine = PolicyEngine(fakeRepo, fakeUsage, clock, zone)

        val scheduler = TestScheduleScheduler()
        val watcher = ScheduleWatcherImpl(
            targetRepository = fakeRepo,
            policyEngine = policyEngine,
            clock = clock,
            zoneId = zone,
            scheduler = scheduler,
            onScheduleLocked = {}
        )

        watcher.start()
        watcher.onForegroundChanged(appA.packageName)
        assertEquals(60 * 60 * 1000L, scheduler.scheduledDelayMillis) // 1 hour for A

        // User switches to App B
        watcher.onForegroundChanged(appB.packageName)
        assertEquals(6 * 60 * 60 * 1000L, scheduler.scheduledDelayMillis) // 6 hours for B
    }

    @Test
    fun test21_policyUpdateDuringRecovery_reschedulesCorrectly() {
        val zone = ZoneId.of("UTC")
        val clock = TestClock.at(2026, 9, 4, 10, 0, 0, 0, zone)
        val targetApp = LockedApp(
            packageName = "com.test.update.recovery",
            enabled = true,
            timeLimit = TimeLimit(enabled = true, dailyLimitMinutes = 30) // 30 mins
        )
        val fakeRepo = FakeTargetRepository(mutableMapOf(targetApp.packageName to targetApp))
        val fakeUsage = UsageTracker(clock, zoneId = zone)
        val policyEngine = PolicyEngine(fakeRepo, fakeUsage, clock, zone)

        val scheduler = TestLimitScheduler()
        val watcher = UsageLimitWatcherImpl(
            targetRepository = fakeRepo,
            usageTracker = fakeUsage,
            policyEngine = policyEngine,
            scheduler = scheduler,
            onLimitReached = {}
        )

        watcher.start()
        watcher.onForegroundChanged(targetApp.packageName)
        assertEquals(30 * 60 * 1000L, scheduler.scheduledDelayMillis)

        // Policy updated while process running / watching: limit extended to 60 mins
        fakeRepo.apps[targetApp.packageName] = targetApp.copy(
            timeLimit = TimeLimit(enabled = true, dailyLimitMinutes = 60)
        )
        watcher.onPolicyUpdated(targetApp.packageName)

        assertEquals("Deadline must reschedule to new 60min limit", 60 * 60 * 1000L, scheduler.scheduledDelayMillis)
    }

    @Test
    fun test22_dailyLimitAlreadyExhaustedOnRestart_evaluatesLockImmediatelyOnForeground() {
        val zone = ZoneId.of("UTC")
        val clock = TestClock.at(2026, 9, 4, 10, 0, 0, 0, zone)
        val targetApp = LockedApp(
            packageName = "com.test.exhausted",
            enabled = true,
            timeLimit = TimeLimit(enabled = true, dailyLimitMinutes = 10)
        )
        val fakeRepo = FakeTargetRepository(mutableMapOf(targetApp.packageName to targetApp))
        val fakeUsage = UsageTracker(clock, zoneId = zone)
        // 10 minutes already used
        fakeUsage.recordUsageSegment(targetApp.packageName, clock.wallTimeMillis() - 600_000L, clock.wallTimeMillis(), 600_000L)
        val policyEngine = PolicyEngine(fakeRepo, fakeUsage, clock, zone)

        // Immediate policy decision before any new usage is already LOCK
        assertEquals(PolicyDecision.LOCK, policyEngine.evaluate(targetApp.packageName))

        val scheduler = TestLimitScheduler()
        var lockFired = false
        val watcher = UsageLimitWatcherImpl(
            targetRepository = fakeRepo,
            usageTracker = fakeUsage,
            policyEngine = policyEngine,
            scheduler = scheduler,
            onLimitReached = { lockFired = true }
        )

        watcher.start()
        assertFalse("No blind lock before foreground event", lockFired)

        // Fresh foreground arrives: remaining is <= 0, schedules immediate runnable
        watcher.onForegroundChanged(targetApp.packageName)
        assertEquals(0L, scheduler.scheduledDelayMillis)
        scheduler.trigger()
        assertTrue(lockFired)
    }

    @Test
    fun test23_noBlindLockBeforeFreshForegroundEvent() {
        val zone = ZoneId.of("UTC")
        // Clock is inside lock schedule (14:00-18:00)
        val clock = TestClock.at(2026, 9, 4, 15, 0, 0, 0, zone)
        val targetApp = LockedApp(
            packageName = "com.android.chrome",
            enabled = true,
            schedule = TimeSchedule(enabled = true, startHour = 14, startMinute = 0, endHour = 18, endMinute = 0)
        )
        val fakeRepo = FakeTargetRepository(mutableMapOf(targetApp.packageName to targetApp))
        val fakeUsage = UsageTracker(clock, zoneId = zone)
        val policyEngine = PolicyEngine(fakeRepo, fakeUsage, clock, zone)

        var scheduleLockFired = false
        var usageLockFired = false

        val scheduleWatcher = ScheduleWatcherImpl(
            targetRepository = fakeRepo,
            policyEngine = policyEngine,
            clock = clock,
            zoneId = zone,
            scheduler = TestScheduleScheduler(),
            onScheduleLocked = { scheduleLockFired = true }
        )

        val usageWatcher = UsageLimitWatcherImpl(
            targetRepository = fakeRepo,
            usageTracker = fakeUsage,
            policyEngine = policyEngine,
            scheduler = TestLimitScheduler(),
            onLimitReached = { usageLockFired = true }
        )

        // Both watchers start after process cold start
        scheduleWatcher.start()
        usageWatcher.start()

        // Invariant: Must NOT blindly lock previous target or default target before fresh foreground event
        assertFalse("ScheduleWatcher must not blind-lock on restart", scheduleLockFired)
        assertFalse("UsageLimitWatcher must not blind-lock on restart", usageLockFired)
    }

    @Test
    fun test24_corruptedAndNegativeUsageInPersistedData_safelySanitized() {
        val zone = ZoneId.of("UTC")
        val clock = TestClock.at(2026, 9, 4, 10, 0, 0, 0, zone)
        val tracker = UsageTracker(clock = clock, zoneId = zone)

        // Corrupted payload with negative values, blank keys, and malformed strings
        val corruptedJson = """
            {
                "": 50000,
                "2026-09-04_com.test.corrupted": -999999,
                "2026-09-04_com.test.valid": 12000
            }
        """.trimIndent()

        tracker.loadFromJson(corruptedJson)

        assertEquals("Negative usage must be ignored / sanitized to 0", 0L, tracker.getTodayUsage("com.test.corrupted"))
        assertEquals("Valid usage must be loaded accurately", 12000L, tracker.getTodayUsage("com.test.valid"))
    }

    // --- HELPER TEST DOUBLES ---

    private class FakeTargetRepository(val apps: MutableMap<String, LockedApp> = mutableMapOf()) : com.example.selfdisciplinepoc01.target.repository.TargetRepository {
        override fun getLockedPackages(): Flow<List<LockedApp>> = kotlinx.coroutines.flow.flowOf(apps.values.toList())
        override fun isLocked(packageName: String): Boolean = apps[packageName]?.enabled == true
        override fun getTarget(packageName: String): LockedApp? = apps[packageName]
        override suspend fun add(packageName: String) {
            apps[packageName] = LockedApp(packageName, enabled = true)
        }
        override suspend fun remove(packageName: String) {
            apps.remove(packageName)
        }
        override suspend fun setEnabled(packageName: String, enabled: Boolean) {
            apps[packageName] = (apps[packageName] ?: LockedApp(packageName)).copy(enabled = enabled)
        }
        override suspend fun updatePolicy(packageName: String, schedule: TimeSchedule?, timeLimit: TimeLimit?) {
            apps[packageName] = (apps[packageName] ?: LockedApp(packageName)).copy(schedule = schedule, timeLimit = timeLimit)
        }
    }
}
