// Created by pisces312 on 2026/09/26.
// Debug-only log viewer: dumps all logcat entries produced by this process so far.
package com.alibaba.mnnllm.android.debug

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.view.View
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.alibaba.mnnllm.android.R

class LogViewerActivity : AppCompatActivity() {

    private lateinit var logContent: TextView
    private lateinit var scrollView: ScrollView
    private var logCache: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log_viewer)
        logContent = findViewById(R.id.tv_log_content)
        scrollView = findViewById(R.id.sv_log_container)
        findViewById<Button>(R.id.btn_refresh_log).setOnClickListener { refreshLogs() }
        findViewById<Button>(R.id.btn_copy_log).setOnClickListener { copyLogs() }
        findViewById<Button>(R.id.btn_share_log).setOnClickListener { shareLogs() }
        refreshLogs()
    }

    private fun refreshLogs() {
        logCache = dumpLogcat()
        logContent.text = logCache
    }

    /**
     * Reads the whole logcat buffer filtered to this process PID. Since Android 4.1
     * an app is allowed to read its OWN log lines without READ_LOGS, so this works
     * without extra permissions. Covers everything the app has logged since the
     * current process started (bounded by the kernel log buffer size).
     */
    private fun dumpLogcat(): String {
        return try {
            val pid = Process.myPid()
            val process = Runtime.getRuntime()
                .exec(arrayOf("logcat", "-d", "-v", "time", "--pid=$pid"))
            val stdout = process.inputStream.bufferedReader().use { it.readText() }
            val stderr = process.errorStream.bufferedReader().use { it.readText() }
            process.waitFor()
            val result = if (stdout.isNotBlank()) stdout else stderr
            if (result.isBlank()) {
                getString(R.string.log_empty)
            } else if (result.length > MAX_LOG_CHARS) {
                // Keep the tail; the head of an oversized buffer is the least useful.
                getString(R.string.log_truncated) + "\n" +
                    result.substring(result.length - MAX_LOG_CHARS)
            } else {
                result
            }
        } catch (e: Exception) {
            "Failed to read logcat: ${e.message}"
        }
    }

    private fun copyLogs() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("mnnchat_logs", logCache))
        Toast.makeText(this, R.string.log_copied_to_clipboard, Toast.LENGTH_SHORT).show()
    }

    private fun shareLogs() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.log_viewer_title))
            putExtra(Intent.EXTRA_TEXT, logCache)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share)))
    }

    companion object {
        // ~4MB of text is enough for review; avoids TextView OOM on huge buffers.
        private const val MAX_LOG_CHARS = 4 * 1024 * 1024
    }
}
