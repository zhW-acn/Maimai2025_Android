package kt.payload

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
        /** 非成绩类动作使用的默认成绩，用来带起一次完整 UpsertUserAll。 */
        fun default(
            musicId: Int = 363,
            level: Int = 1,
            playCount: Int = 1,
            achievement: Int = 114,
            dxScore: Int = 514,
        ): MusicDetail =
            MusicDetail(musicId, level, playCount, achievement, deluxscoreMax = dxScore)

        fun version(
            musicId: Int = 363,
            level: Int = 1,
            playCount: Int = 1,
            achievement: Int = 114,
            dxScore: Int = 1,
        ): MusicDetail = MusicDetail(musicId, level, playCount, achievement, deluxscoreMax = dxScore)

        fun chargeTicket(): MusicDetail = MusicDetail(17, 0, 1, 100_0000, 0)
    }
}
