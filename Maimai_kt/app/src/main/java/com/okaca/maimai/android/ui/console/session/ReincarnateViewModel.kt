package com.okaca.maimai.android.ui.console.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.blankj.utilcode.util.Utils
import com.okaca.maimai.android.R
import com.okaca.maimai.android.logging.AppMaimaiLogger
import com.okaca.maimai.android.storage.SnapshotStore
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kt.constants.PayloadKeys
import kt.payload.SnapshotNormalizer
import kt.service.MaimaiActions
import kt.service.ReincarnateListener
import kt.service.ReincarnateOptions
import kt.service.ReincarnatePlan
import kt.service.ReincarnateResult
import kt.service.SnapshotCaptureService
import kt.transport.JsonSupport

/**
 * 账号迁移（转生）页面的 ViewModel。
 *
 * 业务动作全部走核心库的 [MaimaiActions.reincarnate] / [MaimaiActions.snapshots]，
 * 这里只负责解析输入、维护页面状态、把过程回调整理成日志。
 *
 * 源数据有两条路：**登录源账号联网抓取**（对齐 Python 的 data/&lt;uid&gt;.json）
 * 和手动粘贴 / 导入 JSON，抓取结果落在 [SnapshotStore]，之后可离线复用。
 *
 * ★ 迁移会**真实写入**目标账号，不可撤销；而且每包之间要留间隔，
 *   整个流程可能跑很久，所以任务必须可取消。
 */
@HiltViewModel
class ReincarnateViewModel @Inject constructor(
    private val actions: MaimaiActions,
    private val snapshots: SnapshotStore,
    private val logger: AppMaimaiLogger,
) : ViewModel() {

    private val _state = MutableStateFlow(ReincarnateUiState())
    val state: StateFlow<ReincarnateUiState> = _state.asStateFlow()

    private var currentJob: Job? = null
    private var captureJob: Job? = null

    init {
        refreshSaved()
    }

    /**
     * 页面输入草稿。旋转屏幕时 Fragment 的视图会重建，
     * 但 ViewModel 还在，靠它把粘贴进来的大 JSON 留住。
     */
    var inputs: ReincarnateInputs = ReincarnateInputs()
        private set

    /** 记录当前输入，供视图重建后恢复。 */
    fun saveInputs(value: ReincarnateInputs) {
        inputs = value
    }

    /** 清空页面日志。 */
    fun clearLogs() {
        _state.update { it.copy(logs = "") }
    }

    /**
     * 供 Fragment 往页面日志里追加一条（例如「已从文件导入源数据」）。
     *
     * Fragment 自己不存日志，统一走这里，免得两边显示不一致。
     */
    fun log(message: String) {
        appendLog(message)
    }

    /** 供 Fragment 上报页面级错误（读取文件失败、剪贴板里没有二维码之类）。 */
    fun reportError(message: String) {
        setError(message)
    }

    /** 取消正在跑的迁移 / 抓取。取消后核心库会尽力把账号登出。 */
    fun cancel() {
        captureJob?.takeIf { it.isActive }?.let { job ->
            appendLog(text(R.string.log_reincarnate_capture_cancelled))
            job.cancel(CancellationException(text(R.string.error_reincarnate_cancelled)))
            return
        }

        val job = currentJob
        if (job == null || job.isCompleted) {
            return
        }
        appendLog(text(R.string.log_reincarnate_cancel_requested))
        job.cancel(CancellationException(text(R.string.error_reincarnate_cancelled)))
    }

    // =========================================================================
    // 源账号：联网抓取 / 本地快照
    // =========================================================================

    /** 源账号抓取结束后的收尾（无论成功失败都要复位状态）。 */
    private fun finishCapture() {
        captureJob = null
        _state.update {
            it.copy(busy = false, capturing = false, status = "", captureProgressText = "")
        }
    }

    /**
     * 用源账号二维码联网抓一份快照，落盘到 [SnapshotStore]，并直接载入为当前源。
     *
     * 对齐 Python `fetch_source_by_qr`：抓完会用 type=4 登出源账号（核心库里做）。
     */
    fun captureSource(sourceQr: String) {
        if (state.value.busy) {
            return
        }
        if (sourceQr.isBlank()) {
            setError(text(R.string.error_reincarnate_source_qr_required))
            return
        }

        captureJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    busy = true,
                    capturing = true,
                    status = text(R.string.status_reincarnate_capturing),
                    captureProgressText = "",
                    lastError = null,
                )
            }
            try {
                val result = actions.snapshots.captureByQr(sourceQr, captureListener)
                val file = snapshots.save(result.userId, result.snapshot)
                val musicCount = snapshots.musicCountOf(result.snapshot)
                // 快照有 1～2 MB，序列化别占着主线程
                val json =
                    withContext(Dispatchers.Default) { JsonSupport.stringify(result.snapshot) }
                appendLog(
                    text(
                        R.string.log_reincarnate_capture_saved,
                        file.name,
                        file.length(),
                        musicCount,
                    )
                )
                if (result.failedNodes.isNotEmpty()) {
                    appendLog(
                        text(
                            R.string.log_reincarnate_capture_failed_nodes,
                            result.failedNodes.size,
                            result.failedNodes.joinToString("/"),
                        )
                    )
                }
                refreshSaved()
                adoptSource(uid = result.userId, json = json, musicCount = musicCount)
            } catch (error: CancellationException) {
                appendLog(text(R.string.log_reincarnate_capture_cancelled))
            } catch (error: Throwable) {
                val message = error.message ?: error::class.java.simpleName
                logger.error(text(R.string.log_reincarnate_capture_failed), error)
                appendLog(text(R.string.log_reincarnate_capture_failed) + "：" + message)
                setError(message)
            } finally {
                finishCapture()
            }
        }
    }

    /** 重新扫描本机已保存的快照。 */
    fun refreshSaved() {
        viewModelScope.launch {
            val list = snapshots.list()
            _state.update { it.copy(savedSnapshots = list) }
            appendLog(text(R.string.log_reincarnate_snapshots_refreshed, list.size))
        }
    }

    /** 把某份已保存的快照载入为当前源。 */
    fun loadSaved(uid: Long) {
        viewModelScope.launch {
            val content = snapshots.readText(uid)
            if (content.isNullOrBlank()) {
                setError(text(R.string.error_reincarnate_snapshot_missing))
                return@launch
            }
            // 曲目条数优先用列表里已经算好的；没有才自己解析一遍（放到后台线程）
            val cached = _state.value.savedSnapshots.firstOrNull { it.uid == uid }
            val musicCount = try {
                cached?.musicCount ?: withContext(Dispatchers.Default) {
                    snapshots.musicCountOf(JsonSupport.parseObject(content))
                }
            } catch (error: Throwable) {
                setError(
                    text(R.string.error_reincarnate_snapshot_invalid) + "：" +
                            (error.message ?: error::class.java.simpleName)
                )
                return@launch
            }
            adoptSource(uid, content, musicCount)
        }
    }

    /** 把输入框 / 剪贴板 / 文件来的 JSON 记为当前源（不进输入框，理由见 [ReincarnateUiState.sourceJson]）。 */
    fun adoptManualSource(json: String, name: String) {
        if (json.isBlank()) {
            setError(text(R.string.error_reincarnate_clipboard_empty))
            return
        }
        _state.update {
            it.copy(
                sourceJson = json,
                sourceName = name,
                clearManualInput = true,
                sourceSummary = text(R.string.reincarnate_source_chars, json.length),
                lastError = null,
            )
        }
    }

    /** 清空当前源（「清空」按钮）。 */
    fun clearSource() {
        _state.update {
            it.copy(
                sourceJson = null,
                sourceName = null,
                clearManualInput = true,
                sourceSummary = "",
                planSummary = "",
                lastError = null,
            )
        }
    }

    /** 删除一份已保存的快照。 */
    fun deleteSaved(uid: Long) {
        viewModelScope.launch {
            if (!snapshots.delete(uid)) {
                setError(text(R.string.error_reincarnate_snapshot_missing))
                return@launch
            }
            appendLog(text(R.string.log_reincarnate_snapshot_deleted, uid))
            _state.update {
                it.copy(selectedSnapshotUid = if (it.selectedSnapshotUid == uid) null else it.selectedSnapshotUid)
            }
            refreshSaved()
        }
    }

    /**
     * 读出某份快照交给 Fragment 走 SAF 导出。
     *
     * 回调在主线程（`viewModelScope` 默认 Main），Fragment 拿到后直接拉起系统保存对话框。
     */
    fun prepareExport(uid: Long, onReady: (fileName: String, content: String) -> Unit) {
        viewModelScope.launch {
            val text = snapshots.readText(uid)
            if (text.isNullOrBlank()) {
                setError(text(R.string.error_reincarnate_snapshot_missing))
                return@launch
            }
            onReady("$uid.json", text)
        }
    }

    /** 选中「已保存的快照」里的某一项（只记选中态，不载入）。 */
    fun selectSaved(uid: Long?) {
        _state.update { it.copy(selectedSnapshotUid = uid) }
    }

    /** Fragment 清空输入框后调用。 */
    fun consumeClearManualInput() {
        _state.update { it.copy(clearManualInput = false) }
    }

    /** 抓取成功后，把这份快照同时设为「选中项」和「当前源」。 */
    private fun adoptSource(uid: Long, json: String, musicCount: Int) {
        _state.update {
            it.copy(
                selectedSnapshotUid = uid,
                sourceJson = json,
                sourceName = text(R.string.source_name_snapshot, uid),
                clearManualInput = true,
                sourceSummary = text(R.string.reincarnate_source_summary, musicCount),
                lastError = null,
            )
        }
        appendLog(text(R.string.log_reincarnate_snapshot_loaded, uid, musicCount))
    }

    /**
     * 取当前要用的源：**输入框优先**（用户手动粘贴的就是最后一手），
     * 输入框空着才用 ViewModel 里持有的快照。
     */
    private fun resolveSource(manualJson: String): Map<String, Any?>? {
        val manual = manualJson.trim()
        if (manual.isNotEmpty()) {
            return parseSource(manual, text(R.string.source_name_input))
        }
        val adopted = state.value.sourceJson
        if (adopted.isNullOrBlank()) {
            setError(text(R.string.error_reincarnate_source_required))
            return null
        }
        return parseSource(adopted, state.value.sourceName.orEmpty())
    }

    /**
     * 只算计划，不发包。
     *
     * [manualJson] 是输入框里的文本（可以是空的，那就用当前源）。
     * 源 JSON 解析失败或参数非法时把错误写到 [ReincarnateUiState.lastError]。
     */
    fun preview(manualJson: String, options: ReincarnateOptions) {
        val source = resolveSource(manualJson) ?: return
        try {
            val plan = actions.reincarnate.plan(source, options)
            if (plan.totalMusics == 0) {
                setError(text(R.string.error_reincarnate_source_empty))
                return
            }
            _state.update {
                it.copy(
                    sourceSummary = text(R.string.reincarnate_source_summary, plan.totalMusics),
                    planSummary = describePlan(plan),
                    lastError = null,
                )
            }
            appendLog(
                text(
                    R.string.reincarnate_plan_preview,
                    plan.totalMusics,
                    plan.batchSizes.size
                )
            )
        } catch (error: Throwable) {
            setError(error.message ?: error::class.java.simpleName)
        }
    }

    /**
     * 确认开始前解析目标账号：只解析二维码、不登录，再找该账号最近一次的上传记录目录。
     *
     * 解析失败时写错误并返回 null（此时不该弹确认框）。
     */
    suspend fun resolveTarget(targetQr: String): ResumeTarget? {
        if (targetQr.isBlank()) {
            setError(text(R.string.error_qrcode_required))
            return null
        }
        return try {
            val userId = actions.sessions.resolveByQr(targetQr, fetchPreview = false).userId
            ResumeTarget(userId, snapshots.latestUploadFolder(userId))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            setError(error.message ?: error::class.java.simpleName)
            null
        }
    }

    /** 真正开始迁移（会写入目标账号）。[manualJson] 同 [preview]，[target] 来自 [resolveTarget]。 */
    fun start(
        manualJson: String,
        targetQr: String,
        options: ReincarnateOptions,
        target: ResumeTarget,
    ) {
        if (state.value.running) {
            return
        }
        val source = resolveSource(manualJson) ?: return
        if (targetQr.isBlank()) {
            setError(text(R.string.error_qrcode_required))
            return
        }

        currentJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    busy = true,
                    running = true,
                    status = text(R.string.status_reincarnate_running),
                    lastError = null,
                    packetText = "",
                    waitText = "",
                    waitProgress = 0,
                )
            }
            try {
                val journal =
                    target.lastFolder?.let { snapshots.resumeUploadJournal(target.userId, it) }
                        ?: snapshots.newUploadJournal(target.userId)
                appendLog(
                    text(
                        R.string.log_reincarnate_journal_dir,
                        journal.directory.absolutePath
                    )
                )
                actions.reincarnate.run(source, targetQr, options, listener, journal)
            } catch (error: CancellationException) {
                appendLog(text(R.string.log_reincarnate_cancelled))
            } catch (error: Throwable) {
                val message = error.message ?: error::class.java.simpleName
                logger.error(text(R.string.log_reincarnate_failed), error)
                appendLog(text(R.string.log_reincarnate_failed) + "：" + message)
                setError(message)
            } finally {
                currentJob = null
                _state.update {
                    it.copy(
                        busy = false,
                        running = false,
                        status = "",
                        waitText = "",
                        waitProgress = 0,
                    )
                }
            }
        }
    }

    // =========================================================================
    // 过程回调
    // =========================================================================

    private val listener = object : ReincarnateListener {
        override fun onLog(message: String) {
            appendLog(message)
        }

        override fun onPlan(plan: ReincarnatePlan) {
            _state.update {
                it.copy(
                    sourceSummary = text(R.string.reincarnate_source_summary, plan.totalMusics),
                    planSummary = describePlan(plan),
                )
            }
            appendLog(
                text(
                    R.string.reincarnate_plan_upload,
                    plan.totalMusics,
                    plan.batchSizes.size
                )
            )
        }

        override fun onWait(label: String, remainingSeconds: Int, totalSeconds: Int) {
            _state.update {
                it.copy(
                    waitText = text(R.string.reincarnate_wait_text, label, remainingSeconds),
                    waitProgress = if (totalSeconds <= 0) {
                        100
                    } else {
                        ((totalSeconds - remainingSeconds) * 100 / totalSeconds).coerceIn(0, 100)
                    },
                )
            }
        }

        override fun onPacketStart(index: Int, total: Int, size: Int) {
            _state.update {
                it.copy(
                    packetText = text(R.string.reincarnate_packet_progress, index + 1, total, size),
                )
            }
            appendLog(text(R.string.reincarnate_packet_start, index + 1, total, size))
        }

        override fun onPacketSent(index: Int, total: Int) {
            appendLog(text(R.string.reincarnate_packet_sent, index + 1, total))
        }

        override fun onFinished(result: ReincarnateResult) {
            appendLog(text(R.string.reincarnate_finished, result.packets, result.musics))
        }
    }

    // =========================================================================
    // 工具
    // =========================================================================

    /** 抓取进度回调：节点级进度写状态，日志交给 [SnapshotCaptureService] 自己发。 */
    private val captureListener = object : SnapshotCaptureService.Listener {
        override fun onLog(message: String) {
            appendLog(message)
        }

        override fun onNode(node: String, index: Int, total: Int) {
            _state.update {
                it.copy(
                    captureProgressText = text(
                        R.string.reincarnate_capture_progress,
                        node,
                        index,
                        total
                    )
                )
            }
        }
    }

    /** 解析源 JSON；失败时写错误并返回 null。[name] 只用来写日志。 */
    private fun parseSource(sourceJson: String, name: String): Map<String, Any?>? {
        if (sourceJson.isBlank()) {
            setError(text(R.string.error_reincarnate_source_required))
            return null
        }
        return try {
            val source = SnapshotNormalizer.normalizeJson(sourceJson)
            val musics = source[PayloadKeys.USER_MUSIC_DETAIL_LIST] as? List<*> ?: emptyList<Any?>()
            if (musics.isEmpty()) {
                setError(text(R.string.error_reincarnate_source_empty))
                return null
            }
            _state.update {
                it.copy(
                    sourceSummary = text(R.string.reincarnate_source_summary, musics.size),
                    lastError = null,
                )
            }
            appendLog(text(R.string.log_reincarnate_source_used, name, musics.size))
            source
        } catch (error: Throwable) {
            setError(
                text(R.string.error_reincarnate_source_invalid) + "：" +
                        (error.message ?: error::class.java.simpleName)
            )
            null
        }
    }

    private fun describePlan(plan: ReincarnatePlan): String {
        val head = plan.batchSizes.take(PLAN_PREVIEW_BATCHES).joinToString("/")
        val tail = if (plan.batchSizes.size > PLAN_PREVIEW_BATCHES) "..." else ""
        return text(
            R.string.reincarnate_plan_summary,
            plan.batchSizes.size,
            head + tail,
        )
    }

    private fun appendLog(message: String) {
        val timestamp = SimpleDateFormat(TIME_PATTERN, Locale.getDefault()).format(Date())
        val line = "[$timestamp] $message\n"
        val lineCount = _state.value.logs.count { it == '\n' }
        _state.update { current ->
            val logs = if (lineCount >= MAX_LOG_LINES) {
                current.logs.substringAfter('\n')
            } else {
                current.logs
            }
            current.copy(logs = logs + line)
        }
    }

    private fun setError(message: String) {
        logger.error(message, null)
        _state.update { it.copy(lastError = message) }
    }

    private fun text(resId: Int, vararg args: Any): String =
        Utils.getApp().getString(resId, *args)

    private companion object {
        const val TIME_PATTERN = "HH:mm:ss"
        const val MAX_LOG_LINES = 3000
        const val PLAN_PREVIEW_BATCHES = 12
    }
}

/** 目标账号 B 的解析结果；[lastFolder] 是它最近一次上传记录的目录名，没有则为 null。 */
data class ResumeTarget(val userId: Long, val lastFolder: String?)
