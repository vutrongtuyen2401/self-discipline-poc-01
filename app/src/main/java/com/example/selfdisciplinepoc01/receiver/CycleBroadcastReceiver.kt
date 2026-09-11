package com.example.selfdisciplinepoc01.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.selfdisciplinepoc01.domain.canonical.cycle.CycleTransitionManager

/**
 * BroadcastReceiver nhận tín hiệu chuyển đổi chu kỳ 04:00:00 từ AlarmManager.
 * Đảm bảo thực thi ngầm 100% tuân thủ SSOT (không rung, không chuông, không notification, không popup vô cớ).
 */
class CycleBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) return
        Log.i(TAG, "[RECEIVE] Nhận tín hiệu Alarm 04:00:00: action=${intent?.action}")

        if (intent?.action == CycleTransitionManager.ACTION_CYCLE_0400) {
            CycleTransitionManager.onCycleBoundaryReached(context.applicationContext)
        }
    }

    companion object {
        private const val TAG = "CycleBroadcastReceiver"
    }
}
