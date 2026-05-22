package kt.transport

import kt.config.ClientConfig
import kt.constants.HttpConstants
import kt.constants.PayloadKeys
import kt.crypto.TitleCodec
import kt.error.MaimaiRequestException
import kt.error.MaimaiResponseException
import kt.log.MaimaiLogger
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.ByteArrayContent
import kotlinx.coroutines.delay
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 * TitleServer 网络传输层。
 *
 * 它只关心“怎样把一个 API 名称和 payload 发送出去”，不写业务逻辑。
 */
class TitleTransport(
    private val config: ClientConfig = ClientConfig(),
    private val client: HttpClient = defaultHttpClient(config),
    private val logger: MaimaiLogger = MaimaiLogger.None,
) {
    private val codec = TitleCodec(config.aesKey, config.aesIv, config.obfuscateParam)

    /** 发送加密请求，返回解密后的原始字符串和可选 cookie。 */
    suspend fun postRaw(
        apiName: String,
        payload: Map<String, Any?>,
        userAgentExtra: Any,
        cookie: Map<String, String>? = null,
        captureCookie: Boolean = false,
    ): TitleRawResponse {
        val apiHash = codec.apiHash(apiName)
        val encodedBody = codec.encodeRequest(JsonSupport.stringify(payload))
        var lastError: Throwable? = null

        repeat(config.maxRetries) { index ->
            try {
                logger.debug("POST $apiName -> ${config.titleEndpoint + apiHash}")
                logger.debug("POST $apiName request: ${JsonSupport.stringify(payload)}")
                val response = client.post(config.titleEndpoint + apiHash) {
                    setBody(ByteArrayContent(encodedBody, ContentType.Application.Json))
                    headers {
                        titleHeaders(apiHash, userAgentExtra).forEach { (key, value) -> append(key, value) }
                        cookie?.forEach { (key, value) ->
                            append(HttpHeaders.Cookie, "$key${HttpConstants.KEY_VALUE_SEPARATOR}$value")
                        }
                    }
                }
                if (response.status != HttpStatusCode.OK) {
                    throw MaimaiRequestException("$apiName 请求失败，HTTP ${response.status.value}")
                }
                val decoded = codec.decodeResponse(response.body<ByteArray>())
                logger.debug("POST $apiName response: $decoded")
                return TitleRawResponse(decoded, if (captureCookie) parseCookies(response.headers.getAll(HttpHeaders.SetCookie)) else emptyMap())
            } catch (error: MaimaiRequestException) {
                logger.error("POST $apiName failed", error)
                throw error
            } catch (error: Throwable) {
                logger.error("POST $apiName attempt ${index + 1} failed", error)
                lastError = error
                if (index + 1 < config.maxRetries) delay(2_000)
            }
        }

        throw MaimaiResponseException("$apiName 多次重试后仍然请求失败", lastError)
    }

    /** 发送请求并把响应解析成 Map。 */
    suspend fun postJson(
        apiName: String,
        payload: Map<String, Any?>,
        userAgentExtra: Any,
        cookie: Map<String, String>? = null,
        captureCookie: Boolean = false,
    ): MutableMap<String, Any?> {
        val raw = postRaw(apiName, payload, userAgentExtra, cookie, captureCookie)
        val parsed = JsonSupport.parseObject(raw.body)
        if (captureCookie) parsed[PayloadKeys.COOKIE] = raw.cookies
        return parsed
    }

    /** 发送请求并把响应解析成指定对象。 */
    suspend fun <T> postJsonAs(
        apiName: String,
        payload: Map<String, Any?>,
        userAgentExtra: Any,
        responseClass: Class<T>,
        cookie: Map<String, String>? = null,
    ): T {
        val raw = postRaw(apiName, payload, userAgentExtra, cookie)
        return JsonSupport.parse(raw.body, responseClass)
    }

    private fun titleHeaders(apiHash: String, userAgentExtra: Any): Map<String, String> =
        mapOf(
            HttpConstants.USER_AGENT to "$apiHash${HttpConstants.TITLE_USER_AGENT_SEPARATOR}$userAgentExtra",
            HttpConstants.CONTENT_TYPE to HttpConstants.APPLICATION_JSON,
            HttpConstants.MAI_ENCODING to HttpConstants.MAI_ENCODING_VALUE,
            HttpConstants.ACCEPT_ENCODING to "",
            HttpConstants.CHARSET to HttpConstants.UTF_8,
            HttpConstants.CONTENT_ENCODING to HttpConstants.DEFLATE,
            HttpConstants.HOST to HttpConstants.TITLE_HOST,
        )
}

/** TitleServer 原始响应，body 已解密解压，cookies 来自响应头。 */
data class TitleRawResponse(
    val body: String,
    val cookies: Map<String, String> = emptyMap(),
)

/** 从 Set-Cookie 响应头提取 JSESSIONID 等 cookie。 */
private fun parseCookies(values: List<String>?): Map<String, String> {
    if (values.isNullOrEmpty()) return emptyMap()
    return values.mapNotNull { value ->
        val first = value.substringBefore(HttpConstants.COOKIE_SEPARATOR)
        val key = first.substringBefore(HttpConstants.KEY_VALUE_SEPARATOR, "")
        val cookieValue = first.substringAfter(HttpConstants.KEY_VALUE_SEPARATOR, "")
        if (key.isBlank()) null else key to cookieValue
    }.toMap()
}

/** 创建 Ktor HTTP 客户端。verifyTls=false 时会对齐 Python verify=False 的行为。 */
private fun defaultHttpClient(config: ClientConfig): HttpClient =
    HttpClient(OkHttp) {
        install(HttpTimeout) {
            requestTimeoutMillis = config.titleTimeoutMillis
            connectTimeoutMillis = config.titleTimeoutMillis
            socketTimeoutMillis = config.titleTimeoutMillis
        }
        engine {
            config {
                if (!config.verifyTls) {
                    val trustManager = trustAllManager()
                    val sslContext = SSLContext.getInstance(HttpConstants.TLS)
                    sslContext.init(null, arrayOf(trustManager), SecureRandom())
                    sslSocketFactory(sslContext.socketFactory, trustManager)
                    hostnameVerifier { _, _ -> true }
                }
            }
        }
    }

private fun trustAllManager(): X509TrustManager =
    object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }
