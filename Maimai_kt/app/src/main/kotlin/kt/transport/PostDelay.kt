package kt.transport

import kt.log.MaimaiLogger
import kotlinx.coroutines.delay

suspend fun waitBeforePostWithCountdown(
    waitMillis: Long,
    label: String,
    logger: MaimaiLogger = MaimaiLogger.None,
    observer: PostDelayObserver = PostDelayObserver.None,
) {
    val wait = waitMillis.coerceAtLeast(0L)
    val totalSeconds = (wait + 999L) / 1_000L
    val targetEpochMillis = System.currentTimeMillis() + wait
    val deadlineNanos = System.nanoTime() + wait * 1_000_000L
    observer.onScheduled(label, totalSeconds, targetEpochMillis)

    try {
        while (true) {
            val remainingMillis = ((deadlineNanos - System.nanoTime()) + 999_999L) / 1_000_000L
            if (remainingMillis <= 0L) break

            val remainingSeconds = (remainingMillis + 999L) / 1_000L
            val message = "Waiting ${remainingSeconds}s before $label POST..."
            println(message)
            logger.debug(message)
            observer.onTick(label, remainingSeconds, totalSeconds)
            delay(minOf(1_000L, remainingMillis))
        }
    } finally {
        observer.onComplete(label)
    }
}
