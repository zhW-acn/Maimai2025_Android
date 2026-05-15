package com.maimai.android.session

/**
 * 主页面的完整 UI 状态。
 *
 * 在 MVVM 中，ViewModel 不应该直接拿 Activity 或 Button 来改界面。
 * 更推荐的方式是：
 * 1. ViewModel 修改 ConsoleUiState。
 * 2. Activity 观察 ConsoleUiState。
 * 3. Activity 根据状态刷新 TextView、Button、ProgressBar。
 *
 * 这样数据流是单向的，排查问题会简单很多。
 */
data class ConsoleUiState(
    /** 用户输入的二维码字符串。 */
    val qrCode: String = "",

    /** 当前是否正在执行登录或业务请求。busy=true 时按钮会被禁用。 */
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
