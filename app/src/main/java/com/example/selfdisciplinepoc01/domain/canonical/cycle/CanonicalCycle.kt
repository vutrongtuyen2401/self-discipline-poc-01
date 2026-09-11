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
