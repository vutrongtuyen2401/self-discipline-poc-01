package com.example.selfdisciplinepoc01.domain.enforcement

import android.content.Context
import com.example.selfdisciplinepoc01.data.repository.CoreDataRepositoryProvider
import com.example.selfdisciplinepoc01.policy.PolicyEngine
import com.example.selfdisciplinepoc01.target.repository.TargetRepositoryProvider
import com.example.selfdisciplinepoc01.time.BusinessDayProviderHolder
import com.example.selfdisciplinepoc01.usage.UsageTrackerProvider

/**
 * Thread-safe Singleton Provider cho [TaskAppEnforcementAdapter].
 *
 * Đảm bảo toàn bộ ứng dụng (Bao gồm AppDetectorAccessibilityService,
 * VaultViewModel, và MissionHallViewModel) chia sẻ cùng một snapshot cache
 * duy nhất trong bộ nhớ, tối ưu hóa thời gian đánh giá (< 0.05ms) và
 * đảm bảo invalidation tức thì khi Ký chủ hoàn thành nhiệm vụ.
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
                val coreDataRepo = CoreDataRepositoryProvider.getRepository(appContext)
                val targetRepo = TargetRepositoryProvider.getRepository(appContext)
                val usageTracker = UsageTrackerProvider.getTracker(appContext)
                val policyEngine = PolicyEngine(targetRepo, usageTracker)
                val businessDayProvider = BusinessDayProviderHolder.instance

                TaskAppEnforcementAdapter(
                    coreDataRepository = coreDataRepo,
                    policyEngine = policyEngine,
                    businessDayProvider = businessDayProvider
                ).also { instance = it }
            }
        }
    }

    /**
     * Cung cấp instance kiểm thử phục vụ unit test hoặc reset.
     */
    fun setTestAdapter(adapter: TaskAppEnforcementAdapter?) {
        synchronized(this) {
            instance = adapter
        }
    }
}
