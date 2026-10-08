package kt.service

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kt.api.TitleApiClient
import kt.constants.ApiNames
import kt.constants.LogoutTypes
import kt.constants.PayloadKeys
import kt.constants.UserAllNodes
import kt.transport.JsonSupport

/**
 * 账号快照抓取：把账号的全部基线数据抓成一份 JSON。
 *
 * 逐条对齐 Python 版 `api/get_need_user_data.py`：
 *
 * ```
 * 二维码 -> 预览（顶号保护）-> 登录 -> 逐个抓 19 个节点 -> 登出(type=4)
 * ```
 *
 * 返回的结构和 Python 版 `data/<userId>.json` **同构**：
 * 节点名 -> 该接口的原始响应（个别节点是按多页/多类别合并后的结果），
 * 另外带 `_failed`（抓取失败的节点）和 `_userId`。
 *
 * ★ 抓取的字段一律保留服务端原文 —— 回传 UpsertUserAll 需要响应里的全部字段，
 *   映射成 data class 会丢东西，所以这里只做「合并 / 摊平」，不做字段裁剪。
 */
class SnapshotCaptureService(
    private val api: TitleApiClient,
    private val sessions: SessionService,
) {

    /** 抓取进度回调。 */
    interface Listener {
        /** 一条过程日志。 */
        fun onLog(message: String)

        /** 正在抓第 index/total 个节点。 */
        fun onNode(node: String, index: Int, total: Int)
    }

    /** 什么都不做的实现，给不关心进度的调用方用。 */
    object NoListener : Listener {
        override fun onLog(message: String) = Unit
        override fun onNode(node: String, index: Int, total: Int) = Unit
    }

    /** 抓取结果。 */
    data class CaptureResult(
        val userId: Long,
        val snapshot: MutableMap<String, Any?>,
        /** 抓取失败的节点名。 */
        val failedNodes: List<String>,
    )

    /**
     * 用**源账号**的二维码联网抓一份完整快照，并自动登出。
     *
     * 对齐 Python 版 `reincarnate.py` 的 `fetch_source_by_qr`：
     * 先看预览里的 isLogin（账号在机台上就不抢），抓完用 **type=4（TestIn）** 登出。
     *
     * @throws kt.error.MaimaiLoginException 二维码过期 / 解析或登录失败
     * @throws IllegalStateException 源账号正在机台上游玩
     */
    suspend fun captureByQr(qr: String, listener: Listener = NoListener): CaptureResult {
        listener.onLog("解析源账号二维码 ...")
        val resolved = sessions.resolveByQr(qr)
        if (resolved.isLoggedIn) {
            throw IllegalStateException("源账号处于已登录状态，请先在机台登出或等待会话超时")
        }

        listener.onLog("登录源账号 ...")
        val session = sessions.loginResolved(resolved)
        try {
            val snapshot = capture(session.userId, session.cookie, listener)
            return CaptureResult(session.userId, snapshot, failedNodesOf(snapshot))
        } finally {
            // ★ 取消 / 异常都要登出，否则会话挂在服务端会顶号，所以跑在 NonCancellable 里
            withContext(NonCancellable) {
                try {
                    sessions.logout(
                        userId = session.userId,
                        cookie = session.cookie,
                        type = LogoutTypes.TEST_IN,
                    )
                } catch (error: Throwable) {
                    listener.onLog(
                        "[收尾] 源账号登出失败（会话可能需等待服务端超时）：" +
                                (error.message ?: error::class.java.simpleName)
                    )
                }
            }
        }
    }

    /**
     * 抓取 [nodes]（默认 [UserAllNodes.SNAPSHOT_NODES] 全部 19 个）节点。
     *
     * 单个节点失败不中断整体（对齐 Python 的 `strict=False`），失败项记进 `_failed`。
     *
     * @param cookie 已登录会话的 cookie（JSESSIONID）
     * @param nodes 要抓的节点名；目标账号只需抓算位串用到的那几个
     */
    suspend fun capture(
        userId: Long,
        cookie: Map<String, String>,
        listener: Listener = NoListener,
        nodes: List<String> = UserAllNodes.SNAPSHOT_NODES,
    ): MutableMap<String, Any?> {
        val raw = mutableMapOf<String, Any?>()
        val failed = mutableListOf<Any?>()

        nodes.forEachIndexed { index, node ->
            listener.onNode(node, index + 1, nodes.size)

            if (node == PayloadKeys.USER_SHOP_ITEM_STOCK_LIST && SHOP_ITEM_IDS.isEmpty()) {
                // 对齐 Python：没配置商品清单就不抓这个节点（首包会因此不带商店库存）
                listener.onLog("未配置商店商品清单，跳过节点 $node")
                return@forEachIndexed
            }

            try {
                val value = fetchNode(node, userId, cookie, listener)
                raw[node] = value
                if (node == PayloadKeys.USER_FAVORITE_LIST) {
                    // 收藏夹额外留一份**原始**副本，供 Upsert 差分算位串用
                    val favorites = value as? Map<*, *>
                    val rawItems = favorites?.get(PayloadKeys.RAW) as? List<*>
                    if (!rawItems.isNullOrEmpty()) {
                        raw[PayloadKeys.RAW_FAVORITE] = rawItems
                    }
                }
            } catch (error: Throwable) {
                val detail = error::class.java.simpleName + ": " +
                        (error.message ?: "")
                failed += mapOf("node" to node, "error" to detail)
                listener.onLog("节点 $node 抓取失败：$detail")
            }
        }

        raw[PayloadKeys.FAILED] = failed
        raw[PayloadKeys.SNAPSHOT_USER_ID] = userId
        listener.onLog(
            "快照抓取完成：成功 ${nodes.size - failed.size} 个节点，失败 ${failed.size} 个"
        )
        return raw
    }

    // =========================================================================
    // 逐个节点
    // =========================================================================

    /** 抓一个节点。分页 / 多类别 / 摊平的差异都在这里。 */
    private suspend fun fetchNode(
        node: String,
        userId: Long,
        cookie: Map<String, String>,
        listener: Listener,
    ): Any? = when (node) {
        PayloadKeys.USER_MAP_LIST -> mapOf(
            node to getAllPages(
                apiName = nodeApi("Map"),
                userId = userId,
                listKey = node,
                cookie = cookie,
                listener = listener,
                body = { next -> pageBody(userId, next, MAP_PAGE_SIZE) },
            )
        )

        PayloadKeys.USER_LOGIN_BONUS_LIST -> mapOf(
            node to getAllPages(
                apiName = nodeApi("LoginBonus"),
                userId = userId,
                listKey = node,
                cookie = cookie,
                listener = listener,
                body = { next -> pageBody(userId, next, LOGIN_BONUS_PAGE_SIZE) },
            )
        )

        PayloadKeys.USER_ITEM_LIST -> mapOf(
            node to fetchAllItems(userId, cookie, listener)
        )

        PayloadKeys.USER_MUSIC_DETAIL_LIST -> mapOf(
            PayloadKeys.USER_MUSIC_LIST to fetchAllMusicDetails(userId, cookie, listener)
        )

        // ★ 课题模式的请求体只有 userId + nextIndex，**没有 maxCount**
        PayloadKeys.USER_COURSE_LIST -> mapOf(
            node to getAllPages(
                apiName = nodeApi("Course"),
                userId = userId,
                listKey = node,
                cookie = cookie,
                listener = listener,
                body = { next ->
                    mapOf(
                        PayloadKeys.USER_ID to userId,
                        PayloadKeys.NEXT_INDEX to next
                    )
                },
            )
        )

        PayloadKeys.USER_FAVORITE_LIST -> fetchFavorites(userId, cookie)

        PayloadKeys.USER_FAVORITE_MUSIC_LIST -> mapOf(
            PayloadKeys.USER_FAVORITE_ITEM_LIST to getAllPages(
                apiName = nodeApi("FavoriteItem"),
                userId = userId,
                listKey = PayloadKeys.USER_FAVORITE_ITEM_LIST,
                cookie = cookie,
                listener = listener,
                body = { next ->
                    mapOf(
                        PayloadKeys.USER_ID to userId,
                        // ★ 字段名是 kind，不是 itemKind
                        PayloadKeys.KIND to FAVORITE_ITEM_KIND_MUSIC,
                        PayloadKeys.NEXT_INDEX to next,
                        PayloadKeys.MAX_COUNT to FAVORITE_ITEM_PAGE_SIZE,
                        PayloadKeys.IS_ALL_FAVORITE_ITEM to false,
                    )
                },
            )
        )

        PayloadKeys.USER_SHOP_ITEM_STOCK_LIST -> get(
            thing = "ShopStock",
            userId = userId,
            cookie = cookie,
            extra = mapOf(PayloadKeys.SHOP_ITEM_ID_LIST to SHOP_ITEM_IDS),
        )

        else -> {
            val thing = THING_BY_NODE[node]
                ?: throw IllegalStateException("没有为节点 $node 配置取数方式")
            get(thing, userId, cookie)
        }
    }

    /** 单次请求的节点（userId 之外没有别的参数）。 */
    private suspend fun get(
        thing: String,
        userId: Long,
        cookie: Map<String, String>,
        extra: Map<String, Any?> = emptyMap(),
    ): MutableMap<String, Any?> = api.request(
        nodeApi(thing),
        mapOf(PayloadKeys.USER_ID to userId) + extra,
        userId,
        cookie,
    )

    /**
     * 按 itemKind 逐个类别翻页抓全部道具。
     *
     * ★ 首页的 nextIndex 是 `itemKind * 1e10` 这个哨兵值（真机抓包就是 10000000000），
     *   传 0 会被服务端拒绝。数值超出 Int 范围，必须用 Long。
     */
    private suspend fun fetchAllItems(
        userId: Long,
        cookie: Map<String, String>,
        listener: Listener,
    ): List<Any?> {
        val merged = mutableListOf<Any?>()

        ITEM_KINDS.forEach { kind ->
            var nextIndex: Long? = kind * ITEM_NEXT_INDEX_FACTOR
            var pages = 0
            while (nextIndex != null && nextIndex != 0L) {
                if (pages >= MAX_PAGES) {
                    listener.onLog("道具类别 $kind 超过 $MAX_PAGES 页仍未结束，已中止翻页")
                    break
                }
                val response = get(
                    thing = "Item",
                    userId = userId,
                    cookie = cookie,
                    extra = mapOf(
                        PayloadKeys.NEXT_INDEX to nextIndex,
                        PayloadKeys.MAX_COUNT to ITEM_PAGE_SIZE,
                    ),
                )
                // 响应的 userItemList 可能是数组，也可能是单个对象（Python 同样按 [items] 处理）
                when (val items = response[PayloadKeys.USER_ITEM_LIST]) {
                    is List<*> -> merged.addAll(items)
                    is Map<*, *> -> merged.add(items)
                    else -> Unit
                }
                nextIndex = (response[PayloadKeys.NEXT_INDEX] as? Number)?.toLong()
                pages++
            }
        }

        listener.onLog("道具获取完成：${merged.size} 条")
        return merged
    }

    /**
     * 曲目成绩：翻页取全并**摊平成单条**（对齐 Python `GetUserMusicDetailAll`）。
     *
     * 接口原始响应是两层嵌套（外层按曲分组，内层才是 (musicId, level) 条目），
     * 存盘时摊平成一层，和 Python 落盘的形态保持一致。
     */
    private suspend fun fetchAllMusicDetails(
        userId: Long,
        cookie: Map<String, String>,
        listener: Listener,
    ): List<Any?> {
        val groups = getAllPages(
            apiName = ApiNames.GET_USER_MUSIC,
            userId = userId,
            listKey = PayloadKeys.USER_MUSIC_LIST,
            cookie = cookie,
            listener = listener,
            body = { next -> pageBody(userId, next, MUSIC_PAGE_SIZE) },
        )

        val flat = mutableListOf<Any?>()
        groups.forEach { group ->
            when (group) {
                is Map<*, *> -> when (val details = group[PayloadKeys.USER_MUSIC_DETAIL_LIST]) {
                    is List<*> -> flat.addAll(details)
                    is Map<*, *> -> flat.add(details)
                    else -> Unit
                }

                is List<*> -> flat.addAll(group)
                else -> Unit
            }
        }

        listener.onLog("曲目成绩获取完成：${flat.size} 条（原始 ${groups.size} 组）")
        return flat
    }

    /**
     * 收藏夹：按真机顺序逐类别请求（3/1/2/10/11），返回恒 5 条。
     *
     * ★ 真机上 itemKind=10 的响应回的是 `{"itemKind":null,...}`，
     *   回传的副本要按请求值补齐 itemKind，同时留一份原始副本给位串用。
     */
    private suspend fun fetchFavorites(
        userId: Long,
        cookie: Map<String, String>,
    ): Map<String, Any?> {
        val result = mutableListOf<Any?>()
        val rawItems = mutableListOf<Any?>()

        FAVORITE_ITEM_KINDS.forEach { kind ->
            val response = get("Favorite", userId, cookie, mapOf(PayloadKeys.ITEM_KIND to kind))
            val favorite = toMap(response[PayloadKeys.USER_FAVORITE])
                .ifEmpty {
                    mutableMapOf(
                        PayloadKeys.ITEM_KIND to kind,
                        PayloadKeys.ITEM_ID_LIST to emptyList<Any?>(),
                    )
                }

            rawItems += toMap(favorite)

            val filled = toMap(favorite)
            if (isFalsy(filled[PayloadKeys.ITEM_KIND])) {
                filled[PayloadKeys.ITEM_KIND] = kind
            }
            result += filled
        }

        return mapOf(
            PayloadKeys.USER_FAVORITE_LIST to result,
            PayloadKeys.RAW to rawItems,
        )
    }

    // =========================================================================
    // 翻页
    // =========================================================================

    /**
     * 自动翻页取全某个列表接口。
     *
     * 分页约定（对齐 Python `get_all_pages`）：`nextIndex == 0` 表示没有下一页。
     */
    private suspend fun getAllPages(
        apiName: String,
        userId: Long,
        listKey: String,
        cookie: Map<String, String>,
        listener: Listener,
        body: (Long) -> Map<String, Any?>,
    ): List<Any?> {
        val all = mutableListOf<Any?>()
        var nextIndex = 0L

        repeat(MAX_PAGES) { page ->
            val response = api.request(apiName, body(nextIndex), userId, cookie)
            when (val items = response[listKey]) {
                is List<*> -> all.addAll(items)
                is Map<*, *> -> all.add(items)      // 单条也要收，对齐 Python 的 [items]
                null -> Unit
                else -> Unit
            }

            nextIndex = (response[PayloadKeys.NEXT_INDEX] as? Number)?.toLong() ?: 0L
            if (nextIndex == 0L) {
                return all
            }
            if (page == MAX_PAGES - 1) {
                listener.onLog("$apiName 超过 $MAX_PAGES 页仍未结束，已中止翻页")
            }
        }

        return all
    }

    private fun pageBody(userId: Long, nextIndex: Long, maxCount: Int): Map<String, Any?> =
        mapOf(
            PayloadKeys.USER_ID to userId,
            PayloadKeys.NEXT_INDEX to nextIndex,
            PayloadKeys.MAX_COUNT to maxCount,
        )

    // =========================================================================
    // 工具
    // =========================================================================

    /** 节点名 -> 接口名（`GetUser<thing>Api`）。 */
    private fun nodeApi(thing: String): String =
        "${ApiNames.GET_USER_PREFIX}$thing${ApiNames.API_SUFFIX}"

    @Suppress("UNCHECKED_CAST")
    private fun toMap(value: Any?): MutableMap<String, Any?> =
        if (value is Map<*, *>) {
            JsonSupport.parseObject(JsonSupport.stringify(value))
        } else {
            mutableMapOf()
        }

    /**
     * Python `if not value` 的等价物：null / false / 0 / "" 都算「没值」。
     *
     * 对齐 `GetUserFavoriteAll` 的 `if not filled.get("itemKind")`。
     */
    private fun isFalsy(value: Any?): Boolean = when (value) {
        null, false -> true
        is Number -> value.toInt() == 0
        is String -> value.isEmpty()
        else -> false
    }

    /** 从快照里取出抓取失败的节点名。 */
    private fun failedNodesOf(snapshot: Map<String, Any?>): List<String> =
        (snapshot[PayloadKeys.FAILED] as? List<*>)
            ?.mapNotNull { (it as? Map<*, *>)?.get("node")?.toString() }
            ?: emptyList()

    companion object {
        /** 单次抓取的节点：节点名 -> `GetUser<thing>Api` 的后缀。 */
        private val THING_BY_NODE = mapOf(
            PayloadKeys.USER_DATA to "Data",
            PayloadKeys.USER_EXTEND to "Extend",
            PayloadKeys.USER_OPTION to "Option",
            PayloadKeys.USER_CHARACTER_LIST to "Character",
            PayloadKeys.USER_RATING_LIST to "Rating",
            PayloadKeys.USER_CHARGE_LIST to "Charge",
            PayloadKeys.USER_ACTIVITY_LIST to "Activity",
            PayloadKeys.USER_MISSION_DATA_LIST to "MissionData",
            PayloadKeys.USER_INTIMATE_LIST to "Intimate",
            PayloadKeys.USER_KALEIDX_SCOPE_LIST to "KaleidxScope",
            PayloadKeys.USER_GHOST to "Ghost",
        )

        /** GetUserItemApi 的类别（对应 ItemKind，真机依次下载这些）。 */
        private val ITEM_KINDS = listOf(1, 2, 3, 4, 5, 6, 7, 8, 10, 11, 12, 14)

        /**
         * ★ 道具首页 nextIndex 的哨兵系数。
         *
         * 依据真机抓包（PacketGetUserItem 里是 `itemId * 10000000000L`），传 0 会被服务端拒绝。
         */
        private const val ITEM_NEXT_INDEX_FACTOR = 10000000000L

        /** GetUserFavoriteApi 的类别（真机顺序固定 3/1/2/10/11）。 */
        private val FAVORITE_ITEM_KINDS = listOf(3, 1, 2, 10, 11)

        /** 收藏曲目（GetUserFavoriteItemApi 的 kind）。 */
        private const val FAVORITE_ITEM_KIND_MUSIC = 1

        /**
         * 商店商品清单。
         *
         * 这些 id 来自游戏 Master 数据，脚本无法自行推导；不配就跳过商店库存节点
         * （Python 侧 `config.PLAY_SHOP_ITEM_IDS` 默认也是空列表）。
         */
        private val SHOP_ITEM_IDS: List<Long> = emptyList()

        private const val MAP_PAGE_SIZE = 1000
        private const val LOGIN_BONUS_PAGE_SIZE = 20
        private const val ITEM_PAGE_SIZE = 100
        private const val MUSIC_PAGE_SIZE = 50
        private const val FAVORITE_ITEM_PAGE_SIZE = 100

        /** 翻页上限，防御性（对齐 Python `get_all_pages` 的 max_pages）。 */
        private const val MAX_PAGES = 200
    }
}
