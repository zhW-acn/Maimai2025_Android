package com.maimai.kt.service

import com.maimai.kt.constants.PayloadKeys
import com.maimai.kt.payload.MusicDetail

class UnlockService(private val fullPlay: FullPlayService) {
    suspend fun item(
        userId: Long,
        loginTimestamp: Long,
        loginResult: Map<String, Any?>,
        itemId: Int,
        itemKind: Int,
    ): MutableMap<String, Any?> = items(
        userId,
        loginTimestamp,
        loginResult,
        listOf(
            mapOf(
                PayloadKeys.ITEM_KIND to itemKind,
                PayloadKeys.ITEM_ID to itemId,
                PayloadKeys.STOCK to 1,
                PayloadKeys.IS_VALID to true,
            )
        ),
    )

    suspend fun musicMaster(userId: Long, loginTimestamp: Long, loginResult: Map<String, Any?>, musicId: Int): MutableMap<String, Any?> =
        item(userId, loginTimestamp, loginResult, musicId, ITEM_KIND_MUSIC_MASTER)

    suspend fun points(userId: Long, loginTimestamp: Long, loginResult: Map<String, Any?>, amount: Int = 999): MutableMap<String, Any?> =
        items(
            userId,
            loginTimestamp,
            loginResult,
            listOf(
                mapOf(
                    PayloadKeys.ITEM_KIND to 4,
                    PayloadKeys.ITEM_ID to 0,
                    PayloadKeys.STOCK to amount,
                    PayloadKeys.IS_VALID to true,
                )
            ),
        )

    suspend fun items(
        userId: Long,
        loginTimestamp: Long,
        loginResult: Map<String, Any?>,
        itemList: List<Map<String, Any?>>,
    ): MutableMap<String, Any?> {
        val music = MusicDetail.default(playCount = 0, achievement = 0, dxScore = 0)
        val patch = mapOf(
            PayloadKeys.UPSERT_USER_ALL to mapOf(
                PayloadKeys.USER_MUSIC_DETAIL_LIST to listOf(music.toMap()),
                PayloadKeys.IS_NEW_MUSIC_DETAIL_LIST to "1",
                PayloadKeys.USER_ITEM_LIST to itemList,
                PayloadKeys.IS_NEW_ITEM_LIST to "1".repeat(itemList.size),
            )
        )
        return fullPlay.submit(userId, loginTimestamp, loginResult, listOf(music), patch)
    }

    companion object {
        const val ITEM_KIND_MUSIC_MASTER = 6
    }
}
