package com.okaca.maimai.android.ui.console.session

/**
 * MainActivity 的完整界面状态。
 *
 * XML 通过 DataBinding 读取这个对象里的字段，决定文案、按钮是否可点、
 * ProgressBar 是否显示等。
 */
data class ConsoleUiState(
    /** 用户输入的二维码字符串。 */
    val qrCode: String = "",

    /** 当前是否正在执行登录、请求或登出。 */
    val busy: Boolean = false,

    /** 当前是否持有 LoginSession。 */
    val loggedIn: Boolean = false,

    /** 已经登录，但用户不在白名单内，只允许手动登出。 */
    val accessBlocked: Boolean = false,

    /**
     * 本次登录后是否已经成功执行过 upsertUserAll。
     *
     * 业务规则：没有 upsertUserAll 前不允许登出，否则登出无效，
     * 下一次登录可能会被服务端短时间拒绝。
     */
    val upsertUserAllCompleted: Boolean = false,

    /**
     * 登录后超过 60 秒仍未 upsert 时，允许用户手动登出。
     */
    val logoutAllowedByTimeout: Boolean = false,

    /** 顶部状态文案。 */
    val status: String = "",

    /** 登录成功后的用户 ID。 */
    val userId: String = "",

    /** 登录成功后的 timestamp。 */
    val timestamp: String = "",

    /** Cookie 状态。 */
    val cookieStatus: String = "",

    /** Token 状态。 */
    val tokenStatus: String = "",

    /** upsertUserAll 状态。 */
    val upsertStatus: String = "",

    /** 是否正在等待 upsertUserAll 前的强制倒计时。 */
    val upsertWaiting: Boolean = false,

    /** upsertUserAll 请求前还需要等待多少秒。 */
    val upsertWaitRemainingSeconds: Int = 0,

    /** upsertUserAll 请求前总共需要等待多少秒。 */
    val upsertWaitTotalSeconds: Int = 0,

    /** upsertUserAll 等待进度，范围 0-100。 */
    val upsertWaitProgress: Int = 0,

    /** 展示给用户看的倒计时文案。 */
    val upsertWaitText: String = "",

    /** 最近一次错误信息。为 null 时隐藏错误区域。 */
    val lastError: String? = null,
)

