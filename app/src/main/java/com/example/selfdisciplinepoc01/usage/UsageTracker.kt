package com.example.selfdisciplinepoc01.usage

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.selfdisciplinepoc01.time.BusinessDayProvider
import com.example.selfdisciplinepoc01.time.BusinessDayProviderImpl
import com.example.selfdisciplinepoc01.time.Clock
import com.example.selfdisciplinepoc01.time.SystemClockImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks application usage time accurately using monotonic elapsed realtime,
 * with business day determination and 04:00 rollover splitting based on wall-clock time.
 */
class UsageTracker(
    val clock: Clock = SystemClockImpl(),
    private val dataStore: DataStore<Preferences>? = null,
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    val zoneIdProvider: () -> ZoneId = { ZoneId.systemDefault() },
    val businessDayProvider: BusinessDayProvider = BusinessDayProviderImpl()
) : UsageProvider {

    val zoneId: ZoneId get() = zoneIdProvider()

    constructor(
        clock: Clock = SystemClockImpl(),
        dataStore: DataStore<Preferences>? = null,
        coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        zoneId: ZoneId
    ) : this(clock, dataStore, coroutineScope, { zoneId }, BusinessDayProviderImpl())

    constructor(
        clock: Clock = SystemClockImpl(),
        dataStore: DataStore<Preferences>? = null,
        coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        zoneId: ZoneId,
        businessDayProvider: BusinessDayProvider
    ) : this(clock, dataStore, coroutineScope, { zoneId }, businessDayProvider)

    // In-memory aggregates map: key = "${YYYY-MM-DD}_${packageName}", value = usageMillis
    private val usageMap = ConcurrentHashMap<String, Long>()

    // Active session state protected by lock
    private val lock = Any()
    private var activeUsagePackage: String? = null
    private var activeSessionStartElapsed: Long = 0L
    private var activeSessionStartWall: Long = 0L

    init {
        // Hydrate from DataStore if available
        if (dataStore != null) {
            coroutineScope.launch {
                try {
                    val prefs = dataStore.data.first()
                    val jsonStr = prefs[KEY_USAGE_AGGREGATES]
                    if (!jsonStr.isNullOrBlank()) {
                        loadFromJson(jsonStr)
                    }
                } catch (e: Throwable) {
                    Log.e(TAG, "Error hydrating usage aggregates from DataStore", e)
                }
            }
        }
    }

    /**
     * Starts an active usage session for [packageName].
     *
     * IDEMPOTENT: If [packageName] is already actively tracking, this call is a NO-OP
     * and will NOT reset the session start timestamp.
     *
     * If a different package was previously active, that session is closed and recorded
     * before the new package starts.
     */
    fun startSession(packageName: String) {
        val cleanPkg = packageName.trim()
        if (cleanPkg.isBlank()) return

        synchronized(lock) {
            if (activeUsagePackage == cleanPkg) {
                // Idempotent: already tracking this exact package
                return
            }

            // If another package was active, close it first
            if (activeUsagePackage != null) {
                stopActiveSessionLocked()
            }

            activeUsagePackage = cleanPkg
            activeSessionStartElapsed = clock.elapsedRealtimeMillis()
            activeSessionStartWall = clock.wallTimeMillis()
            Log.i(TAG, "[USAGE: START] Started tracking $cleanPkg at elapsed=$activeSessionStartElapsed")
        }
    }

    /**
     * Stops the active usage session.
     *
     * If [packageName] is specified, only stops if it matches the currently active package.
     * Calculates duration via monotonic elapsed realtime, splits at midnight if needed,
     * updates aggregate usage, and triggers background persistence.
     */
    fun stopSession(packageName: String? = null) {
        synchronized(lock) {
            if (activeUsagePackage == null) return
            if (packageName != null && activeUsagePackage != packageName) return

            stopActiveSessionLocked()
        }
    }

    private fun stopActiveSessionLocked() {
        val pkg = activeUsagePackage ?: return
        val endElapsed = clock.elapsedRealtimeMillis()
        val endWall = clock.wallTimeMillis()
        val startElapsed = activeSessionStartElapsed
        val startWall = activeSessionStartWall

        val duration = maxOf(0L, endElapsed - startElapsed)
        if (duration > 0L) {
            recordUsageSegment(pkg, startWall, endWall, duration)
        }
        Log.i(TAG, "[USAGE: STOP] Stopped tracking $pkg, elapsed=${duration}ms")

        activeUsagePackage = null
        activeSessionStartElapsed = 0L
        activeSessionStartWall = 0L

        persistAggregatesAsync()
    }

    /**
     * Records a completed usage duration, splitting proportionally across business days
     * if the wall clock crossed the 04:00 business day boundary.
     */
    internal fun recordUsageSegment(
        pkg: String,
        startWall: Long,
        endWall: Long,
        elapsedDuration: Long
    ) {
        if (elapsedDuration <= 0L) return
        val startDate = businessDayProvider.getBusinessDate(startWall, zoneId)
        val endDate = businessDayProvider.getBusinessDate(endWall, zoneId)

        if (startDate == endDate) {
            // All duration belongs to single business day
            addUsage(startDate, pkg, elapsedDuration)
        } else if (endDate.isAfter(startDate)) {
            // Crossed 04:00 business cycle boundary: split between cycles
            val boundaryWall = businessDayProvider.getNextBoundaryWall(startWall, zoneId)

            val wallTimeBeforeBoundary = maxOf(0L, boundaryWall - startWall)
            val wallTotal = maxOf(1L, endWall - startWall)

            // Allocate duration proportionally based on wall time boundary
            val durationFirstDay = minOf(elapsedDuration, (elapsedDuration * wallTimeBeforeBoundary) / wallTotal)
            val durationSecondDay = maxOf(0L, elapsedDuration - durationFirstDay)

            addUsage(startDate, pkg, durationFirstDay)
            addUsage(endDate, pkg, durationSecondDay)

            Log.i(
                TAG,
                "[USAGE: CYCLE_SPLIT] $pkg crossed 04:00 boundary! $startDate: +${durationFirstDay}ms, $endDate: +${durationSecondDay}ms (total: ${elapsedDuration}ms)"
            )
        } else {
            // Wall clock moved backward during session!
            // Monotonic elapsed time is still authoritative: assign to startDate
            addUsage(startDate, pkg, elapsedDuration)
        }
    }

    private fun addUsage(date: LocalDate, pkg: String, duration: Long) {
        val key = makeKey(date, pkg)
        usageMap.compute(key) { _, current ->
            (current ?: 0L) + duration
        }
    }

    /**
     * Returns total usage for [packageName] today in milliseconds for current business cycle,
     * including completed sessions and the currently running active session.
     */
    override fun getTodayUsage(packageName: String): Long {
        val today = businessDayProvider.getBusinessDate(clock.wallTimeMillis(), zoneId)
        val key = makeKey(today, packageName)
        var total = maxOf(0L, usageMap[key] ?: 0L)

        synchronized(lock) {
            if (activeUsagePackage == packageName) {
                val nowElapsed = clock.elapsedRealtimeMillis()
                val nowWall = clock.wallTimeMillis()
                val currentDuration = maxOf(0L, nowElapsed - activeSessionStartElapsed)

                val startDay = businessDayProvider.getBusinessDate(activeSessionStartWall, zoneId)
                if (startDay == today) {
                    total += currentDuration
                } else if (today.isAfter(startDay)) {
                    // Session started in previous business day and is still running in today's business cycle
                    val boundaryWall = businessDayProvider.getBusinessDayStartWall(today, zoneId)
                    val wallAfterBoundary = maxOf(0L, nowWall - boundaryWall)
                    val wallTotal = maxOf(1L, nowWall - activeSessionStartWall)
                    val todayPortion = minOf(currentDuration, (currentDuration * wallAfterBoundary) / wallTotal)
                    total += todayPortion
                }
            }
        }
        return total
    }

    /**
     * Screen turned OFF: pause active usage session and persist data.
     */
    fun onScreenOff() {
        synchronized(lock) {
            if (activeUsagePackage != null) {
                Log.i(TAG, "[USAGE: SCREEN_OFF] Pausing active session for $activeUsagePackage")
                stopActiveSessionLocked()
            }
        }
    }

    /**
     * Screen turned ON: Do NOT blindly restart. Wait for AccessibilityService event.
     */
    fun onScreenOn() {
        Log.i(TAG, "[USAGE: SCREEN_ON] Screen turned on, waiting for foreground event")
    }

    /**
     * Returns true if there is an active usage session running for [packageName].
     */
    fun isSessionActive(packageName: String): Boolean {
        synchronized(lock) {
            return activeUsagePackage == packageName
        }
    }

    fun getActivePackage(): String? {
        synchronized(lock) {
            return activeUsagePackage
        }
    }

    fun getActiveUsagePackage(): String? = synchronized(lock) { activeUsagePackage }

    private fun persistAggregatesAsync() {
        if (dataStore == null) return
        val snapshotJson = serializeToJson()
        coroutineScope.launch {
            try {
                dataStore.edit { prefs ->
                    prefs[KEY_USAGE_AGGREGATES] = snapshotJson
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist usage aggregates to DataStore", e)
            }
        }
    }

    internal fun serializeToJson(): String {
        val json = JSONObject()
        for ((key, value) in usageMap) {
            json.put(key, value)
        }
        return json.toString()
    }

    internal fun loadFromJson(jsonStr: String) {
        try {
            val json = JSONObject(jsonStr)
            val keys = json.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val value = json.optLong(k, -1L)
                if (value >= 0L && k.isNotBlank()) {
                    usageMap[k] = value
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse usage aggregates json: $jsonStr", e)
        }
    }

    companion object {
        private const val TAG = "UsageTracker"
        val KEY_USAGE_AGGREGATES = stringPreferencesKey("usage_aggregates_json")
        private val DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE

        fun makeKey(date: LocalDate, pkg: String): String = "${date.format(DATE_FORMATTER)}_$pkg"
    }
}
