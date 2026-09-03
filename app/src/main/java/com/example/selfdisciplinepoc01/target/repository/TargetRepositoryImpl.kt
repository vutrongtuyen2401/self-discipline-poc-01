package com.example.selfdisciplinepoc01.target.repository

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.selfdisciplinepoc01.target.model.LockedApp
import com.example.selfdisciplinepoc01.target.model.TimeLimit
import com.example.selfdisciplinepoc01.target.model.TimeSchedule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

val Context.targetDataStore: DataStore<Preferences> by preferencesDataStore(name = "target_preferences")

class TargetRepositoryImpl(
    private val dataStore: DataStore<Preferences>,
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : TargetRepository {

    constructor(context: Context) : this(context.applicationContext.targetDataStore)

    private val activeLockedPackages = ConcurrentHashMap.newKeySet<String>()
    private val targetMap = ConcurrentHashMap<String, LockedApp>()

    init {
        // 1. Safe default cache initialized immediately: com.android.chrome enabled
        val defaultApp = LockedApp(packageName = DEFAULT_TARGET_PACKAGE, enabled = true)
        activeLockedPackages.add(DEFAULT_TARGET_PACKAGE)
        targetMap[DEFAULT_TARGET_PACKAGE] = defaultApp

        // 2. Asynchronously hydrate in-memory cache from persistent DataStore
        coroutineScope.launch {
            try {
                getLockedPackages().collect { apps ->
                    syncCache(apps)
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Error in background DataStore collector, keeping safe default", e)
                activeLockedPackages.add(DEFAULT_TARGET_PACKAGE)
                targetMap[DEFAULT_TARGET_PACKAGE] = defaultApp
            }
        }
    }

    private fun syncCache(apps: List<LockedApp>) {
        val enabledPackages = apps.filter { it.enabled }.map { it.packageName }.toSet()
        activeLockedPackages.clear()
        activeLockedPackages.addAll(enabledPackages)

        targetMap.clear()
        for (app in apps) {
            targetMap[app.packageName] = app
        }
        Log.d(TAG, "In-memory cache synchronized: $activeLockedPackages (targets: ${targetMap.size})")
    }

    override fun getLockedPackages(): Flow<List<LockedApp>> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                Log.e(TAG, "Failed reading DataStore preferences, emitting empty", exception)
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val jsonStr = preferences[KEY_LOCKED_APPS]
            val apps = deserialize(jsonStr)
            syncCache(apps)
            apps
        }

    override fun isLocked(packageName: String): Boolean {
        return try {
            activeLockedPackages.contains(packageName)
        } catch (e: Throwable) {
            Log.e(TAG, "Error checking isLocked for $packageName, falling back to default", e)
            packageName == DEFAULT_TARGET_PACKAGE
        }
    }

    override fun getTarget(packageName: String): LockedApp? {
        return targetMap[packageName]
    }

    override suspend fun add(packageName: String) {
        val cleanPkg = packageName.trim()
        if (cleanPkg.isBlank()) return

        val newApp = LockedApp(cleanPkg, enabled = true)
        activeLockedPackages.add(cleanPkg)
        targetMap[cleanPkg] = newApp

        try {
            dataStore.edit { preferences ->
                val currentJson = preferences[KEY_LOCKED_APPS]
                val currentList = deserialize(currentJson).toMutableList()
                val existingIndex = currentList.indexOfFirst { it.packageName.equals(cleanPkg, ignoreCase = true) }
                if (existingIndex >= 0) {
                    currentList[existingIndex] = currentList[existingIndex].copy(enabled = true)
                } else {
                    currentList.add(newApp)
                }
                preferences[KEY_LOCKED_APPS] = serialize(currentList)
                syncCache(currentList)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist add target: $cleanPkg", e)
        }
    }

    override suspend fun remove(packageName: String) {
        val cleanPkg = packageName.trim()
        if (cleanPkg.isBlank()) return

        activeLockedPackages.remove(cleanPkg)
        targetMap.remove(cleanPkg)

        try {
            dataStore.edit { preferences ->
                val currentJson = preferences[KEY_LOCKED_APPS]
                val currentList = deserialize(currentJson).toMutableList()
                currentList.removeAll { it.packageName.equals(cleanPkg, ignoreCase = true) }
                preferences[KEY_LOCKED_APPS] = serialize(currentList)
                syncCache(currentList)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist remove target: $cleanPkg", e)
        }
    }

    override suspend fun setEnabled(packageName: String, enabled: Boolean) {
        val cleanPkg = packageName.trim()
        if (cleanPkg.isBlank()) return

        if (enabled) {
            activeLockedPackages.add(cleanPkg)
        } else {
            activeLockedPackages.remove(cleanPkg)
        }
        targetMap.computeIfPresent(cleanPkg) { _, existing -> existing.copy(enabled = enabled) }

        try {
            dataStore.edit { preferences ->
                val currentJson = preferences[KEY_LOCKED_APPS]
                val currentList = deserialize(currentJson).toMutableList()
                val index = currentList.indexOfFirst { it.packageName.equals(cleanPkg, ignoreCase = true) }
                if (index >= 0) {
                    currentList[index] = currentList[index].copy(enabled = enabled)
                } else if (enabled) {
                    currentList.add(LockedApp(cleanPkg, enabled = true))
                }
                preferences[KEY_LOCKED_APPS] = serialize(currentList)
                syncCache(currentList)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist setEnabled for $cleanPkg", e)
        }
    }

    override suspend fun updatePolicy(
        packageName: String,
        schedule: TimeSchedule?,
        timeLimit: TimeLimit?
    ) {
        val cleanPkg = packageName.trim()
        if (cleanPkg.isBlank()) return

        targetMap.compute(cleanPkg) { _, existing ->
            (existing ?: LockedApp(cleanPkg, enabled = true)).copy(
                schedule = schedule,
                timeLimit = timeLimit
            )
        }

        try {
            dataStore.edit { preferences ->
                val currentJson = preferences[KEY_LOCKED_APPS]
                val currentList = deserialize(currentJson).toMutableList()
                val index = currentList.indexOfFirst { it.packageName.equals(cleanPkg, ignoreCase = true) }
                if (index >= 0) {
                    currentList[index] = currentList[index].copy(
                        schedule = schedule,
                        timeLimit = timeLimit
                    )
                } else {
                    currentList.add(
                        LockedApp(
                            packageName = cleanPkg,
                            enabled = true,
                            schedule = schedule,
                            timeLimit = timeLimit
                        )
                    )
                }
                preferences[KEY_LOCKED_APPS] = serialize(currentList)
                syncCache(currentList)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist updatePolicy for $cleanPkg", e)
        }
    }

    companion object {
        private const val TAG = "TargetRepository"
        const val DEFAULT_TARGET_PACKAGE = "com.android.chrome"
        val KEY_LOCKED_APPS = stringPreferencesKey("locked_apps_json")

        val DEFAULT_APPS: List<LockedApp> = listOf(
            LockedApp(packageName = DEFAULT_TARGET_PACKAGE, enabled = true)
        )

        fun serialize(apps: List<LockedApp>): String {
            val jsonArray = JSONArray()
            for (app in apps) {
                val jsonObject = JSONObject().apply {
                    put("packageName", app.packageName)
                    put("enabled", app.enabled)
                    if (app.schedule != null) {
                        val schedObj = JSONObject().apply {
                            put("enabled", app.schedule.enabled)
                            put("startHour", app.schedule.startHour)
                            put("startMinute", app.schedule.startMinute)
                            put("endHour", app.schedule.endHour)
                            put("endMinute", app.schedule.endMinute)
                        }
                        put("schedule", schedObj)
                    }
                    if (app.timeLimit != null) {
                        val limitObj = JSONObject().apply {
                            put("enabled", app.timeLimit.enabled)
                            put("dailyLimitMinutes", app.timeLimit.dailyLimitMinutes)
                            if (app.timeLimit.dailyLimitSeconds != null) {
                                put("dailyLimitSeconds", app.timeLimit.dailyLimitSeconds)
                            }
                        }
                        put("timeLimit", limitObj)
                    }
                }
                jsonArray.put(jsonObject)
            }
            return jsonArray.toString()
        }

        fun deserialize(json: String?): List<LockedApp> {
            if (json == null) {
                return DEFAULT_APPS
            }
            return try {
                val jsonArray = JSONArray(json)
                val result = mutableListOf<LockedApp>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val pkg = obj.optString("packageName")
                    val enabled = obj.optBoolean("enabled", true)

                    val schedule = if (obj.has("schedule")) {
                        val sObj = obj.getJSONObject("schedule")
                        TimeSchedule(
                            enabled = sObj.optBoolean("enabled", false),
                            startHour = sObj.optInt("startHour", 9),
                            startMinute = sObj.optInt("startMinute", 0),
                            endHour = sObj.optInt("endHour", 17),
                            endMinute = sObj.optInt("endMinute", 0)
                        )
                    } else null

                    val timeLimit = if (obj.has("timeLimit")) {
                        val lObj = obj.getJSONObject("timeLimit")
                        val sec = if (lObj.has("dailyLimitSeconds")) lObj.optInt("dailyLimitSeconds") else null
                        TimeLimit(
                            enabled = lObj.optBoolean("enabled", false),
                            dailyLimitMinutes = lObj.optInt("dailyLimitMinutes", 0),
                            dailyLimitSeconds = sec
                        )
                    } else null

                    if (pkg.isNotBlank()) {
                        result.add(LockedApp(pkg, enabled, schedule, timeLimit))
                    }
                }
                result
            } catch (e: Exception) {
                Log.e(TAG, "Error deserializing locked apps json: $json, falling back to safe default", e)
                DEFAULT_APPS
            }
        }
    }
}
