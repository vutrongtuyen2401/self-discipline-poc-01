package com.example.selfdisciplinepoc01.usage

import android.os.Handler
import android.os.Looper
import android.util.Log
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
 * Interface contract for UsageLimitWatcher.
 */
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
    private val onLimitReached: (packageName: String) -> Unit
) : UsageLimitWatcher {

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
            isRunning = false
            currentGeneration++
            scheduler.cancel()
            activePackage = null
            Log.d(TAG, "[WATCHER: STOPPED] Watcher completely stopped")
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
            // 1. Stale Callback Guard: verify watcher is running, generation matches, and package matches
            if (!isRunning) {
                Log.w(TAG, "[STALE_GUARD] Watcher is stopped, ignoring deadline callback for $capturedPackage")
                return
            }
            if (capturedGeneration != currentGeneration) {
                Log.w(
                    TAG,
                    "[STALE_GUARD] Generation mismatch ($capturedGeneration != $currentGeneration), ignoring callback for $capturedPackage"
                )
                return
            }
            if (capturedPackage != activePackage) {
                Log.w(
                    TAG,
                    "[STALE_GUARD] Package mismatch ($capturedPackage != activePackage=$activePackage), ignoring callback"
                )
                return
            }

            // 2. Non-blocking Policy Evaluation: NEVER blindly lock
            val decision = policyEngine.evaluate(capturedPackage)
            Log.i(TAG, "[WATCHER: EVALUATED] Deadline fired for $capturedPackage -> PolicyDecision: $decision")

            when (decision) {
                PolicyDecision.LOCK -> {
                    // Confirmed LOCK: notify shared lock callback
                    Log.i(TAG, "[WATCHER: LOCK_TRIGGERED] Daily limit reached -> invoking onLimitReached($capturedPackage)")
                    onLimitReached(capturedPackage)
                }
                PolicyDecision.ALLOW -> {
                    // Scheduler jitter or timing race: remaining usage is still below limit.
                    // Reschedule with newly recomputed remaining time!
                    Log.i(TAG, "[WATCHER: JITTER_RESCHEDULE] Still ALLOW, recomputing deadline for $capturedPackage")
                    scheduleDeadlineLocked(capturedPackage, currentGeneration)
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
