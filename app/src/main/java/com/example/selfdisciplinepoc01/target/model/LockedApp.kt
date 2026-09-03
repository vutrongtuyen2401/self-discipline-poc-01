package com.example.selfdisciplinepoc01.target.model

/**
 * Schedule policy for a locked target.
 * Defines a daily time window [start, end) during which the target application is locked.
 *
 * Semantic rules:
 * - Normal interval (start < end): LOCKED when start <= now < end
 * - Cross-midnight interval (start > end): LOCKED when now >= start OR now < end
 * - 24-hour interval (start == end): LOCKED all day when enabled == true
 */
data class TimeSchedule(
    val enabled: Boolean = false,
    val startHour: Int = 9,
    val startMinute: Int = 0,
    val endHour: Int = 17,
    val endMinute: Int = 0
) {
    init {
        require(startHour in 0..23) { "startHour must be between 0 and 23, was: $startHour" }
        require(startMinute in 0..59) { "startMinute must be between 0 and 59, was: $startMinute" }
        require(endHour in 0..23) { "endHour must be between 0 and 23, was: $endHour" }
        require(endMinute in 0..59) { "endMinute must be between 0 and 59, was: $endMinute" }
    }
}

/**
 * Daily usage limit policy for a locked target.
 *
 * Semantic rules:
 * - If enabled == true, the target is LOCKED when todayUsage >= limitMillis
 * - Exactly reaching the limit locks immediately (usage >= limit)
 * - Supports minutes (standard) and optional seconds (testing/sub-minute limits)
 */
data class TimeLimit(
    val enabled: Boolean = false,
    val dailyLimitMinutes: Int = 0,
    val dailyLimitSeconds: Int? = null
) {
    init {
        require(dailyLimitMinutes >= 0) { "dailyLimitMinutes must be non-negative, was: $dailyLimitMinutes" }
        require(dailyLimitSeconds == null || dailyLimitSeconds >= 0) {
            "dailyLimitSeconds must be non-negative, was: $dailyLimitSeconds"
        }
    }

    val limitMillis: Long
        get() = if (dailyLimitSeconds != null && dailyLimitSeconds > 0) {
            dailyLimitSeconds * 1000L
        } else {
            dailyLimitMinutes * 60_000L
        }
}

/**
 * Target application model compatible with Phase 05–08.
 *
 * Backward compatibility guarantee:
 * - `packageName` and `enabled` retain their original semantics.
 * - If `schedule == null` and `timeLimit == null`, an enabled target remains locked 24/7 (Phase 06 behavior).
 * - If `enabled == false`, target is ALLOWED regardless of schedule or time limit.
 */
data class LockedApp(
    val packageName: String,
    val enabled: Boolean = true,
    val schedule: TimeSchedule? = null,
    val timeLimit: TimeLimit? = null
)
