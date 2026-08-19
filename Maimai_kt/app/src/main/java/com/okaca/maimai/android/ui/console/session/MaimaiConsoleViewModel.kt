package com.okaca.maimai.android.ui.console.session

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blankj.utilcode.util.Utils
import com.okaca.maimai.android.R
import com.okaca.maimai.android.enums.ComboStatus
import com.okaca.maimai.android.enums.ScoreLevel
import com.okaca.maimai.android.enums.SyncStatus
import com.okaca.maimai.android.logging.AppMaimaiLogger
import com.okaca.maimai.android.notification.OperationResultNotifier
import com.okaca.maimai.android.security.UserWhitelist
import kt.error.MaimaiLoginException
import kt.payload.CharaDetail
import kt.payload.KaleidxScopeGate
import kt.payload.MusicDetail
import kt.payload.UserCharacter
import kt.payload.UserMusicResponse
import kt.service.LoginSession
import kt.service.MaimaiActions
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kt.constants.PayloadKeys

/**
 * MainActivity 对应的 ViewModel。
 *
 * ViewModel 保存界面状态和当前 LoginSession。Activity/XML 只负责展示，
 * 不直接处理 maimai API 的调用细节。
 */
@HiltViewModel
class MaimaiConsoleViewModel @Inject constructor(
    private val actions: MaimaiActions,
    private val logger: AppMaimaiLogger,
    private val upsertDelayMonitor: UpsertDelayMonitor,
    private val operationResultNotifier: OperationResultNotifier,
) : ViewModel() {
    /**
     * _state 是 ViewModel 内部可修改的状态。
     *
     * 变量名前面加下划线是 Android 常见写法：提醒自己这个变量不要直接暴露给外部修改。
     */
    private val _state = MutableStateFlow(initialState())

    /**
     * state 是暴露给 Activity 的只读状态。
     *
     * Activity 只能 collect 它并刷新 UI，不能直接改它，这样数据流会更清楚。
     */
    val state: StateFlow<ConsoleUiState> = _state.asStateFlow()

    /**
     * 当前登录会话。
     *
     * LoginSession 里的 userId、timestamp、cookie、token 会被后续 API 继续使用。
     * 它属于业务状态，所以放在 ViewModel，不放在 Activity。
     */
    private var session: LoginSession? = null
    private var upsertUserAllCompleted: Boolean = false
    private var logoutAllowedByTimeout: Boolean = false
    private var noUpsertLogoutReminderJob: Job? = null
    private var currentOperationJob: Job? = null
    private var userMusicCache: Map<MusicKey, MusicDetail> = emptyMap()

    init {
        collectUpsertDelayProgress()
    }

    /**
     * 更新二维码输入框内容，并清理上一次错误。
     */
    fun setQrCode(value: String) {
        _state.update { it.copy(qrCode = value, lastError = null) }
    }

    /**
     * 清空页面日志。
     */
    fun clearLogs() {
        logger.clear()
    }

    /**
     * 用户在等待 upsertUserAll / upsertChargeLog 倒计时时，可以手动取消当前任务。
     *
     * 这里只取消正在执行的业务协程，不清空登录会话；取消后用户仍然停留在当前登录状态。
     */
    fun cancelCurrentOperation() {
        val job = currentOperationJob
        if (job == null || job.isCompleted) {
            return
        }

        logger.info(text(R.string.log_operation_cancel_requested))
        upsertDelayMonitor.clear()
        operationResultNotifier.cancelUpsertProgress()
        job.cancel(CancellationException(text(R.string.error_operation_cancelled)))
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
            setError(text(R.string.error_qrcode_required))
            return
        }

        viewModelScope.launch {
            runBusy(text(R.string.status_logging_in)) {
                logger.info(text(R.string.log_login_start))
                val loginSession = actions.sessions.loginByQr(qrCode)
                if (!isUserInWhitelist(loginSession.userId)) {
                    rejectNonWhitelistedUser(loginSession)
                    return@runBusy
                }

                session = loginSession
                upsertUserAllCompleted = false
                logoutAllowedByTimeout = false
                userMusicCache = loadUserMusicCacheSafely(loginSession)
                val loginGuardDeadlineEpochMillis =
                    upsertDelayMonitor.startLoginGuard(LOGIN_GUARD_DURATION_MILLIS)
                scheduleNoUpsertLogoutReminder(loginSession.userId, loginGuardDeadlineEpochMillis)

                logger.info(text(R.string.log_login_success, loginSession.userId))

                _state.update {
                    it.copy(
                        busy = false,
                        loggedIn = true,
                        accessBlocked = false,
                        upsertUserAllCompleted = false,
                        logoutAllowedByTimeout = false,
                        status = text(R.string.status_logged_in_waiting_upsert),
                        userId = loginSession.userId.toString(),
                        timestamp = loginSession.timestamp.toString(),
                        cookieStatus = loginSession.cookie.toString(),
                        tokenStatus = loginSession.token,
                        upsertStatus = text(R.string.status_upsert_not_completed_cannot_logout),
                        upsertWaiting = false,
                        upsertWaitRemainingSeconds = 0,
                        upsertWaitTotalSeconds = 0,
                        upsertWaitProgress = 0,
                        upsertWaitText = "",
                        lastError = null,
                    )
                }
            }
        }
    }

    /**
     * 手动登出。
     *
     * 重要业务规则：必须先成功执行 upsertUserAll，才允许登出。
     */
    fun logout() {
        val activeSession = session
        if (activeSession == null) {
            markLoggedOut(text(R.string.status_not_logged_in))
            return
        }
        if (!state.value.accessBlocked && !upsertUserAllCompleted && !logoutAllowedByTimeout) {
            setError(text(R.string.error_logout_not_allowed))
            logger.info(text(R.string.log_logout_rejected_no_upsert))
            return
        }

        viewModelScope.launch {
            runLogout(activeSession, text(R.string.status_logged_out))
        }
    }

    /**
     * 长按登出使用的入口。
     *
     * 当前会话可用时可以直接复用已填入的 cookie；没有会话时也可以手动输入 cookie 登出。
     */
    fun logoutByUserIdCookie(userId: Long, cookieText: String) {
        if (cookieText.isBlank()) {
            setError(text(R.string.error_manual_logout_form_required))
            return
        }

        viewModelScope.launch {
            runBusy(text(R.string.status_manual_logout_running)) {
                val activeSession = session
                val cookie = parseCookieText(cookieText)

                logger.info(text(R.string.log_logout_start, userId))
                actions.sessions.logout(userId, cookie)
                logger.info(text(R.string.log_logout_success))

                if (activeSession == null || activeSession.userId == userId) {
                    session = null
                    upsertUserAllCompleted = false
                    logoutAllowedByTimeout = false
                    userMusicCache = emptyMap()
                    markLoggedOut(text(R.string.status_logged_out))
                } else {
                    _state.update {
                        it.copy(
                            status = text(R.string.status_manual_logout_completed, userId),
                            lastError = null,
                        )
                    }
                }
            }
        }
    }

    /**
     * 用户点击“未 upsert 超时提醒”通知后执行登出。
     *
     * 这个入口会绕过手动登出的 upsertUserAll 检查，因为它只在超时提醒通知中使用。
     */
    fun logoutAfterNoUpsertTimeout() {
        val activeSession = session
        if (activeSession == null) {
            operationResultNotifier.cancelNoUpsertLogoutReminder()
            markLoggedOut(text(R.string.status_not_logged_in))
            return
        }
        if (upsertUserAllCompleted) {
            operationResultNotifier.cancelNoUpsertLogoutReminder()
            return
        }
        logoutAllowedByTimeout = true

        viewModelScope.launch {
            logger.info(text(R.string.log_no_upsert_timeout_logout_start))
            runLogout(activeSession, text(R.string.status_no_upsert_timeout_logged_out))
        }
    }

    /**
     * 根据弹窗表单传入的歌曲成绩上传 upsertUserAll。
     */
    fun uploadScore(
        music: MusicDetail,
        manualPlayCount: Int? = null,
    ) {
        runOperationInViewModel(text(R.string.action_upload_demo_score)) { activeSession ->
            val uploadMusic = music.withAutoPlayCount(manualPlayCount)
            actions.scores.upload(
                userId = activeSession.userId,
                loginTimestamp = activeSession.timestamp,
                loginResult = activeSession.login,
                music = uploadMusic
            )
        }
    }

    /**
     * 更改舞里程
     */
    fun uploadPoint(
        point: Int? = 99999
    ) {
        runOperationInViewModel(text(R.string.action_upload_point)) { activeSession ->
            val patch = mapOf(
                PayloadKeys.UPSERT_USER_ALL to mapOf(
                    PayloadKeys.USER_DATA to listOf(
                        mapOf(
                            PayloadKeys.POINT to point,
                            PayloadKeys.TOTAL_POINT to point,
                        )
                    )
                )
            )
            actions.scores.upload(
                userId = activeSession.userId,
                loginTimestamp = activeSession.timestamp,
                loginResult = activeSession.login,
                music = MusicDetail.point().withAutoPlayCount(),
                extra = patch
            )
        }
    }

    /**
     * 修改剩余里程 MapStock。
     */
    fun uploadMapStock(
        mapStock: Int? = 99999
    ) {
        runOperationInViewModel(text(R.string.action_map_stock)) { activeSession ->
            val patch = mapOf(
                PayloadKeys.UPSERT_USER_ALL to mapOf(
                    PayloadKeys.USER_DATA to listOf(
                        mapOf(
                            PayloadKeys.MAP_STOCK to mapStock,
                        )
                    )
                )
            )
            actions.scores.upload(
                userId = activeSession.userId,
                loginTimestamp = activeSession.timestamp,
                loginResult = activeSession.login,
                music = MusicDetail.mapStock().withAutoPlayCount(),
                extra = patch,
                isNewMusicDetailList = "1",
            )
        }
    }

    /**
     * 旅行伙伴
     */
    fun uploadCharas(
        chara: List<CharaDetail>
    ) {
        runOperationInViewModel(text(R.string.action_character_level)) { activeSession ->
            actions.scores.upload(
                userId = activeSession.userId,
                loginTimestamp = activeSession.timestamp,
                loginResult = activeSession.login,
                music = MusicDetail.chara().withAutoPlayCount(),
                charaDetail = chara,
                userCharacters = chara.map { UserCharacter.fromCharaDetail(it) },
            )
        }
    }

    /**
     * 上传 KaleidxScope Gate 状态。
     */
    fun uploadKaleidxScope(gate: KaleidxScopeGate) {
        runOperationInViewModel(text(R.string.action_kaleidx_scope)) { activeSession ->
            val patch = mapOf(
                PayloadKeys.UPSERT_USER_ALL to mapOf(
                    PayloadKeys.USER_KALEIDX_SCOPE_LIST to listOf(gate.toMap()),
                    PayloadKeys.IS_NEW_KALEIDX_SCOPE_LIST to "0",
                )
            )
            val music = MusicDetail(
                musicId = gate.musicId,
                level = ScoreLevel.Basic.apiValue,
                playCount = 1,
                achievement = 101_0000,
                comboStatus = ComboStatus.AllPerfectPlus.apiValue,
                syncStatus = SyncStatus.FullSyncDxPlus.apiValue,
                deluxscoreMax = 0,
                extNum1 = 0,
            )
            actions.scores.upload(
                userId = activeSession.userId,
                loginTimestamp = activeSession.timestamp,
                loginResult = activeSession.login,
                music = music.withAutoPlayCount(),
                extra = patch,
            )
        }
    }

    /**
     * 购买指定类型的票券。
     */
    fun buyTicket(ticketType: Int = 6) {
        val activeSession = session
        if (activeSession == null) {
            setError(text(R.string.error_login_required))
            return
        }
        if (state.value.accessBlocked) {
            setError(text(R.string.status_access_blocked_need_logout))
            return
        }

        currentOperationJob = viewModelScope.launch {
            runBusy(text(R.string.status_operation_running, text(R.string.action_charge_ticket))) {
                logger.info(text(R.string.log_ticket_buy_start, ticketType))
                actions.tickets.buy(
                    userId = activeSession.userId,
                    ticketType = ticketType,
                    cookie = activeSession.cookie,
                )
                logger.info(text(R.string.log_ticket_buy_success, ticketType))
            }
        }
    }

    /**
     * 给查票弹窗调用的查询入口。
     *
     * 已登录时优先使用当前会话；未登录时使用弹窗里输入的 userId。
     */
    suspend fun queryTicketForDialog(userId: Long? = null): TicketQueryResult {
        val activeSession = session
        if (state.value.accessBlocked) {
            throw IllegalStateException(text(R.string.status_access_blocked_need_logout))
        }
        val queryUserId = activeSession?.userId
            ?: userId
            ?: throw IllegalStateException("请填写用户 ID")

        logger.info(text(R.string.log_query_ticket))
        val query = actions.tickets.query(
            userId = queryUserId,
            cookie = activeSession?.cookie,
        )
        logger.info(text(R.string.log_query_ticket_success) + query)
        return TicketQueryResult.fromMap(query)
    }

    private fun runOperationInViewModel(
        name: String,
        block: suspend (LoginSession) -> Unit,
    ) {
        val activeSession = session
        if (activeSession == null) {
            setError(text(R.string.error_login_required))
            return
        }
        if (state.value.accessBlocked) {
            setError(text(R.string.status_access_blocked_need_logout))
            return
        }

        currentOperationJob = viewModelScope.launch {
            var upsertSucceeded = false
            try {
                logger.info(text(R.string.log_operation_start, name))
                _state.update {
                    it.copy(
                        busy = true,
                        status = text(R.string.status_operation_running, name),
                        lastError = null,
                    )
                }

                block(activeSession)
                upsertSucceeded = true
                upsertUserAllCompleted = true
                logoutAllowedByTimeout = false
                cancelNoUpsertLogoutReminder()
                logger.info(text(R.string.log_operation_success, name))

                _state.update {
                    it.copy(
                        upsertUserAllCompleted = true,
                        logoutAllowedByTimeout = false,
                        upsertStatus = text(R.string.status_upsert_completed_prepare_logout),
                    )
                }

                val logoutSucceeded =
                    runLogout(activeSession, text(R.string.status_operation_completed_auto_logout))
                if (logoutSucceeded) {
                    operationResultNotifier.notifySuccess(
                        title = text(R.string.notification_operation_success_title, name),
                        message = text(R.string.notification_operation_success_message),
                    )
                } else {
                    operationResultNotifier.notifyFailure(
                        title = text(R.string.notification_logout_failed_title, name),
                        message = text(R.string.notification_logout_failed_message),
                    )
                }
            } catch (error: CancellationException) {
                logger.info(text(R.string.log_operation_cancelled, name))
                upsertDelayMonitor.clear()
                operationResultNotifier.cancelUpsertProgress()
                _state.update {
                    it.copy(
                        busy = false,
                        upsertWaiting = false,
                        upsertWaitRemainingSeconds = 0,
                        upsertWaitTotalSeconds = 0,
                        upsertWaitProgress = 0,
                        upsertWaitText = "",
                        status = text(R.string.status_operation_cancelled),
                        lastError = null,
                    )
                }
            } catch (error: Throwable) {
                val message = error.message ?: error::class.java.simpleName
                logger.error(text(R.string.log_operation_failed, name), error)
                setError(message)
                operationResultNotifier.notifyFailure(
                    title = text(R.string.notification_operation_failed_title, name),
                    message = message,
                )
            } finally {
                currentOperationJob = null
                if (!upsertSucceeded) {
                    logger.info(text(R.string.log_operation_no_upsert_skip_logout, name))
                    _state.update { it.copy(busy = false) }
                }
            }
        }
    }

    private suspend fun runLogout(activeSession: LoginSession, loggedOutStatus: String): Boolean {
        var succeeded = false
        try {
            cancelNoUpsertLogoutReminder()
            logger.info(text(R.string.log_logout_start, activeSession.userId))
            actions.sessions.logout(activeSession.userId, activeSession.cookie)
            succeeded = true
            logger.info(text(R.string.log_logout_success))
        } catch (error: Throwable) {
            val message = error.message ?: error::class.java.simpleName
            logger.error(text(R.string.log_logout_failed), error)
            setError(message)
        } finally {
            session = null
            upsertUserAllCompleted = false
            logoutAllowedByTimeout = false
            userMusicCache = emptyMap()
            val finalStatus =
                if (succeeded) loggedOutStatus else text(R.string.status_logout_failed_local_session_cleared)
            markLoggedOut(finalStatus)
        }
        return succeeded
    }

    /**
     * 监听核心库真实等待的倒计时。
     *
     * 这里不自己 delay，避免 UI 倒计时和核心 API 的真实等待脱节。
     */
    private fun collectUpsertDelayProgress() {
        viewModelScope.launch {
            upsertDelayMonitor.progress.collect { progress ->
                val wasWaiting = state.value.upsertWaiting
                if (progress == null && wasWaiting) {
                    operationResultNotifier.notifyUpsertPosting()
                } else if (progress != null) {
                    operationResultNotifier.notifyUpsertProgress(progress)
                }

                _state.update { current ->
                    if (progress == null) {
                        current.copy(
                            upsertWaiting = false,
                            upsertStatus = if (current.upsertWaiting) text(R.string.status_sending_upsert_post) else current.upsertStatus,
                            upsertWaitRemainingSeconds = 0,
                            upsertWaitTotalSeconds = 0,
                            upsertWaitProgress = if (current.upsertWaitProgress > 0) 100 else 0,
                            upsertWaitText = "",
                        )
                    } else {
                        current.copy(
                            upsertWaiting = true,
                            upsertStatus = text(
                                R.string.status_waiting_post_remaining,
                                progress.remainingSeconds
                            ),
                            upsertWaitRemainingSeconds = progress.remainingSeconds,
                            upsertWaitTotalSeconds = progress.totalSeconds,
                            upsertWaitProgress = progress.percent,
                            upsertWaitText = text(
                                R.string.upsert_wait_text,
                                progress.label,
                                progress.remainingSeconds
                            ),
                        )
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
                100 -> text(R.string.error_login_playing)
                102 -> text(R.string.error_qrcode_refresh_required)
                else -> error.message ?: text(R.string.error_login_failed)
            }
            logger.error(message, error)
            setError(message)
        } catch (error: Throwable) {
            val message = error.message ?: error::class.java.simpleName
            if (error is CancellationException) {
                logger.info(text(R.string.log_operation_cancelled, label))
                upsertDelayMonitor.clear()
                operationResultNotifier.cancelUpsertProgress()
                _state.update {
                    it.copy(
                        busy = false,
                        upsertWaiting = false,
                        upsertWaitRemainingSeconds = 0,
                        upsertWaitTotalSeconds = 0,
                        upsertWaitProgress = 0,
                        upsertWaitText = "",
                        status = text(R.string.status_operation_cancelled),
                        lastError = null,
                    )
                }
            } else {
                logger.error(text(R.string.error_operation_failed), error)
                setError(message)
            }
        } finally {
            _state.update { it.copy(busy = false) }
            if (currentOperationJob?.isCompleted == true) {
                currentOperationJob = null
            }
        }
    }

    /**
     * 记录错误并让页面退出 busy 状态。
     */
    private fun setError(message: String) {
        _state.update {
            it.copy(
                busy = false,
                status = text(R.string.status_failed),
                lastError = message
            )
        }
    }

    /**
     * 白名单拦截：用户 ID 不被允许时，先保留 LoginSession，方便用户手动登出；
     * 同时封锁后续业务入口，避免继续调用 upsert、发票等接口。
     */
    private fun rejectNonWhitelistedUser(loginSession: LoginSession) {
        val userId = loginSession.userId
        val message = text(R.string.error_user_not_in_whitelist, userId)

        session = loginSession
        upsertUserAllCompleted = false
        logoutAllowedByTimeout = true
        userMusicCache = emptyMap()
        currentOperationJob = null
        cancelNoUpsertLogoutReminder()
        upsertDelayMonitor.clear()
        operationResultNotifier.cancelUpsertProgress()
        clearClipboard()

        logger.info(text(R.string.log_user_not_in_whitelist, userId))
        _state.update {
            it.copy(
                busy = false,
                loggedIn = true,
                accessBlocked = true,
                upsertUserAllCompleted = false,
                logoutAllowedByTimeout = true,
                qrCode = message,
                status = text(R.string.status_access_blocked_need_logout),
                userId = userId.toString(),
                timestamp = loginSession.timestamp.toString(),
                cookieStatus = loginSession.cookie.toString(),
                tokenStatus = loginSession.token,
                upsertStatus = text(R.string.status_access_blocked_need_logout),
                upsertWaiting = false,
                upsertWaitRemainingSeconds = 0,
                upsertWaitTotalSeconds = 0,
                upsertWaitProgress = 0,
                upsertWaitText = "",
                lastError = message,
            )
        }
    }

    /**
     * 清空剪贴板，避免同一个未授权二维码又被页面自动填回来。
     */
    private fun clearClipboard() {
        val clipboard = Utils.getApp()
            .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
    }

    private fun isUserInWhitelist(userId: Long): Boolean =
//        UserWhitelist.contains(userId)
        true

    /**
     * 登录后 60 秒仍没有 upsertUserAll，就发通知提醒用户点击登出。
     */
    private fun scheduleNoUpsertLogoutReminder(userId: Long, deadlineEpochMillis: Long) {
        noUpsertLogoutReminderJob?.cancel()
        noUpsertLogoutReminderJob = viewModelScope.launch {
            while (true) {
                val stillSameSession = session?.userId == userId
                if (!stillSameSession || upsertUserAllCompleted) {
                    return@launch
                }

                val remainingMillis =
                    (deadlineEpochMillis - System.currentTimeMillis()).coerceAtLeast(0L)
                if (remainingMillis <= 0L) {
                    break
                }
                val remainingSeconds = ((remainingMillis + 999L) / 1_000L).toInt()

                _state.update {
                    if (it.upsertWaiting) {
                        it.copy(logoutAllowedByTimeout = false)
                    } else {
                        it.copy(
                            logoutAllowedByTimeout = false,
                            upsertStatus = text(
                                R.string.status_logout_timeout_countdown,
                                remainingSeconds
                            ),
                        )
                    }
                }
                delay(minOf(1_000L, remainingMillis))
            }

            val stillSameSession = session?.userId == userId
            if (stillSameSession && !upsertUserAllCompleted) {
                logoutAllowedByTimeout = true
                logger.info(text(R.string.log_no_upsert_timeout_reminder))
                operationResultNotifier.notifyNoUpsertLogoutReminder()
                _state.update {
                    if (it.upsertWaiting) {
                        it.copy(logoutAllowedByTimeout = true)
                    } else {
                        it.copy(
                            logoutAllowedByTimeout = true,
                            upsertStatus = text(R.string.status_no_upsert_timeout_logout_allowed),
                        )
                    }
                }
            }
        }
    }

    /**
     * 取消“登录后未 upsert”提醒任务和对应通知。
     */
    private fun cancelNoUpsertLogoutReminder() {
        noUpsertLogoutReminderJob?.cancel()
        noUpsertLogoutReminderJob = null
        upsertDelayMonitor.clearLoginGuard()
        operationResultNotifier.cancelNoUpsertLogoutReminder()
    }

    /**
     * 把本地状态恢复成“未登录”。
     */
    private fun markLoggedOut(status: String) {
        cancelNoUpsertLogoutReminder()
        userMusicCache = emptyMap()
        _state.update {
            it.copy(
                busy = false,
                loggedIn = false,
                accessBlocked = false,
                upsertUserAllCompleted = false,
                logoutAllowedByTimeout = false,
                status = status,
                userId = EMPTY_VALUE,
                timestamp = EMPTY_VALUE,
                cookieStatus = text(R.string.status_none),
                tokenStatus = text(R.string.status_none),
                upsertStatus = text(R.string.status_upsert_not_completed),
                upsertWaiting = false,
                upsertWaitRemainingSeconds = 0,
                upsertWaitTotalSeconds = 0,
                upsertWaitProgress = 0,
                upsertWaitText = "",
            )
        }
    }

    /**
     * 创建页面第一次显示时使用的默认状态。
     */
    private fun initialState(): ConsoleUiState =
        ConsoleUiState(
            status = text(R.string.status_not_logged_in),
            userId = EMPTY_VALUE,
            timestamp = EMPTY_VALUE,
            cookieStatus = text(R.string.status_none),
            tokenStatus = text(R.string.status_none),
            upsertStatus = text(R.string.status_upsert_not_completed),
        )

    /**
     * 读取 string 资源，避免在 ViewModel 中散落硬编码中文。
     */
    private fun text(resId: Int, vararg args: Any): String =
        Utils.getApp().getString(resId, *args)

    private fun parseCookieText(value: String): Map<String, String> {
        val normalized = value.trim().removeSurrounding("{", "}")
        val cookie = normalized
            .split(";")
            .flatMap { it.split(",") }
            .mapNotNull { part ->
                val key = part.substringBefore("=", "").trim()
                val cookieValue = part.substringAfter("=", "").trim()
                if (key.isBlank() || cookieValue.isBlank()) null else key to cookieValue
            }
            .toMap()

        if (cookie.isEmpty()) {
            throw IllegalStateException(text(R.string.error_cookie_invalid))
        }
        return cookie
    }

    /**
     * 登录成功后预先读取歌曲成绩。
     *
     * 之后上传同一首同一难度时，就能用“原 playCount + 1”，不用每次都手动填写。
     */
    private suspend fun loadUserMusicCacheSafely(activeSession: LoginSession): Map<MusicKey, MusicDetail> =
        try {
            loadUserMusicCache(activeSession)
        } catch (error: Throwable) {
            logger.error("读取歌曲成绩缓存失败，后续上传将从 playCount=1 开始", error)
            emptyMap()
        }

    private suspend fun loadUserMusicCache(activeSession: LoginSession): Map<MusicKey, MusicDetail> {
        val allDetails = mutableListOf<MusicDetail>()
        var nextIndex = 0
        var totalLength = Int.MAX_VALUE

        while (nextIndex < totalLength) {
            val response = actions.users.getMusic(
                userId = activeSession.userId,
                nextIndex = nextIndex,
                maxCount = USER_MUSIC_CACHE_PAGE_SIZE,
                cookie = activeSession.cookie,
            )
            totalLength = response.length
            val pageDetails = response.allMusicDetails()
            if (pageDetails.isEmpty()) {
                break
            }

            allDetails += pageDetails
            nextIndex += response.userMusicList.size
        }

        logger.info("已缓存歌曲成绩 ${allDetails.size} 条")
        return allDetails.associateBy { MusicKey(it.musicId, it.level) }
    }

    /**
     * 自动补 playCount。
     *
     * manualPlayCount 预留给“用户手动指定 playCount”的入口；不传时从登录后缓存里查旧成绩。
     */
    private fun MusicDetail.withAutoPlayCount(manualPlayCount: Int? = null): MusicDetail {
        val newPlayCount = manualPlayCount
            ?: ((userMusicCache[MusicKey(musicId, level)]?.playCount ?: 0) + 1)
        return copy(playCount = newPlayCount)
    }

    private fun UserMusicResponse.allMusicDetails(): List<MusicDetail> =
        userMusicList.flatMap { it.userMusicDetailList }

    private data class MusicKey(
        val musicId: Int,
        val level: Int,
    )

    private companion object {
        const val EMPTY_VALUE = "-"
        const val LOGIN_GUARD_DURATION_MILLIS = 60_000L
        const val USER_MUSIC_CACHE_PAGE_SIZE = 500
    }
}

