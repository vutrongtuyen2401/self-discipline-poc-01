package com.example.selfdisciplinepoc01.usage

import android.content.Context
import com.example.selfdisciplinepoc01.target.repository.targetDataStore
import com.example.selfdisciplinepoc01.time.SystemClockImpl

/**
 * Singleton provider for [UsageTracker] ensuring the same in-memory tracking instance
 * is shared between [AppDetectorAccessibilityService] and [MainActivity].
 */
object UsageTrackerProvider {

    @Volatile
    private var instance: UsageTracker? = null

    fun getTracker(context: Context): UsageTracker {
        return instance ?: synchronized(this) {
            instance ?: UsageTracker(
                clock = SystemClockImpl(),
                dataStore = context.applicationContext.targetDataStore
            ).also { instance = it }
        }
    }
}
