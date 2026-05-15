package com.maimai.android

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

/**
 * 整个 Android App 的 Application 类。
 *
 * Application 比 Activity 更早创建，并且在 App 进程存活期间通常只有一个。
 * 适合放全局初始化逻辑，比如 Hilt 和 Timber。
 */
@HiltAndroidApp
class MaimaiApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // DebugTree 会把 Timber 日志输出到 Android Studio Logcat。
        // 只在 debug 包启用，避免正式包输出过多敏感网络信息。
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
