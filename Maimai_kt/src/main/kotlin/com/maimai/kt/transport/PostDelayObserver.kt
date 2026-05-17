package com.maimai.kt.transport

/**
 * Observes the guarded wait before a POST request.
 *
 * Android uses this hook to render a visible countdown while the core client
 * keeps owning the real delay timing.
 */
interface PostDelayObserver {
    fun onTick(label: String, remainingSeconds: Long, totalSeconds: Long)

    fun onComplete(label: String) = Unit

    fun onScheduled(label: String, totalSeconds: Long, targetEpochMillis: Long) = Unit

    object None : PostDelayObserver {
        override fun onTick(label: String, remainingSeconds: Long, totalSeconds: Long) = Unit
    }
}
