package com.maimai.android.logging

import com.maimai.kt.log.MaimaiLogger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

/**
 * Android-side logger adapter.
 *
 * The core maimai client only knows the MaimaiLogger interface.
 * This adapter writes logs to Timber/Logcat and also keeps recent log entries
 * for the log panel at the bottom of the page.
 */
@Singleton
class AppMaimaiLogger @Inject constructor() : MaimaiLogger {
    private val formatter = SimpleDateFormat("HH:mm:ss", Locale.US)

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs

    override fun debug(message: String) {
        Timber.tag(TAG).d(message)
        append("DEBUG", message)
    }

    override fun error(message: String, throwable: Throwable?) {
        Timber.tag(TAG).e(throwable, message)
        append("ERROR", if (throwable == null) message else "$message: ${throwable.message}")
    }

    fun info(message: String) {
        Timber.tag(TAG).i(message)
        append("INFO", message)
    }

    fun clear() {
        _logs.value = emptyList()
    }

    private fun append(level: String, message: String) {
        val line = "${formatter.format(Date())} [$level] ${prettyPrintJsonPayload(message)}"
        _logs.update { current -> (current + line).takeLast(MAX_LINES) }
    }

    /**
     * Turns logs like:
     *
     * POST SomeApi request: {"a":1,"b":{"c":2}}
     *
     * into:
     *
     * POST SomeApi request:
     * {
     *   "a" : 1,
     *   "b" : {
     *     "c" : 2
     *   }
     * }
     */
    private fun prettyPrintJsonPayload(message: String): String {
        val jsonStart = message.indexOfFirst { it == '{' || it == '[' }
        if (jsonStart < 0) return message

        val prefix = message.substring(0, jsonStart).trimEnd()
        val jsonText = message.substring(jsonStart).trim()
        val prettyJson = runCatching {
            when (jsonText.firstOrNull()) {
                '{' -> JSONObject(jsonText).toString(JSON_INDENT)
                '[' -> JSONArray(jsonText).toString(JSON_INDENT)
                else -> jsonText
            }
        }.getOrNull() ?: return message

        return if (prefix.isBlank()) prettyJson else "$prefix\n$prettyJson"
    }

    private companion object {
        const val TAG = "Maimai"
        const val MAX_LINES = 300
        const val JSON_INDENT = 2
    }
}
