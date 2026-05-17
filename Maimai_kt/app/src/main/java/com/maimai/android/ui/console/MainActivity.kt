package com.maimai.android.ui.console

import android.Manifest
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import com.blankj.utilcode.util.SPUtils
import com.maimai.android.AppIntentActions
import com.maimai.android.R
import com.maimai.android.ui.console.actions.ActionGridSpacingDecoration
import com.maimai.android.ui.console.actions.ConsoleActionAdapter
import com.maimai.android.ui.console.actions.ConsoleActionId
import com.maimai.android.ui.console.actions.buildConsoleActions
import com.maimai.android.databinding.ActivityMainBinding
import com.maimai.android.logging.AppMaimaiLogger
import com.maimai.android.ui.console.dialog.UploadScoreDialog
import com.maimai.android.ui.console.session.ConsoleUiState
import com.maimai.android.ui.console.session.MaimaiConsoleViewModel
import com.maimai.kt.constants.AimeConstants
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * App 的主页面，也是 MVVM 里的 View。
 *
 * 现在页面使用 DataBinding：
 * - XML 用 @{state.xxx} 显示状态。
 * - XML 用 @{() -> viewModel.xxx()} 绑定按钮点击。
 * - Activity 只负责把 StateFlow 收集出来，赋给 binding.state。
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val viewModel: MaimaiConsoleViewModel by viewModels()

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                logger.info(getString(R.string.log_notification_permission_granted))
            } else {
                logger.info(getString(R.string.log_notification_permission_denied))
            }
            requestInitialBackgroundPermissionIfNeeded()
        }

    @Inject
    lateinit var logger: AppMaimaiLogger

    private lateinit var binding: ActivityMainBinding
    private lateinit var actionAdapter: ConsoleActionAdapter
    private var latestState = ConsoleUiState()

    /**
     * Activity 创建时初始化 XML Binding、按钮列表、权限请求和状态监听。
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)

        // lifecycleOwner 让 DataBinding 能感知 Activity 生命周期。
        // 后续如果 XML 直接绑定 LiveData，这一行也会让 LiveData 自动刷新界面。
        binding.lifecycleOwner = this

        // viewModel 给 XML 里的 android:onClick / android:onTextChanged 表达式调用。
        binding.viewModel = viewModel

        // state 给 XML 里的 @{state.xxx} 表达式使用，先给一个默认值避免空状态。
        binding.state = latestState

        setContentView(binding.root)

        setupActionsList()
        bindBackPressed()
        requestNotificationPermissionIfNeeded()
        collectState()
        handleIntentAction(intent)
    }

    /**
     * singleTop 模式下，通知栏再次打开已有 Activity 时会回调这里。
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntentAction(intent)
    }

    /**
     * 页面回到前台时尝试从剪切板自动填入二维码。
     */
    override fun onResume() {
        super.onResume()
        // onResume 时 Activity 正在回到前台，但窗口可能还没有真正拿到焦点。
        // post 到下一轮主线程消息，可以提高 Android 10+ 读取剪切板的成功率。
        binding.root.post {
            fillQrCodeFromClipboardIfPossible()
        }
    }

    /**
     * 窗口真正拿到焦点后，再补读一次剪切板。
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            // Android 对剪切板读取有前台/焦点限制。
            // 真正获得窗口焦点后再读一次，比只放在 onResume 更稳。
            binding.root.postDelayed(
                {
                    fillQrCodeFromClipboardIfPossible()
                },
                CLIPBOARD_READ_DELAY_MILLIS,
            )
        }
    }

    /**
     * Android 13 及以上需要主动请求通知权限。
     */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                requestInitialBackgroundPermissionIfNeeded()
            } else {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            requestInitialBackgroundPermissionIfNeeded()
        }
    }

    /**
     * 初始化功能按钮 RecyclerView。
     */
    private fun setupActionsList() {
        actionAdapter = ConsoleActionAdapter(
            onClick = ::handleActionClick,
            onLongClick = ::handleActionLongClick,
        )
        binding.actionsList.layoutManager = GridLayoutManager(this, ACTION_GRID_SPAN_COUNT)
        binding.actionsList.addItemDecoration(
            ActionGridSpacingDecoration(
                spanCount = ACTION_GRID_SPAN_COUNT,
                horizontalSpacing = resources.getDimensionPixelSize(R.dimen.action_grid_horizontal_spacing),
                verticalSpacing = resources.getDimensionPixelSize(R.dimen.action_grid_vertical_spacing),
            )
        )
        binding.actionsList.adapter = actionAdapter
        binding.actionsList.itemAnimator = null
        updateActions(latestState)
    }

    /**
     * 根据登录状态和 busy 状态刷新按钮是否可点击。
     */
    private fun updateActions(state: ConsoleUiState) {
        actionAdapter.submitList(
            buildConsoleActions(
                enabled = !state.busy && state.loggedIn,
            )
        )
    }

    /**
     * 把按钮点击事件分发给对应的 ViewModel 方法。
     */
    private fun handleActionClick(actionId: ConsoleActionId) {
        when (actionId) {
            ConsoleActionId.UploadScore -> showUploadScoreDialog()
            ConsoleActionId.ChargeTicket -> viewModel.buyTicket()
        }
    }

    /**
     * 处理功能按钮长按事件；当前版本暂时没有启用长按能力。
     */
    private fun handleActionLongClick(actionId: ConsoleActionId): Boolean =
        when (actionId) {
            ConsoleActionId.UploadScore,
            ConsoleActionId.ChargeTicket,
                -> false
        }

    /**
     * 处理通知栏点击带回来的内部 action。
     */
    private fun handleIntentAction(intent: Intent?) {
        when (intent?.action) {
            AppIntentActions.LOGOUT_AFTER_NO_UPSERT_TIMEOUT -> {
                viewModel.logoutAfterNoUpsertTimeout()
                intent.action = null
            }
        }
    }

    /**
     * 弹出上传成绩表单，并在用户确认后把表单转换成 MusicDetail。
     */
    private fun showUploadScoreDialog() {
        UploadScoreDialog(
            activity = this,
            onInvalidInput = {
                logger.info(getString(R.string.error_upload_score_form_required))
            },
            onSubmit = viewModel::uploadScore,
        ).show()
    }

    /**
     * 首次启动时弹出后台设置说明，后续启动不再重复打扰用户。
     */
    private fun requestInitialBackgroundPermissionIfNeeded() {
        val preferences = SPUtils.getInstance(PREFS_NAME)
        if (preferences.getBoolean(KEY_BACKGROUND_PERMISSION_REQUESTED, false)) {
            return
        }

        preferences.put(KEY_BACKGROUND_PERMISSION_REQUESTED, true)

        binding.root.postDelayed(
            {
                showInitialBackgroundSettingsDialog()
            },
            INITIAL_BACKGROUND_PERMISSION_DELAY_MILLIS,
        )
    }

    /**
     * 显示自定义说明弹窗，引导用户进入系统应用详情页。
     */
    private fun showInitialBackgroundSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_background_title)
            .setMessage(R.string.dialog_background_message)
            .setPositiveButton(R.string.dialog_background_open_settings) { _, _ ->
                openAppBackgroundSettings()
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    /**
     * 打开当前 App 的系统详情页，让用户手动允许完全后台行为。
     */
    private fun openAppBackgroundSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:$packageName")
        }
        startActivity(intent)
        logger.info(getString(R.string.log_open_background_settings))
    }

    /**
     * 从剪切板自动读取 SGWCMAID 二维码文本。
     *
     * 剪切板没有类似相机、通知那样的运行时权限可以手动申请。
     * Android 10 以后要求普通 App 基本只能在前台/获得焦点时读取剪切板；
     * Android 12 以后读取时系统可能显示“某应用读取了剪切板”的隐私提示。
     *
     * 这里只在未登录、未执行任务时自动填入，避免用户已经登录后又被剪切板覆盖。
     */
    private fun fillQrCodeFromClipboardIfPossible() {
        if (latestState.busy || latestState.loggedIn) {
            logger.info(
                getString(
                    R.string.log_clipboard_skip_busy_logged_in,
                    latestState.busy,
                    latestState.loggedIn
                )
            )
            return
        }

        val clipboard = getSystemService(ClipboardManager::class.java)
        if (!clipboard.hasPrimaryClip()) {
            logger.info(getString(R.string.log_clipboard_no_primary_clip))
            return
        }

        val clip = clipboard.primaryClip
        if (clip == null) {
            logger.info(getString(R.string.log_clipboard_primary_clip_null))
            return
        }
        if (clip.itemCount <= 0) {
            logger.info(getString(R.string.log_clipboard_empty_clip))
            return
        }

        val text = clip.getItemAt(0)
            .coerceToText(this)
            ?.toString()
            ?.trim()
        if (text.isNullOrBlank()) {
            logger.info(getString(R.string.log_clipboard_first_item_not_text))
            return
        }

        if (!text.startsWith(AimeConstants.SGWC_PREFIX)) {
            logger.info(
                getString(
                    R.string.log_clipboard_prefix_mismatch,
                    AimeConstants.SGWC_PREFIX
                )
            )
            return
        }
        if (text == latestState.qrCode) {
            logger.info(getString(R.string.log_clipboard_same_qrcode))
            return
        }

        viewModel.setQrCode(text)
        logger.info(getString(R.string.log_clipboard_qrcode_filled))
    }

    private companion object {
        const val CLIPBOARD_READ_DELAY_MILLIS = 300L
        const val INITIAL_BACKGROUND_PERMISSION_DELAY_MILLIS = 500L
        const val PREFS_NAME = "maimai_android_preferences"
        const val KEY_BACKGROUND_PERMISSION_REQUESTED = "background_permission_requested"
        const val ACTION_GRID_SPAN_COUNT = 2
    }

    private fun bindBackPressed() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            /**
             * 拦截系统返回键，避免还没 upsertUserAll 就误退出并尝试 logout。
             */
            override fun handleOnBackPressed() {
                if (!latestState.loggedIn) {
                    finish()
                    return
                }

                if (latestState.upsertUserAllCompleted || latestState.logoutAllowedByTimeout) {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle(R.string.dialog_logged_in_title)
                        .setMessage(R.string.dialog_logged_in_message)
                        .setPositiveButton(R.string.dialog_logout_and_exit) { _, _ ->
                            viewModel.logout()
                            finish()
                        }
                        .setNegativeButton(R.string.dialog_stay, null)
                        .show()
                } else {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle(R.string.dialog_cannot_logout_title)
                        .setMessage(R.string.dialog_cannot_logout_message)
                        .setPositiveButton(R.string.dialog_stay, null)
                        .show()
                }
            }
        })
    }

    /**
     * 收集 ViewModel 和 logger 暴露出来的数据流。
     *
     * state 的文字、按钮状态、ProgressBar 都已经写进 XML 绑定表达式里。
     * 所以这里拿到新 state 后，只需要 binding.state = state。
     */
    private fun collectState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.state.collect { state ->
                        latestState = state
                        binding.state = state
                        updateActions(state)
                    }
                }
                launch {
                    logger.logs.collect { logs ->
                        val shouldStickToBottom = !binding.logsScroll.canScrollVertically(1)
                        binding.logsText.text =
                            if (logs.isEmpty()) getString(R.string.logs_empty) else logs.joinToString(
                                "\n"
                            )
                        if (shouldStickToBottom) {
                            binding.logsScroll.post {
                                binding.logsScroll.scrollTo(0, binding.logsText.height)
                            }
                        }
                    }
                }
            }
        }
    }
}
