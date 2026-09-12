package com.example.selfdisciplinepoc01.domain.enforcement

import android.content.Context
import com.example.selfdisciplinepoc01.data.repository.CoreDataRepositoryProvider
import com.example.selfdisciplinepoc01.domain.canonical.repository.CanonicalRepositoryProvider
import com.example.selfdisciplinepoc01.policy.PolicyEngine
import com.example.selfdisciplinepoc01.target.repository.TargetRepositoryProvider
import com.example.selfdisciplinepoc01.time.BusinessDayProviderHolder
import com.example.selfdisciplinepoc01.usage.UsageTrackerProvider

/**
 * Thread-safe Singleton Provider cho [TaskAppEnforcementAdapter].
 *
 * Tích hợp Canonical Core Repositories và Evaluator làm nguồn chân lý duy nhất,
 * đồng thời giữ liên kết với legacy CoreDataRepository và PolicyEngine phục vụ non-vault apps.
 */
object TaskAppEnforcementAdapterProvider {

    @Volatile
    private var instance: TaskAppEnforcementAdapter? = null

    /**
     * Lấy hoặc khởi tạo Singleton [TaskAppEnforcementAdapter].
     */
    fun getAdapter(context: Context): TaskAppEnforcementAdapter {
        return instance ?: synchronized(this) {
            instance ?: run {
                val appContext = context.applicationContext
                val vaultRepo = CanonicalRepositoryProvider.getVaultRepository(appContext)
                val taskRepo = CanonicalRepositoryProvider.getTaskRepository(appContext)
                val cycleRepo = CanonicalRepositoryProvider.getCycleRepository(appContext)
                val lockEvaluator = CanonicalRepositoryProvider.getLockEvaluator(appContext)

                val coreDataRepo = CoreDataRepositoryProvider.getRepository(appContext)
                val targetRepo = TargetRepositoryProvider.getRepository(appContext)
                val usageTracker = UsageTrackerProvider.getTracker(appContext)
                val policyEngine = PolicyEngine(targetRepo, usageTracker)
                val businessDayProvider = BusinessDayProviderHolder.instance
                val database = com.example.selfdisciplinepoc01.data.database.AppDatabase.getInstance(appContext)

                TaskAppEnforcementAdapter(
                    canonicalVaultRepository = vaultRepo,
                    canonicalTaskRepository = taskRepo,
                    canonicalCycleRepository = cycleRepo,
                    canonicalLockEvaluator = lockEvaluator,
                    coreDataRepository = coreDataRepo,
                    policyEngine = policyEngine,
                    businessDayProvider = businessDayProvider,
                    database = database
                ).also { instance = it }
            }
        }
    }

    /**
     * Lấy instance hiện tại nếu đã được khởi tạo (không tạo mới nếu chưa có Context).
     */
    fun peekAdapter(): TaskAppEnforcementAdapter? = instance

    /**
     * Cung cấp instance kiểm thử phục vụ unit test hoặc reset.
     */
    fun setTestAdapter(adapter: TaskAppEnforcementAdapter?) {
        synchronized(this) {
            instance = adapter
        }
    }
}
