package kt.service

import kt.api.TitleApiClient
import kt.constants.LoginCodes
import kt.constants.PayloadKeys
import kt.constants.UserAllNodes
import kt.constants.UserDataKinds
import kt.error.MaimaiLoginException
import kt.payload.CharaDetail
import kt.payload.MusicDetail
import kt.payload.SnapshotNormalizer
import kt.payload.UserAllBuilder
import kt.payload.asIntValue
import kt.payload.asLongValue
import kt.payload.calcPlaySpecial
import kt.payload.mergePatch
import kt.transport.JsonSupport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * 账号数据迁移（转生）：把账号 A 的存档克隆到账号 B。
 *
 * 对齐 Python 版 `reincarnate.py`：
 *
 * ```
 * 源账号 A 的节点数据（已有快照 JSON）
 *         |
 *         v
 * 目标账号 B：解析二维码 -> 预览（顶号保护）-> 登录 -> 抓基线
 *   循环：等待 -> UpsertUserAll（一包曲目）-> UserLogout(type=1)
 *         -> UserLogin(续关，拿新 loginId)
 *   收尾：最后一包登出后结束（不再续关）
 * ```
 *
 * ★ 每包几首：1 / 3 / 4 都可以，**恰好 2 首会被服务端拒绝**，
 *   所以 [splitBatches] 会主动避开 2 首包。
 *
 * ★ 每包都必须带**非空**的 userMusicDetailList —— 留空会被服务端当成
 *   无效包静默丢弃（returnCode=1 但不落库）。
 */
class ReincarnateService(
    private val api: TitleApiClient,
    private val sessions: SessionService,
    private val users: UserRepository,
    private val snapshots: SnapshotCaptureService,
) {

    /** 只算计划，不发包。 */
    fun plan(source: Map<String, Any?>, options: ReincarnateOptions): ReincarnatePlan {
        requireAllowedPerPacket(options.perPacket)
        val musics = sourceMusics(source, options.limit)
        return ReincarnatePlan(musics.size, splitBatches(musics.size, options.perPacket))
    }

    /**
     * 真正执行迁移。★ 会写入账号 B，不可撤销。
     *
     * @param journal 每包 POST 之前记录一份报文（断点续传用）；记录失败则这一包不发。
     * @throws MaimaiLoginException 登录失败（含二维码过期 / 账号正在游玩）
     * @throws IllegalStateException 源数据为空 / 目标账号已在机台登录 / 参数非法
     */
    suspend fun run(
        source: Map<String, Any?>,
        targetQr: String,
        options: ReincarnateOptions,
        listener: ReincarnateListener,
        journal: UpsertJournal,
    ): ReincarnateResult {
        requireAllowedPerPacket(options.perPacket)

        val musics = sourceMusics(source, options.limit)
        if (musics.isEmpty()) {
            throw IllegalStateException("源数据里没有 userMusicDetailList，没什么可迁的")
        }

        val sizes = splitBatches(musics.size, options.perPacket)
        var cursor = 0
        val batches = sizes.map { size ->
            musics.subList(cursor, cursor + size).also { cursor += size }
        }
        listener.onPlan(ReincarnatePlan(musics.size, sizes))

        // ---------------- 目标账号 ----------------
        listener.onLog("解析目标账号二维码 ...")
        val resolved = sessions.resolveByQr(targetQr)
        if (resolved.isLoggedIn) {
            throw IllegalStateException("目标账号处于已登录状态，请先在机台登出或等待会话超时")
        }

        listener.onLog("登录目标账号 ...")
        var session = sessions.loginResolved(resolved)
        var loggedIn = true

        try {
            // ---------------- 基线：填骨架 + 算 isNew* 位串 ----------------
            listener.onLog("读取目标账号基线 ...")
            val baseUserData = users.getData(session.userId, session.cookie)
            val baseState = users.getRequiredState(session.userId, session.cookie)
            val baseNodes = loadBaselineNodes(session, source, listener)

            val sharedNodes = collectNodes(source, baseNodes, options.cloneIdentity)
            val sharedCount = sharedNodes.keys.count { it !in UserAllNodes.ARRAY_TO_BITS }
            listener.onLog("非曲目节点 $sharedCount 个将在第 1 包上传")

            val builder = UserAllBuilder(api.config)

            for ((index, batch) in batches.withIndex()) {
                val sequence = index + 1
                val previous = journal.load(sequence)
                if (previous != null && previous.status == UPLOAD_STATUS_SUCCESS) {
                    if (!previous.matches(session.userId, batch)) {
                        throw IllegalStateException(
                            "第 $sequence 包的留底与当前目标账号或分包不一致，停止续传"
                        )
                    }
                    listener.onLog("第 $sequence/${batches.size} 包已成功上传过，跳过")
                    continue
                }
                if (previous != null) {
                    throw IllegalStateException(
                        "第 $sequence 包留底状态为 ${previous.status ?: "未知"}，服务端结果不确定，" +
                                "请先核对账号 B 后再续传（不会自动重发）"
                    )
                }

                listener.onPacketStart(index, batches.size, batch.size)
                val ids = batch.joinToString(", ") { "${it.musicId}/L${it.level}" }
                listener.onLog("    曲目 $ids")

                // ---- 等待（对齐 Python 的 --wait；库里 upsertUserAll 的冷却另算）----
                if (options.waitSeconds > 0) {
                    countdown(options.waitSeconds, "第 ${index + 1}/${batches.size} 包", listener)
                }

                val packetNodes = mutableMapOf<String, Any?>()
                if (index == 0) {
                    packetNodes.putAll(deepCopyMap(sharedNodes))
                }
                packetNodes[PayloadKeys.USER_MUSIC_DETAIL_LIST] = batch.map { it.toMap() }
                bitsFor(
                    PayloadKeys.USER_MUSIC_DETAIL_LIST,
                    batch.map { it.toMap() },
                    baseNodes[PayloadKeys.USER_MUSIC_DETAIL_LIST],
                )?.let { bits ->
                    packetNodes[PayloadKeys.IS_NEW_MUSIC_DETAIL_LIST] = bits
                    listener.onLog("    isNewMusicDetailList = $bits")
                }

                val userAll = builder.build(
                    userId = session.userId,
                    loginResult = session.login,
                    loginTimestamp = session.timestamp,
                    userData = baseUserData,
                    musicDetails = batch,
                    charaDetails = CharaDetail.defaultList(),
                )
                builder.attachCurrentState(userAll, baseState)
                expandGamePlaylogs(userAll, batch.size)
                mergePatch(userAll, mapOf(PayloadKeys.UPSERT_USER_ALL to packetNodes))

                // ★ 报文先留底再发：POST 之前写入，发出去的每一包都有对应的 N.json
                var journaled = false
                val response = try {
                    api.upsertUserAll(session.userId, userAll, session.cookie) {
                        journal.record(sequence, userAll)
                        journaled = true
                        listener.onLog("    已记录上传报文 $sequence.json")
                    }
                } catch (error: Throwable) {
                    if (journaled) {
                        withContext(NonCancellable) {
                            runCatching { journal.finish(sequence, uploadFailure(error)) }
                                .onFailure { error.addSuppressed(it) }
                        }
                    }
                    throw error
                }
                withContext(NonCancellable) { journal.finish(sequence, uploadResult(response)) }
                listener.onPacketSent(index, batches.size)

                // ---- ★ 登出（type=1 Logout）----
                listener.onLog("--- 登出（type=1 Logout）---")
                loggedIn = false
                try {
                    sessions.logout(session.userId, session.cookie)
                } catch (error: Throwable) {
                    // 登出失败不阻断：下一包的续关登录会重新建立会话
                    listener.onLog("登出失败（继续下一包）：${error.message ?: error::class.java.simpleName}")
                }

                // ---- ★ 续关登录：拿下一包的 loginId ----
                if (index < batches.size - 1) {
                    listener.onLog("--- 续关登录（isContinue=true）---")
                    session = continueLoginRecovering(session, listener)
                    listener.onLog("    新 loginId = ${session.login[PayloadKeys.LOGIN_ID]}")
                    loggedIn = true
                }
            }
        } finally {
            if (loggedIn) {
                // ★ 取消时也要把账号登出，否则会话会一直挂在服务端（机台上会顶号）。
                //   所以这里必须跑在 NonCancellable 里，不然 logout 一进来就被取消了。
                withContext(NonCancellable) {
                    try {
                        sessions.logout(session.userId, session.cookie)
                    } catch (error: Throwable) {
                        listener.onLog(
                            "[收尾] 强制登出失败（会话可能需等待服务端超时）：" +
                                    (error.message ?: error::class.java.simpleName)
                        )
                    }
                }
            }
        }

        val result = ReincarnateResult(batches.size, musics.size)
        listener.onFinished(result)
        return result
    }

    // =========================================================================
    // 续关登录
    // =========================================================================

    /**
     * 续关登录；遇到「用户正在游玩中」（returnCode=100）就自动登出再重登。
     *
     * 真机上上一次登出之后服务端的会话状态可能还没清干净，立刻续关会被判为「仍在游玩」。
     * 所以这里的做法是：登出（type=1）→ 等几秒 → 重登，最多 [CONTINUE_RETRY_LIMIT] 次。
     * 其它错误码不重试，直接抛给上层中止迁移。
     */
    private suspend fun continueLoginRecovering(
        session: LoginSession,
        listener: ReincarnateListener,
    ): LoginSession {
        var attempt = 0
        var current = session
        while (true) {
            try {
                return sessions.continueLogin(current)
            } catch (error: MaimaiLoginException) {
                if (error.code != LoginCodes.PLAYING || attempt >= CONTINUE_RETRY_LIMIT) {
                    throw error
                }
                attempt++
                listener.onLog(
                    "    续关返回「用户正在游玩中」，自动登出后重登（第 $attempt/$CONTINUE_RETRY_LIMIT 次）"
                )
                try {
                    sessions.logout(current.userId, current.cookie)
                } catch (logoutError: Throwable) {
                    listener.onLog(
                        "    强制登出失败：${logoutError.message ?: logoutError::class.java.simpleName}"
                    )
                }
                countdown(CONTINUE_RETRY_WAIT_SECONDS, "续关重登", listener)
            }
        }
    }

    // =========================================================================
    // 账号 A -> 待迁节点
    // =========================================================================

    /**
     * 从源快照里取出曲目记录，按 [MusicDetail] 解析，并按 musicId 升序（同一首歌按 level 升序）。
     *
     * ★ 排序要在截取 limit **之前**：试跑的前 N 首正好是全量上传的前 N 首。
     * ★ 这一点和 Python 版不同 —— Python 按源文件里的原始顺序上传。
     */
    private fun sourceMusics(source: Map<String, Any?>, limit: Int): List<MusicDetail> {
        val raw = source[PayloadKeys.USER_MUSIC_DETAIL_LIST] as? List<*> ?: return emptyList()
        val musics = raw.mapNotNull { toMusicDetail(it) }
            .sortedWith(compareBy({ it.musicId }, { it.level }))
        return if (limit > 0) musics.take(limit) else musics
    }

    private fun toMusicDetail(item: Any?): MusicDetail? {
        val map = item as? Map<*, *> ?: return null
        return MusicDetail(
            musicId = map[PayloadKeys.MUSIC_ID].intOr(0),
            level = map[PayloadKeys.LEVEL].intOr(0),
            playCount = map[PayloadKeys.PLAY_COUNT].intOr(1),
            achievement = map[PayloadKeys.ACHIEVEMENT].intOr(0),
            comboStatus = map[PayloadKeys.COMBO_STATUS].intOr(0),
            syncStatus = map[PayloadKeys.SYNC_STATUS].intOr(0),
            deluxscoreMax = map[PayloadKeys.DELUXSCORE_MAX].intOr(0),
            scoreRank = map[PayloadKeys.SCORE_RANK].intOr(0),
            extNum1 = map[PayloadKeys.EXT_NUM_1].intOr(0),
        )
    }

    /**
     * 把源账号的非曲目节点整理成第 1 包的补丁。
     *
     * 与 [SKIP_NODES] 对应的节点不迁；位串按 B 的基线现算。
     */
    private fun collectNodes(
        source: Map<String, Any?>,
        base: Map<String, Any?>,
        cloneIdentity: Boolean,
    ): MutableMap<String, Any?> {
        val out = mutableMapOf<String, Any?>()

        UserAllNodes.ORDER.forEach { node ->
            if (node in SKIP_NODES) {
                return@forEach
            }
            if (UserAllNodes.TYPES[node] == UserAllNodes.KIND_BITS) {
                return@forEach  // 位串单独算
            }
            if (UserAllNodes.TYPES[node] == null) {
                return@forEach
            }

            val value = source[node] ?: return@forEach
            val empty = (value is Collection<*> && value.isEmpty()) ||
                    (value is Map<*, *> && value.isEmpty())
            if (empty) {
                return@forEach
            }
            out[node] = deepCopy(value)
        }

        // ★ 身份字段默认保留 B 自己的，避免把 B 改名
        if (!cloneIdentity) {
            eachMap(out[PayloadKeys.USER_DATA]) { data ->
                IDENTITY_KEYS.forEach { data.remove(it) }
            }
        }

        // ★ userExtend 回传时必须补上这两个字段（Get 侧没有）
        eachMap(out[PayloadKeys.USER_EXTEND]) { extend ->
            extend.putIfAbsent(UserAllNodes.IS_PHOTO_AGREE, false)
            extend.putIfAbsent(UserAllNodes.IS_GOTO_CODE_READ, false)
        }

        // 位串：按 B 的基线算
        UserAllNodes.ARRAY_TO_BITS.forEach { (array, bits) ->
            val items = out[array] as? List<*> ?: return@forEach
            val value = bitsFor(array, items, base[array]) ?: return@forEach
            out[bits] = value
        }

        return out
    }

    /**
     * 按目标账号 B 的基线算 isNew* 位串：
     * B 里没有该条目 -> "1"（新增）；B 里已有 -> "0"（更新）。
     *
     * 主键表见 [UserAllNodes.DIFF_PRIMARY_KEYS]。
     */
    private fun bitsFor(node: String, items: List<Any?>, baseItems: Any?): String? {
        val keys = UserAllNodes.DIFF_PRIMARY_KEYS[node] ?: return null
        if (items.isEmpty()) {
            return null
        }
        val have = primaryKeySet(baseItems, keys)

        return items
            .filterIsInstance<Map<*, *>>()
            .joinToString("") { item ->
                if (primaryKeyOf(item, keys) in have) "0" else "1"
            }
    }

    private fun primaryKeySet(items: Any?, keys: List<String>): Set<List<String>> =
        (items as? List<*>)
            ?.filterIsInstance<Map<*, *>>()
            ?.map { primaryKeyOf(it, keys) }
            ?.toSet()
            ?: emptySet()

    private fun primaryKeyOf(item: Map<*, *>, keys: List<String>): List<String> =
        keys.map { item[it].toString() }

    // =========================================================================
    // 目标账号 B 的基线
    // =========================================================================

    /**
     * 读取目标账号的基线节点，用于算 isNew* 位串。
     *
     * 走的是和源账号同一套抓取（[SnapshotCaptureService]），
     * 只挑「会被迁移、且会被拆成位串」的那几个节点，避免把 19 个节点全拉一遍。
     *
     * ★ 值一律是接口原始字段的 Map —— [bitsFor] 是从 Map 里读主键的，
     *   换成 typed data class 会被当成「B 里什么都没有」，位串全变 1。
     * 单个节点失败不阻断整体，位串会退化成「全部新增」（和 Python 版一致）。
     */
    private suspend fun loadBaselineNodes(
        session: LoginSession,
        source: Map<String, Any?>,
        listener: ReincarnateListener,
    ): MutableMap<String, Any?> {
        val needed = source.keys.filter {
            it in UserAllNodes.DIFF_PRIMARY_KEYS && it in UserAllNodes.SNAPSHOT_NODES
        }
        if (needed.isEmpty()) {
            return mutableMapOf()
        }

        listener.onLog("    （共 ${needed.size} 个节点）")
        val raw = snapshots.capture(
            userId = session.userId,
            cookie = session.cookie,
            listener = baselineLogger(listener),
            nodes = needed,
        )

        val base = SnapshotNormalizer.normalize(raw)
        (raw[PayloadKeys.FAILED] as? List<*>)?.forEach { item ->
            val failed = item as? Map<*, *> ?: return@forEach
            listener.onLog(
                "    ⚠ 基线节点 ${failed["node"]} 读取失败，位串将按「全部新增」计算：" +
                        failed["error"]
            )
        }
        return base
    }

    /** 把抓取服务的日志接进迁移日志（基线阶段的进度只记失败，否则太吵）。 */
    private fun baselineLogger(listener: ReincarnateListener): SnapshotCaptureService.Listener =
        object : SnapshotCaptureService.Listener {
            override fun onLog(message: String) = listener.onLog("    $message")

            override fun onNode(node: String, index: Int, total: Int) = Unit
        }

    // =========================================================================
    // 报文拼装
    // =========================================================================

    /**
     * 把 UserAllBuilder 生成的单条 userGamePlaylogList 展开成「每首一条」。
     *
     * 真机一局最多 4 首，每首 track 各占一条，所以 4 首包应该有 4 条记录。
     */
    @Suppress("UNCHECKED_CAST")
    private fun expandGamePlaylogs(userAll: MutableMap<String, Any?>, trackCount: Int) {
        val upsert = userAll[PayloadKeys.UPSERT_USER_ALL] as? MutableMap<String, Any?> ?: return
        val template = (upsert[PayloadKeys.USER_GAME_PLAYLOG_LIST] as? List<*>)
            ?.firstOrNull() as? Map<String, Any?> ?: return

        upsert[PayloadKeys.USER_GAME_PLAYLOG_LIST] = (1..trackCount).map { track ->
            mutableMapOf<String, Any?>().apply {
                putAll(template)
                this[PayloadKeys.PLAY_TRACK] = track
                this[PayloadKeys.PLAY_SPECIAL] = calcPlaySpecial()
            }
        }
    }

    // =========================================================================
    // 分批
    // =========================================================================

    /**
     * 把 total 首切成「每包 <= per」的批次，**并且不产生被禁止的 2 首包**。
     *
     * 做法：先贪心按 per 切，再修正大小为 2 的批次：
     * 1. 前一批 >= 4 -> 从它借 1 首过来（前一批变 >=3，本批变 3）
     * 2. 借不到（前一批只有 3，或自己就是第一批）-> 把这个 2 拆成 1 + 1
     *
     * ★ 只借位是不够的 —— 前一批是 3 时借完自己变 2，等于把 2 挪了个位置。
     *
     * 例：`10` per=4 -> `[4,4,2]` -> `[4,3,3]`；`6` -> `[4,2]` -> `[3,3]`；
     *     `2` -> `[2]` -> `[1,1]`；`5` per=3 -> `[3,2]` -> `[3,1,1]`。
     */
    fun splitBatches(total: Int, per: Int): List<Int> {
        if (total <= 0) {
            return emptyList()
        }
        val size = per.coerceAtLeast(1)
        val out = mutableListOf<Int>()
        var left = total
        while (left > 0) {
            val n = minOf(size, left)
            out += n
            left -= n
        }

        var i = 0
        while (i < out.size) {
            if (out[i] !in FORBIDDEN_TRACK_COUNTS) {
                i++
                continue
            }
            // 1) 前一批 >= 4 才能借（借完前一批 >= 3，本批 = 3）
            if (i > 0 && out[i - 1] >= 4) {
                out[i - 1] -= 1
                out[i] += 1
                i++
                continue
            }
            // 2) 借不到 -> 把这个 2 拆成 1 + 1
            out[i] = 1
            out.add(i + 1, 1)
            i += 2
        }
        return out
    }

    private fun requireAllowedPerPacket(perPacket: Int) {
        require(perPacket in ALLOWED_PER_PACKET) {
            "每包曲目数只能是 $ALLOWED_PER_PACKET 之一" +
                    "（真机一局最多 $MAX_TRACKS_PER_CREDIT 首；★ 实测恰好 2 首会被服务端拒绝）"
        }
    }

    private suspend fun countdown(seconds: Double, label: String, listener: ReincarnateListener) {
        val total = seconds.toInt()
        if (total <= 0) {
            delay((seconds * 1000).toLong())
            return
        }
        var remaining = total
        while (remaining > 0) {
            listener.onWait(label, remaining, total)
            delay(1000)
            remaining--
        }
        listener.onWait(label, 0, total)
    }

    // =========================================================================
    // 工具
    // =========================================================================

    @Suppress("UNCHECKED_CAST")
    private fun deepCopy(value: Any?): Any? = when (value) {
        is Map<*, *> -> JsonSupport.parseObject(JsonSupport.stringify(value))
        is List<*> -> JsonSupport.mapper.readValue(
            JsonSupport.stringify(value),
            MutableList::class.java,
        ) as MutableList<Any?>

        else -> value
    }

    private fun deepCopyMap(value: Map<String, Any?>): MutableMap<String, Any?> =
        JsonSupport.parseObject(JsonSupport.stringify(value))

    /** 遍历「单元素数组」节点里的那个对象。 */
    private fun eachMap(value: Any?, block: (MutableMap<String, Any?>) -> Unit) {
        (value as? List<*>)
            ?.firstOrNull()
            ?.let { it as? MutableMap<String, Any?> }
            ?.let(block)
    }

    private fun Any?.intOr(default: Int): Int = when (this) {
        is Number -> toInt()
        is String -> toIntOrNull() ?: default
        else -> default
    }

    companion object {
        /** 真机一局最多 4 首。 */
        const val MAX_TRACKS_PER_CREDIT = 4

        /** ★ 被服务端禁止的每包曲目数（实测：**恰好 2 首会被拒**）。 */
        val FORBIDDEN_TRACK_COUNTS = setOf(2)

        /** 允许的每包曲目数。 */
        val ALLOWED_PER_PACKET = setOf(1, 3, 4)

        /** 续关遇到「用户正在游玩中」（returnCode=100）时，最多强制登出重登几次。 */
        const val CONTINUE_RETRY_LIMIT = 3

        /** 强制登出后等多久再重登（秒）。 */
        const val CONTINUE_RETRY_WAIT_SECONDS = 5.0

        /** 迁移时**不**逐条传的节点（要么是位串、要么由 UpsertUserAll 自己算）。 */
        private val SKIP_NODES = setOf(
            PayloadKeys.USER_MUSIC_DETAIL_LIST,     // 单独分批
            PayloadKeys.USER_GAME_PLAYLOG_LIST,     // 由本类按曲目合成
            PayloadKeys.USER_2P_PLAYLOG,            // 2P 记录，迁移无意义
            PayloadKeys.USER_GET_POINT_LIST,        // 里程/奖励，迁移会凭空发点
            PayloadKeys.USER_TRADE_ITEM_LIST,       // 无 Get 来源
            PayloadKeys.USER_FRIEND_SEASON_RANKING_LIST,   // 无 Get 来源
        )

        /** 默认保留目标账号自己的身份字段（不克隆过去，避免把 B 改名）。 */
        private val IDENTITY_KEYS = setOf(
            PayloadKeys.USER_NAME,
            PayloadKeys.ACCESS_CODE,
        )
    }
}

/** 迁移参数。 */
data class ReincarnateOptions(
    /** 只迁按 musicId 升序的前 N 首（试跑用）；0 = 全部。 */
    val limit: Int = 0,
    /** 每包曲目数，只能是 1/3/4（默认 4）。 */
    val perPacket: Int = ReincarnateService.MAX_TRACKS_PER_CREDIT,
    /** 每次上传前的等待秒数（默认 5.0）。 */
    val waitSeconds: Double = 5.0,
    /** 连 userName / accessCode 一起克隆（默认保留 B 自己的）。 */
    val cloneIdentity: Boolean = false,
)

/** 迁移计划（不发包时也能看）。 */
data class ReincarnatePlan(
    val totalMusics: Int,
    val batchSizes: List<Int>,
)

/** 迁移结果。 */
data class ReincarnateResult(
    val packets: Int,
    val musics: Int,
)

/**
 * 每包 UpsertUserAll 的报文留底（断点续传用）。
 *
 * ★ [sequence] 从 1 开始，与包序一致；[payload] 就是即将 POST 的请求体。
 *   抛异常 = 这一包不发，迁移随之中止。[finish] 把 POST 结果写到文件第一行（`{"_result":{...},`）。
 */
interface UpsertJournal {
    suspend fun record(sequence: Int, payload: Map<String, Any?>)
    suspend fun finish(sequence: Int, result: Map<String, Any?>)

    /** 第 [sequence] 包的留底；没有该文件返回 null。 */
    suspend fun load(sequence: Int): JournalEntry?
}

/**
 * 留底里的一包：[status] 来自 `_result.status`；没有 `_result` 行时为 null，表示结果未知。
 */
data class JournalEntry(
    val status: String?,
    val payload: Map<String, Any?>,
) {
    /** 留底是否属于本次目标账号，且曲目与 [batch] 逐条一致（musicId / level 顺序相同）。 */
    fun matches(userId: Long, batch: List<MusicDetail>): Boolean {
        if (payload[PayloadKeys.USER_ID].asLongValue() != userId) return false
        val recorded = (payload[PayloadKeys.UPSERT_USER_ALL] as? Map<*, *>)
            ?.get(PayloadKeys.USER_MUSIC_DETAIL_LIST) as? List<*> ?: return false
        if (recorded.size != batch.size) return false
        return recorded.zip(batch).all { (raw, music) ->
            val map = raw as? Map<*, *> ?: return@all false
            map[PayloadKeys.MUSIC_ID].asIntValue() == music.musicId &&
                    map[PayloadKeys.LEVEL].asIntValue() == music.level
        }
    }
}

private const val UPLOAD_STATUS_SUCCESS = "success"

private const val UPSERT_SUCCESS_CODE = 1

private fun uploadResult(response: Map<String, Any?>): Map<String, Any?> {
    val code = (response[PayloadKeys.RETURN_CODE] as? Number)?.toInt()
    return mapOf(
        "status" to if (code == UPSERT_SUCCESS_CODE) "success" else "rejected",
        "returnCode" to code,
        "response" to response,
    )
}

private fun uploadFailure(error: Throwable): Map<String, Any?> = mapOf(
    "status" to if (error is CancellationException) "cancelled" else "error",
    "exception" to error::class.java.name,
    "message" to error.message,
)

/** 迁移过程回调，UI 靠它刷新日志和进度。 */
interface ReincarnateListener {
    fun onLog(message: String)
    fun onPlan(plan: ReincarnatePlan)
    fun onWait(label: String, remainingSeconds: Int, totalSeconds: Int)
    fun onPacketStart(index: Int, total: Int, size: Int)
    fun onPacketSent(index: Int, total: Int)
    fun onFinished(result: ReincarnateResult)
}
