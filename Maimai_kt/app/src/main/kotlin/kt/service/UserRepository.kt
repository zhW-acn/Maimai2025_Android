package kt.service

import kt.api.TitleApiClient
import kt.constants.PayloadKeys
import kt.constants.UserDataKinds
import kt.payload.UserMusicResponse

class UserRepository(private val api: TitleApiClient) {
    suspend fun get(userId: Long, thing: String, cookie: Map<String, String>? = null): MutableMap<String, Any?> =
        api.getUser(userId, thing, cookie)

    /**
     * 分页读取用户歌曲成绩。
     *
     * nextIndex 表示从第几页/段开始取，maxCount 表示本次最多取多少段；
     * 接口返回里会有 userMusicList，每一项下面再放 userMusicDetailList。
     */
    suspend fun getMusic(
        userId: Long,
        nextIndex: Int = 0,
        maxCount: Int = 1,
        cookie: Map<String, String>? = null
    ): UserMusicResponse =
        api.getUserMusic(userId, nextIndex, maxCount, cookie)

    suspend fun getData(userId: Long, cookie: Map<String, String>? = null): MutableMap<String, Any?> =
        get(userId, UserDataKinds.DATA, cookie)[PayloadKeys.USER_DATA] as MutableMap<String, Any?>

    suspend fun getRequiredState(userId: Long, cookie: Map<String, String>? = null): Map<String, MutableMap<String, Any?>> =
        mapOf(
            UserDataKinds.EXTEND to get(userId, UserDataKinds.EXTEND, cookie),
            UserDataKinds.OPTION to get(userId, UserDataKinds.OPTION, cookie),
            UserDataKinds.RATING to get(userId, UserDataKinds.RATING, cookie),
            UserDataKinds.ACTIVITY to get(userId, UserDataKinds.ACTIVITY, cookie),
            UserDataKinds.CHARGE to get(userId, UserDataKinds.CHARGE, cookie),
            UserDataKinds.MISSION_DATA to get(userId, UserDataKinds.MISSION_DATA, cookie),
        )
}
