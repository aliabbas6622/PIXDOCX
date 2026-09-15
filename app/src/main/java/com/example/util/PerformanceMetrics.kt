package com.example.util

import java.util.Collections

/**
 * Lightweight in-memory performance metrics recorder (deep optimization guide §10.2).
 *
 * Tracks rolling windows of operation durations so regressions can be spotted
 * via `adb logcat` / debugger without pulling in a full APM SDK. Thread-safe.
 */
object PerformanceMetrics {

    object Keys {
        const val DOCUMENT_LOAD_TIME = "document_load_time_ms"
        const val FORMULA_CALCULATION_TIME = "formula_calc_time_ms"
        const val LIST_SCROLL_FRAME_TIME = "scroll_frame_time_ms"
        const val DATABASE_QUERY_TIME = "db_query_time_ms"
        const val SAVE_OPERATION_TIME = "save_operation_time_ms"
    }

    private const val MAX_SAMPLES = 100

    private val metrics: MutableMap<String, MutableList<Long>> =
        Collections.synchronizedMap(mutableMapOf())

    fun record(metricName: String, durationMs: Long) {
        val samples = metrics.getOrPut(metricName) { mutableListOf() }
        synchronized(samples) {
            samples.add(durationMs)
            if (samples.size > MAX_SAMPLES) {
                samples.removeAt(0)
            }
        }
    }

    /** Records how long [block] takes and returns its result. */
    inline fun <T> measure(metricName: String, block: () -> T): T {
        val start = System.currentTimeMillis()
        val result = block()
        record(metricName, System.currentTimeMillis() - start)
        return result
    }

    fun getAverage(metricName: String): Double {
        val samples = metrics[metricName] ?: return 0.0
        return synchronized(samples) {
            if (samples.isEmpty()) 0.0 else samples.average()
        }
    }

    fun get95thPercentile(metricName: String): Double {
        val samples = metrics[metricName] ?: return 0.0
        return synchronized(samples) {
            if (samples.isEmpty()) return@synchronized 0.0
            val sorted = samples.sorted()
            val index = (sorted.size * 0.95).toInt().coerceIn(0, sorted.size - 1)
            sorted[index].toDouble()
        }
    }

    fun snapshot(): Map<String, List<Long>> {
        return metrics.mapValues { (_, samples) ->
            synchronized(samples) { samples.toList() }
        }
    }
}
