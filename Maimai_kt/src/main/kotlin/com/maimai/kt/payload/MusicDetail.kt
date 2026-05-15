package com.maimai.kt.payload

/** UpsertUserAll 和 playlog 中共用的音乐成绩结构。 */
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
            "musicId" to musicId,
            "level" to level,
            "playCount" to playCount,
            "achievement" to achievement,
            "comboStatus" to comboStatus,
            "syncStatus" to syncStatus,
            "deluxscoreMax" to deluxscoreMax,
            "scoreRank" to scoreRank,
            "extNum1" to extNum1,
        )

    companion object {
        /** 非成绩类动作使用的默认成绩，用来带起一次完整 UpsertUserAll。 */
        fun default(
            musicId: Int = 363,
            level: Int = 1,
            playCount: Int = 1,
            achievement: Int = 114,
            dxScore: Int = 1,
        ): MusicDetail = MusicDetail(musicId, level, playCount, achievement, deluxscoreMax = dxScore)
    }
}
