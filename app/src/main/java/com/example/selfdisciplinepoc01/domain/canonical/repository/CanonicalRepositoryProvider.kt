package com.example.selfdisciplinepoc01.domain.canonical.repository

import android.content.Context
import com.example.selfdisciplinepoc01.data.canonical.repository.CanonicalCycleRepositoryImpl
import com.example.selfdisciplinepoc01.data.canonical.repository.CanonicalLockEvaluatorImpl
import com.example.selfdisciplinepoc01.data.canonical.repository.CanonicalTaskRepositoryImpl
import com.example.selfdisciplinepoc01.data.canonical.repository.CanonicalVaultRepositoryImpl
import com.example.selfdisciplinepoc01.data.database.AppDatabase
import java.time.ZoneId

/**
 * Singleton Provider quản lý và cung cấp các Canonical Repositories và [CanonicalLockEvaluator].
 * Đảm bảo toàn bộ Android Runtime Enforcement và ViewModel truy cập cùng một nguồn chân lý dữ liệu Room.
 */
object CanonicalRepositoryProvider {

    @Volatile
    private var vaultRepository: CanonicalVaultRepository? = null

    @Volatile
    private var taskRepository: CanonicalTaskRepository? = null

    @Volatile
    private var cycleRepository: CanonicalCycleRepository? = null

    @Volatile
    private var lockEvaluator: CanonicalLockEvaluator? = null

    fun getVaultRepository(context: Context): CanonicalVaultRepository {
        return vaultRepository ?: synchronized(this) {
            vaultRepository ?: CanonicalVaultRepositoryImpl(
                AppDatabase.getInstance(context.applicationContext)
            ).also { vaultRepository = it }
        }
    }

    fun getTaskRepository(context: Context): CanonicalTaskRepository {
        return taskRepository ?: synchronized(this) {
            taskRepository ?: CanonicalTaskRepositoryImpl(
                AppDatabase.getInstance(context.applicationContext)
            ).also { taskRepository = it }
        }
    }

    fun getCycleRepository(
        context: Context,
        zoneIdProvider: () -> ZoneId = { ZoneId.systemDefault() }
    ): CanonicalCycleRepository {
        return cycleRepository ?: synchronized(this) {
            cycleRepository ?: CanonicalCycleRepositoryImpl(zoneIdProvider).also { cycleRepository = it }
        }
    }

    fun getLockEvaluator(
        context: Context,
        zoneIdProvider: () -> ZoneId = { ZoneId.systemDefault() }
    ): CanonicalLockEvaluator {
        return lockEvaluator ?: synchronized(this) {
            lockEvaluator ?: run {
                val taskRepo = getTaskRepository(context)
                val vaultRepo = getVaultRepository(context)
                val cycleRepo = getCycleRepository(context, zoneIdProvider)
                CanonicalLockEvaluatorImpl(taskRepo, vaultRepo, cycleRepo).also { lockEvaluator = it }
            }
        }
    }

    fun setTestRepositories(
        vaultRepo: CanonicalVaultRepository? = null,
        taskRepo: CanonicalTaskRepository? = null,
        cycleRepo: CanonicalCycleRepository? = null,
        evaluator: CanonicalLockEvaluator? = null
    ) {
        synchronized(this) {
            vaultRepository = vaultRepo
            taskRepository = taskRepo
            cycleRepository = cycleRepo
            lockEvaluator = evaluator
        }
    }

    fun reset() {
        synchronized(this) {
            vaultRepository = null
            taskRepository = null
            cycleRepository = null
            lockEvaluator = null
        }
    }
}
