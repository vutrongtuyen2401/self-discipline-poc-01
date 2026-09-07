package com.example.selfdisciplinepoc01.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.selfdisciplinepoc01.data.dao.AppDao
import com.example.selfdisciplinepoc01.data.dao.DailyTaskCompletionDao
import com.example.selfdisciplinepoc01.data.dao.TaskAppCrossRefDao
import com.example.selfdisciplinepoc01.data.dao.TaskDao
import com.example.selfdisciplinepoc01.data.entity.AppEntity
import com.example.selfdisciplinepoc01.data.entity.DailyTaskCompletionEntity
import com.example.selfdisciplinepoc01.data.entity.TaskAppCrossRef
import com.example.selfdisciplinepoc01.data.entity.TaskEntity

/**
 * Proposed Room Database for Core Data Foundation.
 *
 * PROPOSAL — OPEN-05 REMAINS OPEN.
 * This database serves as the technical foundation for Checkpoints CP3, CP5, CP6.
 * Existing DataStore persistence remains 100% active and untouched for App Lock runtime enforcement.
 */
@Database(
    entities = [
        AppEntity::class,
        TaskEntity::class,
        TaskAppCrossRef::class,
        DailyTaskCompletionEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun appDao(): AppDao
    abstract fun taskDao(): TaskDao
    abstract fun taskAppCrossRefDao(): TaskAppCrossRefDao
    abstract fun dailyTaskCompletionDao(): DailyTaskCompletionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "self_discipline_core.db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
        }

        fun createInMemory(context: Context): AppDatabase {
            return Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                AppDatabase::class.java
            ).allowMainThreadQueries().build()
        }
    }
}
