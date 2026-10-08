package kt.constants

/**
 * UpsertUserAll 的协议表：节点形态/顺序、位串对照、增量主键、字段裁剪表。
 *
 * 逐条对齐 Python 版 `api/upsert_user_all.py`，改动两处必须同步。
 * 这里只放「协议常量」，不含业务流程 —— 迁移逻辑见 [kt.service.ReincarnateService]。
 */
object UserAllNodes {

    /** 单元素数组：值恒为 `listOf(单个对象)`。 */
    const val KIND_SINGLETON = "singleton"

    /** ★ 单个对象，不是数组（userWeeklyData / user2pPlaylog）。 */
    const val KIND_OBJECT = "object"

    /** 普通数组。 */
    const val KIND_ARRAY = "array"

    /** 字符串位串，长度 == 对应数组条数。 */
    const val KIND_BITS = "bits"

    /**
     * 节点名 -> 形态。
     *
     * 顺序即报文序列化顺序（照 `UserAll.cs` 的字段声明顺序），
     * 与真机报文 `app/src/upsert.json` 的键序一致。
     */
    val TYPES: Map<String, String> = linkedMapOf(
        // ---- 单元素数组（4）----
        PayloadKeys.USER_DATA to KIND_SINGLETON,
        PayloadKeys.USER_EXTEND to KIND_SINGLETON,
        PayloadKeys.USER_OPTION to KIND_SINGLETON,
        PayloadKeys.USER_RATING_LIST to KIND_SINGLETON,
        // ---- 对象，非数组（2）★ ----
        PayloadKeys.USER_WEEKLY_DATA to KIND_OBJECT,
        PayloadKeys.USER_2P_PLAYLOG to KIND_OBJECT,
        // ---- 普通数组（19）----
        PayloadKeys.USER_CHARACTER_LIST to KIND_ARRAY,
        PayloadKeys.USER_GHOST to KIND_ARRAY,
        PayloadKeys.USER_MAP_LIST to KIND_ARRAY,
        PayloadKeys.USER_LOGIN_BONUS_LIST to KIND_ARRAY,
        PayloadKeys.USER_ITEM_LIST to KIND_ARRAY,
        PayloadKeys.USER_MUSIC_DETAIL_LIST to KIND_ARRAY,
        PayloadKeys.USER_COURSE_LIST to KIND_ARRAY,
        PayloadKeys.USER_FRIEND_SEASON_RANKING_LIST to KIND_ARRAY,
        PayloadKeys.USER_CHARGE_LIST to KIND_ARRAY,
        PayloadKeys.USER_FAVORITE_LIST to KIND_ARRAY,
        PayloadKeys.USER_ACTIVITY_LIST to KIND_ARRAY,
        PayloadKeys.USER_MISSION_DATA_LIST to KIND_ARRAY,
        PayloadKeys.USER_GAME_PLAYLOG_LIST to KIND_ARRAY,
        PayloadKeys.USER_INTIMATE_LIST to KIND_ARRAY,
        PayloadKeys.USER_SHOP_ITEM_STOCK_LIST to KIND_ARRAY,
        PayloadKeys.USER_GET_POINT_LIST to KIND_ARRAY,
        PayloadKeys.USER_TRADE_ITEM_LIST to KIND_ARRAY,
        PayloadKeys.USER_FAVORITE_MUSIC_LIST to KIND_ARRAY,
        PayloadKeys.USER_KALEIDX_SCOPE_LIST to KIND_ARRAY,
        // ---- 位串（11）----
        PayloadKeys.IS_NEW_CHARACTER_LIST to KIND_BITS,
        PayloadKeys.IS_NEW_MAP_LIST to KIND_BITS,
        PayloadKeys.IS_NEW_LOGIN_BONUS_LIST to KIND_BITS,
        PayloadKeys.IS_NEW_ITEM_LIST to KIND_BITS,
        PayloadKeys.IS_NEW_MUSIC_DETAIL_LIST to KIND_BITS,
        PayloadKeys.IS_NEW_COURSE_LIST to KIND_BITS,
        PayloadKeys.IS_NEW_FAVORITE_LIST to KIND_BITS,
        PayloadKeys.IS_NEW_FRIEND_SEASON_RANKING_LIST to KIND_BITS,
        PayloadKeys.IS_NEW_USER_INTIMATE_LIST to KIND_BITS,
        PayloadKeys.IS_NEW_FAVORITE_MUSIC_LIST to KIND_BITS,
        PayloadKeys.IS_NEW_KALEIDX_SCOPE_LIST to KIND_BITS,
    )

    /** 报文里的节点顺序，共 36 个。 */
    val ORDER: List<String> = TYPES.keys.toList()

    /** 位串 -> 对应数组。 */
    val BITS_TO_ARRAY: Map<String, String> = mapOf(
        PayloadKeys.IS_NEW_CHARACTER_LIST to PayloadKeys.USER_CHARACTER_LIST,
        PayloadKeys.IS_NEW_MAP_LIST to PayloadKeys.USER_MAP_LIST,
        PayloadKeys.IS_NEW_LOGIN_BONUS_LIST to PayloadKeys.USER_LOGIN_BONUS_LIST,
        PayloadKeys.IS_NEW_ITEM_LIST to PayloadKeys.USER_ITEM_LIST,
        PayloadKeys.IS_NEW_MUSIC_DETAIL_LIST to PayloadKeys.USER_MUSIC_DETAIL_LIST,
        PayloadKeys.IS_NEW_COURSE_LIST to PayloadKeys.USER_COURSE_LIST,
        PayloadKeys.IS_NEW_FAVORITE_LIST to PayloadKeys.USER_FAVORITE_LIST,
        PayloadKeys.IS_NEW_FRIEND_SEASON_RANKING_LIST to PayloadKeys.USER_FRIEND_SEASON_RANKING_LIST,
        PayloadKeys.IS_NEW_USER_INTIMATE_LIST to PayloadKeys.USER_INTIMATE_LIST,
        PayloadKeys.IS_NEW_FAVORITE_MUSIC_LIST to PayloadKeys.USER_FAVORITE_MUSIC_LIST,
        PayloadKeys.IS_NEW_KALEIDX_SCOPE_LIST to PayloadKeys.USER_KALEIDX_SCOPE_LIST,
    )

    /** 数组 -> 位串（[BITS_TO_ARRAY] 的反向表）。 */
    val ARRAY_TO_BITS: Map<String, String> =
        BITS_TO_ARRAY.entries.associate { (bits, array) -> array to bits }

    /**
     * 需要算差分的节点 -> 主键字段。
     *
     * 判断依据是真机 Get 侧与 Upsert 侧的条数对照：曲目 739 -> 1、角色 312 -> 5，
     * 说明这些节点是「只回传变化的那几条」；而 userChargeList 是 3 -> 3，整表回传。
     */
    val DIFF_PRIMARY_KEYS: Map<String, List<String>> = mapOf(
        PayloadKeys.USER_CHARACTER_LIST to listOf("characterId"),
        PayloadKeys.USER_MAP_LIST to listOf("mapId"),
        PayloadKeys.USER_LOGIN_BONUS_LIST to listOf("bonusId"),
        PayloadKeys.USER_ITEM_LIST to listOf("itemKind", "itemId"),
        PayloadKeys.USER_MUSIC_DETAIL_LIST to listOf("musicId", "level"),
        PayloadKeys.USER_COURSE_LIST to listOf("courseId"),
        PayloadKeys.USER_FRIEND_SEASON_RANKING_LIST to listOf("seasonId"),
        PayloadKeys.USER_INTIMATE_LIST to listOf("partnerId"),
        PayloadKeys.USER_KALEIDX_SCOPE_LIST to listOf("gateId"),
        PayloadKeys.USER_SHOP_ITEM_STOCK_LIST to listOf("shopItemId"),
        PayloadKeys.USER_FAVORITE_MUSIC_LIST to listOf("musicId"),
    )

    /** 整表回传、不做差分的节点（当差分处理会被误收敛成空数组）。 */
    val FULL_TABLE_NODES: List<String> = listOf(PayloadKeys.USER_CHARGE_LIST)

    /** 无变化时可以留空的节点（服务端接受空数组）。 */
    val MAY_BE_EMPTY_NODES: List<String> = listOf(
        PayloadKeys.USER_GHOST,
        PayloadKeys.USER_MAP_LIST,
        PayloadKeys.USER_LOGIN_BONUS_LIST,
        PayloadKeys.USER_ITEM_LIST,
        PayloadKeys.USER_MUSIC_DETAIL_LIST,
        PayloadKeys.USER_COURSE_LIST,
        PayloadKeys.USER_FRIEND_SEASON_RANKING_LIST,
        PayloadKeys.USER_CHARGE_LIST,
        PayloadKeys.USER_ACTIVITY_LIST,
        PayloadKeys.USER_MISSION_DATA_LIST,
        PayloadKeys.USER_INTIMATE_LIST,
        PayloadKeys.USER_SHOP_ITEM_STOCK_LIST,
        PayloadKeys.USER_GET_POINT_LIST,
        PayloadKeys.USER_TRADE_ITEM_LIST,
        PayloadKeys.USER_FAVORITE_MUSIC_LIST,
        PayloadKeys.USER_KALEIDX_SCOPE_LIST,
        PayloadKeys.USER_CHARACTER_LIST,
        PayloadKeys.USER_RATING_LIST,
    )

    /** 默认恒空的节点（普通游玩结算不产生这些数据）。 */
    val DEFAULT_EMPTY_NODES: List<String> = listOf(
        PayloadKeys.USER_GHOST,
        PayloadKeys.USER_GET_POINT_LIST,
        PayloadKeys.USER_LOGIN_BONUS_LIST,
        PayloadKeys.USER_TRADE_ITEM_LIST,
        PayloadKeys.USER_FRIEND_SEASON_RANKING_LIST,
        PayloadKeys.USER_FAVORITE_MUSIC_LIST,
    )

    // =========================================================================
    // 字段裁剪表（Get 侧 -> Upsert 侧要丢掉的字段）
    // =========================================================================
    // 依据：把真机 Get 侧响应与 Upsert 侧报文逐字段比对得出。

    /** userData：Get 侧有、Upsert 侧没有的字段。 */
    val USER_DATA_DROP_KEYS: List<String> = listOf(
        "friendCode", "nameplateId", "trophyId",
        "cmLastEmoneyCredit", "cmLastEmoneyBrand",
    )

    /** userOption：Upsert 侧没有 tempoVolume。 */
    val USER_OPTION_DROP_KEYS: List<String> = listOf("tempoVolume")

    /** ★ userExtend：Upsert 侧**多出**这两个字段（Get 侧没有），回传时必须补上。 */
    const val IS_PHOTO_AGREE = "isPhotoAgree"
    const val IS_GOTO_CODE_READ = "isGotoCodeRead"

    /** Get 侧响应里 userData 的外层键（banState 在 Get 侧是顶层字段）。 */
    const val BAN_STATE = "banState"

    /**
     * ★ userRating.udemae：服务端 Get 响应里有一批首字母大写的重复字段，
     * 而真机 Upsert 报文里只有小写的 16 键，回传时必须裁掉。
     */
    val USER_RATING_UDEMAE_DROP_KEYS: List<String> = listOf(
        "MaxLoseNum", "NpcTotalWinNum", "NpcTotalLoseNum",
        "NpcMaxWinNum", "NpcMaxLoseNum", "NpcWinNum", "NpcLoseNum",
    )

    /** userCharacterList：只保留这 4 个字段。 */
    val USER_CHARACTER_KEEP_KEYS: List<String> = listOf(
        PayloadKeys.CHARACTER_ID,
        PayloadKeys.LEVEL,
        PayloadKeys.AWAKENING,
        PayloadKeys.USE_COUNT,
    )

    /** userMusicDetailList：丢掉 extNum2。 */
    val USER_MUSIC_DETAIL_DROP_KEYS: List<String> = listOf(PayloadKeys.EXT_NUM_2)

    /** userChargeList：丢掉 extNum1。 */
    val USER_CHARGE_DROP_KEYS: List<String> = listOf(PayloadKeys.EXT_NUM_1)

    /** userMissionDataList / userWeeklyData：丢掉 userId。 */
    val USER_MISSION_DROP_KEYS: List<String> = listOf(PayloadKeys.USER_ID)

    // =========================================================================
    // 快照抓取节点（对齐 Python GetNeedUserData 的 DEFAULT_SNAPSHOT_NODES）
    // =========================================================================

    /** 节点名 -> GetUser*Api 的后缀（`thing`），用于分节点抓取。 */
    val SNAPSHOT_NODES: List<String> = listOf(
        PayloadKeys.USER_DATA,
        PayloadKeys.USER_EXTEND,
        PayloadKeys.USER_OPTION,
        PayloadKeys.USER_CHARACTER_LIST,
        PayloadKeys.USER_MAP_LIST,
        PayloadKeys.USER_LOGIN_BONUS_LIST,
        PayloadKeys.USER_RATING_LIST,
        PayloadKeys.USER_ITEM_LIST,
        PayloadKeys.USER_MUSIC_DETAIL_LIST,
        PayloadKeys.USER_COURSE_LIST,
        PayloadKeys.USER_CHARGE_LIST,
        PayloadKeys.USER_FAVORITE_LIST,
        PayloadKeys.USER_ACTIVITY_LIST,
        PayloadKeys.USER_MISSION_DATA_LIST,
        PayloadKeys.USER_INTIMATE_LIST,
        PayloadKeys.USER_SHOP_ITEM_STOCK_LIST,
        PayloadKeys.USER_FAVORITE_MUSIC_LIST,
        PayloadKeys.USER_KALEIDX_SCOPE_LIST,
        PayloadKeys.USER_GHOST,
    )

    /**
     * 节点名 -> Get 响应里实际装列表的键名。
     *
     * 多数节点同名，但曲目 / 收藏 / 商店 / 里程的键名不一致，见 Python 版 `array_map`。
     */
    val SNAPSHOT_LIST_KEYS: Map<String, String> = mapOf(
        PayloadKeys.USER_CHARACTER_LIST to PayloadKeys.USER_CHARACTER_LIST,
        PayloadKeys.USER_GHOST to "userGhostList",
        PayloadKeys.USER_MAP_LIST to PayloadKeys.USER_MAP_LIST,
        PayloadKeys.USER_LOGIN_BONUS_LIST to PayloadKeys.USER_LOGIN_BONUS_LIST,
        PayloadKeys.USER_ITEM_LIST to PayloadKeys.USER_ITEM_LIST,
        PayloadKeys.USER_MUSIC_DETAIL_LIST to PayloadKeys.USER_MUSIC_LIST,
        PayloadKeys.USER_COURSE_LIST to PayloadKeys.USER_COURSE_LIST,
        PayloadKeys.USER_CHARGE_LIST to PayloadKeys.USER_CHARGE_LIST,
        PayloadKeys.USER_FAVORITE_LIST to "userFavorite",
        PayloadKeys.USER_ACTIVITY_LIST to "userActivity",
        PayloadKeys.USER_MISSION_DATA_LIST to PayloadKeys.USER_MISSION_DATA_LIST,
        PayloadKeys.USER_INTIMATE_LIST to PayloadKeys.USER_INTIMATE_LIST,
        PayloadKeys.USER_SHOP_ITEM_STOCK_LIST to "userShopStockList",
        PayloadKeys.USER_FAVORITE_MUSIC_LIST to "userFavoriteItemList",
        PayloadKeys.USER_KALEIDX_SCOPE_LIST to PayloadKeys.USER_KALEIDX_SCOPE_LIST,
        PayloadKeys.USER_FRIEND_SEASON_RANKING_LIST to PayloadKeys.USER_FRIEND_SEASON_RANKING_LIST,
    )
}
