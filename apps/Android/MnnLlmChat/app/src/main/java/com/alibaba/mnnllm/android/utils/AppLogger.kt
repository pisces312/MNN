// Simple in-memory logger for debugging download and storage issues.
// Created for MNN Android app diagnostics.
package com.alibaba.mnnllm.android.utils

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {

    private const val TAG = "AppLogger"
    private const val MAX_ENTRIES = 300
    private val entries = mutableListOf<LogEntry>()
    private val dateFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.getDefault())

    data class LogEntry(
        val timestamp: Long,
        val level: String,
        val tag: String,
        val message: String
    ) {
        fun formatted(): String {
            val ts = dateFormat.format(Date(timestamp))
            return "$ts [$level] $tag: $message"
        }
    }

    @Synchronized
    fun d(tag: String, msg: String) {
        add(LogEntry(System.currentTimeMillis(), "D", tag, msg))
        Log.d(tag, msg)
    }

    @Synchronized
    fun w(tag: String, msg: String) {
        add(LogEntry(System.currentTimeMillis(), "W", tag, msg))
        Log.w(tag, msg)
    }

    @Synchronized
    fun e(tag: String, msg: String) {
        add(LogEntry(System.currentTimeMillis(), "E", tag, msg))
        Log.e(tag, msg)
    }

    @Synchronized
    fun i(tag: String, msg: String) {
        add(LogEntry(System.currentTimeMillis(), "I", tag, msg))
        Log.i(tag, msg)
    }

    @Synchronized
    fun getEntries(): List<LogEntry> = entries.toList()

    @Synchronized
    fun getRecent(count: Int = 100): List<LogEntry> {
        return entries.takeLast(count)
    }

    @Synchronized
    fun clear() {
        entries.clear()
        Log.i(TAG, "Log cleared")
    }

    private fun add(entry: LogEntry) {
        entries.add(entry)
        if (entries.size > MAX_ENTRIES) {
            entries.removeAt(0)
        }
    }
}
