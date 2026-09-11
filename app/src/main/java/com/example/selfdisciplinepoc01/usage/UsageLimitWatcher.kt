package com.example.selfdisciplinepoc01.usage

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.selfdisciplinepoc01.diagnostics.DiagnosticEvent
import com.example.selfdisciplinepoc01.diagnostics.DiagnosticEventType
import com.example.selfdisciplinepoc01.diagnostics.DiagnosticLogger
import com.example.selfdisciplinepoc01.diagnostics.DiagnosticLoggerProvider
import com.example.selfdisciplinepoc01.policy.PolicyDecision
import com.example.selfdisciplinepoc01.policy.PolicyEngine
import com.example.selfdisciplinepoc01.target.repository.TargetRepository

/**
 * Scheduler abstraction for [UsageLimitWatcher] to enable deterministic unit testing
 * and separation from Android Handler / Looper internals.
 */
interface LimitScheduler {
    fun schedule(delayMillis: Long, action: Runnable)
    fun cancel()
}

class HandlerLimitScheduler(
    private val handler: Handler = Handler(Looper.getMainLooper())
) : LimitScheduler {
    private var pendingRunnable: Runnable? = null

    override fun schedule(delayMillis: Long, action: Runnable) {
        cancel()
        pendingRunnable = action
        if (delayMillis <= 0) {
            handler.post(action)
        } else {
            handler.postDelayed(action, delayMillis)
        }
    }

    override fun cancel() {
        pendingRunnable?.let { handler.removeCallbacks(it) }
        pendingRunnable = null
    }
}

/**
 * LEGACY COMPONENT (POC-01):
 * Interface contract for UsageLimitWatcher.
 *
 * CANONICAL ISOLATION (Phase 1A):
 * Thuộc cơ chế giới hạn thời gian sử dụng POC-01. Không sử dụng cho Hệ thống Phong Ấn Dục Vọng.
 */
@Deprecated(
    message = "Legacy POC-01 Usage Limit Watcher. Do not use for Canonical Self-Discipline rules.",
    level = DeprecationLevel.WARNING
)
interface UsageLimitWatcher {
    fun start()
    fun onForegroundChanged(packageName: String?)
    fun onPolicyUpdated(packageName: String)
    fun stop()
}

/**
 * Dedicated deadline-driven component to enforce daily usage limits in real-time
 * without requiring subsequent Accessibility events.
 *
 * Guaranteed constraints:
 * - NO continuous polling.
 * - NO busy loop / Thread.sleep().
 * - Invalidation generation token protects against stale callbacks.
 * - Double checks PolicyEngine before invoking lock callback.
 * - Shared lock-session entry point invocation only on confirmed LOCK decision.
 */
class UsageLimitWatcherImpl(
    private val targetRepository: TargetRepository,
    private val usageTracker: UsageTracker,
    private val policyEngine: PolicyEngine,
    private val scheduler: LimitScheduler = HandlerLimitScheduler(),
    private val logger: DiagnosticLogger = DiagnosticLoggerProvider.getLogger(),
    private val onLimitReached: (packageName: String) -> Unit
) : UsageLimitWatcher {

    constructor(
        targetRepository: TargetRepository,
        usageTracker: UsageTracker,
        policyEngine: PolicyEngine,
        scheduler: LimitScheduler = HandlerLimitScheduler(),
        onLimitReached: (packageName: String) -> Unit
    ) : this(targetRepository, usageTracker, policyEngine, scheduler, DiagnosticLoggerProvider.getLogger(), onLimitReached)

    private val lock = Any()
    private var currentGeneration: Long = 0L
    private var activePackage: String? = null
    private var isRunning: Boolean = true

    override fun start() {
        synchronized(lock) {
            isRunning = true
            currentGeneration++
            scheduler.cancel()
            activePackage = null
            Log.i(TAG, "[WATCHER: STARTED] Watcher started / recovered (gen=$currentGeneration); awaiting fresh foreground event")
            logger.info(
                DiagnosticEvent(
                    type = DiagnosticEventType.USAGE_WATCHER_STARTED,
                    message = "UsageLimitWatcher started / recovered",
                    generation = currentGeneration
                )
            )
        }
    }

    override fun onForegroundChanged(packageName: String?) {
        val cleanPkg = packageName?.trim()?.takeIf { it.isNotBlank() }

        synchronized(lock) {
            if (!isRunning) return

            // If already actively watching this exact package, do not reschedule or create duplicate timers
            if (cleanPkg != null && cleanPkg == activePackage) {
                Log.d(TAG, "[WATCHER: IDEMPOTENT] Already watching $cleanPkg, skipping duplicate schedule")
                return
            }

            // Invalidate any existing callback
            currentGeneration++
            scheduler.cancel()
            activePackage = cleanPkg

            if (cleanPkg == null) {
                Log.d(TAG, "[WATCHER: CLEARED] Foreground is null or non-target, watcher cleared")
                return
            }

            // Schedule deadline for the new active package if eligible
            scheduleDeadlineLocked(cleanPkg, currentGeneration)
        }
    }

    override fun onPolicyUpdated(packageName: String) {
        val cleanPkg = packageName.trim()
        synchronized(lock) {
            if (!isRunning) return
            if (activePackage == cleanPkg) {
                Log.i(TAG, "[WATCHER: POLICY_UPDATED] Invalidate and reschedule for $cleanPkg")
                currentGeneration++
                scheduler.cancel()
                scheduleDeadlineLocked(cleanPkg, currentGeneration)
            }
        }
    }

    override fun stop() {
        synchronized(lock) {
            if (!isRunning) {
                Log.d(TAG, "[WATCHER: STOP_SKIPPED] Watcher already stopped")
                return
            }
            isRunning = false
            currentGeneration++
            scheduler.cancel()
            activePackage = null
            Log.i(TAG, "[WATCHER: STOPPED] Watcher completely stopped")
            logger.info(
                DiagnosticEvent(
                    type = DiagnosticEventType.USAGE_WATCHER_STOPPED,
                    message = "UsageLimitWatcher completely stopped",
                    generation = currentGeneration
                )
            )
        }
    }

    private fun scheduleDeadlineLocked(packageName: String, generation: Long) {
        val app = targetRepository.getTarget(packageName)
        if (app == null || !app.enabled) {
            Log.d(TAG, "[WATCHER: SKIP] Target $packageName not found or disabled")
            return
        }

        val limit = app.timeLimit
        if (limit == null || !limit.enabled) {
            Log.d(TAG, "[WATCHER: SKIP] Target $packageName has no enabled daily limit")
            return
        }

        val limitMillis = limit.limitMillis
        val usedMillis = usageTracker.getTodayUsage(packageName)
        val remainingMillis = limitMillis - usedMillis

        Log.i(
            TAG,
            "[WATCHER: SCHEDULE] pkg=$packageName, limit=${limitMillis}ms, used=${usedMillis}ms, remaining=${remainingMillis}ms (gen=$generation)"
        )
        logger.debug(
            DiagnosticEvent(
                type = DiagnosticEventType.USAGE_DEADLINE_SCHEDULED,
                message = "Usage deadline scheduled for $packageName in ${remainingMillis}ms",
                packageName = packageName,
                generation = generation,
                remainingMillis = remainingMillis,
                currentUsageMillis = usedMillis,
                limitMillis = limitMillis,
                dailyLimitEnabled = true
            )
        )

        // Capture parameters for the deadline runnable
        val capturedGeneration = generation
        val capturedPackage = packageName

        val deadlineRunnable = Runnable {
            handleDeadlineFired(capturedPackage, capturedGeneration)
        }

        scheduler.schedule(maxOf(0L, remainingMillis), deadlineRunnable)
    }

    private fun handleDeadlineFired(capturedPackage: String, capturedGeneration: Long) {
        synchronized(lock) {
            logger.debug(
                DiagnosticEvent(
                    type = DiagnosticEventType.USAGE_DEADLINE_CALLBACK,
                    message = "Usage deadline callback fired for $capturedPackage (gen=$capturedGeneration)",
                    packageName = capturedPackage,
                    generation = capturedGeneration,
                    watcherRunning = isRunning
                )
            )

            // 1. Stale Callback Guard: verify watcher is running, generation matches, and package matches
            if (!isRunning) {
                Log.w(TAG, "[STALE_GUARD] Watcher is stopped, ignoring deadline callback for $capturedPackage")
                logger.warn(
                    DiagnosticEvent(
                        type = DiagnosticEventType.USAGE_STALE_CALLBACK,
                        message = "Usage deadline callback rejected: watcher stopped",
                        packageName = capturedPackage,
                        generation = capturedGeneration,
                        watcherRunning = false,
                        rejectionReason = "WATCHER_STOPPED"
                    )
                )
                return
            }
            if (capturedGeneration != currentGeneration) {
                Log.w(
                    TAG,
                    "[STALE_GUARD] Generation mismatch ($capturedGeneration != $currentGeneration), ignoring callback for $capturedPackage"
                )
                logger.warn(
                    DiagnosticEvent(
                        type = DiagnosticEventType.USAGE_STALE_CALLBACK,
                        message = "Usage deadline callback rejected: generation mismatch ($capturedGeneration != $currentGeneration)",
                        packageName = capturedPackage,
                        generation = capturedGeneration,
                        watcherRunning = true,
                        rejectionReason = "GENERATION_MISMATCH (captured=$capturedGeneration != current=$currentGeneration)"
                    )
                )
                return
            }
            if (capturedPackage != activePackage) {
                Log.w(
                    TAG,
                    "[STALE_GUARD] Package mismatch ($capturedPackage != activePackage=$activePackage), ignoring callback"
                )
                logger.warn(
                    DiagnosticEvent(
                        type = DiagnosticEventType.USAGE_STALE_CALLBACK,
                        message = "Usage deadline callback rejected: package mismatch ($capturedPackage != activePackage=$activePackage)",
                        packageName = capturedPackage,
                        generation = capturedGeneration,
                        watcherRunning = true,
                        rejectionReason = "PACKAGE_MISMATCH (captured=$capturedPackage != active=$activePackage)"
                    )
                )
                return
            }

            // 2. Non-blocking Policy Evaluation: NEVER blindly lock
            val app = targetRepository.getTarget(capturedPackage)
            val limit = app?.timeLimit
            val usedMillis = usageTracker.getTodayUsage(capturedPackage)
            val limitMillis = limit?.limitMillis ?: 0L
            val isLimitExhausted = limit != null && limit.enabled && (usedMillis >= limitMillis)
            val decision = policyEngine.evaluate(capturedPackage)
            Log.i(TAG, "[WATCHER: EVALUATED] Deadline fired for $capturedPackage -> PolicyDecision: $decision, isLimitExhausted: $isLimitExhausted")

            logger.debug(
                DiagnosticEvent(
                    type = if (isLimitExhausted) DiagnosticEventType.DAILY_LIMIT_EXHAUSTED else DiagnosticEventType.DAILY_LIMIT_REMAINING,
                    message = "Usage evaluated for $capturedPackage: used=${usedMillis}ms, limit=${limitMillis}ms, decision=$decision",
                    packageName = capturedPackage,
                    generation = currentGeneration,
                    remainingMillis = maxOf(0L, limitMillis - usedMillis),
                    currentUsageMillis = usedMillis,
                    limitMillis = limitMillis,
                    dailyLimitEnabled = limit?.enabled ?: false
                )
            )

            when {
                decision == PolicyDecision.LOCK && isLimitExhausted -> {
                    // Confirmed LOCK due to daily limit: notify shared lock callback
                    Log.i(TAG, "[WATCHER: LOCK_TRIGGERED] Daily limit reached -> invoking onLimitReached($capturedPackage)")
                    onLimitReached(capturedPackage)
                }
                decision == PolicyDecision.ALLOW -> {
                    // Scheduler jitter or timing race: remaining usage is still below limit.
                    // Reschedule with newly recomputed remaining time!
                    Log.i(TAG, "[WATCHER: JITTER_RESCHEDULE] Still ALLOW, recomputing deadline for $capturedPackage")
                    scheduleDeadlineLocked(capturedPackage, currentGeneration)
                }
                else -> {
                    // Decision is LOCK but isLimitExhausted is false (e.g. locked due to schedule)
                    // Preserve reason ownership: do not emit DAILY_LIMIT.
                    Log.d(TAG, "[WATCHER: PRESERVE_OWNERSHIP] Locked for other reason, skipping limit lock")
                }
            }
        }
    }

    fun getActivePackage(): String? = synchronized(lock) { activePackage }
    fun getCurrentGeneration(): Long = synchronized(lock) { currentGeneration }

    companion object {
        private const val TAG = "UsageLimitWatcher"
    }
}
