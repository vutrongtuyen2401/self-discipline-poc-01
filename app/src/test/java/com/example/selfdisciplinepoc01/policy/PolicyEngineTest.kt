package com.example.selfdisciplinepoc01.policy

import com.example.selfdisciplinepoc01.target.model.LockedApp
import com.example.selfdisciplinepoc01.target.model.TimeLimit
import com.example.selfdisciplinepoc01.target.model.TimeSchedule
import com.example.selfdisciplinepoc01.target.repository.TargetRepository
import com.example.selfdisciplinepoc01.time.TestClock
import com.example.selfdisciplinepoc01.usage.UsageProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

class PolicyEngineTest {

    private val testZone = ZoneId.of("UTC")

    private class FakeUsageProvider(private val usageMap: MutableMap<String, Long> = mutableMapOf()) : UsageProvider {
        override fun getTodayUsage(packageName: String): Long = usageMap[packageName] ?: 0L
        fun setUsage(packageName: String, millis: Long) {
            usageMap[packageName] = millis
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

    // 1. Target not configured or disabled => ALLOW
    @Test
    fun target_notConfiguredOrDisabled_returnsAllow() {
        val repo = FakeTargetRepository()
        val usage = FakeUsageProvider()
        val clock = TestClock.at(2026, 9, 3, 12, 0, zoneId = testZone)
        val engine = PolicyEngine(repo, usage, clock, testZone)

        assertEquals("Unregistered package must be ALLOWED", PolicyDecision.ALLOW, engine.evaluate("com.random.app"))

        repo.apps["com.disabled.app"] = LockedApp("com.disabled.app", enabled = false)
        assertEquals("Disabled package must be ALLOWED", PolicyDecision.ALLOW, engine.evaluate("com.disabled.app"))
    }

    // 2. Default Chrome (enabled, no schedule, no limit) => LOCK (Phase 06/07-B 24/7 behavior)
    @Test
    fun target_defaultChrome_noPolicy_returnsLock() {
        val repo = FakeTargetRepository()
        repo.apps["com.android.chrome"] = LockedApp("com.android.chrome", enabled = true, schedule = null, timeLimit = null)
        val usage = FakeUsageProvider()
        val clock = TestClock.at(2026, 9, 3, 12, 0, zoneId = testZone)
        val engine = PolicyEngine(repo, usage, clock, testZone)

        assertEquals("Default target with no policy is LOCKED 24/7", PolicyDecision.LOCK, engine.evaluate("com.android.chrome"))
    }

    // 3. Schedule active => LOCK
    @Test
    fun target_scheduleActive_returnsLock() {
        val repo = FakeTargetRepository()
        repo.apps["com.android.chrome"] = LockedApp(
            "com.android.chrome",
            enabled = true,
            schedule = TimeSchedule(enabled = true, startHour = 9, startMinute = 0, endHour = 17, endMinute = 0)
        )
        val usage = FakeUsageProvider()
        // 10:00 is inside 09:00..17:00
        val clock = TestClock.at(2026, 9, 3, 10, 0, zoneId = testZone)
        val engine = PolicyEngine(repo, usage, clock, testZone)

        assertEquals("Active schedule must LOCK", PolicyDecision.LOCK, engine.evaluate("com.android.chrome"))
    }

    // 4. Schedule inactive => ALLOW
    @Test
    fun target_scheduleInactive_returnsAllow() {
        val repo = FakeTargetRepository()
        repo.apps["com.android.chrome"] = LockedApp(
            "com.android.chrome",
            enabled = true,
            schedule = TimeSchedule(enabled = true, startHour = 9, startMinute = 0, endHour = 17, endMinute = 0)
        )
        val usage = FakeUsageProvider()
        // 18:00 is outside 09:00..17:00
        val clock = TestClock.at(2026, 9, 3, 18, 0, zoneId = testZone)
        val engine = PolicyEngine(repo, usage, clock, testZone)

        assertEquals("Inactive schedule must ALLOW", PolicyDecision.ALLOW, engine.evaluate("com.android.chrome"))
    }

    // 5. Daily limit: below limit => ALLOW
    @Test
    fun target_limitBelowThreshold_returnsAllow() {
        val repo = FakeTargetRepository()
        repo.apps["com.android.chrome"] = LockedApp(
            "com.android.chrome",
            enabled = true,
            timeLimit = TimeLimit(enabled = true, dailyLimitMinutes = 30)
        )
        val usage = FakeUsageProvider()
        // 29 minutes and 59 seconds used (1,799,000 ms)
        usage.setUsage("com.android.chrome", 1_799_000L)
        val clock = TestClock.at(2026, 9, 3, 14, 0, zoneId = testZone)
        val engine = PolicyEngine(repo, usage, clock, testZone)

        assertEquals("Usage below limit must ALLOW", PolicyDecision.ALLOW, engine.evaluate("com.android.chrome"))
    }

    // 6. Daily limit: exactly reached (30:00) => LOCK
    @Test
    fun target_limitExactlyReached_returnsLock() {
        val repo = FakeTargetRepository()
        repo.apps["com.android.chrome"] = LockedApp(
            "com.android.chrome",
            enabled = true,
            timeLimit = TimeLimit(enabled = true, dailyLimitMinutes = 30)
        )
        val usage = FakeUsageProvider()
        // Exactly 30 minutes used (1,800,000 ms)
        usage.setUsage("com.android.chrome", 1_800_000L)
        val clock = TestClock.at(2026, 9, 3, 14, 0, zoneId = testZone)
        val engine = PolicyEngine(repo, usage, clock, testZone)

        assertEquals("Usage exactly reaching limit must LOCK", PolicyDecision.LOCK, engine.evaluate("com.android.chrome"))
    }

    // 7. Daily limit: exceeded (30:01) => LOCK
    @Test
    fun target_limitExceeded_returnsLock() {
        val repo = FakeTargetRepository()
        repo.apps["com.android.chrome"] = LockedApp(
            "com.android.chrome",
            enabled = true,
            timeLimit = TimeLimit(enabled = true, dailyLimitMinutes = 30)
        )
        val usage = FakeUsageProvider()
        // 30 minutes and 1 second used (1,801,000 ms)
        usage.setUsage("com.android.chrome", 1_801_000L)
        val clock = TestClock.at(2026, 9, 3, 14, 0, zoneId = testZone)
        val engine = PolicyEngine(repo, usage, clock, testZone)

        assertEquals("Usage exceeding limit must LOCK", PolicyDecision.LOCK, engine.evaluate("com.android.chrome"))
    }

    // 8. Schedule + Limit combination
    @Test
    fun target_scheduleAndLimitCombination() {
        val repo = FakeTargetRepository()
        repo.apps["com.android.chrome"] = LockedApp(
            "com.android.chrome",
            enabled = true,
            // Schedule 22:00..07:00
            schedule = TimeSchedule(enabled = true, startHour = 22, startMinute = 0, endHour = 7, endMinute = 0),
            // Limit 15 minutes
            timeLimit = TimeLimit(enabled = true, dailyLimitMinutes = 15)
        )
        val usage = FakeUsageProvider()
        val clock = TestClock.at(2026, 9, 3, 12, 0, zoneId = testZone)
        val engine = PolicyEngine(repo, usage, clock, testZone)

        // Case A: 12:00 (outside schedule), 10 min usage (under 15 min limit) => ALLOW
        usage.setUsage("com.android.chrome", 10 * 60_000L)
        assertEquals("Outside schedule and under limit => ALLOW", PolicyDecision.ALLOW, engine.evaluate("com.android.chrome"))

        // Case B: 12:00 (outside schedule), 15 min usage (limit reached) => LOCK
        usage.setUsage("com.android.chrome", 15 * 60_000L)
        assertEquals("Outside schedule but limit reached => LOCK", PolicyDecision.LOCK, engine.evaluate("com.android.chrome"))

        // Case C: 23:00 (inside schedule), 5 min usage (under limit) => LOCK (due to schedule)
        usage.setUsage("com.android.chrome", 5 * 60_000L)
        clock.setWallTime(TestClock.at(2026, 9, 3, 23, 0, zoneId = testZone).wallTimeMillis())
        assertEquals("Inside schedule even if under limit => LOCK", PolicyDecision.LOCK, engine.evaluate("com.android.chrome"))
    }
}
