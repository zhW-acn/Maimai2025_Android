package kt.service

import kt.constants.PayloadKeys
import kt.payload.CharaDetail
import kt.payload.MusicDetail
import kt.payload.UserCharacter
import kt.payload.mergePatch

class ScoreService(private val fullPlay: FullPlayService) {
    suspend fun upload(
        userId: Long,
        loginTimestamp: Long,
        loginResult: Map<String, Any?>,
        music: MusicDetail,
        charaDetail: List<CharaDetail> = CharaDetail.defaultList(),
        userCharacters: List<UserCharacter> = emptyList(),
        extra: Map<String, Any?> = mapOf(),
    ): MutableMap<String, Any?> {
        val patch = mutableMapOf<String, Any?>(
            PayloadKeys.UPSERT_USER_ALL to mutableMapOf(
                PayloadKeys.USER_MUSIC_DETAIL_LIST to listOf(music.toMap()),
                PayloadKeys.IS_NEW_MUSIC_DETAIL_LIST to "0",
            ),
        )
        // extra 是整个 upsertUserAll 请求 JSON 的顶层补丁。
        // 例如可以传 userPlaylogList，也可以传 upsertUserAll.userData。
        mergePatch(patch, extra)
        return fullPlay.submit(
            userId,
            loginTimestamp,
            loginResult,
            listOf(music),
            patch,
            charaDetail,
            userCharacters,
        )
    }

    suspend fun delete(
        userId: Long,
        loginTimestamp: Long,
        loginResult: Map<String, Any?>,
        musicItems: List<Map<String, Any?>>,
    ): MutableMap<String, Any?> {
        val musicList = musicItems.map {
            MusicDetail(
                musicId = (it[PayloadKeys.MUSIC_ID] as Number).toInt(),
                level = (it[PayloadKeys.LEVEL] as Number).toInt(),
            )
        }
        val patch = mapOf(
            PayloadKeys.UPSERT_USER_ALL to mapOf(
                PayloadKeys.USER_MUSIC_DETAIL_LIST to musicList.map { it.toMap() },
                PayloadKeys.IS_NEW_MUSIC_DETAIL_LIST to "0".repeat(musicList.size),
            )
        )
        return fullPlay.submit(
            userId,
            loginTimestamp,
            loginResult,
            musicList,
            patch,
        )
    }
}
