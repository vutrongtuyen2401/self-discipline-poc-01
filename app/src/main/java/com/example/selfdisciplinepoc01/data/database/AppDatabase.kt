package com.example.selfdisciplinepoc01.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.selfdisciplinepoc01.data.canonical.dao.CanonicalTaskDao
import com.example.selfdisciplinepoc01.data.canonical.dao.CanonicalVaultDao
import com.example.selfdisciplinepoc01.data.canonical.dao.CanonicalVoucherDao
import com.example.selfdisciplinepoc01.data.canonical.dao.TaskCycleStateDao
import com.example.selfdisciplinepoc01.data.canonical.dao.TaskRewardLinkDao
import com.example.selfdisciplinepoc01.data.canonical.entity.CanonicalTaskEntity
import com.example.selfdisciplinepoc01.data.canonical.entity.CanonicalVaultAppEntity
import com.example.selfdisciplinepoc01.data.canonical.entity.CanonicalVoucherEntity
import com.example.selfdisciplinepoc01.data.canonical.entity.TaskCycleStateEntity
import com.example.selfdisciplinepoc01.data.canonical.entity.TaskRewardLinkEntity
import com.example.selfdisciplinepoc01.data.dao.AppDao
import com.example.selfdisciplinepoc01.data.dao.DailyTaskCompletionDao
import com.example.selfdisciplinepoc01.data.dao.TaskAppCrossRefDao
import com.example.selfdisciplinepoc01.data.dao.TaskDao
import com.example.selfdisciplinepoc01.data.entity.AppEntity
import com.example.selfdisciplinepoc01.data.entity.DailyTaskCompletionEntity
import com.example.selfdisciplinepoc01.data.entity.TaskAppCrossRef
import com.example.selfdisciplinepoc01.data.entity.TaskEntity

/**
 * Proposed Room Database for Core Data Foundation & Canonical Core.
 */
@Database(
    entities = [
        AppEntity::class,
        TaskEntity::class,
        TaskAppCrossRef::class,
        DailyTaskCompletionEntity::class,
        // Canonical Core Entities (Phase 1B)
        CanonicalTaskEntity::class,
        TaskCycleStateEntity::class,
        CanonicalVaultAppEntity::class,
        TaskRewardLinkEntity::class,
        CanonicalVoucherEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun appDao(): AppDao
    abstract fun taskDao(): TaskDao
    abstract fun taskAppCrossRefDao(): TaskAppCrossRefDao
    abstract fun dailyTaskCompletionDao(): DailyTaskCompletionDao

    // Canonical DAOs
    abstract fun canonicalTaskDao(): CanonicalTaskDao
    abstract fun taskCycleStateDao(): TaskCycleStateDao
    abstract fun canonicalVaultDao(): CanonicalVaultDao
    abstract fun taskRewardLinkDao(): TaskRewardLinkDao
    abstract fun canonicalVoucherDao(): CanonicalVoucherDao

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
