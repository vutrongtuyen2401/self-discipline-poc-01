package com.example.selfdisciplinepoc01

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.example.selfdisciplinepoc01.overlay.BlockingShieldOverlay
import com.example.selfdisciplinepoc01.policy.PolicyDecision
import com.example.selfdisciplinepoc01.policy.PolicyEngine
import com.example.selfdisciplinepoc01.target.repository.TargetRepositoryProvider
import com.example.selfdisciplinepoc01.usage.UsageLimitWatcher
import com.example.selfdisciplinepoc01.usage.UsageLimitWatcherImpl
import com.example.selfdisciplinepoc01.usage.UsageTrackerProvider

enum class LockReason {
    ACCESSIBILITY_EVENT,
    DAILY_LIMIT
}

class AppDetectorAccessibilityService : AccessibilityService() {

    private val targetRepository by lazy {
        TargetRepositoryProvider.getRepository(applicationContext)
    }

    private val usageTracker by lazy {
        UsageTrackerProvider.getTracker(applicationContext)
    }

    private val policyEngine by lazy {
        PolicyEngine(targetRepository, usageTracker)
    }

    private val usageLimitWatcher: UsageLimitWatcher by lazy {
        UsageLimitWatcherImpl(
            targetRepository = targetRepository,
            usageTracker = usageTracker,
            policyEngine = policyEngine,
            onLimitReached = { pkg ->
                handleLimitDeadlineReached(pkg)
            }
        )
    }

    private var blockingShieldOverlay: BlockingShieldOverlay? = null

    // Screen State Receiver for accurate usage pause on Screen OFF
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    Log.i(TAG, "[SCREEN_RECEIVER] Screen OFF received -> pausing usage and limit watcher")
                    usageTracker.onScreenOff()
                    usageLimitWatcher.onForegroundChanged(null)
                }
                Intent.ACTION_SCREEN_ON -> {
                    Log.i(TAG, "[SCREEN_RECEIVER] Screen ON received -> awaiting foreground event")
                    usageTracker.onScreenOn()
                }
            }
        }
    }
    private var isScreenReceiverRegistered = false

    // FROZEN CORE STATE MACHINE VARIABLES — DO NOT CHANGE SEMANTICS
    private var lastForegroundPackage: String? = null
    private var lastLockLaunchTimestamp: Long = 0L
    private var isChromeLockedForCurrentTransition: Boolean = false
    private var isLockScreenVisible: Boolean = false
    private var currentSessionId: Long = 0L

    override fun onCreate() {
        super.onCreate()
        instance = this
        if (blockingShieldOverlay == null) {
            blockingShieldOverlay = BlockingShieldOverlay(this)
        }
        registerScreenReceiver()
        usageLimitWatcher.start()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        isRunning = true
        if (blockingShieldOverlay == null) {
            blockingShieldOverlay = BlockingShieldOverlay(this)
        }
        registerScreenReceiver()
        usageLimitWatcher.start()
        Log.d(TAG, "AppDetectorAccessibilityService connected")
    }

    private fun registerScreenReceiver() {
        if (!isScreenReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            }
            registerReceiver(screenReceiver, filter)
            isScreenReceiverRegistered = true
            Log.d(TAG, "Screen state receiver registered")
        }
    }

    private fun unregisterScreenReceiver() {
        if (isScreenReceiverRegistered) {
            try {
                unregisterReceiver(screenReceiver)
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering screen receiver", e)
            }
            isScreenReceiverRegistered = false
        }
    }

    private fun handleLockScreenResumed(sessionId: Long) {
        val prevVisible = isLockScreenVisible
        isLockScreenVisible = true
        Log.d(
            TAG,
            "[LOCKSCREEN_LIFECYCLE] onResume/onStart (sessionId=$sessionId): isLockScreenVisible: $prevVisible -> true"
        )
        // LockScreen is NOT target usage: stop any active target session and cancel limit watcher
        usageTracker.stopSession()
        usageLimitWatcher.onForegroundChanged(null)

        // Hand-off shield when LockScreen is active and confirmed visible
        blockingShieldOverlay?.hide("handoff_to_lockscreen (sessionId=$sessionId)")
    }

    private fun handleLockScreenStopped(sessionId: Long) {
        if (sessionId != 0L && sessionId < currentSessionId) {
            Log.w(
                TAG,
                "[LOCKSCREEN_LIFECYCLE] onStop bỏ qua vì sessionId cũ ($sessionId < currentSessionId=$currentSessionId)"
            )
            return
        }
        val prevVisible = isLockScreenVisible
        val prevLocked = isChromeLockedForCurrentTransition
        isLockScreenVisible = false
        isChromeLockedForCurrentTransition = false
        lastLockLaunchTimestamp = 0L
        lastForegroundPackage = applicationContext.packageName
        Log.i(
            TAG,
            "[LOCKSCREEN_LIFECYCLE] onStop (sessionId=$sessionId): isLockScreenVisible: $prevVisible -> false | isChromeLocked: $prevLocked -> false | cooldown reset -> 0L | lastForegroundPackage -> '$lastForegroundPackage'"
        )
        usageTracker.stopSession()
        usageLimitWatcher.onForegroundChanged(null)
        blockingShieldOverlay?.hide("lockscreen_stopped (sessionId=$sessionId)")
    }

    private fun handleLockScreenExited(sessionId: Long) {
        if (sessionId != 0L && sessionId < currentSessionId) {
            Log.w(
                TAG,
                "[LOCKSCREEN_LIFECYCLE] exit bỏ qua vì sessionId cũ ($sessionId < currentSessionId=$currentSessionId)"
            )
            return
        }
        val prevVisible = isLockScreenVisible
        val prevLocked = isChromeLockedForCurrentTransition
        isLockScreenVisible = false
        isChromeLockedForCurrentTransition = false
        lastLockLaunchTimestamp = 0L
        lastForegroundPackage = applicationContext.packageName
        Log.i(
            TAG,
            "[LOCKSCREEN_LIFECYCLE] goToHomeScreen/exit (sessionId=$sessionId): isLockScreenVisible: $prevVisible -> false | isChromeLocked: $prevLocked -> false | cooldown reset -> 0L | lastForegroundPackage -> '$lastForegroundPackage'"
        )
        usageTracker.stopSession()
        usageLimitWatcher.onForegroundChanged(null)
        blockingShieldOverlay?.hide("lockscreen_exited (sessionId=$sessionId)")
    }

    private fun handleLockScreenDestroyed(sessionId: Long) {
        if (sessionId != 0L && sessionId < currentSessionId) {
            Log.w(
                TAG,
                "[LOCKSCREEN_LIFECYCLE] onDestroy bỏ qua vì sessionId cũ ($sessionId < currentSessionId=$currentSessionId)"
            )
            return
        }
        val prevVisible = isLockScreenVisible
        isLockScreenVisible = false
        isChromeLockedForCurrentTransition = false
        lastLockLaunchTimestamp = 0L
        Log.d(
            TAG,
            "[LOCKSCREEN_LIFECYCLE] onDestroy (sessionId=$sessionId): isLockScreenVisible: $prevVisible -> false | cooldown reset -> 0L"
        )
        usageTracker.stopSession()
        usageLimitWatcher.onForegroundChanged(null)
        blockingShieldOverlay?.hide("lockscreen_destroyed (sessionId=$sessionId)")
    }

    /**
     * Deadline callback from [UsageLimitWatcher] when daily usage reaches the limit in real-time.
     */
    private fun handleLimitDeadlineReached(packageName: String) {
        Log.i(TAG, "[WATCHER: CALLBACK] Deadline reached for $packageName -> launching lock session")
        usageTracker.stopSession(packageName)
        val now = SystemClock.elapsedRealtime()
        val t1 = SystemClock.elapsedRealtimeNanos()
        launchLockSession(packageName, LockReason.DAILY_LIMIT, now, t1)
    }

    /**
     * Shared lock-session entry point.
     * Preserves 100% of the Frozen Core State Machine rules:
     * - Rule A: isLockScreenVisible guard
     * - Rule B: intra-session duplicate guard
     * - Rule C: COOLDOWN_MS guard
     * - currentSessionId increment semantics
     * - BlockingShieldOverlay.show() sequence
     * - startActivity(LockScreenActivity)
     */
    private fun launchLockSession(
        packageName: String,
        reason: LockReason,
        now: Long,
        t1: Long
    ) {
        val prevPkg = lastForegroundPackage
        val isNewTransition = (prevPkg != packageName)
        val elapsedSinceLastLaunch = now - lastLockLaunchTimestamp

        Log.d(
            TAG,
            "[CHECK: TARGET_LOCKED_PKG] Khóa package ($packageName, reason=$reason). prevPkg='$prevPkg', isNewTransition=$isNewTransition, isLockScreenVisible=$isLockScreenVisible, isChromeLocked=$isChromeLockedForCurrentTransition, elapsedSinceLastLaunch=${elapsedSinceLastLaunch}ms (cooldown=${COOLDOWN_MS}ms)"
        )

        // A. If LockScreenActivity is ALREADY active and visible on screen, do NOT duplicate launch
        if (isLockScreenVisible) {
            Log.w(
                TAG,
                "[DECISION: SKIP] BỎ QUA không launch. LockScreenActivity đang hiển thị trên màn hình (isLockScreenVisible=true)."
            )
            return
        }

        // B. If intra-session target event (target was already continuously in foreground) and already locked
        if (!isNewTransition && isChromeLockedForCurrentTransition) {
            Log.w(
                TAG,
                "[DECISION: SKIP] BỎ QUA không launch. Sự kiện trùng lặp nội bộ target trong cùng session (prevPkg='$prevPkg', target đã ở foreground liên tục). Không launch lặp lại."
            )
            return
        }

        // C. Cooldown check: only applies to suppress rapid duplicate events for the SAME target transition
        if (!isNewTransition && (elapsedSinceLastLaunch < COOLDOWN_MS)) {
            Log.w(
                TAG,
                "[DECISION: SKIP] BỎ QUA không launch. Cooldown active cho cùng target transition (${elapsedSinceLastLaunch}ms < ${COOLDOWN_MS}ms)."
            )
            return
        }

        // 4. Eligible to launch LockScreenActivity
        val t2 = SystemClock.elapsedRealtimeNanos()
        val prevSessionState = isChromeLockedForCurrentTransition
        isChromeLockedForCurrentTransition = true
        lastForegroundPackage = packageName
        lastLockLaunchTimestamp = now
        currentSessionId++
        val launchSessionId = currentSessionId
        Log.i(
            TAG,
            "[DECISION: LAUNCH] ĐỦ ĐIỀU KIỆN LAUNCH (sessionId=$launchSessionId, reason=$reason, isNewTransition=$isNewTransition, prevPkg='$prevPkg'). Cập nhật: isChromeLocked: $prevSessionState -> true, lastLockLaunchTimestamp=$now"
        )

        // Step 4 Phase 07-B: Show BlockingShieldOverlay immediately upon LAUNCH decision
        blockingShieldOverlay?.show(t1, t2, launchSessionId, packageName)

        val t3 = SystemClock.elapsedRealtimeNanos()
        val intent = Intent(this, LockScreenActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(LockScreenActivity.EXTRA_SESSION_ID, launchSessionId)
            putExtra(LockScreenActivity.EXTRA_T1_NS, t1)
            putExtra(LockScreenActivity.EXTRA_T2_NS, t2)
            putExtra(LockScreenActivity.EXTRA_T3_NS, t3)
            putExtra(LockScreenActivity.EXTRA_TARGET_PACKAGE, packageName)
        }
        val options = android.app.ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle()
        try {
            startActivity(intent, options)
            Log.i(
                TAG,
                "[LAUNCH: REQUEST_COMPLETED] startActivity() đã gửi request thành công sang ActivityTaskManager (sessionId=$launchSessionId, reason=$reason)"
            )
        } catch (exception: Exception) {
            Log.e(
                TAG,
                "[LAUNCH: EXCEPTION] Lỗi khi gọi startActivity cho package: $packageName",
                exception
            )
            blockingShieldOverlay?.hide("startActivity_exception")
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val t1 = SystemClock.elapsedRealtimeNanos()
            val packageName = event.packageName?.toString() ?: run {
                Log.v(TAG, "[EVENT] TYPE_WINDOW_STATE_CHANGED với packageName null (className=${event.className})")
                return
            }
            val className = event.className?.toString() ?: "unknown"
            val now = SystemClock.elapsedRealtime()
            val elapsedSinceLastLaunch = now - lastLockLaunchTimestamp

            Log.d(
                TAG,
                "[EVENT: RECEIVED] pkg=$packageName, class=$className | lastForegroundPackage=$lastForegroundPackage, isLockScreenVisible=$isLockScreenVisible, isChromeLocked=$isChromeLockedForCurrentTransition, elapsedSinceLastLaunch=${elapsedSinceLastLaunch}ms"
            )

            // 1. Event belongs to this POC application itself
            if (packageName == applicationContext.packageName) {
                val prevPkg = lastForegroundPackage
                lastForegroundPackage = packageName
                if (className.contains("LockScreenActivity")) {
                    isLockScreenVisible = true
                    blockingShieldOverlay?.hide("self_pkg_lockscreen_active")
                    // LockScreen visible: stop target usage and cancel limit watcher
                    usageTracker.stopSession()
                    usageLimitWatcher.onForegroundChanged(null)
                }
                Log.d(
                    TAG,
                    "[CHECK: SELF_PKG] Sự kiện từ POC app ($packageName, class=$className). lastForegroundPackage: '$prevPkg' -> '$packageName' | isLockScreenVisible=$isLockScreenVisible | isChromeLocked giữ nguyên: $isChromeLockedForCurrentTransition"
                )
                return
            }

            // 2. Policy Evaluation: LOCK or ALLOW
            val policyDecision = policyEngine.evaluate(packageName)

            if (policyDecision == PolicyDecision.ALLOW) {
                val target = targetRepository.getTarget(packageName)
                if (target != null && target.enabled) {
                    // Allowed target app in foreground: track usage (idempotent) and watch limit deadline
                    usageTracker.startSession(packageName)
                    usageLimitWatcher.onForegroundChanged(packageName)
                } else {
                    // Non-target app (e.g. Launcher, Settings, System UI): stop previous active usage and clear watcher
                    usageTracker.stopSession()
                    usageLimitWatcher.onForegroundChanged(null)
                }

                blockingShieldOverlay?.hide("allowed_pkg: $packageName")
                val prevPkg = lastForegroundPackage
                val prevLocked = isChromeLockedForCurrentTransition
                lastForegroundPackage = packageName
                isChromeLockedForCurrentTransition = false
                isLockScreenVisible = false
                lastLockLaunchTimestamp = 0L

                Log.i(
                    TAG,
                    "[CHECK: ALLOWED_PKG -> RESET] Ứng dụng được phép: '$packageName'. Reset session: isChromeLocked: $prevLocked -> false, isLockScreenVisible -> false, lastLockLaunchTimestamp -> 0L | lastForegroundPackage: '$prevPkg' -> '$packageName'"
                )
                return
            }

            // Target is LOCKED: stop ongoing usage and cancel watcher
            usageTracker.stopSession(packageName)
            usageLimitWatcher.onForegroundChanged(null)

            // 3. Target locked package detected -> Invoke shared lock session entry point
            launchLockSession(packageName, LockReason.ACCESSIBILITY_EVENT, now, t1)
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "AppDetectorAccessibilityService interrupted")
        usageLimitWatcher.stop()
        usageTracker.stopSession()
        blockingShieldOverlay?.cleanup()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterScreenReceiver()
        usageLimitWatcher.stop()
        usageTracker.stopSession()
        blockingShieldOverlay?.cleanup()
        blockingShieldOverlay = null
        isRunning = false
        if (instance === this) {
            instance = null
        }
        Log.d(TAG, "AppDetectorAccessibilityService destroyed")
    }

    companion object {
        const val TAG = "AppDetectorService"
        const val TARGET_LOCKED_PACKAGE = "com.android.chrome"
        private const val COOLDOWN_MS = 1500L

        @Volatile
        var isRunning: Boolean = false
            private set

        @Volatile
        private var instance: AppDetectorAccessibilityService? = null

        fun onLockScreenResumed(sessionId: Long) {
            instance?.handleLockScreenResumed(sessionId)
        }

        fun onLockScreenStopped(sessionId: Long) {
            instance?.handleLockScreenStopped(sessionId)
        }

        fun onLockScreenExited(sessionId: Long) {
            instance?.handleLockScreenExited(sessionId)
        }

        fun onLockScreenDestroyed(sessionId: Long) {
            instance?.handleLockScreenDestroyed(sessionId)
        }

        fun resetLockSession(sessionId: Long = 0L, reason: String = "explicit") {
            instance?.handleLockScreenExited(sessionId)
        }

        fun onPolicyUpdated(packageName: String) {
            instance?.usageLimitWatcher?.onPolicyUpdated(packageName)
        }
    }
}
