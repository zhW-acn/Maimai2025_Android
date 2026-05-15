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
import timber.log.Timber

/**
 * App 层日志适配器。
 *
 * maimai 客户端库只认识 MaimaiLogger 接口，不直接依赖 Android。
 * 这里把 MaimaiLogger 接到 Timber，同时也保存一份日志给页面底部的日志面板显示。
 */
@Singleton
class AppMaimaiLogger @Inject constructor() : MaimaiLogger {
    private val formatter = SimpleDateFormat("HH:mm:ss", Locale.US)

    /**
     * 页面日志列表。
     *
     * MutableStateFlow 可以被 Activity collect，一旦有新日志，页面会自动刷新。
     */
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

    /**
     * App 自己使用的信息日志。MaimaiLogger 接口里没有 info，所以这里额外提供。
     */
    fun info(message: String) {
        Timber.tag(TAG).i(message)
        append("INFO", message)
    }

    fun clear() {
        _logs.value = emptyList()
    }

    private fun append(level: String, message: String) {
        val line = "${formatter.format(Date())} [$level] $message"

        // 只保留最近 MAX_LINES 行，避免日志无限增长占内存。
        _logs.update { current -> (current + line).takeLast(MAX_LINES) }
    }

    private companion object {
        const val TAG = "Maimai"
        const val MAX_LINES = 300
    }
}
