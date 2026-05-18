package kt.api

import kt.config.ClientConfig
import kt.constants.ApiNames
import kt.constants.LoginCodes
import kt.constants.PayloadKeys
import kt.error.MaimaiLoginException
import kt.log.MaimaiLogger
import kt.payload.asIntValue
import kt.service.FullPlayService
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

    /** 鐧诲嚭褰撳墠鐢ㄦ埛浼氳瘽銆?*/
    suspend fun logout(
        userId: Long,
        timestamp: Long,
        cookie: Map<String, String>
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
                PayloadKeys.TYPE to 1,
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

    /** 鐧诲綍鍓嶈鍙栫敤鎴烽瑙堬紝瀵归綈 test.py 閲岀殑 QR 鐧诲綍娴佺▼銆?*/
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
        maxCount: Int = 50,
        cookie: Map<String, String>? = null
    ): MutableMap<String, Any?> =
        request(
            ApiNames.GET_USER_MUSIC,
            mapOf(
                PayloadKeys.USER_ID to userId,
                PayloadKeys.NEXT_INDEX to nextIndex,
                PayloadKeys.MAX_COUNT to maxCount
            ),
            userId,
            cookie,
        )

    /** 鎻愪氦瀹屾暣 UserAll銆?*/
    suspend fun upsertUserAll(
        userId: Long,
        payload: Map<String, Any?>,
        cookie: Map<String, String>
    ): MutableMap<String, Any?> {
        waitBeforePostWithCountdown(
            waitMillis = config.currentWaitBeforeUpsertMillis(),
            label = ApiNames.UPSERT_USER_ALL,
            logger = logger,
            observer = config.postDelayObserver,
        )
        return request(ApiNames.UPSERT_USER_ALL, payload, userId, cookie)
    }

    /** 鎻愪氦璐エ璁板綍銆?*/
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
