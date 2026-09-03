package com.example.selfdisciplinepoc01.usage

/**
 * Read-only contract for querying usage time.
 * Decouples PolicyEngine from concrete tracking and persistence details.
 */
interface UsageProvider {
    /**
     * Returns the total usage of [packageName] for the current calendar day in milliseconds.
     * Includes both completed sessions and currently active ongoing session time.
     */
    fun getTodayUsage(packageName: String): Long
}
