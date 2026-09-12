package com.example.selfdisciplinepoc01.domain.canonical.cycle

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.selfdisciplinepoc01.AppDetectorAccessibilityService
import com.example.selfdisciplinepoc01.LockScreenActivity
import com.example.selfdisciplinepoc01.domain.enforcement.EnforcementAction
import com.example.selfdisciplinepoc01.domain.enforcement.TaskAppEnforcementAdapterProvider
import com.example.selfdisciplinepoc01.receiver.CycleBroadcastReceiver
import java.time.Instant
import java.time.ZoneId

/**
 * Quản lý chuyển đổi chu kỳ thời gian thực tại mốc 04:00:00 chuẩn MASTER SSOT.
 *
 * Yêu cầu chuẩn:
 * - Không notification, không rung, không popup tự phát sinh tại 04:00.
 * - Nếu managed app đang ở foreground tại mốc 04:00 và bị khóa trong chu kỳ mới:
 *   -> Đẩy người dùng về Android Home (`Intent.ACTION_MAIN, Intent.CATEGORY_HOME`)
 *   -> Kích hoạt System Panel / LockScreenActivity theo hành vi runtime.
 * - Khởi động / Reboot / Offline Catch-up:
 *   -> Nhảy thẳng tới chu kỳ hiện tại chứa thời gian máy.
 *   -> Tuyệt đối không replay lịch sử các chu kỳ bị trễ.
 *   -> Không blanket reset, bảo toàn toàn bộ TaskCycleState lịch sử.
 */
object CycleTransitionManager {

    private const val TAG = "CycleTransitionManager"
    const val ACTION_CYCLE_0400 = "com.example.selfdisciplinepoc01.ACTION_CYCLE_TRANSITION_0400"
    private const val REQUEST_CODE_CYCLE_ALARM = 4000

    /**
     * Lên lịch báo thức exact tại mốc 04:00:00 tiếp theo theo local ZoneId.
     */
    fun scheduleNextTransition(
        context: Context,
        now: Instant = Instant.now(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ) {
        val nextBoundary = CycleEngine.getCurrentCycleBoundary(now, zoneId).endInstant
        val triggerMillis = nextBoundary.toEpochMilli()

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        if (alarmManager == null) {
            Log.e(TAG, "AlarmManager không khả dụng trên thiết bị")
            return
        }

        val intent = Intent(context, CycleBroadcastReceiver::class.java).apply {
            action = ACTION_CYCLE_0400
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getBroadcast(context, REQUEST_CODE_CYCLE_ALARM, intent, flags)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
            }
            Log.i(TAG, "[SCHEDULE] Đã lên lịch 04:00:00 tiếp theo tại: $nextBoundary (epochMillis=$triggerMillis, zone=$zoneId)")
        } catch (e: SecurityException) {
            Log.w(TAG, "Thiếu quyền SCHEDULE_EXACT_ALARM, fallback sang setAndAllowWhileIdle", e)
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
        }
    }

    /**
     * Test seam phục vụ xác thực P0-1 và P0-2 trên thiết bị thực tế:
     * Lên lịch báo thức thật thông qua cùng PendingIntent và CycleBroadcastReceiver sau [delaySeconds] giây.
     */
    fun scheduleTestAlarm(
        context: Context,
        delaySeconds: Long = 5L
    ) {
        val triggerMillis = System.currentTimeMillis() + (delaySeconds * 1000L)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        if (alarmManager == null) {
            Log.e(TAG, "AlarmManager không khả dụng trên thiết bị")
            return
        }

        val intent = Intent(context, CycleBroadcastReceiver::class.java).apply {
            action = ACTION_CYCLE_0400
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getBroadcast(context, REQUEST_CODE_CYCLE_ALARM, intent, flags)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
            }
            Log.i(TAG, "[TEST_ALARM_SCHEDULED] Đã lên lịch AlarmManager test callback sau ${delaySeconds}s (triggerMillis=$triggerMillis)")
        } catch (e: SecurityException) {
            Log.w(TAG, "Thiếu quyền exact alarm trong test seam, fallback sang setAndAllowWhileIdle", e)
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
        }
    }

    /**
     * Xử lý khi chạm mốc 04:00:00 thời gian thực.
     */
    fun onCycleBoundaryReached(
        context: Context,
        now: Instant = Instant.now(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ) {
        val currentBoundary = CycleEngine.getCurrentCycleBoundary(now, zoneId)
        Log.i(TAG, "[CYCLE_TRANSITION_0400] Đã chạm mốc chu kỳ mới: ${currentBoundary.cycleId}. Cập nhật snapshot in-memory...")

        // 1. Cập nhật snapshot in-memory của runtime adapter
        val adapter = TaskAppEnforcementAdapterProvider.getAdapter(context)
        try {
            kotlinx.coroutines.runBlocking {
                adapter.recomputeSnapshot()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Lỗi recompute snapshot: ${e.message}")
        }

        // 2. Lên lịch ngay cho 04:00:00 của ngày tiếp theo
        scheduleNextTransition(context, now, zoneId)

        // 3. Kiểm tra ứng dụng đang foreground (kết hợp Accessibility và UsageStats fallback)
        var fgPackage = AppDetectorAccessibilityService.getCurrentForegroundPackage()
        if (fgPackage.isNullOrBlank() || fgPackage == context.packageName) {
            try {
                val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? android.app.usage.UsageStatsManager
                val time = System.currentTimeMillis()
                val stats = usm?.queryUsageStats(android.app.usage.UsageStatsManager.INTERVAL_DAILY, time - 10000, time)
                val top = stats?.maxByOrNull { it.lastTimeUsed }
                if (top != null && top.packageName != context.packageName) {
                    fgPackage = top.packageName
                }
            } catch (e: Exception) {
                Log.w(TAG, "Lỗi truy vấn UsageStatsManager fallback: ${e.message}")
            }
        }

        Log.i(TAG, "[CYCLE_TRANSITION_0400] Foreground app phát hiện tại mốc 04:00: '$fgPackage'")
        if (fgPackage.isNullOrBlank() || fgPackage == context.packageName) {
            Log.i(TAG, "[CYCLE_TRANSITION_0400] Không có managed target app đang foreground. Kết thúc an toàn không phát sinh popup/rung/notification.")
            return
        }

        // Đánh giá trạng thái của foreground app trong chu kỳ mới
        val evaluation = adapter.evaluateSync(fgPackage)
        Log.i(TAG, "[CYCLE_TRANSITION_0400] Đánh giá foreground app '$fgPackage': finalAction=${evaluation.finalAction}, reason=${evaluation.reason}")

        if (evaluation.isVaultApp && evaluation.finalAction == EnforcementAction.LOCK) {
            Log.w(TAG, "[CYCLE_TRANSITION_0400] Ứng dụng '$fgPackage' bị phong ấn do bắt đầu chu kỳ mới mà chưa hoàn thành nhiệm vụ! Kích hoạt push-to-Home và System Panel.")

            // Push to Home: phối hợp cả AccessibilityService.performHome() và Intent CATEGORY_HOME
            val pushedHome = AppDetectorAccessibilityService.performHome()
            Log.i(TAG, "[CYCLE_TRANSITION_0400] Push to Home via AccessibilityService: $pushedHome")

            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            try {
                context.startActivity(homeIntent)
            } catch (e: Exception) {
                Log.w(TAG, "Lỗi khi startActivity homeIntent: ${e.message}")
            }

            // Khởi chạy System Panel / LockScreenActivity
            val lockIntent = Intent(context, LockScreenActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(LockScreenActivity.EXTRA_TARGET_PACKAGE, fgPackage)
                putExtra(LockScreenActivity.EXTRA_SESSION_ID, System.currentTimeMillis())
            }
            try {
                context.startActivity(lockIntent)
            } catch (e: Exception) {
                Log.w(TAG, "Lỗi khi startActivity lockIntent: ${e.message}")
            }
        }
    }

    /**
     * Hòa giải chu kỳ sau khi khởi động máy (Boot), App Process Restart, Offline dài hạn hoặc thay đổi Timezone.
     */
    fun reconcileCycleOnStartup(
        context: Context,
        now: Instant = Instant.now(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ) {
        val currentBoundary = CycleEngine.getCurrentCycleBoundary(now, zoneId)
        Log.i(TAG, "[RECONCILE] Khởi động / Hòa giải: Nhảy trực tiếp tới chu kỳ hiện tại ${currentBoundary.cycleId} (Zone: $zoneId). Không replay lịch sử.")

        val adapter = TaskAppEnforcementAdapterProvider.getAdapter(context)
        try {
            kotlinx.coroutines.runBlocking {
                adapter.recomputeSnapshot()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Lỗi recompute snapshot on startup: ${e.message}")
        }

        scheduleNextTransition(context, now, zoneId)
    }
}
