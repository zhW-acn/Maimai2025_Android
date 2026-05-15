package com.maimai.kt.service

import com.maimai.kt.constants.PayloadKeys
import com.maimai.kt.payload.MusicDetail

class ScoreService(private val fullPlay: FullPlayService) {
    suspend fun upload(
        userId: Long,
        loginTimestamp: Long,
        loginResult: Map<String, Any?>,
        musicId: Int,
        level: Int,
        achievement: Int,
        dxScore: Int,
        waitBeforeSubmit: Boolean = true,
    ): MutableMap<String, Any?> {
        val music = MusicDetail.default(musicId, level, achievement = achievement, dxScore = dxScore)
        val patch = mapOf(
            PayloadKeys.UPSERT_USER_ALL to mapOf(
                PayloadKeys.USER_MUSIC_DETAIL_LIST to listOf(music.toMap()),
                PayloadKeys.IS_NEW_MUSIC_DETAIL_LIST to "1",
            )
        )
        return fullPlay.submit(userId, loginTimestamp, loginResult, listOf(music), patch, waitBeforeSubmit)
    }

    suspend fun delete(
        userId: Long,
        loginTimestamp: Long,
        loginResult: Map<String, Any?>,
        musicItems: List<Map<String, Any?>>,
        waitBeforeSubmit: Boolean = true,
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
        return fullPlay.submit(userId, loginTimestamp, loginResult, musicList, patch, waitBeforeSubmit)
    }
}
