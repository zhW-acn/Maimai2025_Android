package com.okaca

import com.maimai.kt.log.MaimaiLogger
import com.maimai.kt.service.MaimaiActions

private val logger = object : MaimaiLogger {
    override fun debug(message: String) {
        println("[DEBUG] $message")
    }

    override fun error(message: String, throwable: Throwable?) {
        System.err.println("[ERROR] $message")
        throwable?.printStackTrace()
    }
}

suspend fun main() {
    try {
        val actions = MaimaiActions(logger = logger)
        val session = actions.sessions.loginByQr("SGWCMAID260514161644731FA982A502F49F45BD2C8AFF3C4C91B2DC3ABAF4F197DA7227C8F95035C756")

        actions.scores.upload(
            userId = session.userId,
            loginTimestamp = session.timestamp,
            loginResult = session.login,
            musicId = 363,
            level = 1,
            achievement = 1000000,
            dxScore = 100,
        )
    } catch (error: Throwable) {
        logger.error("Request failed", error)
    }
}
