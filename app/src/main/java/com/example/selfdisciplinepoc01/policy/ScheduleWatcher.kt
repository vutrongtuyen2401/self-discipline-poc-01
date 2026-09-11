package com.example.selfdisciplinepoc01.policy

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.selfdisciplinepoc01.diagnostics.DiagnosticEvent
import com.example.selfdisciplinepoc01.diagnostics.DiagnosticEventType
import com.example.selfdisciplinepoc01.diagnostics.DiagnosticLogger
import com.example.selfdisciplinepoc01.diagnostics.DiagnosticLoggerProvider
import com.example.selfdisciplinepoc01.target.repository.TargetRepository
import com.example.selfdisciplinepoc01.time.Clock
import com.example.selfdisciplinepoc01.time.SystemClockImpl
import java.time.ZoneId

/**
 * Scheduler abstraction for [ScheduleWatcher] to enable deterministic unit testing
 * and separation from Android Handler / Looper internals.
 */
interface ScheduleScheduler {
    fun schedule(delayMillis: Long, action: Runnable)
    fun cancel()
}

class HandlerScheduleScheduler(
    private val handler: Handler = Handler(Looper.getMainLooper())
) : ScheduleScheduler {
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
 * Interface contract for ScheduleWatcher.
 */
@Deprecated(
    message = "Legacy POC-01 Schedule Watcher. Do not use for Canonical Self-Discipline.",
    level = DeprecationLevel.WARNING
)
interface ScheduleWatcher {
    fun start()
    fun onForegroundChanged(packageName: String?)
    fun onPolicyUpdated(packageName: String)
    fun stop()
}

/**
 * Dedicated deadline-driven component to enforce schedule boundaries in real-time
 * without polling, busy loops, or periodic timers.
 *
 * Guarantees:
 * - NO continuous polling.
 * - NO busy loop / Thread.sleep().
 * - NO periodic 1-second loops.
 * - Generation token protects against stale callbacks.
 * - Confirms schedule predicate active before emitting lock reason.
 * - Double checks PolicyEngine before invoking lock callback.
 * - Shared lock-session entry point invocation only on confirmed LOCK decision.
 */
class ScheduleWatcherImpl(
    private val targetRepository: TargetRepository,
    private val policyEngine: PolicyEngine,
    private val clock: Clock = SystemClockImpl(),
    private val zoneIdProvider: () -> ZoneId = { ZoneId.systemDefault() },
    private val scheduler: ScheduleScheduler = HandlerScheduleScheduler(),
    private val logger: DiagnosticLogger = DiagnosticLoggerProvider.getLogger(),
    private val onScheduleLocked: (packageName: String) -> Unit
) : ScheduleWatcher {

    constructor(
        targetRepository: TargetRepository,
        policyEngine: PolicyEngine,
        clock: Clock = SystemClockImpl(),
        zoneId: ZoneId,
        scheduler: ScheduleScheduler = HandlerScheduleScheduler(),
        onScheduleLocked: (packageName: String) -> Unit
    ) : this(targetRepository, policyEngine, clock, { zoneId }, scheduler, DiagnosticLoggerProvider.getLogger(), onScheduleLocked)

    constructor(
        targetRepository: TargetRepository,
        policyEngine: PolicyEngine,
        clock: Clock = SystemClockImpl(),
        zoneId: ZoneId,
        scheduler: ScheduleScheduler = HandlerScheduleScheduler(),
        logger: DiagnosticLogger = DiagnosticLoggerProvider.getLogger(),
        onScheduleLocked: (packageName: String) -> Unit
    ) : this(targetRepository, policyEngine, clock, { zoneId }, scheduler, logger, onScheduleLocked)

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
            Log.i(TAG, "[SCHEDULE_WATCHER: STARTED] Watcher started / recovered (gen=$currentGeneration); awaiting fresh foreground event")
            logger.info(
                DiagnosticEvent(
                    type = DiagnosticEventType.SCHEDULE_WATCHER_STARTED,
                    message = "ScheduleWatcher started / recovered",
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
                Log.d(TAG, "[SCHEDULE_WATCHER: IDEMPOTENT] Already watching $cleanPkg, skipping duplicate schedule")
                return
            }

            // Invalidate any existing callback
            currentGeneration++
            scheduler.cancel()
            activePackage = cleanPkg

            if (cleanPkg == null) {
                Log.i(TAG, "[SCHEDULE_WATCHER: CLEARED] Foreground is null or non-target, watcher cleared")
                return
            }

            scheduleBoundaryLocked(cleanPkg, currentGeneration)
        }
    }

    override fun onPolicyUpdated(packageName: String) {
        val cleanPkg = packageName.trim()
        synchronized(lock) {
            if (!isRunning) return
            if (activePackage == cleanPkg) {
                Log.i(TAG, "[SCHEDULE_WATCHER: POLICY_UPDATED] Invalidate and reschedule for $cleanPkg")
                currentGeneration++
                scheduler.cancel()
                scheduleBoundaryLocked(cleanPkg, currentGeneration)
            }
        }
    }

    override fun stop() {
        synchronized(lock) {
            if (!isRunning) {
                Log.d(TAG, "[SCHEDULE_WATCHER: STOP_SKIPPED] Watcher already stopped")
                return
            }
            isRunning = false
            currentGeneration++
            scheduler.cancel()
            activePackage = null
            Log.i(TAG, "[SCHEDULE_WATCHER: STOPPED] Watcher completely stopped")
            logger.info(
                DiagnosticEvent(
                    type = DiagnosticEventType.SCHEDULE_WATCHER_STOPPED,
                    message = "ScheduleWatcher completely stopped",
                    generation = currentGeneration
                )
            )
        }
    }

    private fun scheduleBoundaryLocked(packageName: String, generation: Long) {
        val app = targetRepository.getTarget(packageName)
        if (app == null || !app.enabled) {
            Log.d(TAG, "[SCHEDULE_WATCHER: SKIP] Target $packageName not found or disabled")
            return
        }

        val schedule = app.schedule
        if (schedule == null || !schedule.enabled) {
            Log.d(TAG, "[SCHEDULE_WATCHER: SKIP] Target $packageName has no enabled schedule")
            return
        }

        val nowWall = clock.wallTimeMillis()
        val currentZone = zoneIdProvider()
        val isScheduleActive = ScheduleEvaluator.isWithinSchedule(schedule, nowWall, currentZone)
        val decision = policyEngine.evaluate(packageName)

        // Reason ownership: only emit lock if schedule condition itself is active
        if (decision == PolicyDecision.LOCK && isScheduleActive) {
            Log.i(TAG, "[SCHEDULE_WATCHER: IMMEDIATE_LOCK] Target $packageName is within active schedule -> invoking lock")
            onScheduleLocked(packageName)
            return
        }

        // Schedule is not active (decision is ALLOW or LOCK due to daily limit):
        // Schedule next boundary callback when schedule is expected to transition
        val nextBoundary = ScheduleEvaluator.getNextBoundaryMillis(schedule, nowWall, currentZone)
        if (nextBoundary != null) {
            val delayMillis = maxOf(0L, nextBoundary - nowWall)
            Log.i(
                TAG,
                "[SCHEDULE_WATCHER: SCHEDULE] pkg=$packageName, nowWall=$nowWall, nextBoundary=$nextBoundary, delay=${delayMillis}ms (gen=$generation)"
            )
            logger.debug(
                DiagnosticEvent(
                    type = DiagnosticEventType.SCHEDULE_DEADLINE_SCHEDULED,
                    message = "Schedule boundary scheduled for $packageName in ${delayMillis}ms",
                    packageName = packageName,
                    generation = generation,
                    remainingMillis = delayMillis,
                    scheduleEnabled = true,
                    isScheduleActive = false,
                    timestampMillis = nowWall
                )
            )
            val capturedGen = generation
            val capturedPkg = packageName
            scheduler.schedule(delayMillis) {
                handleBoundaryFired(capturedPkg, capturedGen)
            }
        }
    }

    private fun handleBoundaryFired(capturedPackage: String, capturedGeneration: Long) {
        val nowWall = clock.wallTimeMillis()
        synchronized(lock) {
            logger.debug(
                DiagnosticEvent(
                    type = DiagnosticEventType.SCHEDULE_DEADLINE_CALLBACK,
                    message = "Schedule boundary callback fired for $capturedPackage (gen=$capturedGeneration)",
                    packageName = capturedPackage,
                    generation = capturedGeneration,
                    watcherRunning = isRunning,
                    timestampMillis = nowWall
                )
            )

            // 1. Stale Callback Guard: verify watcher is running, generation matches, and package matches
            if (!isRunning) {
                Log.w(TAG, "[STALE_GUARD] Watcher is stopped, ignoring schedule boundary callback for $capturedPackage")
                logger.warn(
                    DiagnosticEvent(
                        type = DiagnosticEventType.SCHEDULE_STALE_CALLBACK,
                        message = "Schedule boundary callback rejected: watcher stopped",
                        packageName = capturedPackage,
                        generation = capturedGeneration,
                        watcherRunning = false,
                        rejectionReason = "WATCHER_STOPPED",
                        timestampMillis = nowWall
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
                        type = DiagnosticEventType.SCHEDULE_STALE_CALLBACK,
                        message = "Schedule boundary callback rejected: generation mismatch ($capturedGeneration != $currentGeneration)",
                        packageName = capturedPackage,
                        generation = capturedGeneration,
                        watcherRunning = true,
                        rejectionReason = "GENERATION_MISMATCH (captured=$capturedGeneration != current=$currentGeneration)",
                        timestampMillis = nowWall
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
                        type = DiagnosticEventType.SCHEDULE_STALE_CALLBACK,
                        message = "Schedule boundary callback rejected: package mismatch ($capturedPackage != activePackage=$activePackage)",
                        packageName = capturedPackage,
                        generation = capturedGeneration,
                        watcherRunning = true,
                        rejectionReason = "PACKAGE_MISMATCH (captured=$capturedPackage != active=$activePackage)",
                        timestampMillis = nowWall
                    )
                )
                return
            }

            // 2. Re-read current wall-clock time and re-evaluate policy from active state
            val app = targetRepository.getTarget(capturedPackage)
            if (app == null || !app.enabled || app.schedule == null || !app.schedule.enabled) {
                Log.d(TAG, "[SCHEDULE_WATCHER: BOUNDARY] Target $capturedPackage no longer has active schedule")
                return
            }

            val currentZone = zoneIdProvider()
            val isScheduleActive = ScheduleEvaluator.isWithinSchedule(app.schedule, nowWall, currentZone)
            val decision = policyEngine.evaluate(capturedPackage)

            Log.i(
                TAG,
                "[SCHEDULE_WATCHER: EVALUATED] Boundary fired for $capturedPackage -> Decision: $decision, isScheduleActive: $isScheduleActive"
            )
            logger.debug(
                DiagnosticEvent(
                    type = if (isScheduleActive) DiagnosticEventType.SCHEDULE_ACTIVE else DiagnosticEventType.SCHEDULE_INACTIVE,
                    message = "Schedule evaluated for $capturedPackage: isScheduleActive=$isScheduleActive, decision=$decision",
                    packageName = capturedPackage,
                    generation = currentGeneration,
                    scheduleEnabled = true,
                    isScheduleActive = isScheduleActive,
                    timestampMillis = nowWall
                )
            )

            when {
                decision == PolicyDecision.LOCK && isScheduleActive -> {
                    // Confirmed LOCK due to schedule: notify lock callback immediately
                    Log.i(TAG, "[SCHEDULE_WATCHER: LOCK_TRIGGERED] Schedule boundary reached -> invoking onScheduleLocked($capturedPackage)")
                    onScheduleLocked(capturedPackage)
                }
                else -> {
                    // Schedule is not active (either window ended, or fired early, or locked for other reason).
                    // Do NOT emit schedule lock!
                    // Recompute next boundary from fresh wall clock and reschedule if needed.
                    val nextBoundary = ScheduleEvaluator.getNextBoundaryMillis(app.schedule, nowWall, currentZone)
                    if (nextBoundary != null) {
                        val remainingDelay = maxOf(0L, nextBoundary - nowWall)
                        Log.i(
                            TAG,
                            "[SCHEDULE_WATCHER: RESCHEDULE] Schedule inactive (decision=$decision), rescheduling for remaining ${remainingDelay}ms (gen=$currentGeneration)"
                        )
                        logger.debug(
                            DiagnosticEvent(
                                type = DiagnosticEventType.SCHEDULE_DEADLINE_SCHEDULED,
                                message = "Rescheduling schedule boundary for $capturedPackage in ${remainingDelay}ms",
                                packageName = capturedPackage,
                                generation = currentGeneration,
                                remainingMillis = remainingDelay,
                                scheduleEnabled = true,
                                isScheduleActive = false,
                                timestampMillis = nowWall
                            )
                        )
                        val capturedGen = currentGeneration
                        val capturedPkg = capturedPackage
                        scheduler.schedule(remainingDelay) {
                            handleBoundaryFired(capturedPkg, capturedGen)
                        }
                    }
                }
            }
        }
    }

    fun getActivePackage(): String? = synchronized(lock) { activePackage }
    fun getCurrentGeneration(): Long = synchronized(lock) { currentGeneration }
    fun isWatcherRunning(): Boolean = synchronized(lock) { isRunning }

    companion object {
        private const val TAG = "ScheduleWatcher"
    }
}
