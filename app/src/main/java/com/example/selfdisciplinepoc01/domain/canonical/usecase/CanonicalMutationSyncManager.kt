package com.example.selfdisciplinepoc01.domain.canonical.usecase

import android.util.Log
import com.example.selfdisciplinepoc01.domain.enforcement.TaskAppEnforcementAdapterProvider

/**
 * Trình điều phối đồng bộ hóa trạng thái biến đổi chuẩn (Canonical Mutation Sync Manager).
 * Đảm bảo: Ngay sau khi một mutation nghiệp vụ thành công:
 * 1. Snapshot của TaskAppEnforcementAdapter được tính toán lại ngay lập tức (In-memory update).
 * 2. Phát tín hiệu để Enforcement Runtime (AppDetectorAccessibilityService) kiểm tra và
 *    phong ấn ứng dụng đích ngay lập tức nếu ứng dụng đó đang hiển thị / foreground.
 *
 * TUYỆT ĐỐI KHÔNG CẦN:
 * - Chờ 04:00 AM
 * - Khởi động lại ứng dụng / dịch vụ
 * - Chờ sự kiện Accessibility ngẫu nhiên
 * - Chạm vào màn hình
 */
object CanonicalMutationSyncManager {

    private const val TAG = "CanonicalSyncManager"

    @Volatile
    private var immediateEnforcementListener: ((Collection<String>) -> Unit)? = null

    /**
     * Đăng ký listener từ Enforcement Service (AppDetectorAccessibilityService).
     */
    fun registerEnforcementListener(listener: (Collection<String>) -> Unit) {
        immediateEnforcementListener = listener
        Log.d(TAG, "Enforcement listener registered")
    }

    /**
     * Hủy đăng ký listener khi service bị hủy.
     */
    fun unregisterEnforcementListener() {
        immediateEnforcementListener = null
        Log.d(TAG, "Enforcement listener unregistered")
    }

    /**
     * Phát tín hiệu đồng bộ hóa sau khi hoàn tất atomic mutation trong database.
     */
    suspend fun notifyMutationCommitted(affectedPackages: Collection<String>) {
        try {
            // 1. Cập nhật ngay lập tức snapshot trong TaskAppEnforcementAdapter
            val adapter = TaskAppEnforcementAdapterProvider.peekAdapter()
            if (adapter != null) {
                adapter.recomputeSnapshot()
                Log.i(TAG, "[SYNC: ADAPTER] In-memory snapshot recomputed for ${affectedPackages.size} affected packages: $affectedPackages")
            } else {
                Log.w(TAG, "[SYNC: ADAPTER] TaskAppEnforcementAdapter instance not ready yet")
            }

            // 2. Kích hoạt enforcement runtime kiểm tra và khóa ngay ứng dụng nếu đang mở
            immediateEnforcementListener?.invoke(affectedPackages)
        } catch (e: Exception) {
            Log.e(TAG, "[SYNC: ERROR] Lỗi khi đồng bộ hóa mutation: ${e.message}", e)
        }
    }
}
