package com.okaca.maimai.android.security

/**
 * 用户白名单管理。
 *
 * 目前先使用内存白名单；后续从网络请求拿到新的白名单后，
 * 调用 [replaceAll] 就可以整体更新，ViewModel 不需要关心白名单来源。
 */
object UserWhitelist {
    private val lock = Any()
    private var userIds: Set<Long> = setOf(
        12555966, // test
        12236556, // okaca
        11268304, // 70
        11741734, // Zean_n
    )

    /**
     * 判断用户是否允许使用后续业务功能。
     */
    fun contains(userId: Long): Boolean =
        synchronized(lock) {
            userIds.contains(userId)
        }

    /**
     * 用网络返回的新白名单替换当前白名单。
     */
    fun replaceAll(newUserIds: Collection<Long>) {
        synchronized(lock) {
            userIds = newUserIds.toSet()
        }
    }

    /**
     * 读取当前白名单快照，方便调试或展示。
     */
    fun snapshot(): Set<Long> =
        synchronized(lock) {
            userIds
        }
}

