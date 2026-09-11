package com.example.selfdisciplinepoc01.domain.canonical.task

import com.example.selfdisciplinepoc01.domain.canonical.cycle.CanonicalCycleId
import java.time.Instant

/**
 * Định nghĩa chuẩn của một Nhiệm vụ (Persistent Task Definition).
 * Tách biệt hoàn toàn khỏi trạng thái thực thi của nhiệm vụ theo từng chu kỳ.
 *
 * @param id Định danh duy nhất của nhiệm vụ.
 * @param title Tiêu đề nhiệm vụ.
 * @param description Mô tả chi tiết.
 * @param orderIndex Thứ tự hiển thị / ưu tiên của nhiệm vụ trong Sảnh Nhiệm Vụ.
 * @param hasReward Đánh dấu nhiệm vụ có liên kết ứng dụng thưởng hay không (INV-TASK-001).
 *                  Nếu false hoặc không liên kết app nào, nhiệm vụ là rewardless và không tính vào N.
 * @param createdAt Thời điểm tạo nhiệm vụ.
 * @param isArchived Cờ lưu trữ (nếu nhiệm vụ bị xóa mềm hoặc ẩn khỏi chu kỳ mới).
 */
data class CanonicalTask(
    val id: String,
    val title: String,
    val description: String = "",
    val orderIndex: Int = 0,
    val hasReward: Boolean = true,
    val createdAt: Instant = Instant.now(),
    val isArchived: Boolean = false
) {
    init {
        require(id.isNotBlank()) { "Task ID không được để trống" }
        require(title.isNotBlank()) { "Tiêu đề nhiệm vụ không được để trống" }
    }
}

/**
 * Trạng thái thực thi của nhiệm vụ trong một Chu kỳ cụ thể.
 */
enum class TaskCycleStatus {
    PENDING,
    COMPLETED,
    FAILED
}

/**
 * Biểu diễn trạng thái của một nhiệm vụ gắn với một Chu kỳ xác định (Cycle-Scoped State).
 * Thiết kế này ngăn ngừa việc ghi đè trực tiếp trạng thái toàn cục làm mất lịch sử chu kỳ.
 *
 * @param taskId Định danh nhiệm vụ tương ứng.
 * @param cycleId Chu kỳ mà trạng thái này thuộc về.
 * @param status Trạng thái hoàn thành trong chu kỳ.
 * @param completedAt Thời điểm hoàn thành trong chu kỳ (nếu có).
 */
data class TaskCycleState(
    val taskId: String,
    val cycleId: CanonicalCycleId,
    val status: TaskCycleStatus = TaskCycleStatus.PENDING,
    val completedAt: Instant? = null
) {
    val isCompleted: Boolean
        get() = status == TaskCycleStatus.COMPLETED
}

/**
 * Quan hệ liên kết nhiều-nhiều giữa Nhiệm vụ (Task) và Ứng dụng Phong Ấn (Vault App).
 * Theo MASTER SSOT:
 * - Một nhiệm vụ có thể mở khóa nhiều ứng dụng.
 * - Một ứng dụng có thể được mở khóa bởi nhiều nhiệm vụ.
 * - Xóa Vault App sẽ xóa liên kết này nhưng giữ nguyên Task.
 * - Thêm lại Vault App KHÔNG tự khôi phục liên kết này.
 */
data class TaskRewardLink(
    val taskId: String,
    val appPackageName: String,
    val linkedAt: Instant = Instant.now()
) {
    init {
        require(taskId.isNotBlank()) { "taskId không được để trống" }
        require(appPackageName.isNotBlank()) { "appPackageName không được để trống" }
    }
}
