package com.maimai.android.di

import com.maimai.android.logging.AppMaimaiLogger
import com.maimai.android.ui.console.session.UpsertDelayMonitor
import com.maimai.kt.config.ClientConfig
import com.maimai.kt.log.MaimaiLogger
import com.maimai.kt.service.MaimaiActions
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt 模块：告诉 Hilt “某个接口/类型应该怎么创建”。
 *
 * 没有这个模块时，Hilt 只知道构造函数上有 @Inject 的类，
 * 但不知道 MaimaiLogger 接口应该用哪个实现。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class LoggerModule {
    /**
     * Binds 用于接口绑定。
     *
     * 这里的意思是：当某处需要 MaimaiLogger 时，请提供 AppMaimaiLogger。
     */
    @Binds
    @Singleton
    abstract fun bindMaimaiLogger(logger: AppMaimaiLogger): MaimaiLogger
}

/**
 * Provides 用于创建第三方类或我们不想直接标 @Inject constructor 的对象。
 */
@Module
@InstallIn(SingletonComponent::class)
object MaimaiModule {
    /**
     * ClientConfig 是客户端库的配置对象。
     *
     * 第一版先使用默认配置，后续可以改成从 DataStore/设置页读取。
     */
    @Provides
    @Singleton
    fun provideClientConfig(upsertDelayMonitor: UpsertDelayMonitor): ClientConfig =
        ClientConfig(postDelayObserver = upsertDelayMonitor)

    /**
     * MaimaiActions 是客户端库的统一入口。
     *
     * 这里把 AppMaimaiLogger 注入进去，于是网络请求日志会进入 Timber 和页面日志面板。
     */
    @Provides
    @Singleton
    fun provideMaimaiActions(config: ClientConfig, logger: MaimaiLogger): MaimaiActions =
        MaimaiActions(config, logger)
}
