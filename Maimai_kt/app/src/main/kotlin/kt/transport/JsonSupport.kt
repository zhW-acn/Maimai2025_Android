package kt.transport

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

/** 全项目共用的 JSON 工具，避免到处 new ObjectMapper。 */
object JsonSupport {
    val mapper = jacksonObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    /** 把任意 Kotlin Map/data class 序列化成紧凑 JSON。 */
    fun stringify(value: Any): String = mapper.writeValueAsString(value)

    /** 把 JSON 对象解析成可继续传递的 Map。 */
    fun parseObject(value: String): MutableMap<String, Any?> =
        mapper.readValue(value, object : TypeReference<MutableMap<String, Any?>>() {})

    /** 把 JSON 对象解析成指定 data class，适合返回结构固定的接口。 */
    fun <T> parse(value: String, responseClass: Class<T>): T =
        mapper.readValue(value, responseClass)
}
