package com.example.selfdisciplinepoc01.domain.canonical.task

import com.example.selfdisciplinepoc01.domain.canonical.vault.LockEvaluationResult

/**
 * Thời điểm hiệu lực của việc biến đổi cấu hình phần thưởng (Task Reward Mutation Timing)
 * theo Authoritative SSOT (docs/MASTER SSOT - HỆ THỐNG PHONG ẤN DỤC VỌNG.docx).
 */
enum class RewardMutationTiming {
    /**
     * Áp dụng ngay lập tức trong chu kỳ hiện tại (Current Cycle).
     * Bắt buộc kích hoạt tính toán lại khóa và đồng bộ hóa enforcement runtime tức thì.
     */
    IMMEDIATE_CURRENT_CYCLE,

    /**
     * Chờ áp dụng tại mốc 04:00 AM của chu kỳ tiếp theo (Next Cycle).
     * Chu kỳ hiện tại giữ nguyên cấu hình và trạng thái khóa.
     */
    PENDING_NEXT_CYCLE
}

/**
 * Kết quả sau khi thực hiện thao tác biến đổi nghiệp vụ Task / Reward Link.
 */
data class TaskMutationResult(
    val task: CanonicalTask?,
    val affectedAppsEvaluation: Map<String, LockEvaluationResult> = emptyMap(),
    val timing: RewardMutationTiming = RewardMutationTiming.IMMEDIATE_CURRENT_CYCLE
)
