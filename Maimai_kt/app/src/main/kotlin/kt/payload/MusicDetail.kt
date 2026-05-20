package kt.payload

import com.okaca.maimai.android.enums.ComboStatus
import com.okaca.maimai.android.enums.ScoreLevel
import com.okaca.maimai.android.enums.SyncStatus
import kt.constants.PayloadKeys

/** UpsertUserAll 鍜?playlog 涓叡鐢ㄧ殑闊充箰鎴愮哗缁撴瀯銆?*/
data class MusicDetail(
    val musicId: Int,
    val level: Int,
    val playCount: Int = 1,
    val achievement: Int = 0,
    val comboStatus: Int = 0,
    val syncStatus: Int = 0,
    val deluxscoreMax: Int = 0,
    val scoreRank: Int = 0,
    val extNum1: Int = 0,
) {
    /** 转成 Map，方便和动态 UserAll payload 合并。 */
    fun toMap(): MutableMap<String, Any?> =
        mutableMapOf(
            PayloadKeys.MUSIC_ID to musicId,
            PayloadKeys.LEVEL to level,
            PayloadKeys.PLAY_COUNT to playCount,
            PayloadKeys.ACHIEVEMENT to achievement,
            PayloadKeys.COMBO_STATUS to comboStatus,
            PayloadKeys.SYNC_STATUS to syncStatus,
            PayloadKeys.DELUXSCORE_MAX to deluxscoreMax,
            PayloadKeys.SCORE_RANK to scoreRank,
            PayloadKeys.EXT_NUM_1 to extNum1,
        )

    companion object {
        /**
         * 默认成绩 绿谱 Oshama Scramble! 理论
         */
        fun default(): MusicDetail =
            MusicDetail(
                363,
                ScoreLevel.Basic.apiValue,
                1,
                101_0000,
                ComboStatus.AllPerfectPlus.apiValue,
                SyncStatus.FullSyncDxPlus.apiValue
            )

        /**
         * 发票 绿谱 Future 理论
         */
        fun chargeTicket(): MusicDetail = MusicDetail(
            17,
            ScoreLevel.Basic.apiValue,
            1,
            101_0000,
            ComboStatus.AllPerfectPlus.apiValue,
            SyncStatus.FullSyncDxPlus.apiValue
        )

        /**
         * 舞里程 绿谱 Love You 理论
         */
        fun point(): MusicDetail = MusicDetail(
            18,
            ScoreLevel.Basic.apiValue,
            1,
            101_0000,
            ComboStatus.AllPerfectPlus.apiValue,
            SyncStatus.FullSyncDxPlus.apiValue
        )
    }
}

