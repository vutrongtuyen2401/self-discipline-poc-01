package com.example.selfdisciplinepoc01.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.selfdisciplinepoc01.domain.canonical.cycle.CycleTransitionManager

/**
 * BroadcastReceiver nhận các sự kiện hệ thống:
 * - Khởi động thiết bị hoàn tất (BOOT_COMPLETED)
 * - Nâng cấp/cài đặt lại ứng dụng (MY_PACKAGE_REPLACED)
 * - Thay đổi giờ hệ thống (TIME_SET)
 * - Thay đổi múi giờ (TIMEZONE_CHANGED)
 *
 * Thực hiện hòa giải chu kỳ tức thì (Nhảy trực tiếp đến chu kỳ hiện tại, không replay,
 * tái lập lịch AlarmManager cho 04:00:00 theo múi giờ mới).
 */
class BootAndReconciliationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) return
        val action = intent?.action ?: return
        Log.i(TAG, "[RECEIVE: SYSTEM_EVENT] Nhận sự kiện hệ thống: $action")

        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                CycleTransitionManager.reconcileCycleOnStartup(context.applicationContext)
            }
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
