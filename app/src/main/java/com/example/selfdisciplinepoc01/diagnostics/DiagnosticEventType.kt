package com.example.selfdisciplinepoc01.diagnostics

/**
 * PHASE 11-B: Standardized Finite Diagnostic Event Vocabulary.
 *
 * Covers all system layers without altering runtime or policy semantics:
 * - SERVICE: AccessibilityService lifecycle
 * - FOREGROUND: Target and non-target foreground window events
 * - POLICY: PolicyEngine evaluations and schedule/limit states
 * - WATCHER: ScheduleWatcher and UsageLimitWatcher scheduling, callbacks, and stale rejections
 * - LOCK: Lock decisions, Rule A/B/C rejections, accepted launches, session lifecycle
 * - SHIELD: BlockingShieldOverlay show, hide, cleanup, and timeout lifecycle
 * - LIFECYCLE: LockScreenActivity lifecycle transitions
 */
enum class DiagnosticEventType {
    // SERVICE
    SERVICE_CREATED,
    SERVICE_CONNECTED,
    SERVICE_DESTROYED,
    SERVICE_INTERRUPTED,

    // FOREGROUND
    FOREGROUND_EVENT,
    TARGET_CHANGED,
    TARGET_IGNORED,
    NON_TARGET_FOREGROUND,

    // POLICY
    POLICY_EVALUATION,
    POLICY_UPDATE,
    SCHEDULE_ACTIVE,
    SCHEDULE_INACTIVE,
    DAILY_LIMIT_REMAINING,
    DAILY_LIMIT_EXHAUSTED,

    // WATCHER - SCHEDULE
    SCHEDULE_WATCHER_STARTED,
    SCHEDULE_WATCHER_STOPPED,
    SCHEDULE_DEADLINE_SCHEDULED,
    SCHEDULE_DEADLINE_CALLBACK,
    SCHEDULE_STALE_CALLBACK,

    // WATCHER - USAGE LIMIT
    USAGE_WATCHER_STARTED,
    USAGE_WATCHER_STOPPED,
    USAGE_DEADLINE_SCHEDULED,
    USAGE_DEADLINE_CALLBACK,
    USAGE_STALE_CALLBACK,

    // LOCK
    LOCK_DECISION,
    LOCK_LAUNCH_ACCEPTED,
    LOCK_LAUNCH_REJECTED,
    LOCK_SESSION_STARTED,
    LOCK_SESSION_REUSED,

    // SHIELD
    SHIELD_SHOW_REQUESTED,
    SHIELD_SHOWN,
    SHIELD_SHOW_FAILED,
    SHIELD_HIDE,
    SHIELD_CLEANUP,
    SHIELD_TIMEOUT,

    // LIFECYCLE
    LOCKSCREEN_CREATED,
    LOCKSCREEN_RESUMED,
    LOCKSCREEN_NEW_INTENT,
    LOCKSCREEN_DESTROYED
}
