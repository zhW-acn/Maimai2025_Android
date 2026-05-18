package kt.service

import kt.constants.PayloadKeys
import kt.payload.CharaDetail
import kt.payload.MusicDetail

class PointService(private val fullPlay: FullPlayService) {
    suspend fun upload(
        userId: Long,
        loginTimestamp: Long,
        loginResult: Map<String, Any?>,
        music: MusicDetail,
        charaDetail: List<CharaDetail> = CharaDetail.defaultList(),
        extra: Map<String, Any?> = mapOf(),
    ): MutableMap<String, Any?> {
        val upsertPatch = mutableMapOf<String, Any?>(
            PayloadKeys.USER_MUSIC_DETAIL_LIST to listOf(music.toMap()),
            PayloadKeys.IS_NEW_MUSIC_DETAIL_LIST to "1",
        ).apply {
            // extra 表示 upsertUserAll 内层的额外字段，例如 userData、userItemList。
            putAll(extra)
        }

        val patch = mapOf(
            PayloadKeys.UPSERT_USER_ALL to upsertPatch,
        )
        return fullPlay.submit(
            userId,
            loginTimestamp,
            loginResult,
            listOf(music),
            patch,
            charaDetail
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
