package kt.api

import kt.config.ClientConfig
import kt.constants.AimeConstants
import kt.constants.DatePatterns
import kt.constants.HttpConstants
import kt.constants.PayloadKeys
import kt.constants.ZoneIds
import kt.log.MaimaiLogger
import kt.transport.JsonSupport
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class AimeClient(
    private val config: ClientConfig = ClientConfig(),
    private val client: HttpClient = HttpClient(OkHttp),
    private val logger: MaimaiLogger = MaimaiLogger.None,
) {
    suspend fun resolveQr(qrCode: String): MutableMap<String, Any?> {
        val finalQrCode = if (qrCode.length > AimeConstants.QR_PAYLOAD_LENGTH) {
            qrCode.takeLast(AimeConstants.QR_PAYLOAD_LENGTH)
        } else {
            qrCode
        }
        val timestamp = DateTimeFormatter.ofPattern(DatePatterns.AIME_TIMESTAMP)
            .format(LocalDateTime.now(ZoneId.of(ZoneIds.TOKYO)))
        val chipId = config.chipId.ifBlank { config.clientId }
        val payload = mapOf(
            PayloadKeys.CHIP_ID to chipId,
            PayloadKeys.OPEN_GAME_ID to AimeConstants.OPEN_GAME_ID_MAID,
            PayloadKeys.KEY to authKey(chipId, timestamp),
            PayloadKeys.QR_CODE to finalQrCode,
            PayloadKeys.TIMESTAMP to timestamp,
        )
        logger.debug("POST Aime -> ${config.aimeEndpoint}")
        logger.debug("POST Aime request: ${JsonSupport.stringify(payload)}")
        val response = client.post(config.aimeEndpoint) {
            setBody(TextContent(JsonSupport.stringify(payload), ContentType.Application.Json))
            headers {
                append(HttpConstants.CONNECTION, HttpConstants.KEEP_ALIVE)
                append(HttpConstants.HOST, config.aimeEndpoint.substringAfter("//").substringBefore("/"))
                append(HttpConstants.USER_AGENT, AimeConstants.USER_AGENT)
                append(HttpConstants.CONTENT_TYPE, HttpConstants.APPLICATION_JSON)
            }
        }
        val body = response.bodyAsText()
        logger.debug("POST Aime response: $body")
        return JsonSupport.parseObject(body)
    }

    fun isSgwcFormat(value: String): Boolean =
        value.length == AimeConstants.SGWC_LENGTH &&
            value.startsWith(AimeConstants.SGWC_PREFIX) &&
            value.drop(AimeConstants.SGWC_PREFIX.length + 12).matches(Regex(AimeConstants.HEX_PATTERN))

    private fun authKey(chipId: String, timestamp: String): String =
        sha256("$chipId$timestamp${config.aimeCommonKey}")

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02X".format(it) }
    }
}
