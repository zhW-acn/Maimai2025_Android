package com.maimai.kt.payload

import com.maimai.kt.config.ClientConfig
import com.maimai.kt.constants.DatePatterns
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
    ): MutableMap<String, Any?> {
        val loginId = loginResult["loginId"].asLongValue()
        val now = LocalDateTime.now(ZoneId.of(ZoneIds.SHANGHAI)).format(DateTimeFormatter.ofPattern(DatePatterns.DATE_TIME)) + ".0"
        return mutableMapOf(
            "userId" to userId,
            "playlogId" to loginId,
            "isEventMode" to false,
            "isFreePlay" to false,
            "userPlaylogList" to playlogBuilder.build(musicDetails, userData, loginId),
            "upsertUserAll" to mutableMapOf(
                "userData" to mutableListOf(buildUserData(loginTimestamp, userData, now)),
                "userExtend" to mutableListOf<Any?>(),
                "userOption" to mutableListOf<Any?>(),
                "userGhost" to mutableListOf<Any?>(),
                "userCharacterList" to mutableListOf<Any?>(),
                "userMapList" to mutableListOf<Any?>(),
                "userLoginBonusList" to mutableListOf<Any?>(),
                "userRatingList" to mutableListOf<Any?>(),
                "userItemList" to mutableListOf<Any?>(),
                "userMusicDetailList" to mutableListOf<Any?>(),
                "userCourseList" to mutableListOf<Any?>(),
                "userFriendSeasonRankingList" to mutableListOf<Any?>(),
                "userChargeList" to mutableListOf<Any?>(),
                "userFavoriteList" to mutableListOf<Any?>(),
                "userActivityList" to mutableListOf<Any?>(),
                "userMissionDataList" to mutableListOf<Any?>(),
                "userWeeklyData" to mutableListOf<Any?>(),
                "userGamePlaylogList" to mutableListOf(
                    mapOf(
                        "playlogId" to loginId,
                        "version" to "1.53.00",
                        "playDate" to now,
                        "playMode" to 0,
                        "useTicketId" to -1,
                        "playCredit" to 1,
                        "playTrack" to 1,
                        "clientId" to config.clientId,
                        "isPlayTutorial" to false,
                        "isEventMode" to false,
                        "isNewFree" to false,
                        "playCount" to userData["playCount"],
                        "playSpecial" to calcPlaySpecial(),
                        "playOtherUserId" to 0,
                    )
                ),
                "user2pPlaylog" to mapOf(
                    "userId1" to 0,
                    "userId2" to 0,
                    "userName1" to "",
                    "userName2" to "",
                    "regionId" to 0,
                    "placeId" to 0,
                    "user2pPlaylogDetailList" to emptyList<Any>(),
                ),
                "userIntimateList" to mutableListOf<Any?>(),
                "userShopItemStockList" to mutableListOf<Any?>(),
                "userGetPointList" to mutableListOf<Any?>(),
                "userTradeItemList" to mutableListOf<Any?>(),
                "userFavoritemusicList" to mutableListOf<Any?>(),
                "userKaleidxScopeList" to mutableListOf<Any?>(),
                "isNewCharacterList" to "",
                "isNewMapList" to "",
                "isNewLoginBonusList" to "",
                "isNewItemList" to "",
                "isNewMusicDetailList" to "",
                "isNewCourseList" to "0",
                "isNewFavoriteList" to "",
                "isNewFriendSeasonRankingList" to "",
                "isNewUserIntimateList" to "",
                "isNewFavoritemusicList" to "",
                "isNewKaleidxScopeList" to "",
            )
        )
    }

    /** 把服务器当前状态挂回 UserAll，避免 upsert 时丢状态。 */
    @Suppress("UNCHECKED_CAST")
    fun attachCurrentState(userAll: MutableMap<String, Any?>, current: Map<String, Map<String, Any?>>): MutableMap<String, Any?> {
        val upsert = userAll["upsertUserAll"].asStringAnyMap()
        current["Extend"]?.get("userExtend")?.let { upsert["userExtend"] = mutableListOf(it) }
        current["Option"]?.get("userOption")?.let { upsert["userOption"] = mutableListOf(it) }
        current["Rating"]?.get("userRating")?.let { upsert["userRatingList"] = mutableListOf(it) }
        current["Activity"]?.get("userActivity")?.let { upsert["userActivityList"] = mutableListOf(it) }
        current["MissionData"]?.get("userWeeklyData")?.let { upsert["userWeeklyData"] = it }
        current["Charge"]?.get("userChargeList")?.let { charges ->
            val chargeList = (charges as List<MutableMap<String, Any?>>).map {
                it["stock"] = 0
                it
            }
            upsert["userChargeList"] = chargeList
        }
        return userAll
    }

    private fun buildUserData(loginTimestamp: Long, source: Map<String, Any?>, lastPlayDate: String): MutableMap<String, Any?> {
        val copiedFields = listOf(
            "charaLockSlot", "charaSlot", "classRank", "cmLastEmoneyBrand", "cmLastEmoneyCredit",
            "point", "totalPoint", "compatibleCmVersion", "contentBit", "courseRank", "currentPlayCount",
            "dailyBonusDate", "dailyCourseBonusDate", "eventWatchedDate", "firstDataVersion", "firstPlayDate",
            "firstRomVersion", "frameId", "friendCode", "friendRegistSkip", "gradeRank", "gradeRating",
            "highestRating", "iconId", "lastDataVersion", "lastLoginDate", "lastPairLoginDate", "lastRomVersion",
            "lastSelectCourse", "lastTrialPlayDate", "mapStock", "musicRating", "nameplateId", "partnerId",
            "plateId", "playCount", "playerNewRating", "playerOldRating", "playerRating", "selectMapId",
            "titleId", "totalAchievement", "totalAdvancedAchievement", "totalAdvancedDeluxscore",
            "totalAdvancedSync", "totalAwake", "totalBasicAchievement", "totalBasicDeluxscore", "totalBasicSync",
            "totalDeluxscore", "totalExpertAchievement", "totalExpertDeluxscore", "totalExpertSync",
            "totalMasterAchievement", "totalMasterDeluxscore", "totalMasterSync", "totalReMasterAchievement",
            "totalReMasterDeluxscore", "totalReMasterSync", "totalSync", "trophyId", "userName",
        )
        val data = mutableMapOf<String, Any?>()
        copiedFields.forEach { data[it] = source[it] }
        data += mapOf(
            "accessCode" to "",
            "comboCount" to 0,
            "dateTime" to loginTimestamp,
            "firstGameId" to "SDGB",
            "helpCount" to 0,
            "isNetMember" to 1,
            "lastAllNetId" to 0,
            "lastClientId" to config.clientId,
            "lastCountCourse" to 0,
            "lastCountryCode" to "CHN",
            "lastGameId" to "SDGB",
            "lastPlaceId" to config.placeId,
            "lastPlaceName" to config.placeName,
            "lastPlayCredit" to 1,
            "lastPlayDate" to lastPlayDate,
            "lastPlayMode" to 0,
            "lastRegionId" to config.regionId,
            "lastRegionName" to config.regionName,
            "lastSelectEMoney" to 0,
            "lastSelectTicket" to 0,
            "playSyncCount" to 0,
            "playVsCount" to 0,
            "renameCredit" to 0,
            "winCount" to 0,
        )
        return data
    }
}

/** 生成 userGamePlaylogList 里需要的 playSpecial。 */
fun calcPlaySpecial(): Int {
    val number = Random.nextInt(1, 1_037_934) * 2069 + 1024
    return Integer.reverse(number)
}
