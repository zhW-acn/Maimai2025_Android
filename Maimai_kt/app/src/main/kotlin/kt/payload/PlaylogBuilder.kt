package kt.payload

import kt.config.ClientConfig
import kt.constants.DatePatterns
import kt.constants.PayloadDefaults
import kt.constants.PayloadKeys
import kt.constants.ZoneIds
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 璐熻矗鐢熸垚 userPlaylogList銆?*/
class PlaylogBuilder(private val config: ClientConfig) {
    /** 涓烘瘡鏉℃垚缁╃敓鎴愪竴鏉?playlog銆?*/
    fun build(
        musicDetails: List<MusicDetail>,
        charaDetails: List<CharaDetail>,
        userData: Map<String, Any?>,
        loginId: Long
    ): List<Map<String, Any?>> {
        val now = LocalDateTime.now(ZoneId.of(ZoneIds.SHANGHAI))
        return musicDetails.mapIndexed { index, music ->
            buildOne(
                music,
                charaDetails,
                userData,
                loginId,
                now,
                index + 1
            )
        }
    }

    private fun buildOne(
        music: MusicDetail,
        charaDetails: List<CharaDetail>,
        userData: Map<String, Any?>,
        loginId: Long,
        now: LocalDateTime,
        track: Int,
    ): Map<String, Any?> {
        val charaSlot = userData[PayloadKeys.CHARA_SLOT] as List<*>
        val playerRating = userData[PayloadKeys.PLAYER_RATING]
        return mutableMapOf(
            PayloadKeys.USER_ID to 0,
            PayloadKeys.ORDER_ID to 0,
            PayloadKeys.PLAYLOG_ID to loginId,
            PayloadKeys.VERSION to PayloadDefaults.PLAYLOG_VERSION,
            PayloadKeys.PLACE_ID to config.placeId,
            PayloadKeys.PLACE_NAME to config.placeName,
            PayloadKeys.LOGIN_DATE to System.currentTimeMillis() / 1000,
            PayloadKeys.PLAY_DATE to now.format(DateTimeFormatter.ISO_LOCAL_DATE),
            PayloadKeys.USER_PLAY_DATE to now.plusSeconds(track.toLong())
                .format(DateTimeFormatter.ofPattern(DatePatterns.DATE_TIME)) + PayloadDefaults.DATE_TIME_FRACTION_SUFFIX,
            PayloadKeys.TYPE to 0,
            PayloadKeys.MUSIC_ID to music.musicId,
            PayloadKeys.LEVEL to music.level,
            PayloadKeys.TRACK_NO to track,
            PayloadKeys.VS_MODE to 0,
            PayloadKeys.VS_USER_NAME to PayloadDefaults.EMPTY,
            PayloadKeys.VS_STATUS to 0,
            PayloadKeys.VS_USER_RATING to 0,
            PayloadKeys.VS_USER_ACHIEVEMENT to 0,
            PayloadKeys.VS_USER_GRADE_RANK to 0,
            PayloadKeys.VS_RANK to 0,
            PayloadKeys.PLAYER_NUM to 1,
            PayloadKeys.PLAYED_USER_ID_1 to 0,
            PayloadKeys.PLAYED_USER_NAME_1 to PayloadDefaults.EMPTY,
            PayloadKeys.PLAYED_MUSIC_LEVEL_1 to 0,
            PayloadKeys.PLAYED_USER_ID_2 to 0,
            PayloadKeys.PLAYED_USER_NAME_2 to PayloadDefaults.EMPTY,
            PayloadKeys.PLAYED_MUSIC_LEVEL_2 to 0,
            PayloadKeys.PLAYED_USER_ID_3 to 0,
            PayloadKeys.PLAYED_USER_NAME_3 to PayloadDefaults.EMPTY,
            PayloadKeys.PLAYED_MUSIC_LEVEL_3 to 0,
            /* 鏃呰浼欎即 */
            PayloadKeys.CHARACTER_ID_1 to charaDetails[0].characterId,
            PayloadKeys.CHARACTER_LEVEL_1 to charaDetails[0].characterLevel,
            PayloadKeys.CHARACTER_AWAKENING_1 to charaDetails[0].awake,
            PayloadKeys.CHARACTER_ID_2 to charaDetails[1].characterId,
            PayloadKeys.CHARACTER_LEVEL_2 to charaDetails[1].characterLevel,
            PayloadKeys.CHARACTER_AWAKENING_2 to charaDetails[1].awake,
            PayloadKeys.CHARACTER_ID_3 to charaDetails[2].characterId,
            PayloadKeys.CHARACTER_LEVEL_3 to charaDetails[2].characterLevel,
            PayloadKeys.CHARACTER_AWAKENING_3 to charaDetails[2].awake,
            PayloadKeys.CHARACTER_ID_4 to charaDetails[3].characterId,
            PayloadKeys.CHARACTER_LEVEL_4 to charaDetails[3].characterLevel,
            PayloadKeys.CHARACTER_AWAKENING_4 to charaDetails[3].awake,
            PayloadKeys.CHARACTER_ID_5 to charaDetails[4].characterId,
            PayloadKeys.CHARACTER_LEVEL_5 to charaDetails[4].characterLevel,
            PayloadKeys.CHARACTER_AWAKENING_5 to charaDetails[4].awake,
            /* 姝屾洸鎴愮哗 */
            PayloadKeys.ACHIEVEMENT to music.achievement,
            PayloadKeys.DELUXSCORE to music.deluxscoreMax,
            PayloadKeys.SCORE_RANK to music.scoreRank,
            PayloadKeys.COMBO_STATUS to music.comboStatus,
            PayloadKeys.SYNC_STATUS to music.syncStatus,
            PayloadKeys.MAX_COMBO to if (music.comboStatus == 0) 0 else 1,
            PayloadKeys.TOTAL_COMBO to 1,
            PayloadKeys.MAX_SYNC to 0,
            PayloadKeys.TOTAL_SYNC to 0,

            PayloadKeys.TAP_CRITICAL_PERFECT to if (music.comboStatus == 0) 0 else 1,
            PayloadKeys.TAP_PERFECT to 0,
            PayloadKeys.TAP_GREAT to 0,
            PayloadKeys.TAP_GOOD to 0,
            PayloadKeys.TAP_MISS to if (music.comboStatus == 0) 1 else 0,

            PayloadKeys.HOLD_CRITICAL_PERFECT to 0,
            PayloadKeys.HOLD_PERFECT to 0,
            PayloadKeys.HOLD_GREAT to 0,
            PayloadKeys.HOLD_GOOD to 0,
            PayloadKeys.HOLD_MISS to 0,

            PayloadKeys.SLIDE_CRITICAL_PERFECT to 0,
            PayloadKeys.SLIDE_PERFECT to 0,
            PayloadKeys.SLIDE_GREAT to 0,
            PayloadKeys.SLIDE_GOOD to 0,
            PayloadKeys.SLIDE_MISS to 0,

            PayloadKeys.TOUCH_CRITICAL_PERFECT to 0,
            PayloadKeys.TOUCH_PERFECT to 0,
            PayloadKeys.TOUCH_GREAT to 0,
            PayloadKeys.TOUCH_GOOD to 0,
            PayloadKeys.TOUCH_MISS to 0,

            PayloadKeys.BREAK_CRITICAL_PERFECT to 0,
            PayloadKeys.BREAK_PERFECT to 0,
            PayloadKeys.BREAK_GREAT to 0,
            PayloadKeys.BREAK_GOOD to 0,
            PayloadKeys.BREAK_MISS to 0,

            PayloadKeys.IS_TAP to true,
            PayloadKeys.IS_HOLD to true,
            PayloadKeys.IS_SLIDE to true,
            PayloadKeys.IS_TOUCH to true,
            PayloadKeys.IS_BREAK to true,
            PayloadKeys.IS_CRITICAL_DISP to true,
            PayloadKeys.IS_FAST_LATE_DISP to true,

            PayloadKeys.FAST_COUNT to 0,
            PayloadKeys.LATE_COUNT to 0,

            PayloadKeys.IS_ACHIEVE_NEW_RECORD to true,
            PayloadKeys.IS_DELUXSCORE_NEW_RECORD to true,
            PayloadKeys.IS_CLEAR to false,
            PayloadKeys.BEFORE_RATING to playerRating,
            PayloadKeys.AFTER_RATING to playerRating,
            PayloadKeys.BEFORE_GRADE to 0,
            PayloadKeys.AFTER_GRADE to 0,
            PayloadKeys.AFTER_GRADE_RANK to 1,
            PayloadKeys.BEFORE_DELUX_RATING to playerRating,
            PayloadKeys.AFTER_DELUX_RATING to playerRating,
            PayloadKeys.IS_PLAY_TUTORIAL to false,
            PayloadKeys.IS_EVENT_MODE to false,
            PayloadKeys.IS_FREEDOM_MODE to false,
            PayloadKeys.PLAY_MODE to 0,
            PayloadKeys.IS_NEW_FREE to false,
            PayloadKeys.TRIAL_PLAY_ACHIEVEMENT to -1,
            PayloadKeys.EXT_NUM_1 to 0,
            PayloadKeys.EXT_NUM_2 to 0,
            PayloadKeys.EXT_NUM_4 to 0,
            PayloadKeys.EXT_BOOL_1 to false,
            PayloadKeys.EXT_BOOL_2 to false,
        )
    }
}
