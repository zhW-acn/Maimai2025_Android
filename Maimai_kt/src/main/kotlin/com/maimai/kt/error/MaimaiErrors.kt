package com.maimai.kt.error

/** Kotlin 重构客户端的基础异常。 */
open class MaimaiClientException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/** 请求已经发出，但服务器返回了非 200 或请求格式被拒绝。 */
class MaimaiRequestException(message: String, cause: Throwable? = null) : MaimaiClientException(message, cause)

/** 响应体无法解密、解压或解析。 */
class MaimaiResponseException(message: String, cause: Throwable? = null) : MaimaiClientException(message, cause)

/** 登录流程返回了业务失败状态。 */
class MaimaiLoginException(val code: Int, message: String) : MaimaiClientException(message)
