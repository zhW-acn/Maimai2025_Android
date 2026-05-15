package com.maimai.android.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maimai.android.logging.AppMaimaiLogger
import com.maimai.kt.error.MaimaiLoginException
import com.maimai.kt.service.LoginSession
import com.maimai.kt.service.MaimaiActions
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * MainActivity 对应的 ViewModel。
 *
 * MVVM 可以简单理解成：
 * - View：Activity/XML，只负责显示界面和接收点击。
 * - ViewModel：保存界面状态，处理按钮点击后的业务逻辑。
 * - Model：真正的数据和服务层，这里就是 MaimaiActions、LoginSession 等客户端对象。
 *
 * 这里使用 @HiltViewModel，让 Hilt 自动帮我们创建 ViewModel，并注入 MaimaiActions/logger。
 */
@HiltViewModel
class MaimaiConsoleViewModel @Inject constructor(
    private val actions: MaimaiActions,
    private val logger: AppMaimaiLogger,
) : ViewModel() {
    /**
     * _state 是 ViewModel 内部可修改的状态。
     *
     * 命名上加下划线是 Android 常见写法：提醒自己“这个变量不要暴露给外部直接修改”。
     */
    private val _state = MutableStateFlow(ConsoleUiState())

    /**
     * state 是暴露给 Activity 的只读状态。
     *
     * Activity 只能 collect 它并刷新 UI，不能直接改它，这样数据流会更清晰。
     */
    val state: StateFlow<ConsoleUiState> = _state.asStateFlow()

    /**
     * 当前登录会话。
     *
     * LoginSession 里面的 userId、timestamp、cookie、token 会被后续 API 继续使用。
     * 它属于业务状态，所以放在 ViewModel，不放在 Activity。
     */
    private var session: LoginSession? = null

    fun setQrCode(value: String) {
        _state.update { it.copy(qrCode = value, lastError = null) }
    }

    fun clearLogs() {
        logger.clear()
    }

    /**
     * 登录按钮点击后调用。
     *
     * Activity 不再自己 launch 协程，而是把事件交给 ViewModel。
     * viewModelScope 会在 ViewModel 销毁时自动取消，避免页面关闭后请求还一直跑。
     */
    fun login() {
        val qrCode = state.value.qrCode.trim()
        if (qrCode.isBlank()) {
            setError("请输入二维码字符串")
            return
        }

        viewModelScope.launch {
            runBusy("登录中") {
                logger.info("开始登录")
                val loginSession = actions.sessions.loginByQr(qrCode)
                session = loginSession
                logger.info("登录成功，userId=${loginSession.userId}")

                _state.update {
                    it.copy(
                        busy = false,
                        loggedIn = true,
                        status = "已登录",
                        userId = loginSession.userId.toString(),
                        timestamp = loginSession.timestamp.toString(),
                        cookieStatus = if (loginSession.cookie.isNotEmpty()) "已获取" else "缺失",
                        tokenStatus = if (loginSession.token.isNotBlank()) "已获取" else "缺失",
                        lastError = null,
                    )
                }
            }
        }
    }

    /**
     * 手动登出。
     *
     * 这里故意不调用 runBusy：登出不需要让界面进入 busy 状态。
     */
    fun logout() {
        val activeSession = session
        if (activeSession == null) {
            markLoggedOut("未登录")
            return
        }

        viewModelScope.launch {
            try {
                try {
                    logger.info("正在 logout")
                    actions.sessions.logout(activeSession.userId, activeSession.cookie)
                    logger.info("logout 成功")
                } finally {
                    session = null
                    markLoggedOut("已登出")
                }
            } catch (error: Throwable) {
                val message = error.message ?: error::class.java.simpleName
                logger.error("logout 失败", error)
                setError(message)
            }
        }
    }

    fun uploadDemoScore() {
        runOperationAndLogout("上传示例成绩") { active ->
            actions.scores.upload(
                userId = active.userId,
                loginTimestamp = active.timestamp,
                loginResult = active.login,
                musicId = 363,
                level = 1,
                achievement = 1_000_000,
                dxScore = 100,
            )
        }
    }

    fun unlockDemoMaster() {
        runOperationAndLogout("解锁示例 Master") { active ->
            actions.unlocks.musicMaster(active.userId, active.timestamp, active.login, musicId = 363)
        }
    }

    fun changeVersion() {
        runOperationAndLogout("修改版本") { active ->
            actions.versions.change(active.userId, active.timestamp, active.login)
        }
    }

    /**
     * 执行业务操作，并在 finally 里自动 logout。
     *
     * 这个函数负责统一处理“必须先登录”的判断、busy 状态、异常处理和自动登出。
     */
    private fun runOperationAndLogout(name: String, block: suspend (LoginSession) -> Any?) {
        val activeSession = session
        if (activeSession == null) {
            setError("请先登录")
            return
        }

        viewModelScope.launch {
            runBusy(name) {
                try {
                    logger.info("$name 开始")
                    block(activeSession)
                    logger.info("$name 成功")
                } finally {
                    try {
                        logger.info("$name 完成，开始自动 logout")
                        actions.sessions.logout(activeSession.userId, activeSession.cookie)
                        logger.info("自动 logout 成功")
                    } finally {
                        session = null
                        markLoggedOut("操作完成，已自动登出")
                    }
                }
            }
        }
    }

    /**
     * 包装需要显示 busy 的操作。
     *
     * 进入时 busy=true，结束时 busy=false。
     * Activity 会根据 busy 禁用按钮并显示 ProgressBar。
     */
    private suspend fun runBusy(label: String, block: suspend () -> Unit) {
        _state.update { it.copy(busy = true, status = label, lastError = null) }
        try {
            block()
        } catch (error: MaimaiLoginException) {
            val message = when (error.code) {
                100 -> "正在游玩中，请稍后再试"
                102 -> "二维码已失效，请刷新二维码"
                else -> error.message ?: "登录失败"
            }
            logger.error(message, error)
            setError(message)
        } catch (error: Throwable) {
            val message = error.message ?: error::class.java.simpleName
            logger.error("操作失败", error)
            setError(message)
        } finally {
            _state.update { it.copy(busy = false) }
        }
    }

    private fun setError(message: String) {
        _state.update { it.copy(busy = false, status = "失败", lastError = message) }
    }

    private fun markLoggedOut(status: String) {
        _state.update {
            it.copy(
                busy = false,
                loggedIn = false,
                status = status,
                userId = "-",
                timestamp = "-",
                cookieStatus = "无",
                tokenStatus = "无",
            )
        }
    }
}
