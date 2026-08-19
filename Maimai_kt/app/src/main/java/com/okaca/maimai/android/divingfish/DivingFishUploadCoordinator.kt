package com.okaca.maimai.android.divingfish

import com.okaca.maimai.android.network.vpn.core.LocalVpnService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.IOException

object DivingFishUploadCoordinator {
    private var listener: DivingFishUploadListener? = null
    private var username: String = ""
    private var password: String = ""
    private var difficulties: Set<Int> = setOf(2, 3, 4)

    fun configure(
        username: String,
        password: String,
        difficulties: Set<Int>,
        listener: DivingFishUploadListener,
    ) {
        this.username = username
        this.password = password
        this.difficulties = difficulties.ifEmpty { DEFAULT_DIFFICULTIES }
        this.listener = listener
    }

    fun clearListener(listener: DivingFishUploadListener) {
        if (this.listener === listener) {
            this.listener = null
        }
    }

    @JvmStatic
    fun getWechatAuthUrl(): String? {
        return try {
            DivingFishUploader().getWechatAuthUrl()
        } catch (error: IOException) {
            writeLog("获取微信登录 URL 时出现错误")
            onError(error)
            null
        } catch (error: Exception) {
            writeLog("获取微信登录 URL 时出现错误")
            onError(error)
            null
        }
    }

    @JvmStatic
    fun fetchData(authUrl: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Thread.sleep(VPN_SHUTDOWN_DELAY_MILLIS)
                LocalVpnService.IsRunning = false
                Thread.sleep(VPN_SHUTDOWN_DELAY_MILLIS)
            } catch (error: InterruptedException) {
                onError(error)
            }

            val currentUsername = username
            val currentPassword = password
            if (currentUsername.isBlank() || currentPassword.isBlank()) {
                onError(IllegalStateException("请先填写水鱼账号和密码"))
                return@launch
            }

            DivingFishUploader().fetchAndUploadData(
                username = currentUsername,
                password = currentPassword,
                difficulties = difficulties,
                wechatAuthUrl = authUrl,
            )
        }
    }

    @JvmStatic
    fun writeLog(text: String) {
        CoroutineScope(Dispatchers.Main).launch {
            listener?.onMessageReceived(text)
        }
    }

    @JvmStatic
    fun startAuth() {
        CoroutineScope(Dispatchers.Main).launch {
            listener?.onStartAuth()
        }
    }

    @JvmStatic
    fun finishUpdate() {
        CoroutineScope(Dispatchers.Main).launch {
            listener?.onFinishUpdate()
        }
    }

    @JvmStatic
    fun onError(error: Throwable) {
        CoroutineScope(Dispatchers.Main).launch {
            listener?.onError(error)
        }
    }

    private const val VPN_SHUTDOWN_DELAY_MILLIS = 3_000L
    private val DEFAULT_DIFFICULTIES = setOf(2, 3, 4)
}
