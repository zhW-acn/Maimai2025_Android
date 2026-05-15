package com.maimai.kt.service

import com.maimai.kt.api.AimeClient
import com.maimai.kt.api.TitleApiClient
import com.maimai.kt.constants.PayloadKeys
import com.maimai.kt.constants.ZoneIds
import com.maimai.kt.payload.asLongValue
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

class SessionService(
    private val api: TitleApiClient,
    private val aime: AimeClient,
) {
    suspend fun login(userId: Long, token: String, timestamp: Long = generateLoginTimestamp()): Pair<Long, MutableMap<String, Any?>> =
        timestamp to api.login(userId, timestamp, token)

    suspend fun loginByQr(qrCode: String, preview: Boolean = true, timestamp: Long = generateLoginTimestamp()): LoginSession {
        val qrResult = aime.resolveQr(qrCode)
        val userId = qrResult[PayloadKeys.AIME_USER_ID].asLongValue()
        val token = qrResult[PayloadKeys.TOKEN].toString()
        val previewResult = if (preview) api.getPreview(userId, token) else null
        val loginResult = api.login(userId, timestamp, token)
        return LoginSession(
            userId = userId,
            token = token,
            timestamp = timestamp,
            qr = qrResult,
            preview = previewResult,
            login = loginResult,
            cookie = loginResult[PayloadKeys.COOKIE] as Map<String, String>,
        )
    }

    suspend fun logout(userId: Long, cookie: Map<String, String>, timestamp: Long = System.currentTimeMillis() / 1000): MutableMap<String, Any?> =
        api.logout(userId, timestamp, cookie)
}

data class LoginSession(
    val userId: Long,
    val token: String,
    val timestamp: Long,
    val qr: Map<String, Any?>,
    val preview: Map<String, Any?>?,
    val login: MutableMap<String, Any?>,
    val cookie: Map<String, String>,
)

fun generateLoginTimestamp(): Long {
    val base = LocalDate.now(ZoneId.of(ZoneIds.SHANGHAI)).atTime(10, 0).atZone(ZoneId.systemDefault()).toEpochSecond()
    return base + Random.nextInt(-600, 601)
}
