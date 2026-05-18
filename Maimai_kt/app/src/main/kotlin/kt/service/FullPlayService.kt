package kt.service

import kt.api.TitleApiClient
import kt.constants.PayloadKeys
import kt.constants.UserDataKinds
import kt.payload.CharaDetail
import kt.payload.MusicDetail
import kt.payload.UserAllBuilder
import kt.payload.mergePatch

class FullPlayService(
    private val api: TitleApiClient,
    private val users: UserRepository,
) {
    private val builder = UserAllBuilder(api.config)

    suspend fun submit(
        userId: Long,
        loginTimestamp: Long,
        loginResult: Map<String, Any?>,
        musicDetails: List<MusicDetail>,
        patch: Map<String, Any?>,
        charaDetail: List<CharaDetail> = CharaDetail.defaultList(),
    ): MutableMap<String, Any?> {
        val cookie = loginResult[PayloadKeys.COOKIE] as Map<String, String>
        val userData = users.getData(userId, cookie)
        val currentState = users.getRequiredState(userId, cookie)
        val userAll =
            builder.build(userId, loginResult, loginTimestamp, userData, musicDetails, charaDetail)
        builder.attachCurrentState(userAll, currentState)
        mergePatch(userAll, patch)
        return api.upsertUserAll(userId, userAll, cookie)
    }

    suspend fun chargeList(userId: Long, cookie: Map<String, String>): List<MutableMap<String, Any?>> =
        users.get(userId, UserDataKinds.CHARGE, cookie)[PayloadKeys.USER_CHARGE_LIST] as List<MutableMap<String, Any?>>
}
