package kt.payload

import kt.constants.PayloadKeys
import kt.constants.UserAllNodes
import kt.transport.JsonSupport

/**
 * 把各种形态的账号快照统一成「节点字典」（节点名 -> 节点值）。
 *
 * 逐条对齐 Python 版 `api/upsert_user_all.py` 的 `normalize_snapshot`。
 *
 * 接受的形态：
 * - 节点字典（36 个节点名）—— 原样做一次形态强制
 * - `{"upsertUserAll": {...}}` —— 取内层
 * - `{"_responses": {...}}` —— 取 `_responses`
 * - 各 Get 接口的原始响应字典（也就是 Python 版 `data/<userId>.json` 的内容）
 */
object SnapshotNormalizer {

    /** 从 JSON 文本导入（剪贴板 / 文件）。 */
    fun normalizeJson(json: String): MutableMap<String, Any?> =
        normalize(JsonSupport.parseObject(json))

    /** 从已解析的对象导入。 */
    @Suppress("UNCHECKED_CAST")
    fun normalize(snapshot: Any?): MutableMap<String, Any?> {
        if (snapshot == null) {
            return mutableMapOf()
        }
        if (snapshot !is Map<*, *>) {
            throw IllegalArgumentException(
                "快照形态不认识：" + snapshot::class.java.simpleName
            )
        }

        var raw: Map<*, *> = snapshot
        (raw[PayloadKeys.UPSERT_USER_ALL] as? Map<*, *>)?.let { raw = it }
        (raw[RESPONSES_KEY] as? Map<*, *>)?.let { raw = it }

        // ★ 只按值的形态区分「节点字典」和「原始响应集合」：
        //   两者键名高度重叠（userData / userMapList … 既是节点名也是 Get 响应的外层键），
        //   但节点字典里 userData 是 list，原始响应里它是 dict。
        if (looksLikeNodeDict(raw)) {
            val out = mutableMapOf<String, Any?>()
            raw.forEach { (key, value) ->
                val node = key.toString()
                when {
                    UserAllNodes.TYPES.containsKey(node) -> out[node] = coerceNodeValue(node, value)
                    node.startsWith("_") -> out[node] = value
                }
            }
            return out
        }

        return extractFromGetResponses(raw)
    }

    /** 按节点形态返回空值。 */
    fun emptyNode(node: String): Any? = when (UserAllNodes.TYPES[node]) {
        UserAllNodes.KIND_BITS -> ""
        UserAllNodes.KIND_OBJECT -> mutableMapOf<String, Any?>()
        else -> mutableListOf<Any?>()
    }

    /**
     * 把值强制成该节点应有的形态；传错直接抛异常。
     *
     * ★ userWeeklyData / user2pPlaylog 传数组、userData 传 dict，
     *   都是很容易犯又很难查的错，所以这里刻意防呆。
     */
    fun coerceNodeValue(node: String, value: Any?): Any? {
        val kind = UserAllNodes.TYPES[node]
            ?: throw IllegalArgumentException("未知节点名：$node")

        if (value == null) {
            return emptyNode(node)
        }

        when (kind) {
            UserAllNodes.KIND_BITS ->
                require(value is String) { "节点 $node 是位串，必须传字符串" }

            UserAllNodes.KIND_OBJECT ->
                require(value is Map<*, *>) { "节点 $node 是对象（不是数组），必须传 JSON 对象" }

            UserAllNodes.KIND_SINGLETON,
            UserAllNodes.KIND_ARRAY,
            -> when (value) {
                is Map<*, *> -> return mutableListOf(value)
                is List<*> -> return value
                else -> throw IllegalArgumentException("节点 $node 必须是数组或对象")
            }
        }
        return value
    }

    // =========================================================================
    // 内部：形态判断
    // =========================================================================

    /**
     * 按值的形态判断是不是「已整理好的节点字典」。
     *
     * 节点名对应的值若符合 `TYPES` 声明的形态则 +1，反之 -1，总分 > 0 视为节点字典。
     */
    private fun looksLikeNodeDict(snapshot: Map<*, *>): Boolean {
        var score = 0
        snapshot.forEach { (key, value) ->
            when (UserAllNodes.TYPES[key.toString()]) {
                UserAllNodes.KIND_SINGLETON,
                UserAllNodes.KIND_ARRAY,
                -> score += if (value is List<*>) 1 else -1

                UserAllNodes.KIND_OBJECT -> score += if (value is Map<*, *>) 1 else -1
                UserAllNodes.KIND_BITS -> score += if (value is String) 1 else -1
            }
        }
        return score > 0
    }

    // =========================================================================
    // 内部：从各 Get 接口的原始响应里提取节点
    // =========================================================================

    /**
     * 把「各 Get 接口的原始响应」整理成节点字典（含字段裁剪）。
     *
     * 输入形如：
     * ```
     * {
     *   "userData":       <GetUserDataApi 的响应>,
     *   "userMapList":    <GetUserMapApi 的响应>,
     *   ...
     * }
     * ```
     */
    @Suppress("UNCHECKED_CAST")
    private fun extractFromGetResponses(raw: Map<*, *>): MutableMap<String, Any?> {
        val out = mutableMapOf<String, Any?>()

        // ---- 单元素数组 ----
        (raw[PayloadKeys.USER_DATA])?.let { src ->
            val ud = unwrap(src, PayloadKeys.USER_DATA)
            if (ud is Map<*, *>) {
                val data = stripKeys(ud, UserAllNodes.USER_DATA_DROP_KEYS)
                // banState 在 Get 侧是顶层字段，回传时要搬进 userData 内部
                val banState = (src as? Map<*, *>)?.get(UserAllNodes.BAN_STATE)
                if (banState != null && data[UserAllNodes.BAN_STATE] == null) {
                    data[UserAllNodes.BAN_STATE] = banState
                }
                out[PayloadKeys.USER_DATA] = mutableListOf<Any?>(data)
            }
        }

        (raw[PayloadKeys.USER_EXTEND])?.let { src ->
            val extend = unwrap(src, PayloadKeys.USER_EXTEND)
            if (extend is Map<*, *>) {
                out[PayloadKeys.USER_EXTEND] = mutableListOf<Any?>(deepCopy(extend))
            }
        }

        (raw[PayloadKeys.USER_OPTION])?.let { src ->
            val option = unwrap(src, PayloadKeys.USER_OPTION)
            if (option is Map<*, *>) {
                out[PayloadKeys.USER_OPTION] =
                    mutableListOf<Any?>(stripKeys(option, UserAllNodes.USER_OPTION_DROP_KEYS))
            }
        }

        (raw[PayloadKeys.USER_RATING_LIST])?.let { src ->
            val rating = unwrap(src, "userRating")
            if (rating is Map<*, *>) {
                val copy = deepCopy(rating)
                // ★ 裁掉 udemae 里服务端冗余的大写重复字段
                val udemae = copy["udemae"]
                if (udemae is Map<*, *>) {
                    copy["udemae"] =
                        stripKeys(udemae, UserAllNodes.USER_RATING_UDEMAE_DROP_KEYS)
                }
                out[PayloadKeys.USER_RATING_LIST] = mutableListOf<Any?>(copy)
            }
        }

        // ---- 对象 ----
        (raw[PayloadKeys.USER_WEEKLY_DATA])?.let { src ->
            val weekly = unwrap(src, PayloadKeys.USER_WEEKLY_DATA)
            if (weekly is Map<*, *>) {
                out[PayloadKeys.USER_WEEKLY_DATA] =
                    stripKeys(weekly, UserAllNodes.USER_MISSION_DROP_KEYS)
            }
        }

        // ★ userWeeklyData 的另一个来源：GetUserMissionDataApi 的响应里就带着它。
        //   真机值 = 服务器下发的周界，不是本地算的，所以优先用快照里的值。
        if (out[PayloadKeys.USER_WEEKLY_DATA] == null) {
            val mission = raw[PayloadKeys.USER_MISSION_DATA_LIST]
            val weekly = (mission as? Map<*, *>)?.get(PayloadKeys.USER_WEEKLY_DATA)
            if (weekly is Map<*, *> && weekly.isNotEmpty()) {
                out[PayloadKeys.USER_WEEKLY_DATA] =
                    stripKeys(weekly, UserAllNodes.USER_MISSION_DROP_KEYS)
            }
        }

        (raw[PayloadKeys.USER_2P_PLAYLOG])?.let { src ->
            val twoP = unwrap(src, PayloadKeys.USER_2P_PLAYLOG)
            if (twoP is Map<*, *>) {
                out[PayloadKeys.USER_2P_PLAYLOG] = deepCopy(twoP)
            }
        }

        // ---- 普通数组 ----
        UserAllNodes.SNAPSHOT_LIST_KEYS.forEach { (node, listKey) ->
            val src = raw[node] ?: return@forEach
            val items = extractList(node, listKey, src) ?: return@forEach
            out[node] = mutableListOf<Any?>().apply {
                items.forEach { add(trimItem(node, it)) }
            }
        }

        // ★ 收藏夹的**原始**基线（不补 itemKind）：真机 itemKind=10 的响应回的是
        //   {"itemKind":null}，位串里的 "1" 就来自它。单独存一份供差分用。
        raw["_raw_favorite"]?.let { favorite ->
            if (favorite is List<*>) {
                out["_raw_favorite"] = mutableListOf<Any?>().apply { addAll(favorite) }
            }
        }

        return out
    }

    /** 取出节点响应里的列表；取不到返回 null。 */
    private fun extractList(node: String, listKey: String, src: Any?): List<Any?>? {
        if (src is List<*>) {
            return src
        }
        if (src !is Map<*, *>) {
            return null
        }

        if (listKey == PayloadKeys.USER_MUSIC_LIST) {
            return flattenMusicGroups(src[listKey])
        }

        var items = src[listKey]
        // ★ 兜底：取数脚本返回的字典有时用**节点名**作键
        //   （如 GetUserFavoriteAll -> {"userFavoriteList": [...], "_raw": [...]}，
        //    而 GetUserFavoriteApi 的原始响应是 {"userFavorite": {...}}）。
        if (items == null) {
            items = src[node]
        }
        return when (items) {
            null -> emptyList()
            is List<*> -> items
            is Map<*, *> -> listOf(items)
            else -> null
        }
    }

    /**
     * ★ 曲目成绩有两种形态，都要支持：
     *   a) 接口原始响应：两层嵌套（外层按曲分组，内层 userMusicDetailList 才是条目）
     *   b) 已摊平的单条列表
     *   判据：元素里带 "userMusicDetailList" 键 -> 嵌套。
     */
    private fun flattenMusicGroups(groups: Any?): List<Any?> {
        val list = when (groups) {
            is List<*> -> groups
            is Map<*, *> -> listOf(groups)
            else -> return emptyList()
        }
        val nested =
            list.any { it is Map<*, *> && it.containsKey(PayloadKeys.USER_MUSIC_DETAIL_LIST) }
        if (!nested) {
            return list
        }
        val out = mutableListOf<Any?>()
        list.forEach { group ->
            when (group) {
                is Map<*, *> -> (group[PayloadKeys.USER_MUSIC_DETAIL_LIST] as? List<*>)?.let {
                    out.addAll(
                        it
                    )
                }

                is List<*> -> out.addAll(group)
            }
        }
        return out
    }

    /** 按节点裁剪字段。 */
    private fun trimItem(node: String, item: Any?): Any? = when (node) {
        PayloadKeys.USER_CHARACTER_LIST -> keepKeys(item, UserAllNodes.USER_CHARACTER_KEEP_KEYS)
        PayloadKeys.USER_MUSIC_DETAIL_LIST -> stripKeys(
            item,
            UserAllNodes.USER_MUSIC_DETAIL_DROP_KEYS
        )

        PayloadKeys.USER_CHARGE_LIST -> stripKeys(item, UserAllNodes.USER_CHARGE_DROP_KEYS)
        PayloadKeys.USER_MISSION_DATA_LIST -> stripKeys(item, UserAllNodes.USER_MISSION_DROP_KEYS)
        else -> item
    }

    // =========================================================================
    // 内部：动态 Map 工具
    // =========================================================================

    /** 若 src 是有外层包装的响应，取出内层；否则原样返回。 */
    private fun unwrap(src: Any?, key: String): Any? =
        if (src is Map<*, *> && src[key] != null) src[key] else src

    private fun stripKeys(item: Any?, drop: Collection<String>): MutableMap<String, Any?> =
        if (item is Map<*, *>) {
            mutableMapOf<String, Any?>().apply {
                item.forEach { (key, value) ->
                    val name = key.toString()
                    if (name !in drop) put(name, value)
                }
            }
        } else {
            mutableMapOf()
        }

    private fun keepKeys(item: Any?, keep: Collection<String>): Any? =
        if (item is Map<*, *>) {
            mutableMapOf<String, Any?>().apply {
                item.forEach { (key, value) ->
                    val name = key.toString()
                    if (name in keep) put(name, value)
                }
            }
        } else {
            item
        }

    /** 深拷贝一个对象节点：序列化再解析，保证后续改动不会串到源快照上。 */
    private fun deepCopy(value: Any?): MutableMap<String, Any?> =
        if (value == null) {
            mutableMapOf()
        } else {
            JsonSupport.parseObject(JsonSupport.stringify(value))
        }

    private const val RESPONSES_KEY = "_responses"
}
