package com.example.selfdisciplinepoc01.diagnostics

/**
 * Structured diagnostic event representation.
 *
 * Lightweight, non-blocking metadata container.
 * Absolutely NO sensitive user content, Window contents, or AccessibilityNodeInfo dumped.
 */
data class DiagnosticEvent(
    val type: DiagnosticEventType,
    val message: String,
    val packageName: String? = null,
    val sessionId: Long? = null,
    val generation: Long? = null,
    val lockReason: String? = null,
    val currentForegroundPackage: String? = null,
    val elapsedTimestamp: Long? = null,
    val scheduleEnabled: Boolean? = null,
    val isScheduleActive: Boolean? = null,
    val dailyLimitEnabled: Boolean? = null,
    val currentUsageMillis: Long? = null,
    val limitMillis: Long? = null,
    val remainingMillis: Long? = null,
    val shieldState: String? = null,
    val watcherRunning: Boolean? = null,
    val rejectionReason: String? = null,
    val details: Map<String, Any?> = emptyMap(),
    val timestampMillis: Long = System.currentTimeMillis()
) {
    // Secondary constructor accepting Any? for lockReason (e.g. LockReason enum)
    constructor(
        type: DiagnosticEventType,
        message: String,
        packageName: String? = null,
        sessionId: Long? = null,
        generation: Long? = null,
        lockReason: Any?,
        currentForegroundPackage: String? = null,
        elapsedTimestamp: Long? = null,
        scheduleEnabled: Boolean? = null,
        isScheduleActive: Boolean? = null,
        dailyLimitEnabled: Boolean? = null,
        currentUsageMillis: Long? = null,
        limitMillis: Long? = null,
        remainingMillis: Long? = null,
        shieldState: String? = null,
        watcherRunning: Boolean? = null,
        rejectionReason: String? = null,
        details: Map<String, Any?> = emptyMap(),
        timestampMillis: Long = System.currentTimeMillis()
    ) : this(
        type = type,
        message = message,
        packageName = packageName,
        sessionId = sessionId,
        generation = generation,
        lockReason = lockReason?.toString(),
        currentForegroundPackage = currentForegroundPackage,
        elapsedTimestamp = elapsedTimestamp,
        scheduleEnabled = scheduleEnabled,
        isScheduleActive = isScheduleActive,
        dailyLimitEnabled = dailyLimitEnabled,
        currentUsageMillis = currentUsageMillis,
        limitMillis = limitMillis,
        remainingMillis = remainingMillis,
        shieldState = shieldState,
        watcherRunning = watcherRunning,
        rejectionReason = rejectionReason,
        details = details,
        timestampMillis = timestampMillis
    )

    /**
     * Formats the event into a single-line, grep-friendly structured log string.
     */
    fun formatTrace(): String {
        val sb = StringBuilder(128)
        sb.append("[").append(type.name).append("] ").append(message)

        val meta = ArrayList<String>(12 + details.size)
        packageName?.let { meta.add("pkg='$it'") }
        sessionId?.let { meta.add("sessionId=$it") }
        generation?.let { meta.add("gen=$it") }
        lockReason?.let { meta.add("reason=$it") }
        currentForegroundPackage?.let { meta.add("fgPkg='$it'") }
        elapsedTimestamp?.let { meta.add("elapsed=${it}ms") }
        scheduleEnabled?.let { meta.add("schEnabled=$it") }
        isScheduleActive?.let { meta.add("schActive=$it") }
        dailyLimitEnabled?.let { meta.add("limitEnabled=$it") }
        currentUsageMillis?.let { meta.add("used=${it}ms") }
        limitMillis?.let { meta.add("limit=${it}ms") }
        remainingMillis?.let { meta.add("remaining=${it}ms") }
        shieldState?.let { meta.add("shield='$it'") }
        watcherRunning?.let { meta.add("watcherRunning=$it") }
        rejectionReason?.let { meta.add("rejection='$it'") }

        for ((k, v) in details) {
            meta.add("$k=$v")
        }

        if (meta.isNotEmpty()) {
            sb.append(" | ").append(meta.joinToString(", "))
        }

        return sb.toString()
    }
}
