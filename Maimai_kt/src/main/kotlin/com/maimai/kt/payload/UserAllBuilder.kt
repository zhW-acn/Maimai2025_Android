package com.maimai.kt.payload

import com.maimai.kt.config.ClientConfig
import com.maimai.kt.constants.DatePatterns
import com.maimai.kt.constants.PayloadDefaults
import com.maimai.kt.constants.PayloadKeys
import com.maimai.kt.constants.UserDataKinds
import com.maimai.kt.constants.ZoneIds
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.random.Random

/** 负责生成和补齐 UpsertUserAll payload。 */
class UserAllBuilder(private val config: ClientConfig) {
    private val playlogBuilder = PlaylogBuilder(config)

    /** 生成基础 UserAll，动作层会再叠加自己的 patch。 */
    fun build(
        userId: Long,
        loginResult: Map<String, Any?>,
        loginTimestamp: Long,
        userData: Map<String, Any?>,
        musicDetails: List<MusicDetail>,
        charaDetails: List<CharaDetail>,
    ): MutableMap<String, Any?> {
        val loginId = loginResult[PayloadKeys.LOGIN_ID].asLongValue()
        val now = LocalDateTime.now(ZoneId.of(ZoneIds.SHANGHAI))
            .format(DateTimeFormatter.ofPattern(DatePatterns.DATE_TIME)) + PayloadDefaults.DATE_TIME_FRACTION_SUFFIX
        return mutableMapOf(
            PayloadKeys.USER_ID to userId,
            PayloadKeys.PLAYLOG_ID to loginId,
            PayloadKeys.IS_EVENT_MODE to false,
            PayloadKeys.IS_FREE_PLAY to false,
            PayloadKeys.USER_PLAYLOG_LIST to playlogBuilder.build(
                musicDetails = musicDetails,
                charaDetails = charaDetails,
                userData = userData,
                loginId = loginId
            ),
            PayloadKeys.UPSERT_USER_ALL to mutableMapOf(
                PayloadKeys.USER_DATA to mutableListOf(
                    buildUserData(
                        loginTimestamp,
                        userData,
                        now
                    )
                ),
                PayloadKeys.USER_EXTEND to mutableListOf<Any?>(),
                PayloadKeys.USER_OPTION to mutableListOf<Any?>(),
                PayloadKeys.USER_GHOST to mutableListOf<Any?>(),
                PayloadKeys.USER_CHARACTER_LIST to mutableListOf<Any?>(),
                PayloadKeys.USER_MAP_LIST to mutableListOf<Any?>(),
                PayloadKeys.USER_LOGIN_BONUS_LIST to mutableListOf<Any?>(),
                PayloadKeys.USER_RATING_LIST to mutableListOf<Any?>(),
                PayloadKeys.USER_ITEM_LIST to mutableListOf<Any?>(),
                PayloadKeys.USER_MUSIC_DETAIL_LIST to mutableListOf<Any?>(),
                PayloadKeys.USER_COURSE_LIST to mutableListOf<Any?>(),
                PayloadKeys.USER_FRIEND_SEASON_RANKING_LIST to mutableListOf<Any?>(),
                PayloadKeys.USER_CHARGE_LIST to mutableListOf<Any?>(),
                PayloadKeys.USER_FAVORITE_LIST to mutableListOf<Any?>(),
                PayloadKeys.USER_ACTIVITY_LIST to mutableListOf<Any?>(),
                PayloadKeys.USER_MISSION_DATA_LIST to mutableListOf<Any?>(),
                PayloadKeys.USER_WEEKLY_DATA to mutableListOf<Any?>(),
                PayloadKeys.USER_GAME_PLAYLOG_LIST to mutableListOf(
                    mapOf(
                        PayloadKeys.PLAYLOG_ID to loginId,
                        PayloadKeys.VERSION to PayloadDefaults.GAME_PLAYLOG_VERSION,
                        PayloadKeys.PLAY_DATE to now,
                        PayloadKeys.PLAY_MODE to 0,
                        PayloadKeys.USE_TICKET_ID to -1,
                        PayloadKeys.PLAY_CREDIT to 1,
                        PayloadKeys.PLAY_TRACK to 1,
                        PayloadKeys.CLIENT_ID to config.clientId,
                        PayloadKeys.IS_PLAY_TUTORIAL to false,
                        PayloadKeys.IS_EVENT_MODE to false,
                        PayloadKeys.IS_NEW_FREE to false,
                        PayloadKeys.PLAY_COUNT to userData[PayloadKeys.PLAY_COUNT],
                        PayloadKeys.PLAY_SPECIAL to calcPlaySpecial(),
                        PayloadKeys.PLAY_OTHER_USER_ID to 0,
                    )
                ),
                PayloadKeys.USER_2P_PLAYLOG to mapOf(
                    PayloadKeys.USER_ID_1 to 0,
                    PayloadKeys.USER_ID_2 to 0,
                    PayloadKeys.USER_NAME_1 to PayloadDefaults.EMPTY,
                    PayloadKeys.USER_NAME_2 to PayloadDefaults.EMPTY,
                    PayloadKeys.REGION_ID to 0,
                    PayloadKeys.PLACE_ID to 0,
                    PayloadKeys.USER_2P_PLAYLOG_DETAIL_LIST to emptyList<Any>(),
                ),
                PayloadKeys.USER_INTIMATE_LIST to mutableListOf<Any?>(),
                PayloadKeys.USER_SHOP_ITEM_STOCK_LIST to mutableListOf<Any?>(),
                PayloadKeys.USER_GET_POINT_LIST to mutableListOf<Any?>(),
                PayloadKeys.USER_TRADE_ITEM_LIST to mutableListOf<Any?>(),
                PayloadKeys.USER_FAVORITE_MUSIC_LIST to mutableListOf<Any?>(),
                PayloadKeys.USER_KALEIDX_SCOPE_LIST to mutableListOf<Any?>(),
                PayloadKeys.IS_NEW_CHARACTER_LIST to PayloadDefaults.EMPTY,
                PayloadKeys.IS_NEW_MAP_LIST to PayloadDefaults.EMPTY,
                PayloadKeys.IS_NEW_LOGIN_BONUS_LIST to PayloadDefaults.EMPTY,
                PayloadKeys.IS_NEW_ITEM_LIST to PayloadDefaults.EMPTY,
                PayloadKeys.IS_NEW_MUSIC_DETAIL_LIST to PayloadDefaults.EMPTY,
                PayloadKeys.IS_NEW_COURSE_LIST to PayloadDefaults.ZERO_STRING,
                PayloadKeys.IS_NEW_FAVORITE_LIST to PayloadDefaults.EMPTY,
                PayloadKeys.IS_NEW_FRIEND_SEASON_RANKING_LIST to PayloadDefaults.EMPTY,
                PayloadKeys.IS_NEW_USER_INTIMATE_LIST to PayloadDefaults.EMPTY,
                PayloadKeys.IS_NEW_FAVORITE_MUSIC_LIST to PayloadDefaults.EMPTY,
                PayloadKeys.IS_NEW_KALEIDX_SCOPE_LIST to PayloadDefaults.EMPTY,
            )
        )
    }

    /** 把服务器当前状态挂回 UserAll，避免 upsert 时丢状态。 */
    @Suppress("UNCHECKED_CAST")
    fun attachCurrentState(
        userAll: MutableMap<String, Any?>,
        current: Map<String, Map<String, Any?>>
    ): MutableMap<String, Any?> {
        val upsert = userAll[PayloadKeys.UPSERT_USER_ALL].asStringAnyMap()
        current[UserDataKinds.EXTEND]?.get(PayloadKeys.USER_EXTEND)
            ?.let { upsert[PayloadKeys.USER_EXTEND] = mutableListOf(it) }
        current[UserDataKinds.OPTION]?.get(PayloadKeys.USER_OPTION)
            ?.let { upsert[PayloadKeys.USER_OPTION] = mutableListOf(it) }
        current[UserDataKinds.RATING]?.get(PayloadKeys.USER_RATING)
            ?.let { upsert[PayloadKeys.USER_RATING_LIST] = mutableListOf(it) }
        current[UserDataKinds.ACTIVITY]?.get(PayloadKeys.USER_ACTIVITY)
            ?.let { upsert[PayloadKeys.USER_ACTIVITY_LIST] = mutableListOf(it) }
        current[UserDataKinds.MISSION_DATA]?.get(PayloadKeys.USER_WEEKLY_DATA)
            ?.let { upsert[PayloadKeys.USER_WEEKLY_DATA] = it }
        current[UserDataKinds.CHARGE]?.get(PayloadKeys.USER_CHARGE_LIST)?.let { charges ->
            val chargeList = (charges as List<MutableMap<String, Any?>>).map {
                it[PayloadKeys.STOCK] = 0
                it
            }
            upsert[PayloadKeys.USER_CHARGE_LIST] = chargeList
        }
        return userAll
    }

    private fun buildUserData(
        loginTimestamp: Long,
        source: Map<String, Any?>,
        lastPlayDate: String
    ): MutableMap<String, Any?> {
        val copiedFields = listOf(
            PayloadKeys.CHARA_LOCK_SLOT,
            PayloadKeys.CHARA_SLOT,
            PayloadKeys.CLASS_RANK,
            PayloadKeys.CM_LAST_EMONEY_BRAND,
            PayloadKeys.CM_LAST_EMONEY_CREDIT,
            PayloadKeys.POINT,
            PayloadKeys.TOTAL_POINT,
            PayloadKeys.COMPATIBLE_CM_VERSION,
            PayloadKeys.CONTENT_BIT,
            PayloadKeys.COURSE_RANK,
            PayloadKeys.CURRENT_PLAY_COUNT,
            PayloadKeys.DAILY_BONUS_DATE,
            PayloadKeys.DAILY_COURSE_BONUS_DATE,
            PayloadKeys.EVENT_WATCHED_DATE,
            PayloadKeys.FIRST_DATA_VERSION,
            PayloadKeys.FIRST_PLAY_DATE,
            PayloadKeys.FIRST_ROM_VERSION,
            PayloadKeys.FRAME_ID,
            PayloadKeys.FRIEND_CODE,
            PayloadKeys.FRIEND_REGIST_SKIP,
            PayloadKeys.GRADE_RANK,
            PayloadKeys.GRADE_RATING,
            PayloadKeys.HIGHEST_RATING,
            PayloadKeys.ICON_ID,
            PayloadKeys.LAST_DATA_VERSION,
            PayloadKeys.LAST_LOGIN_DATE,
            PayloadKeys.LAST_PAIR_LOGIN_DATE,
            PayloadKeys.LAST_ROM_VERSION,
            PayloadKeys.LAST_SELECT_COURSE,
            PayloadKeys.LAST_TRIAL_PLAY_DATE,
            PayloadKeys.MAP_STOCK,
            PayloadKeys.MUSIC_RATING,
            PayloadKeys.NAMEPLATE_ID,
            PayloadKeys.PARTNER_ID,
            PayloadKeys.PLATE_ID,
            PayloadKeys.PLAY_COUNT,
            PayloadKeys.PLAYER_NEW_RATING,
            PayloadKeys.PLAYER_OLD_RATING,
            PayloadKeys.PLAYER_RATING,
            PayloadKeys.SELECT_MAP_ID,
            PayloadKeys.TITLE_ID,
            PayloadKeys.TOTAL_ACHIEVEMENT,
            PayloadKeys.TOTAL_ADVANCED_ACHIEVEMENT,
            PayloadKeys.TOTAL_ADVANCED_DELUXSCORE,
            PayloadKeys.TOTAL_ADVANCED_SYNC,
            PayloadKeys.TOTAL_AWAKE,
            PayloadKeys.TOTAL_BASIC_ACHIEVEMENT,
            PayloadKeys.TOTAL_BASIC_DELUXSCORE,
            PayloadKeys.TOTAL_BASIC_SYNC,
            PayloadKeys.TOTAL_DELUXSCORE,
            PayloadKeys.TOTAL_EXPERT_ACHIEVEMENT,
            PayloadKeys.TOTAL_EXPERT_DELUXSCORE,
            PayloadKeys.TOTAL_EXPERT_SYNC,
            PayloadKeys.TOTAL_MASTER_ACHIEVEMENT,
            PayloadKeys.TOTAL_MASTER_DELUXSCORE,
            PayloadKeys.TOTAL_MASTER_SYNC,
            PayloadKeys.TOTAL_REMASTER_ACHIEVEMENT,
            PayloadKeys.TOTAL_REMASTER_DELUXSCORE,
            PayloadKeys.TOTAL_REMASTER_SYNC,
            PayloadKeys.TOTAL_SYNC,
            PayloadKeys.TROPHY_ID,
            PayloadKeys.USER_NAME,
        )
        val data = mutableMapOf<String, Any?>()
        copiedFields.forEach { data[it] = source[it] }
        data += mapOf(
            PayloadKeys.ACCESS_CODE to PayloadDefaults.EMPTY,
            PayloadKeys.COMBO_COUNT to 0,
            PayloadKeys.DATE_TIME to loginTimestamp,
            PayloadKeys.FIRST_GAME_ID to PayloadDefaults.GAME_ID,
            PayloadKeys.HELP_COUNT to 0,
            PayloadKeys.IS_NET_MEMBER to 1,
            PayloadKeys.LAST_ALL_NET_ID to 0,
            PayloadKeys.LAST_CLIENT_ID to config.clientId,
            PayloadKeys.LAST_COUNT_COURSE to 0,
            PayloadKeys.LAST_COUNTRY_CODE to PayloadDefaults.COUNTRY_CODE,
            PayloadKeys.LAST_GAME_ID to PayloadDefaults.GAME_ID,
            PayloadKeys.LAST_PLACE_ID to config.placeId,
            PayloadKeys.LAST_PLACE_NAME to config.placeName,
            PayloadKeys.LAST_PLAY_CREDIT to 1,
            PayloadKeys.LAST_PLAY_DATE to lastPlayDate,
            PayloadKeys.LAST_PLAY_MODE to 0,
            PayloadKeys.LAST_REGION_ID to config.regionId,
            PayloadKeys.LAST_REGION_NAME to config.regionName,
            PayloadKeys.LAST_SELECT_EMONEY to 0,
            PayloadKeys.LAST_SELECT_TICKET to 0,
            PayloadKeys.PLAY_SYNC_COUNT to 0,
            PayloadKeys.PLAY_VS_COUNT to 0,
            PayloadKeys.RENAME_CREDIT to 0,
            PayloadKeys.WIN_COUNT to 0,
        )
        return data
    }
}

/** 生成 userGamePlaylogList 里需要的 playSpecial。 */
fun calcPlaySpecial(): Int {
    val number = Random.nextInt(1, 1_037_934) * 2069 + 1024
    return Integer.reverse(number)
}
