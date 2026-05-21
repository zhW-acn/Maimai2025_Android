package kt.service

import kt.api.TitleApiClient
import kt.constants.PayloadKeys
import kt.constants.UserDataKinds
import kt.payload.CharaDetail
import kt.payload.MusicDetail
import kt.payload.UserAllBuilder
import kt.payload.UserCharacter
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
        userCharacters: List<UserCharacter> = emptyList(),
    ): MutableMap<String, Any?> {
        val cookie = loginResult[PayloadKeys.COOKIE] as Map<String, String>
        val userData = users.getData(userId, cookie)
        val currentState = users.getRequiredState(userId, cookie)
        val userAll =
            builder.build(userId, loginResult, loginTimestamp, userData, musicDetails, charaDetail)
        builder.attachCurrentState(userAll, currentState)
        attachUserCharacters(userAll, userData, userCharacters)
        mergePatch(userAll, patch)
        return api.upsertUserAll(userId, userAll, cookie)
    }

    private fun attachUserCharacters(
        userAll: MutableMap<String, Any?>,
        userData: Map<String, Any?>,
        userCharacters: List<UserCharacter>,
    ) {
        if (userCharacters.isEmpty()) {
            return
        }

        val charaSlot = userData[PayloadKeys.CHARA_SLOT].asIntList()
        val resolvedCharacters = userCharacters.mapIndexed { index, character ->
            if (character.characterId == CharaDetail.CHARA_ID_NONE) {
                character.withCharacterId(charaSlot.getOrNull(index) ?: CharaDetail.CHARA_ID_NONE)
            } else {
                character
            }
        }
        val upsert = userAll[PayloadKeys.UPSERT_USER_ALL] as MutableMap<String, Any?>
        upsert[PayloadKeys.USER_CHARACTER_LIST] = resolvedCharacters.map { it.toMap() }
        upsert[PayloadKeys.IS_NEW_CHARACTER_LIST] = "0".repeat(resolvedCharacters.size)
    }

    private fun Any?.asIntList(): List<Int> =
        (this as? List<*>)?.mapNotNull { value ->
            when (value) {
                is Number -> value.toInt()
                is String -> value.toIntOrNull()
                else -> null
            }
        } ?: emptyList()

    suspend fun chargeList(userId: Long, cookie: Map<String, String>): List<MutableMap<String, Any?>> =
        users.get(userId, UserDataKinds.CHARGE, cookie)[PayloadKeys.USER_CHARGE_LIST] as List<MutableMap<String, Any?>>
}
