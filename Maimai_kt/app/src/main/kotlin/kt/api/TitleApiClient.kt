package kt.api

import kt.config.ClientConfig
import kt.constants.ApiNames
import kt.constants.LoginCodes
import kt.constants.LogoutTypes
import kt.constants.PayloadKeys
import kt.error.MaimaiLoginException
import kt.log.MaimaiLogger
import kt.payload.UserMusicResponse
import kt.payload.asIntValue
import kt.transport.TitleTransport
import kt.transport.waitBeforePostWithCountdown

/**
 * TitleServer 逻辑 API 封装。
 *
 * 上层 service 不需要关心 API hash、加密、cookie 等细节，只调用这里的方法。
 */
class TitleApiClient(
    val config: ClientConfig = ClientConfig(),
    private val logger: MaimaiLogger = MaimaiLogger.None,
    private val transport: TitleTransport = TitleTransport(config, logger = logger),
) {
    /** 按逻辑 API 名称调用 TitleServer。 */
    suspend fun request(
        apiName: String,
        payload: Map<String, Any?>,
        userId: Long,
        cookie: Map<String, String>? = null,
    ): MutableMap<String, Any?> = transport.postJson(apiName, payload, userId, cookie)

    /** 使用二维码解析出的 token 登录，并捕获 JSESSIONID。 */
    suspend fun login(userId: Long, timestamp: Long, token: String): MutableMap<String, Any?> {
        val result = transport.postJson(
            ApiNames.USER_LOGIN,
            mapOf(
                PayloadKeys.USER_ID to userId,
                PayloadKeys.ACCESS_CODE to "",
                PayloadKeys.REGION_ID to config.regionId,
                PayloadKeys.PLACE_ID to config.placeId,
                PayloadKeys.CLIENT_ID to config.clientId,
                PayloadKeys.DATE_TIME to timestamp,
                PayloadKeys.IS_CONTINUE to true,
                PayloadKeys.GENERIC_FLAG to 0,
                PayloadKeys.TOKEN to token,
            ),
            userId,
            captureCookie = true,
        )
        when (val code = result.loginCode()) {
            LoginCodes.SUCCESS -> return result
            LoginCodes.PLAYING -> throw MaimaiLoginException(code, "用户正在游玩中")
            LoginCodes.QR_REFRESH_REQUIRED -> throw MaimaiLoginException(
                code,
                "二维码需要刷新"
            )

            else -> throw MaimaiLoginException(code, "登录失败，错误码 $code")
        }
    }

    /**
     * 登出当前用户会话。
     *
     * type 默认 [LogoutTypes.LOGOUT]（真机抓包实证 UserLogoutApi 就是 type=1）；
     * 抓源账号快照后要用 [LogoutTypes.TEST_IN]，和 Python 版 `fetch_source_by_qr` 一致。
     */
    suspend fun logout(
        userId: Long,
        timestamp: Long,
        cookie: Map<String, String>,
        type: Int = LogoutTypes.LOGOUT,
    ): MutableMap<String, Any?> =
        request(
            ApiNames.USER_LOGOUT,
            mapOf(
                PayloadKeys.USER_ID to userId,
                PayloadKeys.ACCESS_CODE to "",
                PayloadKeys.REGION_ID to config.regionId,
                PayloadKeys.PLACE_ID to config.placeId,
                PayloadKeys.CLIENT_ID to config.clientId,
                PayloadKeys.DATE_TIME to timestamp,
                PayloadKeys.TYPE to type,
            ),
            userId,
            cookie,
        )

    /** 读取 GetUser* 数据，例如 Data、Charge、Option、Rating。 */
    suspend fun getUser(
        userId: Long,
        thing: String,
        cookie: Map<String, String>? = null
    ): MutableMap<String, Any?> =
        request(
            "${ApiNames.GET_USER_PREFIX}$thing${ApiNames.API_SUFFIX}",
            mapOf(PayloadKeys.USER_ID to userId),
            userId,
            cookie
        )

    /** 登录前读取用户预览，对齐 test.py 里的 QR 登录流程。 */
    suspend fun getPreview(userId: Long, token: String): MutableMap<String, Any?> =
        request(
            ApiNames.GET_USER_PREVIEW,
            mapOf(
                PayloadKeys.USER_ID to userId,
                PayloadKeys.SEGA_ID_AUTH_KEY to "",
                PayloadKeys.TOKEN to token,
                PayloadKeys.CLIENT_ID to config.clientId,
            ),
            userId,
        )

    /** 分页读取用户歌曲成绩。 */
    suspend fun getUserMusic(
        userId: Long,
        nextIndex: Int = 0,
        maxCount: Int = 1,
        cookie: Map<String, String>? = null
    ): UserMusicResponse =
        transport.postJsonAs(
            ApiNames.GET_USER_MUSIC,
            mapOf(
                PayloadKeys.USER_ID to userId,
                PayloadKeys.NEXT_INDEX to nextIndex,
                PayloadKeys.MAX_COUNT to maxCount
            ),
            userId,
            UserMusicResponse::class.java,
            cookie,
        )

    /**
     * 提交完整 UserAll。
     *
     * [beforeSend] 在冷却等待结束、真正发出 POST 之前调用一次（迁移用它把请求 JSON 留底）。
     */
    suspend fun upsertUserAll(
        userId: Long,
        payload: Map<String, Any?>,
        cookie: Map<String, String>,
        beforeSend: suspend () -> Unit = {},
    ): MutableMap<String, Any?> {
        waitBeforePostWithCountdown(
            waitMillis = config.currentWaitBeforeUpsertMillis(),
            label = ApiNames.UPSERT_USER_ALL,
            logger = logger,
            observer = config.postDelayObserver,
        )
        beforeSend()
        return request(ApiNames.UPSERT_USER_ALL, payload, userId, cookie)
    }

    /** 提交购票记录。 */
    suspend fun upsertChargeLog(
        userId: Long,
        payload: Map<String, Any?>,
        cookie: Map<String, String>
    ): MutableMap<String, Any?> {
        waitBeforePostWithCountdown(
            waitMillis = config.currentWaitBeforeUpsertMillis(),
            label = ApiNames.UPSERT_CHARGE_LOG,
            logger = logger,
            observer = config.postDelayObserver,
        )

        return request(ApiNames.UPSERT_CHARGE_LOG, payload, userId, cookie)
    }
}

private fun Map<String, Any?>.loginCode(): Int =
    firstNotNullOfOrNull { (key, value) ->
        if (key.equals(PayloadKeys.RETURN_CODE, ignoreCase = true) ||
            key.equals(PayloadKeys.RESULT_CODE, ignoreCase = true) ||
            key.equals(PayloadKeys.STATUS, ignoreCase = true) ||
            key.equals(PayloadKeys.CODE, ignoreCase = true)
        ) {
            value.asIntValue()
        } else {
            null
        }
    } ?: LoginCodes.SUCCESS

/**
 * 登录响应换发的新 token（`UserLoginApi` 响应里的 `token`）。
 *
 * ★ 服务端每次登录都会换发，二维码里那个用过一次就作废 —— 续关登录要用这一个。
 *   响应里没带（或为空）时返回 null，让调用方退回原 token。
 */
internal fun Map<String, Any?>.tokenOrNull(): String? =
    get(PayloadKeys.TOKEN)?.toString()?.takeIf { it.isNotBlank() }
