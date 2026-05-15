package com.maimai.kt.service

import com.maimai.kt.api.AimeClient
import com.maimai.kt.api.TitleApiClient
import com.maimai.kt.config.ClientConfig
import com.maimai.kt.log.MaimaiLogger

/**
 * Kotlin 版统一入口。
 *
 * 外部使用时通常只需要创建这个类，然后从 sessions/scores/unlocks/tickets/versions 里选动作。
 */
class MaimaiActions(config: ClientConfig = ClientConfig(), logger: MaimaiLogger = MaimaiLogger.None) {
    val api = TitleApiClient(config, logger)
    val aime = AimeClient(config, logger = logger)
    val sessions = SessionService(api, aime)
    val users = UserRepository(api)
    val fullPlay = FullPlayService(api, users)
    val scores = ScoreService(fullPlay)
    val unlocks = UnlockService(fullPlay)
    val tickets = TicketService(api, fullPlay, users)
    val versions = VersionService(fullPlay)
}
