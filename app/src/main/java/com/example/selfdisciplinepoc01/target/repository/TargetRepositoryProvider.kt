package com.example.selfdisciplinepoc01.target.repository

import android.content.Context

object TargetRepositoryProvider {

    @Volatile
    private var instance: TargetRepository? = null

    fun getRepository(context: Context): TargetRepository {
        return instance ?: synchronized(this) {
            instance ?: TargetRepositoryImpl(context.applicationContext).also {
                instance = it
            }
        }
    }

    /**
     * Allows injecting a custom/test repository during unit testing.
     */
    fun setTestRepository(repository: TargetRepository?) {
        instance = repository
    }
}
