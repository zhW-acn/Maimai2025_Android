package com.maimai.android.session

import com.maimai.android.logging.AppMaimaiLogger
import com.maimai.kt.error.MaimaiLoginException
import com.maimai.kt.service.LoginSession
import com.maimai.kt.service.MaimaiActions
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 主页面的业务控制器。
 *
 * 你可以把它理解成“简化版 ViewModel/Presenter”：
 * - Activity 只负责显示和点击事件。
 * - Controller 负责调用 maimai 客户端、维护 LoginSession、更新 UI 状态。
 *
 * 这里暂时没有使用 AndroidX ViewModel，是为了第一版代码更直观。
 */
@Singleton
class MaimaiConsoleController @Inject constructor(
    private val actions: MaimaiActions,
    private val logger: AppMaimaiLogger,
) {
    /**
     * MutableStateFlow 是可修改的状态流，只在 Controller 内部持有。
     */
    private val _state = MutableStateFlow(ConsoleUiState())

    /**
     * 对外暴露只读 StateFlow，Activity 只能观察，不能直接修改。
     */
    val state: StateFlow<ConsoleUiState> = _state.asStateFlow()

    /**
     * 当前登录会话。
     *
     * LoginSession 里的 timestamp/cookie/token 很重要：
     * 后续 API 基本都依赖它们，所以统一放在 Controller 管理。
     */
    private var session: LoginSession? = null

    fun setQrCode(value: String) {
        _state.update { it.copy(qrCode = value, lastError = null) }
    }

    fun clearLogs() {
        logger.clear()
    }

    /**
     * 登录流程：QRCode -> Aime 解析 -> Preview -> TitleServer 登录。
     */
    suspend fun login() {
        val qrCode = state.value.qrCode.trim()
        if (qrCode.isBlank()) {
            setError("请输入二维码字符串")
            return
        }

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

    /**
     * 手动 logout。
     *
     * 只要拿到 LoginSession，离开前就应该尽量 logout，避免服务端认为仍在登录中。
     */
    suspend fun logout() {
        val activeSession = session
        if (activeSession == null) {
            markLoggedOut("未登录")
            return
        }

        runBusy("登出中") {
            try {
                logger.info("正在 logout")
                actions.sessions.logout(activeSession.userId, activeSession.cookie)
                logger.info("logout 成功")
            } finally {
                session = null
                markLoggedOut("已登出")
            }
        }
    }

    suspend fun uploadDemoScore() {
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

    suspend fun unlockDemoMaster() {
        runOperationAndLogout("解锁示例 Master") { active ->
            actions.unlocks.musicMaster(active.userId, active.timestamp, active.login, musicId = 363)
        }
    }

    suspend fun changeVersion() {
        runOperationAndLogout("修改版本") { active ->
            actions.versions.change(active.userId, active.timestamp, active.login)
        }
    }

    /**
     * 执行业务操作，并在 finally 中自动 logout。
     *
     * finally 的含义是：不管操作成功还是失败，都会尽力执行 logout。
     * 这正好符合这个 App 最重要的会话策略。
     */
    private suspend fun runOperationAndLogout(name: String, block: suspend (LoginSession) -> Any?) {
        val activeSession = session
        if (activeSession == null) {
            setError("请先登录")
            return
        }

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

    /**
     * 包装一个忙碌状态。
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
