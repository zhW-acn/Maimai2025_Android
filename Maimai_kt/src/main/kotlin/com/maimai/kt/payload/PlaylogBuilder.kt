package com.maimai.kt.payload

import com.maimai.kt.config.ClientConfig
import com.maimai.kt.constants.DatePatterns
import com.maimai.kt.constants.ZoneIds
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.random.Random

/** 负责生成 userPlaylogList。 */
class PlaylogBuilder(private val config: ClientConfig) {
    /** 为每条成绩生成一条 playlog。 */
    fun build(musicDetails: List<MusicDetail>, userData: Map<String, Any?>, loginId: Long): List<Map<String, Any?>> {
        val now = LocalDateTime.now(ZoneId.of(ZoneIds.SHANGHAI))
        return musicDetails.mapIndexed { index, music -> buildOne(music, userData, loginId, now, index + 1) }
    }

    private fun buildOne(
        music: MusicDetail,
        userData: Map<String, Any?>,
        loginId: Long,
        now: LocalDateTime,
        track: Int,
    ): Map<String, Any?> {
        val charaSlot = userData["charaSlot"] as List<*>
        val playerRating = userData["playerRating"]
        return mutableMapOf(
            "userId" to 0,
            "orderId" to 0,
            "playlogId" to loginId,
            "version" to 1053000,
            "placeId" to config.placeId,
            "placeName" to config.placeName,
            "loginDate" to System.currentTimeMillis() / 1000,
            "playDate" to now.format(DateTimeFormatter.ISO_LOCAL_DATE),
            "userPlayDate" to now.plusSeconds(track.toLong()).format(DateTimeFormatter.ofPattern(DatePatterns.DATE_TIME)) + ".0",
            "type" to 0,
            "musicId" to music.musicId,
            "level" to music.level,
            "trackNo" to track,
            "vsMode" to 0,
            "vsUserName" to "",
            "vsStatus" to 0,
            "vsUserRating" to 0,
            "vsUserAchievement" to 0,
            "vsUserGradeRank" to 0,
            "vsRank" to 0,
            "playerNum" to 1,
            "playedUserId1" to 0,
            "playedUserName1" to "",
            "playedMusicLevel1" to 0,
            "playedUserId2" to 0,
            "playedUserName2" to "",
            "playedMusicLevel2" to 0,
            "playedUserId3" to 0,
            "playedUserName3" to "",
            "playedMusicLevel3" to 0,
            "characterId1" to charaSlot[0],
            "characterLevel1" to Random.nextInt(1000, 6501),
            "characterAwakening1" to 5,
            "characterId2" to charaSlot[1],
            "characterLevel2" to Random.nextInt(1000, 6501),
            "characterAwakening2" to 5,
            "characterId3" to charaSlot[2],
            "characterLevel3" to Random.nextInt(1000, 6501),
            "characterAwakening3" to 5,
            "characterId4" to charaSlot[3],
            "characterLevel4" to Random.nextInt(1000, 6501),
            "characterAwakening4" to 5,
            "characterId5" to charaSlot[4],
            "characterLevel5" to Random.nextInt(1000, 6501),
            "characterAwakening5" to 5,
            "achievement" to music.achievement,
            "deluxscore" to music.deluxscoreMax,
            "scoreRank" to music.scoreRank,
            "maxCombo" to 0,
            "totalCombo" to Random.nextInt(700, 901),
            "maxSync" to 0,
            "totalSync" to 0,
            "tapMiss" to Random.nextInt(1, 11),
            "holdMiss" to Random.nextInt(1, 16),
            "slideMiss" to Random.nextInt(1, 16),
            "touchMiss" to Random.nextInt(1, 16),
            "breakMiss" to Random.nextInt(1, 16),
            "isTap" to true,
            "isHold" to true,
            "isSlide" to true,
            "isTouch" to true,
            "isBreak" to true,
            "isCriticalDisp" to true,
            "isFastLateDisp" to true,
            "fastCount" to 0,
            "lateCount" to 0,
            "isAchieveNewRecord" to true,
            "isDeluxscoreNewRecord" to true,
            "comboStatus" to music.comboStatus,
            "syncStatus" to music.syncStatus,
            "isClear" to false,
            "beforeRating" to playerRating,
            "afterRating" to playerRating,
            "beforeGrade" to 0,
            "afterGrade" to 0,
            "afterGradeRank" to 1,
            "beforeDeluxRating" to playerRating,
            "afterDeluxRating" to playerRating,
            "isPlayTutorial" to false,
            "isEventMode" to false,
            "isFreedomMode" to false,
            "playMode" to 0,
            "isNewFree" to false,
            "trialPlayAchievement" to -1,
            "extNum1" to 0,
            "extNum2" to 0,
            "extNum4" to 0,
            "extBool1" to false,
            "extBool2" to false,
        )
    }
}
