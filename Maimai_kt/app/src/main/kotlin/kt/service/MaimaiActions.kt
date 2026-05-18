package kt.service

import kt.api.AimeClient
import kt.api.TitleApiClient
import kt.config.ClientConfig
import kt.log.MaimaiLogger

/**
 * Kotlin 版统一入口。
 *
 * 外部通常只需要创建这个类，然后从 sessions、scores、unlocks、tickets、versions 里选择动作。
 */
class MaimaiActions(config: ClientConfig = ClientConfig(), logger: MaimaiLogger = MaimaiLogger.None) {
    val api = TitleApiClient(config, logger)
    val aime = kt.api.AimeClient(config, logger = logger)
    val sessions = SessionService(api, aime)
    val users = UserRepository(api)
    val fullPlay = FullPlayService(api, users)
    val scores = ScoreService(fullPlay)
    val unlocks = UnlockService(fullPlay)
    val tickets = TicketService(api, fullPlay, users)
    val versions = VersionService(fullPlay)
}
