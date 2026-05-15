package com.maimai.kt.service

import com.maimai.kt.constants.PayloadKeys
import com.maimai.kt.constants.VersionDefaults
import com.maimai.kt.payload.MusicDetail

class VersionService(private val fullPlay: FullPlayService) {
    suspend fun change(
        userId: Long,
        loginTimestamp: Long,
        loginResult: Map<String, Any?>,
        romVersion: String = VersionDefaults.ROM_VERSION,
        dataVersion: String = VersionDefaults.DATA_VERSION,
        waitBeforeSubmit: Boolean = true,
    ): MutableMap<String, Any?> {
        val cookie = loginResult[PayloadKeys.COOKIE] as Map<String, String>
        val charges = fullPlay.chargeList(userId, cookie)
        val music = MusicDetail.default()
        val patch = mapOf(
            PayloadKeys.UPSERT_USER_ALL to mapOf(
                PayloadKeys.USER_DATA to listOf(
                    mapOf(PayloadKeys.LAST_ROM_VERSION to romVersion, PayloadKeys.LAST_DATA_VERSION to dataVersion)
                ),
                PayloadKeys.USER_CHARGE_LIST to charges,
                PayloadKeys.USER_MUSIC_DETAIL_LIST to listOf(music.toMap()),
                PayloadKeys.IS_NEW_MUSIC_DETAIL_LIST to "1",
            )
        )
        return fullPlay.submit(userId, loginTimestamp, loginResult, listOf(music), patch, waitBeforeSubmit)
    }
}
