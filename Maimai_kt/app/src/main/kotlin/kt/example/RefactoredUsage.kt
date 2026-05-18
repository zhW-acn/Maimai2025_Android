package kt.example

import kt.constants.PayloadKeys
import kt.payload.MusicDetail
import kt.service.MaimaiActions
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val qrCode =
        "SGWCMAID260515232120E1625030C2F5668D50331F328CEF1C1DF4A856F50AD1BA4FC848C497BD03078B"
    val actions = MaimaiActions()
    val session = actions.sessions.loginByQr(qrCode)
    var upsertUserAllCompleted = false

    try {
        val musicDetails = listOf(
            MusicDetail.default()
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
            println("upsertUserAll did not finish; skip logout.")
        }
    }
}
