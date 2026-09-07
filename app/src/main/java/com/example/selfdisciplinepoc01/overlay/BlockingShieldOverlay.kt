package com.example.selfdisciplinepoc01.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import com.example.selfdisciplinepoc01.diagnostics.DiagnosticEvent
import com.example.selfdisciplinepoc01.diagnostics.DiagnosticEventType
import com.example.selfdisciplinepoc01.diagnostics.DiagnosticLogger
import com.example.selfdisciplinepoc01.diagnostics.DiagnosticLoggerProvider
import java.util.Locale

/**
 * BlockingShieldOverlay — Preemptive Accessibility Shield (Phase 07-B).
 *
 * Nhiệm vụ duy nhất:
 * Tạo và hiển thị một tấm chắn mờ đục toàn màn hình thông qua WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
 * ngay khi cỗ máy trạng thái quyết định [DECISION: LAUNCH], nhằm che phủ ứng dụng đích trước khi LockScreenActivity
 * kịp hoàn thành khung hình đầu tiên.
 *
 * Thuộc quyền sở hữu hoàn toàn của AppDetectorAccessibilityService.
 * Tuyệt đối không chứa logic quản lý mục tiêu hay can thiệp State Machine.
 */
class BlockingShieldOverlay(
    private val context: Context? = null,
    private val windowManager: WindowManager = context?.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        ?: error("WindowManager is required"),
    private val logger: DiagnosticLogger = DiagnosticLoggerProvider.getLogger()
) {
    private val mainHandler: Handler? = try {
        Looper.getMainLooper()?.let { Handler(it) }
    } catch (e: Exception) {
        null
    }
    private var shieldView: View? = null

    @Volatile
    private var isCurrentlyShown: Boolean = false

    internal var viewFactory: () -> View = {
        View(context).apply {
            setBackgroundColor(Color.WHITE)
            isClickable = true
            isFocusable = false
            setOnTouchListener { _, _ -> true }
        }
    }

    private val timeoutRunnable = Runnable {
        Log.w(TAG, "[SHIELD_TIMEOUT] Safety timeout 2000ms kích hoạt. Tự động thu hồi shield để tránh treo màn hình.")
        logger.warn(
            DiagnosticEvent(
                type = DiagnosticEventType.SHIELD_TIMEOUT,
                message = "Safety timeout 2000ms triggered, automatically revoking shield",
                details = mapOf("timeoutMs" to SAFETY_TIMEOUT_MS)
            )
        )
        hide("safety_timeout_2000ms")
    }

    private val lock = Any()

    fun isShown(): Boolean = synchronized(lock) { isCurrentlyShown }

    /**
     * Hiển thị tấm chắn bảo vệ đón đầu.
     * Idempotent: Nếu đã hiển thị, không add thêm view thứ hai.
     */
    fun show(t1: Long, t2: Long, sessionId: Long, targetPackage: String) {
        synchronized(lock) {
            if (isCurrentlyShown) {
                Log.w(TAG, "[SHIELD: SHOW_SKIPPED] Shield đã đang hiển thị (sessionId=$sessionId, pkg=$targetPackage). Không add duplicate view.")
                return
            }

            logger.debug(
                DiagnosticEvent(
                    type = DiagnosticEventType.SHIELD_SHOW_REQUESTED,
                    message = "Shield show requested",
                    packageName = targetPackage,
                    sessionId = sessionId,
                    details = mapOf("t1" to t1, "t2" to t2)
                )
            )

            try {
                val view = viewFactory()

                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.OPAQUE
                )

                val t6 = SystemClock.elapsedRealtimeNanos()

                try {
                    view.viewTreeObserver?.addOnDrawListener(object : ViewTreeObserver.OnDrawListener {
                        private var hasDrawn = false
                        override fun onDraw() {
                            if (!hasDrawn) {
                                hasDrawn = true
                                val t7 = SystemClock.elapsedRealtimeNanos()
                                view.post {
                                    try {
                                        view.viewTreeObserver?.removeOnDrawListener(this)
                                    } catch (e: Exception) {
                                        Log.w(TAG, "Không thể remove OnDrawListener của Shield", e)
                                    }
                                }
                                emitShieldLatencyLog(sessionId, targetPackage, t1, t2, t6, t7)
                            }
                        }
                    })
                } catch (e: Exception) {
                    Log.w(TAG, "Không thể addOnDrawListener cho Shield View (môi trường test hoặc UI chưa sẵn sàng)", e)
                }

                windowManager.addView(view, params)
                shieldView = view
                isCurrentlyShown = true
                Log.i(TAG, "[SHIELD: SHOWN] Đã add BlockingShieldOverlay lên WindowManager (sessionId=$sessionId, pkg=$targetPackage, t6=$t6)")

                logger.info(
                    DiagnosticEvent(
                        type = DiagnosticEventType.SHIELD_SHOWN,
                        message = "BlockingShieldOverlay successfully shown",
                        packageName = targetPackage,
                        sessionId = sessionId,
                        details = mapOf("t6" to t6)
                    )
                )

                // Kích hoạt safety fuse 2000ms
                mainHandler?.removeCallbacks(timeoutRunnable)
                mainHandler?.postDelayed(timeoutRunnable, SAFETY_TIMEOUT_MS)

            } catch (e: WindowManager.BadTokenException) {
                Log.e(TAG, "[SHIELD: ERROR] BadTokenException khi addView", e)
                logger.error(
                    DiagnosticEvent(
                        type = DiagnosticEventType.SHIELD_SHOW_FAILED,
                        message = "BadTokenException when adding shield view",
                        packageName = targetPackage,
                        sessionId = sessionId,
                        details = mapOf("error" to "BadTokenException")
                    ),
                    e
                )
                isCurrentlyShown = false
                shieldView = null
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "[SHIELD: ERROR] IllegalArgumentException khi addView", e)
                logger.error(
                    DiagnosticEvent(
                        type = DiagnosticEventType.SHIELD_SHOW_FAILED,
                        message = "IllegalArgumentException when adding shield view",
                        packageName = targetPackage,
                        sessionId = sessionId,
                        details = mapOf("error" to "IllegalArgumentException")
                    ),
                    e
                )
                isCurrentlyShown = false
                shieldView = null
            } catch (e: Throwable) {
                Log.e(TAG, "[SHIELD: ERROR] Lỗi không xác định khi addView", e)
                logger.error(
                    DiagnosticEvent(
                        type = DiagnosticEventType.SHIELD_SHOW_FAILED,
                        message = "Unknown error when adding shield view: ${e.javaClass.simpleName}",
                        packageName = targetPackage,
                        sessionId = sessionId,
                        details = mapOf("error" to e.javaClass.simpleName)
                    ),
                    e
                )
                isCurrentlyShown = false
                shieldView = null
            }
        }
    }

    /**
     * Thu hồi tấm chắn bảo vệ đón đầu.
     * Idempotent: Nếu đã ẩn, không làm gì cả (no-op).
     */
    fun hide(reason: String = "explicit") {
        synchronized(lock) {
            mainHandler?.removeCallbacks(timeoutRunnable)
            if (!isCurrentlyShown && shieldView == null) {
                return
            }

            val viewToRemove = shieldView
            shieldView = null
            isCurrentlyShown = false

            logger.info(
                DiagnosticEvent(
                    type = DiagnosticEventType.SHIELD_HIDE,
                    message = "BlockingShieldOverlay hide requested",
                    details = mapOf("reason" to reason)
                )
            )

            if (viewToRemove != null) {
                try {
                    windowManager.removeView(viewToRemove)
                    Log.i(TAG, "[SHIELD: HIDDEN] Đã gỡ bỏ BlockingShieldOverlay khỏi WindowManager (reason='$reason')")
                } catch (e: IllegalArgumentException) {
                    Log.w(TAG, "[SHIELD: WARN] View đã không còn trên WindowManager khi hide (reason='$reason')", e)
                } catch (e: Throwable) {
                    Log.e(TAG, "[SHIELD: ERROR] Lỗi khi removeView (reason='$reason')", e)
                }
            }
        }
    }

    /**
     * Dọn dẹp an toàn tài nguyên. Có thể gọi lặp lại nhiều lần an toàn.
     */
    fun cleanup() {
        synchronized(lock) {
            mainHandler?.removeCallbacks(timeoutRunnable)
            val viewToRemove = shieldView
            shieldView = null
            isCurrentlyShown = false

            logger.info(
                DiagnosticEvent(
                    type = DiagnosticEventType.SHIELD_CLEANUP,
                    message = "BlockingShieldOverlay cleanup called"
                )
            )

            if (viewToRemove != null) {
                try {
                    windowManager.removeView(viewToRemove)
                    Log.i(TAG, "[SHIELD: CLEANUP] Cleanup thành công")
                } catch (e: Throwable) {
                    Log.w(TAG, "[SHIELD: CLEANUP] Gặp ngoại lệ khi cleanup (đã an toàn bỏ qua)", e)
                }
            }
        }
    }

    private fun emitShieldLatencyLog(
        sessionId: Long,
        targetPkg: String,
        t1: Long,
        t2: Long,
        t6: Long,
        t7: Long
    ) {
        val eventToShieldReqMs = if (t1 > 0L) (t6 - t1) / 1_000_000.0 else 0.0
        val eventToShieldFrameMs = if (t1 > 0L) (t7 - t1) / 1_000_000.0 else 0.0
        val reqToFrameMs = (t7 - t6) / 1_000_000.0

        val logBlock = buildString {
            appendLine("========== [SHIELD_LATENCY] ==========")
            appendLine("sessionId=$sessionId")
            appendLine("target_package=$targetPkg")
            appendLine("event_received_ns=$t1")
            appendLine("launch_decision_ns=$t2")
            appendLine("shield_request_ns=$t6")
            appendLine("shield_first_frame_ns=$t7")
            appendLine(String.format(Locale.US, "event_to_shield_request_ms=%.2f", eventToShieldReqMs))
            appendLine(String.format(Locale.US, "event_to_shield_firstFrame_ms=%.2f", eventToShieldFrameMs))
            appendLine(String.format(Locale.US, "shield_request_to_firstFrame_ms=%.2f", reqToFrameMs))
            appendLine("=======================================")
        }
        Log.i(TAG, logBlock)

        Log.i(
            TAG,
            String.format(
                Locale.US,
                "[SHIELD_LATENCY_CSV] %d,%s,%d,%d,%d,%d,%.2f,%.2f,%.2f",
                sessionId,
                targetPkg,
                t1,
                t2,
                t6,
                t7,
                eventToShieldReqMs,
                eventToShieldFrameMs,
                reqToFrameMs
            )
        )
    }

    companion object {
        const val TAG = "BlockingShieldOverlay"
        private const val SAFETY_TIMEOUT_MS = 2000L
    }
}
