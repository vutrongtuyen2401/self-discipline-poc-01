package com.example.selfdisciplinepoc01.data.repository

import android.content.Context
import com.example.selfdisciplinepoc01.data.database.AppDatabase

/**
 * Thread-safe Singleton Provider for [CoreDataRepository].
 *
 * Keeps consistency with TargetRepositoryProvider and UsageTrackerProvider.
 */
object CoreDataRepositoryProvider {

    @Volatile
    private var instance: CoreDataRepository? = null

    fun getRepository(context: Context): CoreDataRepository {
        return instance ?: synchronized(this) {
            instance ?: CoreDataRepositoryImpl(
                AppDatabase.getInstance(context.applicationContext)
            ).also { instance = it }
        }
    }

    /**
     * Testing hook to inject an in-memory or mock repository.
     */
    fun setRepositoryForTesting(mockRepository: CoreDataRepository?) {
        synchronized(this) {
            instance = mockRepository
        }
    }
}
