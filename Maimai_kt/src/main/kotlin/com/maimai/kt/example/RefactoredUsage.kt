package com.maimai.kt.example

import com.maimai.kt.service.MaimaiActions
import kotlinx.coroutines.runBlocking

/** Kotlin 版使用示例：先 QR 登录，再执行具体动作。 */
fun main() = runBlocking {
    val qrCode = "SGWCMAID260510143156E4C4AEEDF3DD61EE02D652ED3C703A33D31EEF02C7919DC518FF5D7872BAB440"
    val actions = MaimaiActions()
    val session = actions.sessions.loginByQr(qrCode)

    val result = actions.versions.change(
        userId = session.userId,
        loginTimestamp = session.timestamp,
        loginResult = session.login,
    )
    println(result)
}
