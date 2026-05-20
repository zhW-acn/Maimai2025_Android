package com.okaca.maimai.android.di

import com.okaca.maimai.android.logging.AppMaimaiLogger
import com.okaca.maimai.android.ui.console.session.UpsertDelayMonitor
import kt.config.ClientConfig
import kt.log.MaimaiLogger
import kt.service.MaimaiActions
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt 模块：告诉 Hilt 某些接口或第三方类型应该怎么创建。
 *
 * 没有这个模块时，Hilt 不知道 MaimaiLogger 接口应该使用哪个实现。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class LoggerModule {
    /** 当某处需要 MaimaiLogger 时，提供 AppMaimaiLogger。 */
    @Binds
    @Singleton
    abstract fun bindMaimaiLogger(logger: AppMaimaiLogger): MaimaiLogger
}

/**
 * Provides 用于创建第三方类，或我们不想直接标 @Inject constructor 的对象。
 */
@Module
@InstallIn(SingletonComponent::class)
object MaimaiModule {
    /** 第一版先使用默认配置，后续可以改成从 DataStore 或设置页读取。 */
    @Provides
    @Singleton
    fun provideClientConfig(upsertDelayMonitor: UpsertDelayMonitor): ClientConfig =
        ClientConfig(
            waitBeforeUpsertMillisProvider = upsertDelayMonitor::remainingLoginGuardMillis,
            postDelayObserver = upsertDelayMonitor,
        )

    /** 把 AppMaimaiLogger 注入核心库，网络请求日志会进入 Timber 和页面日志面板。 */
    @Provides
    @Singleton
    fun provideMaimaiActions(config: ClientConfig, logger: MaimaiLogger): MaimaiActions =
        MaimaiActions(config, logger)
}

