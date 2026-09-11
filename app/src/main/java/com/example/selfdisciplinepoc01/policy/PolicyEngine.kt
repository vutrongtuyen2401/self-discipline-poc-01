package com.example.selfdisciplinepoc01.policy

import android.util.Log
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
 * LEGACY COMPONENT (POC-01):
 * Evaluates whether a package should be locked at the current moment
 * based on its configured schedule, daily time limit, and usage.
 *
 * CANONICAL ISOLATION (Phase 1A):
 * PolicyEngine KHÔNG ĐƯỢC PHÉP làm nguồn chân lý (authority) cho Hệ thống Phong Ấn Dục Vọng.
 * Đối với Vault Apps, CanonicalLockPolicy là nguồn chân lý duy nhất.
 */
@Deprecated(
    message = "Legacy POC-01 Technical Policy Engine. Do not use for Canonical Self-Discipline Vault logic.",
    level = DeprecationLevel.WARNING
)
class PolicyEngine(
    private val targetRepository: TargetRepository,
    private val usageProvider: UsageProvider,
    private val clock: Clock = SystemClockImpl(),
    private val zoneIdProvider: () -> ZoneId = { ZoneId.systemDefault() }
) {

    constructor(
        targetRepository: TargetRepository,
        usageProvider: UsageProvider,
        clock: Clock = SystemClockImpl(),
        zoneId: ZoneId
    ) : this(targetRepository, usageProvider, clock, { zoneId })

    /**
     * Evaluates policy for [packageName].
     */
    fun evaluate(packageName: String): PolicyDecision {
        val app = targetRepository.getTarget(packageName) ?: return PolicyDecision.ALLOW
        return evaluate(app)
    }

    /**
     * Checks if a target application is configured and registered.
     */
    fun isTargetConfigured(packageName: String): Boolean {
        val app = targetRepository.getTarget(packageName)
        return app != null && app.enabled
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
            val currentZone = zoneIdProvider()
            if (ScheduleEvaluator.isWithinSchedule(schedule, nowWall, currentZone)) {
                return PolicyDecision.LOCK
            }
        }

        // 4. If daily usage >= limit => LOCK (exactly reaching the limit locks immediately)
        if (hasLimit) {
            val todayUsage = usageProvider.getTodayUsage(app.packageName)
            val limitMillis = limit!!.limitMillis
            Log.d(TAG, "[POLICY: EVAL_LIMIT] pkg=${app.packageName}, todayUsage=${todayUsage}ms, limitMillis=${limitMillis}ms")
            if (todayUsage >= limitMillis) {
                return PolicyDecision.LOCK
            }
        }

        // 5. Otherwise => ALLOW (policies are configured, but neither condition is currently violated)
        return PolicyDecision.ALLOW
    }

    companion object {
        private const val TAG = "PolicyEngine"
    }
}
