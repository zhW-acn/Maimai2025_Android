package com.okaca.maimai.android.ui.console

import android.content.ClipboardManager
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.okaca.maimai.android.R
import com.okaca.maimai.android.databinding.FragmentReincarnateBinding
import com.okaca.maimai.android.storage.SnapshotStore
import com.okaca.maimai.android.ui.console.session.ReincarnateInputs
import com.okaca.maimai.android.ui.console.session.ReincarnateUiState
import com.okaca.maimai.android.ui.console.session.ReincarnateViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kt.constants.AimeConstants
import kt.service.ReincarnateOptions

/**
 * 「账号迁移（转生）」页。
 *
 * 把 Python 版 reincarnate.py 的整账号克隆搬到手机上。源数据有两条路：
 *
 * 1. **登录源账号联网抓取**（等价于 Python 的 `python -m api.get_need_user_data <uid>`）——
 *    抓完落在应用私有目录 `data/<uid>.json`，之后可离线反复使用；
 * 2. 粘贴 / 导入现成的 JSON 文件（PC 上抓好的那份也能直接用）。
 *
 * 目标账号用 SGWCMAID 二维码登录后分批写入。
 *
 * ★ 这一页会**真实写入**目标账号，写入不可撤销，所以开始前必须过确认弹窗。
 */
@AndroidEntryPoint
class ReincarnateFragment : Fragment() {
    private val viewModel: ReincarnateViewModel by viewModels()

    private lateinit var binding: FragmentReincarnateBinding
    private var renderedLogs: String? = null

    /** 当前 Spinner 里列出的快照，按钮取值时按位置索引它。 */
    private var listedSnapshots: List<SnapshotStore.SavedSnapshot> = emptyList()

    /** 已渲染过的快照列表，没变就不重建 adapter（重建会把选中项弹回第一项）。 */
    private var renderedSnapshots: List<SnapshotStore.SavedSnapshot>? = null

    /** 待写出的快照内容（导出走 SAF，先选位置再写）。 */
    private var pendingExport: String? = null

    private val sourceFilePicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { importSourceFile(it) }
        }

    private val snapshotExporter =
        registerForActivityResult(ActivityResultContracts.CreateDocument(JSON_MIME_TYPE)) { uri ->
            writeExport(uri)
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = FragmentReincarnateBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        applyInputs(viewModel.inputs)

        binding.pasteSourceQrButton.setOnClickListener { pasteSourceQrFromClipboard() }
        binding.captureSourceButton.setOnClickListener { captureSource() }
        binding.loadSnapshotButton.setOnClickListener { loadSelectedSnapshot() }
        binding.exportSnapshotButton.setOnClickListener { exportSelectedSnapshot() }
        binding.deleteSnapshotButton.setOnClickListener { confirmDeleteSnapshot() }
        binding.refreshSnapshotsButton.setOnClickListener { viewModel.refreshSaved() }
        binding.savedSnapshotSpinner.onItemSelectedListener = onSnapshotSelected

        binding.pasteSourceButton.setOnClickListener { pasteSourceFromClipboard() }
        binding.pickSourceFileButton.setOnClickListener { pickSourceFile() }
        binding.clearSourceButton.setOnClickListener { clearSource() }
        // 输入框里手动敲 / 粘了东西，「当前源」那一行要跟着变
        binding.sourceJsonInput.doAfterTextChanged { renderActiveSource(viewModel.state.value) }
        binding.pasteQrButton.setOnClickListener { pasteQrFromClipboard() }
        binding.previewButton.setOnClickListener { previewPlan() }
        binding.startButton.setOnClickListener { confirmStart() }
        binding.cancelButton.setOnClickListener { viewModel.cancel() }
        binding.clearLogsButton.setOnClickListener { viewModel.clearLogs() }

        collectState()
    }

    /**
     * 页面回到前台时尝试从剪贴板补一次二维码，省得每次都要手动粘贴。
     *
     * 只在目标二维码还是空的时候填，避免覆盖用户自己输入的内容。
     */
    override fun onResume() {
        super.onResume()
        if (!::binding.isInitialized) {
            return
        }
        binding.root.post { fillQrFromClipboardIfPossible() }
    }

    // =========================================================================
    // 状态渲染
    // =========================================================================

    private fun collectState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state -> render(state) }
            }
        }
    }

    private fun render(state: ReincarnateUiState) {
        binding.sourceSummaryText.text = state.sourceSummary
        binding.statusText.text = state.status
        binding.captureProgressText.text = state.captureProgressText
        binding.planSummaryText.text = state.planSummary
        binding.packetText.text = state.packetText
        binding.waitText.text = state.waitText
        binding.waitProgress.progress = state.waitProgress
        binding.errorText.text = state.lastError.orEmpty()
        binding.busyProgress.visibility = if (state.busy) View.VISIBLE else View.GONE

        renderSnapshots(state)

        // ★ 抓取 / 载入 / 粘贴进来的源**不进输入框**（几百 KB 的 setText 会把主线程卡到 ANR），
        //   只把输入框让出来，源本身留在 ViewModel 里；这里顺手清掉旧的手动文本。
        if (state.clearManualInput) {
            binding.sourceJsonInput.text?.clear()
            viewModel.consumeClearManualInput()
        }
        renderActiveSource(state)

        // 日志只在真的变了以后才重设文本，倒计时每秒刷新一次，别白白重排一遍
        if (renderedLogs !== state.logs) {
            renderedLogs = state.logs
            binding.logsText.text = state.logs
            binding.logsScroll.post { binding.logsScroll.fullScroll(View.FOCUS_DOWN) }
        }

        val idle = !state.busy
        binding.cancelButton.isEnabled = state.busy
        binding.previewButton.isEnabled = idle
        binding.startButton.isEnabled = idle
        binding.pasteSourceButton.isEnabled = idle
        binding.pickSourceFileButton.isEnabled = idle
        binding.clearSourceButton.isEnabled = idle
        binding.pasteQrButton.isEnabled = idle
        binding.limitInput.isEnabled = idle
        binding.waitInput.isEnabled = idle
        binding.perPacketSpinner.isEnabled = idle
        binding.cloneIdentityCheck.isEnabled = idle

        binding.sourceQrInput.isEnabled = idle
        binding.pasteSourceQrButton.isEnabled = idle
        binding.captureSourceButton.isEnabled = idle
        binding.savedSnapshotSpinner.isEnabled = idle && listedSnapshots.isNotEmpty()
        val hasSnapshot = listedSnapshots.isNotEmpty()
        binding.loadSnapshotButton.isEnabled = idle && hasSnapshot
        binding.exportSnapshotButton.isEnabled = idle && hasSnapshot
        binding.deleteSnapshotButton.isEnabled = idle && hasSnapshot
        binding.refreshSnapshotsButton.isEnabled = idle
    }

    /**
     * 渲染「已保存的快照」列表：**内容**变了才重建 adapter，选中态每帧对齐。
     *
     * ★ 比较的是内容（`!=`），不是引用 —— `SnapshotStore.list()` 每次都新建一个 List，
     *   用 `!==` 会让每次刷新都重建 adapter，把 Spinner 弹回第一项并顺带改掉选中的 uid，
     *   接着「删除」就会删错文件。
     */
    private fun renderSnapshots(state: ReincarnateUiState) {
        val snapshots = state.savedSnapshots
        listedSnapshots = snapshots
        binding.savedEmptyText.visibility = if (snapshots.isEmpty()) View.VISIBLE else View.GONE

        if (renderedSnapshots != snapshots) {
            renderedSnapshots = snapshots
            val labels = snapshots.map { snapshotLabel(it) }
            binding.savedSnapshotSpinner.adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_item,
                labels,
            ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        }

        val index = snapshots.indexOfFirst { it.uid == state.selectedSnapshotUid }
        if (index >= 0 && binding.savedSnapshotSpinner.selectedItemPosition != index) {
            binding.savedSnapshotSpinner.setSelection(index)
        }
    }

    private fun snapshotLabel(snapshot: SnapshotStore.SavedSnapshot): String = getString(
        R.string.reincarnate_snapshot_item,
        snapshot.uid,
        snapshot.musicCount,
        SNAPSHOT_TIME_FORMAT.format(Date(snapshot.modifiedAt)),
    )

    /**
     * 「当前源」那一行。
     *
     * ★ 输入框里有文本就以输入框为准（迁移实际用的就是它）—— 这一行必须如实反映，
     *   否则用户载入快照后又在输入框里粘了点东西，就不知道会上传哪一份了。
     */
    private fun renderActiveSource(state: ReincarnateUiState) {
        // ★ 用 length() 而不是 text.toString()：输入框里可能有几百 KB，别在渲染里拷字符串
        val manualChars = binding.sourceJsonInput.length()
        binding.activeSourceText.text = when {
            manualChars > 0 -> getString(
                R.string.label_reincarnate_active_source,
                getString(R.string.source_name_input),
                manualChars,
            )

            state.sourceJson != null -> getString(
                R.string.label_reincarnate_active_source,
                state.sourceName.orEmpty(),
                state.sourceJson.length,
            )

            else -> getString(R.string.label_reincarnate_active_source_none)
        }
    }

    // =========================================================================
    // 源账号：抓取 / 快照
    // =========================================================================

    /** 用源账号二维码联网抓快照（会顶号，抓完自动登出）。 */
    private fun captureSource() {
        val inputs = currentInputs()
        viewModel.saveInputs(inputs)
        viewModel.captureSource(inputs.sourceQr)
    }

    private fun loadSelectedSnapshot() {
        val uid = selectedSnapshotUid() ?: return
        viewModel.loadSaved(uid)
    }

    private fun exportSelectedSnapshot() {
        val uid = selectedSnapshotUid() ?: return
        viewModel.prepareExport(uid) { fileName, content ->
            // 读文件是耗时操作，回调回来时页面可能已经销毁（旋转 / 退出），不能再拉 SAF
            if (!isAdded || view == null || !lifecycle.currentState.isAtLeast(Lifecycle.State.CREATED)) {
                return@prepareExport
            }
            pendingExport = content
            if (launchSaf(snapshotExporter, fileName, R.string.error_reincarnate_no_save_picker)) {
                viewModel.log(getString(R.string.log_reincarnate_export_ready, fileName))
            } else {
                pendingExport = null
            }
        }
    }

    private fun confirmDeleteSnapshot() {
        val uid = selectedSnapshotUid() ?: return
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.dialog_reincarnate_delete_title)
            .setMessage(getString(R.string.dialog_reincarnate_delete_message, uid))
            .setPositiveButton(R.string.dialog_reincarnate_delete_confirm) { _, _ ->
                viewModel.deleteSaved(uid)
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    /** 把 SAF 选定的位置写成导出文件。 */
    private fun writeExport(uri: Uri?) {
        val content = pendingExport
        pendingExport = null
        if (uri == null || content == null) {
            return
        }
        val context = requireContext().applicationContext
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(content.toByteArray(Charsets.UTF_8))
                    } != null
                }.getOrDefault(false)
            }
            if (!ok) {
                viewModel.reportError(getString(R.string.error_reincarnate_export_failed))
            }
        }
    }

    /** Spinner 选中态同步回 ViewModel。 */
    private val onSnapshotSelected = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(
            parent: AdapterView<*>?,
            view: View?,
            position: Int,
            id: Long,
        ) {
            viewModel.selectSaved(listedSnapshots.getOrNull(position)?.uid)
        }

        override fun onNothingSelected(parent: AdapterView<*>?) {
            viewModel.selectSaved(null)
        }
    }

    /** 当前 Spinner 选中的快照 uid；没有可选项时报错并返回 null。 */
    private fun selectedSnapshotUid(): Long? {
        val uid = listedSnapshots.getOrNull(binding.savedSnapshotSpinner.selectedItemPosition)?.uid
        if (uid == null) {
            viewModel.reportError(getString(R.string.error_reincarnate_snapshot_unselected))
        }
        return uid
    }

    // =========================================================================
    // 按钮
    // =========================================================================

    /** 只算一下要发多少包，不写入账号。 */
    private fun previewPlan() {
        val inputs = currentInputs()
        viewModel.saveInputs(inputs)
        viewModel.preview(inputs.sourceJson, options(inputs))
    }


    /**
     * 开始前先算一次计划，既能把输入错误挡下来，也能把包数写进确认弹窗。
     */
    private fun confirmStart() {
        val inputs = currentInputs()
        viewModel.saveInputs(inputs)
        val options = options(inputs)
        viewModel.preview(inputs.sourceJson, options)
        if (viewModel.state.value.lastError != null) {
            return
        }

        lifecycleScope.launch {
            val target = viewModel.resolveTarget(inputs.targetQr) ?: return@launch
            val resumeHint = target.lastFolder
                ?.let { getString(R.string.reincarnate_resume_folder, it) }
                ?: getString(R.string.reincarnate_resume_new)
            val planSummary = viewModel.state.value.planSummary
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.dialog_reincarnate_confirm_title)
                .setMessage(
                    getString(
                        R.string.dialog_reincarnate_confirm_message,
                        "$planSummary\n$resumeHint"
                    )
                )
                .setPositiveButton(R.string.dialog_reincarnate_confirm) { _, _ ->
                    viewModel.start(inputs.sourceJson, inputs.targetQr, options, target)
                }
                .setNegativeButton(R.string.dialog_cancel, null)
                .show()
        }
    }

    private fun pickSourceFile() {
        launchSaf(
            sourceFilePicker,
            SOURCE_FILE_MIME_TYPES,
            R.string.error_reincarnate_no_file_picker
        )
    }

    /**
     * 拉起 SAF 对话框，并兜住「本机没有系统文件选择器」这种 ROM 缺陷。
     *
     * ★ 精简 ROM 可能没装系统「文件」应用（DocumentsUI），这时
     *   `ACTION_OPEN_DOCUMENT` / `ACTION_CREATE_DOCUMENT` 找不到处理者，
     *   `launch` 会抛 `ActivityNotFoundException` 把整个 App 崩掉 —— 必须自己接住，
     *   退回「粘贴」入口，而不是让用户看到一个闪退。
     *
     * @return 是否成功拉起
     */
    private fun <I> launchSaf(
        launcher: ActivityResultLauncher<I>,
        input: I,
        errorRes: Int,
    ): Boolean = runCatching { launcher.launch(input) }
        .onFailure { viewModel.reportError(getString(errorRes)) }
        .isSuccess

    private fun importSourceFile(uri: Uri) {
        val context = requireContext().applicationContext
        val name = displayName(uri)
        lifecycleScope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        input.bufferedReader().readText()
                    }
                }.getOrNull()
            }
            if (text.isNullOrBlank()) {
                viewModel.reportError(getString(R.string.error_reincarnate_file_read_failed, name))
                return@launch
            }
            // ★ 只交给 ViewModel（它才是源的归属），不进输入框 —— 大 JSON 的 setText 会卡死主线程。
            viewModel.adoptManualSource(text, getString(R.string.source_name_file, name))
            viewModel.log(getString(R.string.log_reincarnate_source_from_file, name, text.length))
        }
    }

    private fun pasteSourceFromClipboard() {
        val text = clipboardText()
        if (text == null) {
            viewModel.reportError(getString(R.string.error_reincarnate_clipboard_empty))
            return
        }
        viewModel.adoptManualSource(text, getString(R.string.source_name_clipboard))
        viewModel.log(getString(R.string.log_reincarnate_source_from_clipboard, text.length))
    }

    private fun pasteQrFromClipboard() {
        pasteQrInto(binding.targetQrInput)
    }

    private fun pasteSourceQrFromClipboard() {
        pasteQrInto(binding.sourceQrInput)
    }

    /** 把剪贴板里的 SGWCMAID 二维码填进指定输入框。 */
    private fun pasteQrInto(field: EditText) {
        val text = clipboardText()
        if (text == null || !text.startsWith(AimeConstants.SGWC_PREFIX)) {
            viewModel.reportError(getString(R.string.error_reincarnate_clipboard_qr_missing))
            return
        }
        field.setText(text)
        viewModel.log(getString(R.string.log_reincarnate_qr_from_clipboard))
    }

    private fun clearSource() {
        binding.sourceJsonInput.text?.clear()
        viewModel.clearSource()
        viewModel.log(getString(R.string.log_reincarnate_source_cleared))
    }

    /**
     * 自动填充：没登录二维码文本、页面空闲、**本页当前可见**、且目标框还是空的时候才填。
     *
     * ★ 必须判可见性 —— 三个 Tab 是 add + hide/show 的关系，切到别的页时本 Fragment
     *   依然是 RESUMED，onResume 照样会把剪贴板里的二维码填进这个看不见的输入框。
     */
    private fun fillQrFromClipboardIfPossible() {
        val state = viewModel.state.value
        if (state.busy || targetQr().isNotEmpty() || !binding.root.isShown) {
            return
        }
        val text = clipboardText() ?: return
        if (!text.startsWith(AimeConstants.SGWC_PREFIX)) {
            return
        }
        binding.targetQrInput.setText(text)
        viewModel.log(getString(R.string.log_reincarnate_qr_from_clipboard))
    }

    // =========================================================================
    // 输入读取
    // =========================================================================

    private fun sourceJson(): String = binding.sourceJsonInput.text?.toString().orEmpty()

    private fun sourceQr(): String = binding.sourceQrInput.text?.toString()?.trim().orEmpty()

    private fun targetQr(): String = binding.targetQrInput.text?.toString()?.trim().orEmpty()

    /**
     * 把草稿翻译成核心库的参数。
     *
     * 每包数量按 Python 版的规则只允许 4/3/1（2 首一包会被服务器拒收）；
     * 间隔至少留 1 秒，免得把服务器打爆。
     */
    private fun options(inputs: ReincarnateInputs): ReincarnateOptions = ReincarnateOptions(
        limit = inputs.limit,
        perPacket = inputs.perPacket,
        waitSeconds = inputs.waitSeconds,
        cloneIdentity = inputs.cloneIdentity,
    )

    /** 把草稿写回界面。首次进入时草稿就是默认值，旋转回来后则是用户填过的内容。 */
    private fun applyInputs(inputs: ReincarnateInputs) {
        binding.sourceJsonInput.setText(inputs.sourceJson)
        binding.sourceQrInput.setText(inputs.sourceQr)
        binding.targetQrInput.setText(inputs.targetQr)
        binding.limitInput.setText(inputs.limit.toString())
        binding.waitInput.setText(inputs.waitSeconds.toString())
        binding.perPacketSpinner.setSelection(
            when (inputs.perPacket) {
                3 -> PER_PACKET_INDEX_THREE
                1 -> PER_PACKET_INDEX_ONE
                else -> PER_PACKET_INDEX_FOUR
            }
        )
        binding.cloneIdentityCheck.isChecked = inputs.cloneIdentity
    }

    private fun currentInputs(): ReincarnateInputs = ReincarnateInputs(
        sourceJson = sourceJson(),
        sourceQr = sourceQr(),
        targetQr = targetQr(),
        limit = binding.limitInput.text?.toString()?.trim()?.toIntOrNull()
            ?.coerceAtLeast(0) ?: DEFAULT_LIMIT,
        perPacket = perPacket(),
        waitSeconds = binding.waitInput.text?.toString()?.trim()?.toDoubleOrNull()
            ?.coerceAtLeast(MIN_WAIT_SECONDS) ?: DEFAULT_WAIT_SECONDS,
        cloneIdentity = binding.cloneIdentityCheck.isChecked,
    )

    private fun perPacket(): Int = when (binding.perPacketSpinner.selectedItemPosition) {
        PER_PACKET_INDEX_THREE -> 3
        PER_PACKET_INDEX_ONE -> 1
        else -> 4
    }

    /**
     * 视图销毁前存一次草稿（旋转屏幕同样走这里），
     * 免得用户刚粘贴的几百 KB JSON 白填。
     */
    override fun onDestroyView() {
        if (::binding.isInitialized) {
            viewModel.saveInputs(currentInputs())
        }
        renderedLogs = null
        renderedSnapshots = null
        listedSnapshots = emptyList()
        pendingExport = null
        super.onDestroyView()
    }

    // =========================================================================
    // 剪贴板 / 文件
    // =========================================================================

    private fun clipboardText(): String? {
        val clipboard =
            requireContext().getSystemService(ClipboardManager::class.java) ?: return null
        if (!clipboard.hasPrimaryClip()) {
            return null
        }
        val clip = clipboard.primaryClip ?: return null
        if (clip.itemCount <= 0) {
            return null
        }
        return clip.getItemAt(0)
            .coerceToText(requireContext())
            ?.toString()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    /** 取 SAF 里显示的文件名，取不到就退回路径末段。 */
    private fun displayName(uri: Uri): String {
        val queried = runCatching {
            requireContext().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
        }.getOrNull()
        return queried ?: uri.lastPathSegment ?: uri.toString()
    }

    companion object {
        const val TAG = "ReincarnateFragment"

        private const val JSON_MIME_TYPE = "application/json"

        private val SOURCE_FILE_MIME_TYPES = arrayOf(
            JSON_MIME_TYPE,
            "text/plain",
            "application/octet-stream",
        )

        /** 快照列表里显示的时间，例如 `10-08 21:30`。 */
        private val SNAPSHOT_TIME_FORMAT = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

        private const val DEFAULT_LIMIT = 0
        private const val DEFAULT_WAIT_SECONDS = 5.0
        private const val MIN_WAIT_SECONDS = 1.0
        private const val PER_PACKET_INDEX_FOUR = 0
        private const val PER_PACKET_INDEX_THREE = 1
        private const val PER_PACKET_INDEX_ONE = 2

        fun newInstance() = ReincarnateFragment()
    }
}
