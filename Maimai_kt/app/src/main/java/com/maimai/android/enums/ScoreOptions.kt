package com.maimai.android.enums

import androidx.annotation.StringRes
import com.maimai.android.R

/**
 * 上传成绩弹窗里 Spinner 选项的通用接口。
 *
 * labelRes 用于界面显示，apiValue 用于最终传给 maimai API。
 */
interface ScoreOption {
    val apiValue: Int

    @get:StringRes
    val labelRes: Int
}

/**
 * 歌曲难度枚举。
 */
enum class ScoreLevel(
    override val apiValue: Int,
    override val labelRes: Int,
) : ScoreOption {
    Basic(0, R.string.score_level_basic),
    Advanced(1, R.string.score_level_advanced),
    Expert(2, R.string.score_level_expert),
    Master(3, R.string.score_level_master),
    ReMaster(4, R.string.score_level_remaster),
    Strong(5, R.string.score_level_strong),
    Utage(10, R.string.score_level_utage),
}

/**
 * 连击状态枚举。
 */
enum class ComboStatus(
    override val apiValue: Int,
    override val labelRes: Int,
) : ScoreOption {
    None(0, R.string.combo_status_none),
    FullCombo(1, R.string.combo_status_full_combo),
    FullComboPlus(2, R.string.combo_status_full_combo_plus),
    AllPerfect(3, R.string.combo_status_all_perfect),
    AllPerfectPlus(4, R.string.combo_status_all_perfect_plus),
}

/**
 * 同步状态枚举。
 */
enum class SyncStatus(
    override val apiValue: Int,
    override val labelRes: Int,
) : ScoreOption {
    None(0, R.string.sync_status_none),
    FullSync(1, R.string.sync_status_full_sync),
    FullSyncPlus(2, R.string.sync_status_full_sync_plus),
    FullSyncDx(3, R.string.sync_status_full_sync_dx),
    FullSyncDxPlus(4, R.string.sync_status_full_sync_dx_plus),
}

/**
 * 评级枚举。
 */
enum class ScoreRank(
    val apiValue: Int,
    val minAchievement: Int,
) {
    Rank_D(0, 0),
    Rank_C(1, 50_0000),
    Rank_B(2, 60_0000),
    Rank_BB(3, 70_0000),
    Rank_BBB(4, 75_0000),
    Rank_A(5, 80_0000),
    Rank_AA(6, 90_0000),
    Rank_AAA(7, 94_0000),
    Rank_S(8, 97_0000),
    Rank_SP(9, 98_0000),
    Rank_SS(10, 99_0000),
    Rank_SSP(11, 99_5000),
    Rank_SSS(12, 100_0000),
    Rank_SSSP(13, 100_5000);

    companion object {
        /**
         * 根据 achievement 自动计算评级。
         *
         * 当前上传弹窗中 achievement 的计算方式是：
         * 整数部分 * 10000 + 小数部分。
         */
        fun fromAchievement(achievement: Int): ScoreRank =
            values()
                .filter { achievement >= it.minAchievement }
                .maxByOrNull { it.minAchievement }
                ?: Rank_D
    }
}
