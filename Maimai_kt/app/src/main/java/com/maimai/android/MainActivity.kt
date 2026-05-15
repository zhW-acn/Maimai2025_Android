package com.maimai.android

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.maimai.android.databinding.ActivityMainBinding
import com.maimai.android.logging.AppMaimaiLogger
import com.maimai.android.session.ConsoleUiState
import com.maimai.android.session.MaimaiConsoleController
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * App 的主页面。
 *
 * Activity 可以理解成 Android 里的一块屏幕。这里负责：
 * 1. 加载 XML 布局。
 * 2. 绑定按钮/输入框事件。
 * 3. 观察 Controller 暴露的 UI 状态，并把状态渲染到界面上。
 *
 * 真正的登录、请求、logout 逻辑不放在 Activity 里，是为了让页面代码保持简单。
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    /**
     * @Inject 表示这个对象由 Hilt 自动提供。
     *
     * MaimaiConsoleController 是页面的业务控制器，Activity 不需要手动 new 它。
     */
    @Inject lateinit var controller: MaimaiConsoleController

    /**
     * 这个 logger 同时写 Timber/Logcat 和页面底部日志面板。
     */
    @Inject lateinit var logger: AppMaimaiLogger

    /**
     * ViewBinding 由 activity_main.xml 自动生成。
     *
     * 开启 ViewBinding 后，XML 里有 id 的控件会变成 binding.xxx，避免 findViewById。
     */
    private lateinit var binding: ActivityMainBinding

    /**
     * 保存最近一次 UI 状态，主要给返回键判断是否仍处于登录状态。
     */
    private var latestState = ConsoleUiState()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // inflate 会把 XML 布局文件转换成真正的 View 对象树。
        binding = ActivityMainBinding.inflate(layoutInflater)

        // setContentView 告诉 Activity：当前屏幕显示 binding.root 这棵 View 树。
        setContentView(binding.root)

        setupLabels()
        bindEvents()
        collectState()
    }

    /**
     * include 进来的状态行共用同一个 XML，所以它们的 label 在代码里分别设置。
     */
    private fun setupLabels() {
        binding.statusRow.labelText.text = "状态"
        binding.userIdRow.labelText.text = "User ID"
        binding.timestampRow.labelText.text = "Timestamp"
        binding.cookieRow.labelText.text = "Cookie"
        binding.tokenRow.labelText.text = "Token"
    }

    /**
     * 这里集中绑定 UI 事件。
     *
     * 注意：网络请求必须放进协程里执行，不能直接阻塞主线程。
     * lifecycleScope 是和 Activity 生命周期绑定的协程作用域。
     */
    private fun bindEvents() {
        // TextWatcher 用来监听输入框变化，把二维码字符串同步到 Controller 的状态里。
        binding.qrInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                controller.setQrCode(s?.toString().orEmpty())
            }

            override fun afterTextChanged(s: Editable?) = Unit
        })

        // 每个按钮点击后都启动一个协程，避免在主线程直接做网络请求。
        binding.loginButton.setOnClickListener { lifecycleScope.launch { controller.login() } }
        binding.logoutButton.setOnClickListener { lifecycleScope.launch { controller.logout() } }
        binding.uploadButton.setOnClickListener { lifecycleScope.launch { controller.uploadDemoScore() } }
        binding.unlockButton.setOnClickListener { lifecycleScope.launch { controller.unlockDemoMaster() } }
        binding.versionButton.setOnClickListener { lifecycleScope.launch { controller.changeVersion() } }
        binding.clearLogsButton.setOnClickListener { controller.clearLogs() }

        // 拦截系统返回键：如果当前已经登录，提醒用户先 logout。
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (latestState.loggedIn) {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("当前仍处于登录状态")
                        .setMessage("离开前建议先 logout，否则会被关小黑屋。")
                        .setPositiveButton("Logout 并退出") { _, _ ->
                            lifecycleScope.launch {
                                controller.logout()
                                finish()
                            }
                        }
                        .setNegativeButton("留在页面", null)
                        .show()
                } else {
                    finish()
                }
            }
        })
    }

    /**
     * 收集 StateFlow 并刷新界面。
     *
     * repeatOnLifecycle 会在页面 STARTED 时开始收集，页面停止时自动暂停，
     * 这样可以避免 Activity 不可见时还继续刷新 UI。
     */
    private fun collectState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    controller.state.collect { state ->
                        latestState = state
                        renderState(state)
                    }
                }
                launch {
                    logger.logs.collect { logs ->
                        binding.logsText.text = if (logs.isEmpty()) "暂无日志" else logs.joinToString("\n")

                        // post 会等 ScrollView 完成布局后再滚动到底部，确保最新日志可见。
                        binding.logsScroll.post { binding.logsScroll.fullScroll(View.FOCUS_DOWN) }
                    }
                }
            }
        }
    }

    /**
     * 根据 UI 状态刷新控件内容和按钮可用性。
     *
     * Activity 不自己判断业务流程，只根据 ConsoleUiState 做展示。
     */
    private fun renderState(state: ConsoleUiState) {
        // 避免重复 setText 触发 TextWatcher，只有内容不一致时才回填。
        if (binding.qrInput.text.toString() != state.qrCode) {
            binding.qrInput.setText(state.qrCode)
            binding.qrInput.setSelection(binding.qrInput.text.length)
        }

        binding.statusRow.valueText.text = state.status
        binding.userIdRow.valueText.text = state.userId
        binding.timestampRow.valueText.text = state.timestamp
        binding.cookieRow.valueText.text = state.cookieStatus
        binding.tokenRow.valueText.text = state.tokenStatus

        binding.errorText.text = state.lastError.orEmpty()
        binding.errorText.visibility = if (state.lastError == null) View.GONE else View.VISIBLE
        binding.progress.visibility = if (state.busy) View.VISIBLE else View.GONE

        // 登录中/请求中禁用控件，防止重复点击导致并发请求。
        binding.qrInput.isEnabled = !state.busy && !state.loggedIn
        binding.loginButton.isEnabled = !state.busy && !state.loggedIn && state.qrCode.isNotBlank()
        binding.logoutButton.isEnabled = !state.busy && state.loggedIn
        binding.uploadButton.isEnabled = !state.busy && state.loggedIn
        binding.unlockButton.isEnabled = !state.busy && state.loggedIn
        binding.versionButton.isEnabled = !state.busy && state.loggedIn
    }
}
