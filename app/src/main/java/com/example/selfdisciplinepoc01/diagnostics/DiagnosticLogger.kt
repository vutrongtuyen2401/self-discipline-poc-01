package com.example.selfdisciplinepoc01.diagnostics

import android.util.Log
import java.util.ArrayDeque

/**
 * DiagnosticLogger contract for production observability.
 */
interface DiagnosticLogger {
    fun debug(event: DiagnosticEvent)
    fun info(event: DiagnosticEvent)
    fun warn(event: DiagnosticEvent)
    fun error(event: DiagnosticEvent, throwable: Throwable? = null)
}

/**
 * Production implementation outputting to Android Logcat with a bounded in-memory ring buffer.
 *
 * Guarantees:
 * - Completely NON-BLOCKING: no disk writes, no DataStore writes, no network calls.
 * - Thread-safe bounded buffer to capture the most recent events for debugging.
 */
class AndroidDiagnosticLogger(
    private val tag: String = TAG,
    private val bufferCapacity: Int = DEFAULT_CAPACITY
) : DiagnosticLogger {

    private val lock = Any()
    private val ringBuffer = ArrayDeque<DiagnosticEvent>(bufferCapacity)

    override fun debug(event: DiagnosticEvent) {
        recordEvent(event)
        Log.i(tag, event.formatTrace())
    }

    override fun info(event: DiagnosticEvent) {
        recordEvent(event)
        Log.i(tag, event.formatTrace())
    }

    override fun warn(event: DiagnosticEvent) {
        recordEvent(event)
        Log.w(tag, event.formatTrace())
    }

    override fun error(event: DiagnosticEvent, throwable: Throwable?) {
        recordEvent(event)
        if (throwable != null) {
            Log.e(tag, event.formatTrace(), throwable)
        } else {
            Log.e(tag, event.formatTrace())
        }
    }

    private fun recordEvent(event: DiagnosticEvent) {
        synchronized(lock) {
            if (ringBuffer.size >= bufferCapacity) {
                ringBuffer.pollFirst()
            }
            ringBuffer.addLast(event)
        }
    }

    fun getRecentEvents(): List<DiagnosticEvent> {
        synchronized(lock) {
            return ringBuffer.toList()
        }
    }

    fun clear() {
        synchronized(lock) {
            ringBuffer.clear()
        }
    }

    companion object {
        const val TAG = "DiagnosticTrace"
        const val DEFAULT_CAPACITY = 250
        val defaultInstance = AndroidDiagnosticLogger()
    }
}

/**
 * No-op implementation for environments where logging is disabled or unnecessary.
 */
class NoOpDiagnosticLogger : DiagnosticLogger {
    override fun debug(event: DiagnosticEvent) {}
    override fun info(event: DiagnosticEvent) {}
    override fun warn(event: DiagnosticEvent) {}
    override fun error(event: DiagnosticEvent, throwable: Throwable?) {}
}

/**
 * Deterministic in-memory logger designed for Unit Tests.
 */
class InMemoryDiagnosticLogger : DiagnosticLogger {
    private val lock = Any()
    private val events = mutableListOf<DiagnosticEvent>()

    override fun debug(event: DiagnosticEvent) { record(event) }
    override fun info(event: DiagnosticEvent) { record(event) }
    override fun warn(event: DiagnosticEvent) { record(event) }
    override fun error(event: DiagnosticEvent, throwable: Throwable?) { record(event) }

    private fun record(event: DiagnosticEvent) {
        synchronized(lock) {
            events.add(event)
        }
    }

    fun getEvents(): List<DiagnosticEvent> = synchronized(lock) { events.toList() }

    fun getEventsOfType(type: DiagnosticEventType): List<DiagnosticEvent> = synchronized(lock) {
        events.filter { it.type == type }
    }

    fun hasEvent(type: DiagnosticEventType): Boolean = synchronized(lock) {
        events.any { it.type == type }
    }

    fun lastEventOfType(type: DiagnosticEventType): DiagnosticEvent? = synchronized(lock) {
        events.lastOrNull { it.type == type }
    }

    fun clear() = synchronized(lock) {
        events.clear()
    }
}

/**
 * Injectable provider for DiagnosticLogger across components.
 */
object DiagnosticLoggerProvider {
    @Volatile
    private var instance: DiagnosticLogger = AndroidDiagnosticLogger.defaultInstance

    fun getLogger(): DiagnosticLogger = instance

    fun setLoggerForTesting(logger: DiagnosticLogger) {
        instance = logger
    }

    fun reset() {
        instance = AndroidDiagnosticLogger.defaultInstance
    }
}
