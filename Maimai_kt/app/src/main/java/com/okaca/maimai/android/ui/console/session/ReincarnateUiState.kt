package com.okaca.maimai.android.ui.console.session

import com.okaca.maimai.android.storage.SnapshotStore

/**
 * 账号迁移（转生）页面状态。
 *
 * logs 直接累积成一段文本交给 TextView 显示，和「上传到水鱼」页的日志面板一致。
 */
data class ReincarnateUiState(
    /** 页面日志（带时间戳的文本块）。 */
    val logs: String = "",
    /** 当前阶段的一句话说明。 */
    val status: String = "",
    /** 是否有任务在跑（含预览）。 */
    val busy: Boolean = false,
    /** 是否正在真实迁移（用于禁用输入）。 */
    val running: Boolean = false,
    /** 是否正在抓取源账号快照。 */
    val capturing: Boolean = false,
    /** 抓取进度，例如「正在抓取 userMapList（3/19）」。 */
    val captureProgressText: String = "",
    /** 本机已保存的快照（新的在前）。 */
    val savedSnapshots: List<SnapshotStore.SavedSnapshot> = emptyList(),
    /** 当前在「已保存的快照」里选中项。 */
    val selectedSnapshotUid: Long? = null,
    /**
     * 当前源数据（抓取 / 载入 / 粘贴 / 导入进来的那一份）。
     *
     * ★ **整份 JSON 只留在这里，绝不塞进输入框**：源快照动辄 1～2 MB，
     *   `EditText.setText()` 一次就是好几秒的主线程阻塞 —— 实测 1.68 MB 会把页面卡到
     *   ANR（系统直接把 App 杀掉，看起来就是「闪退」）。界面只显示名字和字符数。
     */
    val sourceJson: String? = null,
    /** 当前源的名字，例如「快照 12236556」「剪贴板」「文件 a.json」。 */
    val sourceName: String? = null,
    /**
     * 是否要把手动输入框清空。
     *
     * 因为输入框里的文本**优先于** [sourceJson]（手动粘贴是最后一手），
     * 新源进来时必须把输入框让出来，否则用户刚抓的快照会被输入框里的旧文本顶掉。
     * Fragment 清完调 [ReincarnateViewModel.consumeClearManualInput]。
     */
    val clearManualInput: Boolean = false,
    /** 源数据摘要，例如「曲目记录 739 条」。 */
    val sourceSummary: String = "",
    /** 迁移计划摘要，例如「共 739 首 -> 186 包（4/4/3/...）」。 */
    val planSummary: String = "",
    /** 当前包进度，例如「第 12/186 包（4 首） 已上传 11 包」。 */
    val packetText: String = "",
    /** 等待倒计时文案。 */
    val waitText: String = "",
    /** 等待进度百分比，给横向进度条用。 */
    val waitProgress: Int = 0,
    val lastError: String? = null,
)

/**
 * 页面输入的草稿。
 *
 * 源 JSON 可能有几百 KB，屏幕旋转会把 EditText 清空，
 * 所以草稿放在 ViewModel 里（旋转后还在），由 Fragment 在视图销毁前存、创建后取。
 */
data class ReincarnateInputs(
    val sourceJson: String = "",
    /** 源账号二维码（抓取用）。 */
    val sourceQr: String = "",
    val targetQr: String = "",
    /** 曲目上限，0 = 全部。 */
    val limit: Int = 0,
    /** 每包曲目数，只能是 1/3/4。 */
    val perPacket: Int = 4,
    val waitSeconds: Double = 60.0,
    val cloneIdentity: Boolean = false,
)
