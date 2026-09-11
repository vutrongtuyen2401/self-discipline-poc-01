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
 * Bộ quy tắc đánh giá khóa chuẩn (Canonical Lock Policy) theo MASTER SSOT.
 *
 * Invariant tối thượng theo SSOT:
 * APP LOCKED iff
 *    at least one incomplete current-cycle linked reward task
 *    AND
 *    no effective voucher.
 *
 * Các quy tắc bắt buộc:
 * 1. Ứng dụng mới vào Vault chưa gán nhiệm vụ thưởng (N=0) => UNLOCKED.
 * 2. Nhiệm vụ không có reward (rewardless) KHÔNG được tính vào mẫu số N (INV-TASK-001).
 * 3. Bảng chuẩn hóa số nhiệm vụ cần hoàn thành:
 *    N=0 -> 0
 *    N=1 -> 1
 *    N=2 -> 1  (Đặc xá khởi đầu theo SSOT: người dùng chỉ cần hoàn thành 1/2 nhiệm vụ)
 *    N=3 -> 2
 *    N=4 -> 3
 *    N=5 -> 4
 *    N=6 -> 4
 *    N>6 -> ceil(2N/3)
 * 4. Nếu có Voucher còn hiệu lực cho app => UNLOCKED (Voucher đè bẹp điều kiện khóa).
 */
object CanonicalLockPolicy {

    /**
     * Tính toán số lượng nhiệm vụ cần hoàn thành theo bảng chuẩn hóa SSOT.
     */
    fun calculateRequiredCompletions(totalLinkedRewardTasks: Int): Int {
        if (totalLinkedRewardTasks <= 0) return 0
        return when (totalLinkedRewardTasks) {
            1 -> 1
            2 -> 1 // Đặc xá khởi đầu theo MASTER SSOT
            3 -> 2
            4 -> 3
            5 -> 4
            6 -> 4
            else -> Math.ceil((2.0 * totalLinkedRewardTasks) / 3.0).toInt()
        }
    }

    /**
     * Đánh giá trạng thái khóa của một ứng dụng.
     *
     * @param packageName Package name của ứng dụng cần đánh giá.
     * @param linkedTasks Danh sách các nhiệm vụ được liên kết với ứng dụng này.
     * @param cycleTaskStates Trạng thái của các nhiệm vụ trong chu kỳ hiện tại.
     * @param activeVouchers Danh sách voucher đang có hiệu lực tại thời điểm đánh giá.
     * @param evaluationInstant Thời điểm đánh giá (mặc định Instant.now()).
     */
    fun evaluateLock(
        packageName: String,
        linkedTasks: List<CanonicalTask>,
        cycleTaskStates: Map<String, TaskCycleState>,
        activeVouchers: List<VoucherEffect> = emptyList(),
        evaluationInstant: Instant = Instant.now()
    ): LockEvaluationResult {
        // 1. Kiểm tra Voucher còn hiệu lực
        val effectiveVoucher = activeVouchers.firstOrNull { voucher ->
            voucher.isEffectiveAt(evaluationInstant) && voucher.appliesTo(packageName)
        }
        if (effectiveVoucher != null) {
            return LockEvaluationResult(
                packageName = packageName,
                decision = CanonicalLockDecision.UNLOCKED,
                reason = "UNLOCKED_BY_VOUCHER: Thẻ bài '${effectiveVoucher.voucherName}' đang có hiệu lực",
                totalLinkedRewardTasks = linkedTasks.count { it.hasReward },
                completedLinkedRewardTasks = 0,
                requiredCompletions = 0,
                effectiveVoucher = effectiveVoucher
            )
        }

        // 2. Lọc các nhiệm vụ hợp lệ: Phải có hasReward == true (INV-TASK-001)
        val validRewardTasks = linkedTasks.filter { it.hasReward && !it.isArchived }
        val n = validRewardTasks.size

        // 3. Quy tắc N=0: Ứng dụng mới thêm hoặc không có linked reward task nào => UNLOCKED
        if (n == 0) {
            return LockEvaluationResult(
                packageName = packageName,
                decision = CanonicalLockDecision.UNLOCKED,
                reason = "UNLOCKED_NO_REWARD_TASKS: Ứng dụng chưa liên kết nhiệm vụ thưởng nào (N=0)",
                totalLinkedRewardTasks = 0,
                completedLinkedRewardTasks = 0,
                requiredCompletions = 0,
                effectiveVoucher = null
            )
        }

        // 4. Tính toán số nhiệm vụ đã hoàn thành trong chu kỳ hiện tại
        val completedCount = validRewardTasks.count { task ->
            cycleTaskStates[task.id]?.isCompleted == true
        }

        val required = calculateRequiredCompletions(n)

        // 5. Đánh giá theo ngưỡng yêu cầu
        return if (completedCount >= required) {
            LockEvaluationResult(
                packageName = packageName,
                decision = CanonicalLockDecision.UNLOCKED,
                reason = "UNLOCKED_THRESHOLD_MET: Đã hoàn thành $completedCount/$n nhiệm vụ (yêu cầu $required)",
                totalLinkedRewardTasks = n,
                completedLinkedRewardTasks = completedCount,
                requiredCompletions = required,
                effectiveVoucher = null
            )
        } else {
            val incompleteCount = n - completedCount
            LockEvaluationResult(
                packageName = packageName,
                decision = CanonicalLockDecision.LOCKED,
                reason = "LOCKED_INCOMPLETE_TASKS: Còn $incompleteCount nhiệm vụ chưa hoàn thành (đã đạt $completedCount/$n, cần $required)",
                totalLinkedRewardTasks = n,
                completedLinkedRewardTasks = completedCount,
                requiredCompletions = required,
                effectiveVoucher = null
            )
        }
    }
}
