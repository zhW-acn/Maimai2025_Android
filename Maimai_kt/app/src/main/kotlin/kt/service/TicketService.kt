package kt.service

import kt.api.TitleApiClient
import kt.constants.DatePatterns
import kt.constants.PayloadKeys
import kt.constants.UserDataKinds
import kt.constants.ZoneIds
import kt.payload.MusicDetail
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class TicketService(
    private val api: TitleApiClient,
    private val fullPlay: FullPlayService,
    private val users: UserRepository,
) {
    suspend fun query(userId: Long, cookie: Map<String, String>? = null): MutableMap<String, Any?> =
        api.getUser(userId, UserDataKinds.CHARGE, cookie)

    suspend fun buy(
        userId: Long,
        ticketType: Int,
        cookie: Map<String, String>
    ): MutableMap<String, Any?> {
        val userData = users.getData(userId, cookie)
        val now = LocalDateTime.now(ZoneId.of(ZoneIds.SHANGHAI))
        val formatter = DateTimeFormatter.ofPattern(DatePatterns.DATE_TIME)
        val purchaseDate = now.format(formatter) + ".0"
        val payload = mapOf(
            PayloadKeys.USER_ID to userId,
            PayloadKeys.USER_CHARGE_LOG to mapOf(
                PayloadKeys.CHARGE_ID to 6,
                PayloadKeys.PRICE to 4,
                PayloadKeys.PURCHASE_DATE to purchaseDate,
                PayloadKeys.PLAY_COUNT to userData[PayloadKeys.PLAY_COUNT],
                PayloadKeys.PLAYER_RATING to userData[PayloadKeys.PLAYER_RATING],
                PayloadKeys.PLACE_ID to api.config.placeId,
                PayloadKeys.REGION_ID to api.config.regionId,
                PayloadKeys.CLIENT_ID to api.config.clientId,
            ),
            PayloadKeys.USER_CHARGE to mapOf(
                PayloadKeys.CHARGE_ID to ticketType,
                PayloadKeys.STOCK to 1,
                PayloadKeys.PURCHASE_DATE to purchaseDate,
                PayloadKeys.VALID_DATE to now.plusDays(90).withHour(4).withMinute(0).withSecond(0)
                    .format(formatter),
            ),
        )
        return api.upsertChargeLog(userId, payload, cookie)
    }
}
