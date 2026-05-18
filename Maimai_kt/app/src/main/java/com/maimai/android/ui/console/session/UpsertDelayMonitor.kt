package com.maimai.android.ui.console.session

import kt.transport.PostDelayObserver
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 接收核心库 waitBeforePostWithCountdown 的每秒回调。
 *
 * 这个类是 Hilt 单例，所以 ClientConfig 和 ViewModel 拿到的是同一个对象：
 * ClientConfig 把等待进度写进来，ViewModel 监听 progress 并刷新页面。
 */
@Singleton
class UpsertDelayMonitor @Inject constructor() : PostDelayObserver {
    private val _progress = MutableStateFlow<UpsertDelayProgress?>(null)
    private var currentTargetEpochMillis: Long? = null

    val progress: StateFlow<UpsertDelayProgress?> = _progress

    /**
     * 核心库每等待一秒会回调这里，用于刷新页面和通知栏倒计时。
     */
    override fun onTick(label: String, remainingSeconds: Long, totalSeconds: Long) {
        _progress.value = UpsertDelayProgress(
            label = label,
            remainingSeconds = remainingSeconds.toInt(),
            totalSeconds = totalSeconds.toInt().coerceAtLeast(1),
            targetEpochMillis = currentTargetEpochMillis,
        )
    }

    /**
     * 核心库完成等待并准备发送 POST 时回调这里。
     */
    override fun onComplete(label: String) {
        currentTargetEpochMillis = null
        _progress.value = null
    }

    /**
     * 核心库刚开始安排等待时回调这里，用于立刻显示总时长和目标时间。
     */
    override fun onScheduled(label: String, totalSeconds: Long, targetEpochMillis: Long) {
        currentTargetEpochMillis = targetEpochMillis
        _progress.value = UpsertDelayProgress(
            label = label,
            remainingSeconds = totalSeconds.toInt(),
            totalSeconds = totalSeconds.toInt().coerceAtLeast(1),
            targetEpochMillis = targetEpochMillis,
        )
    }
}

/**
 * UI 层需要的倒计时数据。
 *
 * percent 是已经等待完成的百分比，用于横向进度条。
 */
data class UpsertDelayProgress(
    val label: String,
    val remainingSeconds: Int,
    val totalSeconds: Int,
    val targetEpochMillis: Long? = null,
) {
    val percent: Int
        get() {
            val elapsedSeconds = (totalSeconds - remainingSeconds).coerceAtLeast(0)
            return (elapsedSeconds * 100 / totalSeconds).coerceIn(0, 100)
        }
}
