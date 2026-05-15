package com.maimai.android.session

/**
 * 主页面的完整 UI 状态。
 *
 * Android UI 最好由一个明确的状态对象驱动：
 * - Controller 修改 ConsoleUiState。
 * - Activity 观察状态并刷新控件。
 *
 * 这样可以减少“某个按钮忘记禁用”“某个文本忘记刷新”之类的问题。
 */
data class ConsoleUiState(
    /** 用户输入的二维码字符串。 */
    val qrCode: String = "",

    /** 当前是否正在执行登录、请求或 logout。busy=true 时按钮会被禁用。 */
    val busy: Boolean = false,

    /** 当前是否持有有效 LoginSession。 */
    val loggedIn: Boolean = false,

    /** 顶部状态文案，例如：未登录、登录中、已登录、失败。 */
    val status: String = "未登录",

    /** 登录成功后的用户 ID。未登录时显示 "-"。 */
    val userId: String = "-",

    /** 登录成功后的 timestamp。后续请求依赖它。 */
    val timestamp: String = "-",

    /** 是否已经拿到 cookie。cookie 对后续 API 很重要。 */
    val cookieStatus: String = "无",

    /** 是否已经拿到 token。token 用于登录。 */
    val tokenStatus: String = "无",

    /** 最近一次错误信息。为 null 时隐藏错误区域。 */
    val lastError: String? = null,
)
