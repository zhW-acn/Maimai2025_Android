package kt.service

import kt.api.AimeClient
import kt.api.TitleApiClient
import kt.api.tokenOrNull
import kt.constants.LogoutTypes
import kt.constants.PayloadKeys
import kt.payload.asLongValue

class SessionService(
    private val api: TitleApiClient,
    private val aime: kt.api.AimeClient,
) {
    suspend fun login(userId: Long, token: String, timestamp: Long = generateLoginTimestamp()): Pair<Long, MutableMap<String, Any?>> =
        timestamp to api.login(userId, timestamp, token)

    suspend fun loginByQr(
        qrCode: String,
        preview: Boolean = true,
        timestamp: Long = generateLoginTimestamp()
    ): LoginSession =
        loginResolved(resolveByQr(qrCode, fetchPreview = preview), timestamp)

    /**
     * 只解析二维码（可选读取预览），**不登录**。
     *
     * 用于「账号已在机台登录」的顶号保护：真机流程是先查 UserPreview 的 isLogin，
     * 确认没人在打才登录。直接 loginByQr 的话预览和登录是一起的，来不及拦截。
     */
    suspend fun resolveByQr(qrCode: String, fetchPreview: Boolean = true): QrResolution {
        val qrResult = aime.resolveQr(qrCode)
        val userId = qrResult[PayloadKeys.AIME_USER_ID].asLongValue()
        val token = qrResult[PayloadKeys.TOKEN].toString()
        return QrResolution(
            userId = userId,
            token = token,
            qr = qrResult,
            preview = if (fetchPreview) api.getPreview(userId, token) else null,
        )
    }

    /**
     * 用 [resolveByQr] 的结果登录，并捕获 JSESSIONID。
     *
     * ★ 服务端**每次登录都会换发新 token**（响应里的 `token`），而二维码里那个 token 用过一次
     *   就作废。续关登录必须拿换发后的新 token，否则服务端直接返回 `returnCode=110`
     *   （实测：第 1 包发完 -> 登出 -> 用二维码里的旧 token 续关登录 -> 110）。
     *   响应里没带 token 时才退回原值。
     */
    suspend fun loginResolved(
        resolved: QrResolution,
        timestamp: Long = generateLoginTimestamp()
    ): LoginSession {
        val loginResult = api.login(resolved.userId, timestamp, resolved.token)
        return LoginSession(
            userId = resolved.userId,
            token = loginResult.tokenOrNull() ?: resolved.token,
            timestamp = timestamp,
            qr = resolved.qr,
            preview = resolved.preview,
            login = loginResult,
            cookie = loginResult[PayloadKeys.COOKIE] as Map<String, String>,
        )
    }

    /**
     * 续关登录：用**上一次登录换发的新 token** 重新登录，拿一个**新的 loginId**。
     *
     * 对应真机的 Process/ContinueProcess.cs:403 —— 一局结束后会话被消费掉，
     * 要继续上传必须重新登录，而 UpsertUserAll 的 playlogId 必须等于本次的 loginId。
     */
    suspend fun continueLogin(
        session: LoginSession,
        timestamp: Long = generateLoginTimestamp()
    ): LoginSession =
        loginResolved(
            QrResolution(
                userId = session.userId,
                token = session.token,
                qr = session.qr,
                preview = session.preview,
            ),
            timestamp,
        )

    /**
     * 登出。
     *
     * type 默认是正常登出（1）；抓源账号快照收尾时传 [LogoutTypes.TEST_IN]（4）。
     */
    suspend fun logout(
        userId: Long,
        cookie: Map<String, String>,
        timestamp: Long = System.currentTimeMillis() / 1000,
        type: Int = LogoutTypes.LOGOUT,
    ): MutableMap<String, Any?> =
        api.logout(userId, timestamp, cookie, type)
}

/** 二维码解析结果（尚未登录）。 */
data class QrResolution(
    val userId: Long,
    val token: String,
    val qr: Map<String, Any?>,
    val preview: Map<String, Any?>?,
) {
    /** 预览里 isLogin 为真表示该账号正在机台上游玩/已登录。 */
    val isLoggedIn: Boolean
        get() = preview?.get(KEY_IS_LOGIN) == true

    private companion object {
        const val KEY_IS_LOGIN = "isLogin"
    }
}

data class LoginSession(
    val userId: Long,
    val token: String,
    val timestamp: Long,
    val qr: Map<String, Any?>,
    val preview: Map<String, Any?>?,
    val login: MutableMap<String, Any?>,
    val cookie: Map<String, String>,
)

/**
 * 登录请求的 dateTime：当前 Unix 秒，对齐 Python `UserLogin` 的 `int(time.time())`。
 *
 * ★ 必须随时间递增。之前用的是「10:00 ± 10 分钟」的随机值，续关时值会回退
 *   （实测出现过 10:09:45 -> 09:54:48，随后续关返回 `returnCode=100`）。
 */
fun generateLoginTimestamp(): Long = System.currentTimeMillis() / 1000
