package com.maimai.android

/**
 * App 内部使用的 Intent action 常量。
 *
 * 这些 action 不对外暴露，只用于通知栏点击后把事件交回 MainActivity。
 */
object AppIntentActions {
    const val LOGOUT_AFTER_NO_UPSERT_TIMEOUT =
        "com.maimai.android.action.LOGOUT_AFTER_NO_UPSERT_TIMEOUT"
}
