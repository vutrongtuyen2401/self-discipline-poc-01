package com.example.selfdisciplinepoc01.policy

import com.example.selfdisciplinepoc01.target.model.LockedApp
import com.example.selfdisciplinepoc01.target.repository.TargetRepository
import com.example.selfdisciplinepoc01.time.Clock
import com.example.selfdisciplinepoc01.time.SystemClockImpl
import com.example.selfdisciplinepoc01.usage.UsageProvider
import java.time.ZoneId

/**
 * Result of evaluating policy for a target package.
 */
enum class PolicyDecision {
    /** Target application must be locked right now */
    LOCK,
    /** Target application is allowed to be used right now */
    ALLOW
}

/**
 * Evaluates whether a package should be locked at the current moment
 * based on its configured schedule, daily time limit, and usage.
 *
 * Separation of concerns:
 * - PolicyEngine does NOT manage sessions or UI.
 * - PolicyEngine only answers: LOCK or ALLOW.
 */
class PolicyEngine(
    private val targetRepository: TargetRepository,
    private val usageProvider: UsageProvider,
    private val clock: Clock = SystemClockImpl(),
    private val zoneId: ZoneId = ZoneId.systemDefault()
) {

    /**
     * Evaluates policy for [packageName].
     */
    fun evaluate(packageName: String): PolicyDecision {
        val app = targetRepository.getTarget(packageName) ?: return PolicyDecision.ALLOW
        return evaluate(app)
    }

    /**
     * Evaluates policy for a specific [LockedApp].
     */
    fun evaluate(app: LockedApp): PolicyDecision {
        // 1. If target does not exist or is disabled => ALLOW
        if (!app.enabled) {
            return PolicyDecision.ALLOW
        }

        val schedule = app.schedule
        val limit = app.timeLimit
        val hasSchedule = schedule != null && schedule.enabled
        val hasLimit = limit != null && limit.enabled && limit.dailyLimitMinutes >= 0

        // 2. If target has no active schedule and no active limit => LOCK (Phase 06/07 24/7 behavior)
        if (!hasSchedule && !hasLimit) {
            return PolicyDecision.LOCK
        }

        // 3. If schedule is active => LOCK
        if (hasSchedule) {
            val nowWall = clock.wallTimeMillis()
            if (ScheduleEvaluator.isWithinSchedule(schedule, nowWall, zoneId)) {
                return PolicyDecision.LOCK
            }
        }

        // 4. If daily usage >= limit => LOCK (exactly reaching the limit locks immediately)
        if (hasLimit) {
            val todayUsage = usageProvider.getTodayUsage(app.packageName)
            val limitMillis = limit!!.limitMillis
            if (todayUsage >= limitMillis) {
                return PolicyDecision.LOCK
            }
        }

        // 5. Otherwise => ALLOW (policies are configured, but neither condition is currently violated)
        return PolicyDecision.ALLOW
    }
}
