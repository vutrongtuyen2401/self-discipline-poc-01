package com.example.selfdisciplinepoc01.domain.canonical.cycle

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Định danh duy nhất cho một Chu kỳ (Cycle) trong Hệ thống Phong Ấn Dục Vọng.
 * Chu kỳ bắt đầu từ 04:00 ngày T đến 03:59:59 ngày T+1 (giờ địa phương).
 *
 * @param dateIdentifier Ngày địa phương bắt đầu chu kỳ (YYYY-MM-DD).
 */
data class CanonicalCycleId(
    val dateIdentifier: String
) {
    init {
        require(dateIdentifier.matches(Regex("""\d{4}-\d{2}-\d{2}"""))) {
            "dateIdentifier phải có định dạng YYYY-MM-DD, giá trị nhận: $dateIdentifier"
        }
    }

    override fun toString(): String = dateIdentifier

    companion object {
        fun of(localDate: LocalDate): CanonicalCycleId =
            CanonicalCycleId(localDate.toString())
    }
}

/**
 * Khung thời gian ranh giới của một Chu kỳ (Cycle Boundary).
 *
 * Theo MASTER SSOT:
 * - Điểm mốc chuyển giao chu kỳ là đúng 04:00 sáng giờ địa phương.
 * - Chu kỳ T bao gồm toàn bộ thời gian từ [04:00 ngày T, 04:00 ngày T+1).
 */
data class CanonicalCycleBoundary(
    val cycleId: CanonicalCycleId,
    val startInstant: Instant,
    val endInstant: Instant
) {
    init {
        require(startInstant.isBefore(endInstant)) {
            "startInstant ($startInstant) phải trước endInstant ($endInstant)"
        }
    }

    fun contains(instant: Instant): Boolean {
        return !instant.isBefore(startInstant) && instant.isBefore(endInstant)
    }

    companion object {
        val CYCLE_START_TIME: LocalTime = LocalTime.of(4, 0, 0, 0)

        /**
         * Tính toán ranh giới chu kỳ cho một thời điểm bất kỳ theo ZoneId.
         * Nếu thời điểm trước 04:00 sáng, nó thuộc chu kỳ của ngày hôm trước.
         */
        fun fromInstant(instant: Instant, zoneId: ZoneId): CanonicalCycleBoundary {
            val localDateTime = LocalDateTime.ofInstant(instant, zoneId)
            val cycleStartDate = if (localDateTime.toLocalTime().isBefore(CYCLE_START_TIME)) {
                localDateTime.toLocalDate().minusDays(1)
            } else {
                localDateTime.toLocalDate()
            }

            val cycleStartLdt = LocalDateTime.of(cycleStartDate, CYCLE_START_TIME)
            val cycleEndLdt = cycleStartLdt.plusDays(1)

            val startInstant = cycleStartLdt.atZone(zoneId).toInstant()
            val endInstant = cycleEndLdt.atZone(zoneId).toInstant()

            return CanonicalCycleBoundary(
                cycleId = CanonicalCycleId.of(cycleStartDate),
                startInstant = startInstant,
                endInstant = endInstant
            )
        }

        /**
         * Lấy ranh giới chu kỳ từ một CanonicalCycleId cụ thể.
         */
        fun fromCycleId(cycleId: CanonicalCycleId, zoneId: ZoneId): CanonicalCycleBoundary {
            val localDate = LocalDate.parse(cycleId.dateIdentifier)
            val cycleStartLdt = LocalDateTime.of(localDate, CYCLE_START_TIME)
            val cycleEndLdt = cycleStartLdt.plusDays(1)

            return CanonicalCycleBoundary(
                cycleId = cycleId,
                startInstant = cycleStartLdt.atZone(zoneId).toInstant(),
                endInstant = cycleEndLdt.atZone(zoneId).toInstant()
            )
        }
    }
}

/**
 * Động cơ Quản lý Chu kỳ (Canonical Cycle Engine) theo MASTER SSOT.
 *
 * Nhiệm vụ:
 * 1. Tính toán ranh giới chu kỳ chuẩn 04:00:00.000 theo múi giờ thiết bị (local ZoneId).
 * 2. Cung cấp cơ chế Offline Catch-up: khi mở lại ứng dụng sau nhiều ngày offline,
 *    lập tức chuyển sang chu kỳ của thời điểm hiện tại, TUYỆT ĐỐI KHÔNG replay/chạy bù
 *    lịch sử từng ngày.
 * 3. Hỗ trợ thay đổi múi giờ (timezone conversion) linh hoạt.
 */
object CycleEngine {

    fun getCurrentCycleBoundary(
        instant: Instant = Instant.now(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): CanonicalCycleBoundary {
        return CanonicalCycleBoundary.fromInstant(instant, zoneId)
    }

    fun getCurrentCycleId(
        instant: Instant = Instant.now(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): CanonicalCycleId {
        return getCurrentCycleBoundary(instant, zoneId).cycleId
    }

    fun resolveCycleBoundary(
        cycleId: CanonicalCycleId,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): CanonicalCycleBoundary {
        return CanonicalCycleBoundary.fromCycleId(cycleId, zoneId)
    }

    /**
     * Xác định chu kỳ hiện tại khi hệ thống trở lại hoạt động sau thời gian offline.
     * Quy tắc SSOT: Không replay các chu kỳ cũ, trả về chu kỳ bao chứa thời điểm hiện tại.
     */
    fun resolveCatchUpCycle(
        lastActiveInstant: Instant?,
        currentInstant: Instant = Instant.now(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): CanonicalCycleBoundary {
        // Dù lastActiveInstant cách đây bao lâu (ví dụ > 5 ngày), chu kỳ áp dụng
        // luôn là chu kỳ tại thời điểm hiện tại.
        return CanonicalCycleBoundary.fromInstant(currentInstant, zoneId)
    }

    /**
     * Kiểm tra xem 2 mốc thời gian có cùng thuộc về một chu kỳ hay không.
     */
    fun isSameCycle(
        instant1: Instant,
        instant2: Instant,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Boolean {
        val cycle1 = getCurrentCycleId(instant1, zoneId)
        val cycle2 = getCurrentCycleId(instant2, zoneId)
        return cycle1 == cycle2
    }
}
