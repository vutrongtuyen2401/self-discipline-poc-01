package com.example.selfdisciplinepoc01.domain.canonical.vault

import com.example.selfdisciplinepoc01.domain.canonical.task.CanonicalTask
import com.example.selfdisciplinepoc01.domain.canonical.task.TaskCycleState
import java.time.Instant

/**
 * Định danh và thông tin của một Ứng dụng trong Kho Phong Ấn (Vault App).
 */
data class CanonicalVaultApp(
    val packageName: String,
    val displayName: String,
    val addedAt: Instant = Instant.now()
) {
    init {
        require(packageName.isNotBlank()) { "packageName không được để trống" }
    }
}

/**
 * Hiệu lực của một Thẻ Bài / Voucher (Voucher Effect) tác động lên việc mở khóa.
 */
data class VoucherEffect(
    val voucherId: String,
    val voucherName: String,
    val targetPackageName: String? = null, // null nghĩa là áp dụng cho mọi app
    val effectiveFrom: Instant,
    val effectiveUntil: Instant
) {
    init {
        require(voucherId.isNotBlank()) { "voucherId không được để trống" }
        require(!effectiveUntil.isBefore(effectiveFrom)) {
            "effectiveUntil ($effectiveUntil) phải sau hoặc bằng effectiveFrom ($effectiveFrom)"
        }
    }

    fun isEffectiveAt(instant: Instant): Boolean {
        return !instant.isBefore(effectiveFrom) && instant.isBefore(effectiveUntil)
    }

    fun appliesTo(packageName: String): Boolean {
        return targetPackageName == null || targetPackageName == packageName
    }
}

/**
 * Quyết định khóa của Canonical Engine.
 */
enum class CanonicalLockDecision {
    LOCKED,
    UNLOCKED
}

/**
 * Kết quả đánh giá trạng thái khóa của một ứng dụng trong chu kỳ hiện tại.
 */
data class LockEvaluationResult(
    val packageName: String,
    val decision: CanonicalLockDecision,
    val reason: String,
    val totalLinkedRewardTasks: Int,
    val completedLinkedRewardTasks: Int,
    val requiredCompletions: Int,
    val effectiveVoucher: VoucherEffect? = null
) {
    val isLocked: Boolean
        get() = decision == CanonicalLockDecision.LOCKED
    val isUnlocked: Boolean
        get() = decision == CanonicalLockDecision.UNLOCKED
}

/**
 * =========================================================================
 * CANONICAL APP LOCK POLICY (CHÍNH SÁCH PHONG ẤN ỨNG DỤNG CHUẨN TẮC SSOT)
 * =========================================================================
 *
 * Nguyên tắc bất biến tối thượng:
 * "Một ứng dụng bị khóa khi và chỉ khi nó đang được ít nhất một nhiệm vụ yêu cầu trong chu kỳ hiện tại."
 *
 * Formal rule:
 * requiredTaskCount(app) > 0 => LOCKED
 * requiredTaskCount(app) == 0 => UNLOCKED
 *
 * Điều kiện LOCK/UNLOCK:
 * - KHÔNG phụ thuộc K (số task đã hoàn thành).
 * - KHÔNG phụ thuộc N (tổng số task).
 * - KHÔNG phụ thuộc ceil(2N/3).
 * - KHÔNG dùng Required(N) để quyết định App có LOCK hay UNLOCK.
 * - Một task đã COMPLETED vẫn là task đang yêu cầu app nếu reward link của nó vẫn còn hiệu lực trong current cycle.
 * - Chỉ khi app không còn task hiện tại nào yêu cầu nó thì app mới UNLOCK.
 * - Voucher hiệu lực có thể override lock (UNLOCKED_BY_VOUCHER).
 */
object CanonicalAppLockPolicy {

    /**
     * Đánh giá trạng thái khóa của một ứng dụng theo đúng quy tắc SSOT.
     */
    fun evaluateLock(
        packageName: String,
        linkedTasks: List<CanonicalTask>,
        cycleTaskStates: Map<String, TaskCycleState>,
        activeVouchers: List<VoucherEffect> = emptyList(),
        evaluationInstant: Instant = Instant.now()
    ): LockEvaluationResult {
        // 1. Kiểm tra Voucher còn hiệu lực -> Voucher override lock
        val effectiveVoucher = activeVouchers.firstOrNull { voucher ->
            voucher.isEffectiveAt(evaluationInstant) && voucher.appliesTo(packageName)
        }
        if (effectiveVoucher != null) {
            return LockEvaluationResult(
                packageName = packageName,
                decision = CanonicalLockDecision.UNLOCKED,
                reason = "UNLOCKED_BY_VOUCHER: Thẻ bài '${effectiveVoucher.voucherName}' đang có hiệu lực",
                totalLinkedRewardTasks = linkedTasks.count { it.hasReward && !it.isArchived },
                completedLinkedRewardTasks = 0,
                requiredCompletions = 0,
                effectiveVoucher = effectiveVoucher
            )
        }

        // 2. Lọc các nhiệm vụ hợp lệ: Phải có hasReward == true và không bị archived
        val validRewardTasks = linkedTasks.filter { it.hasReward && !it.isArchived }
        val n = validRewardTasks.size

        // 3. Tính toán số nhiệm vụ đã hoàn thành (phục vụ hiển thị Task Chain và Deletion policy, KHÔNG quyết định lock)
        val completedCount = validRewardTasks.count { task ->
            cycleTaskStates[task.id]?.isCompleted == true
        }

        // 4. Quy tắc SSOT chuẩn xác:
        // App bị khóa iff n > 0 (có ít nhất 1 nhiệm vụ yêu cầu/thưởng ứng dụng này trong chu kỳ).
        // n == 0 => UNLOCKED.
        val requiredForDeletion = CanonicalAppDeletionPolicy.calculateRequiredForDeletion(n)
        return if (n > 0) {
            LockEvaluationResult(
                packageName = packageName,
                decision = CanonicalLockDecision.LOCKED,
                reason = "LOCKED_TASK_REQUIRED: Ứng dụng đang được $n nhiệm vụ yêu cầu (đã hoàn thành $completedCount/$n)",
                totalLinkedRewardTasks = n,
                completedLinkedRewardTasks = completedCount,
                requiredCompletions = requiredForDeletion,
                effectiveVoucher = null
            )
        } else {
            LockEvaluationResult(
                packageName = packageName,
                decision = CanonicalLockDecision.UNLOCKED,
                reason = "UNLOCKED_NO_TASK_REQUIRED: Ứng dụng không có nhiệm vụ nào yêu cầu (N=0)",
                totalLinkedRewardTasks = 0,
                completedLinkedRewardTasks = 0,
                requiredCompletions = 0,
                effectiveVoucher = null
            )
        }
    }
}

/**
 * Adapter/Delegate tương thích ngược cho CanonicalLockPolicy.
 */
object CanonicalLockPolicy {
    fun evaluateLock(
        packageName: String,
        linkedTasks: List<CanonicalTask>,
        cycleTaskStates: Map<String, TaskCycleState>,
        activeVouchers: List<VoucherEffect> = emptyList(),
        evaluationInstant: Instant = Instant.now()
    ): LockEvaluationResult = CanonicalAppLockPolicy.evaluateLock(
        packageName, linkedTasks, cycleTaskStates, activeVouchers, evaluationInstant
    )

    @Deprecated("Dùng CanonicalAppDeletionPolicy.calculateRequiredForDeletion(n) để kiểm tra điều kiện xóa app")
    fun calculateRequiredCompletions(totalLinkedRewardTasks: Int): Int {
        return CanonicalAppDeletionPolicy.calculateRequiredForDeletion(totalLinkedRewardTasks)
    }
}

/**
 * =========================================================================
 * CANONICAL APP DELETION POLICY (CHÍNH SÁCH GIẢI PHÓNG / XÓA APP KHỎI BẢO KHỐ)
 * =========================================================================
 *
 * Điều kiện 2/3 CHỈ ÁP DỤNG DUY NHẤT CHO QUYỀN XÓA ỨNG DỤNG KHỎI BẢO KHỐ.
 * TUYỆT ĐỐI KHÔNG PHẢI LÀ ĐIỀU KIỆN MỞ KHÓA ỨNG DỤNG.
 *
 * Bảng quy chuẩn RequiredForDeletion(N):
 * N=0 -> 0
 * N=1 -> 1
 * N=2 -> 1  (Đặc xá khởi đầu theo MASTER SSOT)
 * N=3 -> 2
 * N=4 -> 3
 * N=5 -> 4
 * N=6 -> 4
 * N>6 -> ceil(2N/3)
 *
 * Delete allowed iff K >= RequiredForDeletion(N)
 */
object CanonicalAppDeletionPolicy {

    /**
     * Tính toán số lượng nhiệm vụ cần hoàn thành để ĐỦ ĐIỀU KIỆN XÓA APP khỏi Vault.
     */
    fun calculateRequiredForDeletion(totalLinkedRewardTasks: Int): Int {
        if (totalLinkedRewardTasks <= 0) return 0
        return when (totalLinkedRewardTasks) {
            1 -> 1
            2 -> 1 // Đặc xá khởi đầu theo MASTER SSOT: N=2 chỉ cần 1
            3 -> 2
            4 -> 3
            5 -> 4
            6 -> 4
            else -> Math.ceil((2.0 * totalLinkedRewardTasks) / 3.0).toInt()
        }
    }

    /**
     * Kiểm tra xem ứng dụng có đủ điều kiện để xóa khỏi Bảo Khố hay không.
     *
     * @param totalLinkedRewardTasks Tổng số task liên kết (N)
     * @param completedRewardTasks Số task đã hoàn thành trong chu kỳ hiện tại (K)
     * @return true nếu K >= RequiredForDeletion(N), ngược lại false
     */
    fun canDelete(
        totalLinkedRewardTasks: Int,
        completedRewardTasks: Int
    ): Boolean {
        if (totalLinkedRewardTasks <= 0) return true
        val required = calculateRequiredForDeletion(totalLinkedRewardTasks)
        return completedRewardTasks >= required
    }
}
