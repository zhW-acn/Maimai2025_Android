package com.maimai.kt.example

import com.maimai.kt.constants.PayloadKeys
import com.maimai.kt.payload.MusicDetail
import com.maimai.kt.service.MaimaiActions
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val qrCode =
        "SGWCMAID260515232120E1625030C2F5668D50331F328CEF1C1DF4A856F50AD1BA4FC848C497BD03078B"
    val actions = MaimaiActions()
    val session = actions.sessions.loginByQr(qrCode)
    var upsertUserAllCompleted = false

    try {
        val musicDetails = listOf(
            MusicDetail.default(
                musicId = 363,
                level = 1,
                achievement = 100_0000,
                dxScore = 100,
            )
        )
        val musicData = musicDetails.map { it.toMap() }
        val userAllPatches = mapOf(
            PayloadKeys.UPSERT_USER_ALL to mapOf(
                PayloadKeys.USER_MUSIC_DETAIL_LIST to musicData,
                PayloadKeys.IS_NEW_MUSIC_DETAIL_LIST to "1",
            )
        )

        val result = actions.fullPlay.submit(
            userId = session.userId,
            loginTimestamp = session.timestamp,
            loginResult = session.login,
            musicDetails = musicDetails,
            patch = userAllPatches,
        )
        upsertUserAllCompleted = true
        println(result)
    } finally {
        if (upsertUserAllCompleted) {
            print(actions.sessions.logout(session.userId, session.cookie))
        } else {
            println("upsertUserAll 未成功完成，跳过 logout，避免触发无效登出。")
        }
    }
}
