package com.maimai.kt.transport

import kotlinx.coroutines.delay

suspend fun waitBeforePostWithCountdown(waitMillis: Long, label: String) {
    var remainingMillis = waitMillis.coerceAtLeast(0L)
    while (remainingMillis > 0L) {
        val remainingSeconds = (remainingMillis + 999L) / 1_000L
        println("Waiting ${remainingSeconds}s before $label POST...")
        val stepMillis = minOf(1_000L, remainingMillis)
        delay(stepMillis)
        remainingMillis -= stepMillis
    }
}
