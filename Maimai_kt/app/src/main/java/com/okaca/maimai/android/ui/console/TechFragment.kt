package com.okaca.maimai.android.ui.console

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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import com.blankj.utilcode.util.SPUtils
import com.okaca.maimai.android.AppIntentActions
import com.okaca.maimai.android.R
import com.okaca.maimai.android.ui.console.actions.ActionGridSpacingDecoration
import com.okaca.maimai.android.ui.console.actions.ConsoleActionAdapter
import com.okaca.maimai.android.ui.console.actions.ConsoleActionId
import com.okaca.maimai.android.ui.console.actions.buildConsoleActions
import com.okaca.maimai.android.databinding.FragmentTechBinding
import com.okaca.maimai.android.logging.AppMaimaiLogger
import com.okaca.maimai.android.ui.console.dialog.KaleidxScopeDialog
import com.okaca.maimai.android.ui.console.dialog.ManualLogoutDialog
import com.okaca.maimai.android.ui.console.dialog.TicketQueryDialog
import com.okaca.maimai.android.ui.console.dialog.UploadCharasDialog
import com.okaca.maimai.android.ui.console.dialog.UploadPointDialog
import com.okaca.maimai.android.ui.console.dialog.UploadScoreDialog
import com.okaca.maimai.android.ui.console.session.ConsoleUiState
import com.okaca.maimai.android.ui.console.session.MaimaiConsoleViewModel
import kt.constants.AimeConstants
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import timber.log.Timber
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

/**
 * App 的主页面，也是 MVVM 里的 View。
 *
 * 页面使用 DataBinding：XML 通过 @{state.xxx} 显示状态，
 * XML 通过 @{viewModel.xxx()} 调用简单事件，Activity 负责收集 StateFlow 并把新状态交给 binding。
 */
@AndroidEntryPoint
class TechFragment : Fragment() {
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

    private lateinit var binding: FragmentTechBinding
    private lateinit var actionAdapter: ConsoleActionAdapter
    private var latestState = ConsoleUiState()
    private var whitelistBlockedDialogUserId: String? = null

    /**
     * Activity 创建时初始化 Binding、按钮列表、权限请求和状态监听。
     */
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = FragmentTechBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // lifecycleOwner 璁?DataBinding 鑳芥劅鐭?Activity 鐢熷懡鍛ㄦ湡銆?

        binding.lifecycleOwner = viewLifecycleOwner

        binding.viewModel = viewModel

        binding.state = latestState

        setupActionsList()
        setupLogoutLongClick()
        bindBackPressed()
        requestNotificationPermissionIfNeeded()
        collectState()
    }

    fun handleNewIntent(intent: Intent) {
        handleIntentAction(intent)
    }

    /**
     * 页面回到前台时，尝试从剪贴板自动填入二维码。
     */
    override fun onResume() {
        super.onResume()


        binding.root.post {
            fillQrCodeFromClipboardIfPossible()
        }
    }

    /**
     * 窗口真正拿到焦点后，再补读一次剪贴板。
     */
    fun onHostWindowFocusChanged(hasFocus: Boolean) {
        if (hasFocus) {


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
            if (requireContext().checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                requestInitialBackgroundPermissionIfNeeded()
            } else {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            requestInitialBackgroundPermissionIfNeeded()
        }
    }

    /**
     * 初始化功能按钮列表，并把点击事件分发给 ViewModel。
     */
    private fun setupActionsList() {
        actionAdapter = ConsoleActionAdapter(
            onClick = ::handleActionClick,
            onLongClick = ::handleActionLongClick,
        )
        binding.actionsList.layoutManager =
            GridLayoutManager(requireContext(), ACTION_GRID_SPAN_COUNT)
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
     * 根据登录状态和 busy 状态刷新按钮是否可点。
     */
    private fun updateActions(state: ConsoleUiState) {
        actionAdapter.submitList(
            buildConsoleActions(
                enabled = !state.busy && state.loggedIn && !state.accessBlocked,
                loggedIn = state.loggedIn,
                accessBlocked = state.accessBlocked,
                busy = state.busy,
            )
        )
    }

    /**
     * 处理功能按钮点击事件。
     */
    private fun handleActionClick(actionId: ConsoleActionId) {
        when (actionId) {
            ConsoleActionId.UploadScore -> showUploadScoreDialog()
            ConsoleActionId.ChargeTicket -> viewModel.buyTicket()
            ConsoleActionId.Point -> viewModel.uploadPoint()
            ConsoleActionId.CharacterLevels -> showUploadCharasDialog()
            ConsoleActionId.KaleidxScope -> showKaleidxScopeDialog()
            ConsoleActionId.MapSock -> showUploadMapStockDialog()
            ConsoleActionId.DivingFishUpload -> (activity as? MainActivity)?.showDivingFishUploadTab()
        }
    }

    /**
     * 处理功能按钮长按事件。
     */
    private fun handleActionLongClick(actionId: ConsoleActionId): Boolean {
        return when (actionId) {
            ConsoleActionId.ChargeTicket -> {
                showTicketQueryDialog()
                true
            }

            ConsoleActionId.Point -> {
                if (!latestState.loggedIn || latestState.accessBlocked) {
                    logger.info(getString(R.string.status_access_blocked_need_logout))
                    return true
                }
                showUploadPointDialog()
                true
            }

            else -> false
        }
    }

    /**
     * 处理从通知栏进入 App 后的动作。
     */
    private fun handleIntentAction(intent: Intent?) {
        when (intent?.action) {
            AppIntentActions.LOGOUT_AFTER_NO_UPSERT_TIMEOUT -> {
                viewModel.logoutAfterNoUpsertTimeout()
                intent.action = null
            }
        }
    }

    private fun setupLogoutLongClick() {
        binding.logoutButton.setOnLongClickListener {
            showManualLogoutDialog()
            true
        }
    }

    private fun showManualLogoutDialog() {
        ManualLogoutDialog(
            activity = requireActivity() as AppCompatActivity,
            initialUserId = latestLoginUserId(),
            initialCookie = latestLoginCookie(),
            onSubmit = viewModel::logoutByUserIdCookie,
        ).show()
    }

    private fun latestLoginUserId(): String {
        if (latestState.loggedIn && latestState.userId.isUsableLoginValue()) {
            return latestState.userId
        }
        return SPUtils.getInstance(PREFS_NAME).getString(KEY_LAST_LOGIN_USER_ID, "")
    }

    private fun latestLoginCookie(): String {
        if (latestState.loggedIn && latestState.cookieStatus.isUsableLoginValue()) {
            return latestState.cookieStatus
        }
        return SPUtils.getInstance(PREFS_NAME).getString(KEY_LAST_LOGIN_COOKIE, "")
    }

    /**
     * 弹出上传成绩表单，并在用户确认后把表单转换成 MusicDetail。
     */
    private fun showUploadScoreDialog() {
        UploadScoreDialog(
            activity = requireActivity() as AppCompatActivity,
            onInvalidInput = {
                logger.info(getString(R.string.error_upload_score_form_required))
            },
            onSubmit = {
                viewModel.uploadScore(it)
            },
        ).show()
    }

    /**
     * 更改舞里程
     */
    private fun showUploadPointDialog() {
        UploadPointDialog(
            activity = requireActivity() as AppCompatActivity,
            onSubmit = {
                viewModel.uploadPoint(it)
            },
        ).show()
    }

    /**
     * 修改存储里程 MapStock
     */
    private fun showUploadMapStockDialog() {
        UploadPointDialog(
            activity = requireActivity() as AppCompatActivity,
            titleRes = R.string.action_map_stock,
            onSubmit = {
                viewModel.uploadMapStock(it)
            },
        ).show()
    }

    /**
     * 旅行伙伴
     */
    private fun showUploadCharasDialog() {
        UploadCharasDialog(
            activity = requireActivity() as AppCompatActivity,
            onInvalidInput = {
                logger.info(getString(R.string.error_character_level_required))
            },
            onSubmit = {
                viewModel.uploadCharas(it)
            },
        ).show()
    }

    private fun showKaleidxScopeDialog() {
        KaleidxScopeDialog(
            activity = requireActivity() as AppCompatActivity,
            onSubmit = viewModel::uploadKaleidxScope,
        ).show()
    }

    /**
     * 弹出查票表单。已登录时直接查询，未登录时由弹窗要求输入用户 ID。
     */
    private fun showTicketQueryDialog() {
        val currentUserId = latestState.userId.toLongOrNull()
        TicketQueryDialog(
            activity = requireActivity() as AppCompatActivity,
            initialUserId = if (latestState.loggedIn) currentUserId else null,
            load = viewModel::queryTicketForDialog,
        ).show()
    }

    private fun showWhitelistBlockedDialogIfNeeded(state: ConsoleUiState) {
        if (!state.accessBlocked) {
            whitelistBlockedDialogUserId = null
            return
        }
        if (whitelistBlockedDialogUserId == state.userId) {
            return
        }

        whitelistBlockedDialogUserId = state.userId
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.dialog_whitelist_blocked_title)
            .setMessage(R.string.dialog_whitelist_blocked_message)
            .show()
    }

    /**
     * 显示自定义说明弹窗，引导用户进入系统应用详情页。
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
     * 打开当前 App 的系统详情页，让用户手动允许完全后台行为。
     */
    private fun showInitialBackgroundSettingsDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.dialog_background_title)
            .setMessage(R.string.dialog_background_message)
            .setPositiveButton(R.string.dialog_background_open_settings) { _, _ ->
                openAppBackgroundSettings()
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    /**
     * 从剪贴板自动读取 SGWCMAID 二维码文本。
     *
     * 剪贴板没有普通运行时权限可以申请，Android 10 以后基本只能在前台且获得焦点时读取。
     * 这里只在未登录、未执行任务时自动填入，避免覆盖用户已经使用的会话。
     */
    private fun openAppBackgroundSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${requireContext().packageName}")
        }
        startActivity(intent)
        logger.info(getString(R.string.log_open_background_settings))
    }

    /**
     * 拦截系统返回键，避免还没 upsertUserAll 就误退出并尝试登出。
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

        val clipboard = requireContext().getSystemService(ClipboardManager::class.java)
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
            .coerceToText(requireContext())
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

    companion object {
        const val TAG = "TechFragment"
        const val CLIPBOARD_READ_DELAY_MILLIS = 300L
        const val INITIAL_BACKGROUND_PERMISSION_DELAY_MILLIS = 500L
        const val PREFS_NAME = "maimai_android_preferences"
        const val KEY_BACKGROUND_PERMISSION_REQUESTED = "background_permission_requested"
        const val KEY_LAST_LOGIN_USER_ID = "last_login_user_id"
        const val KEY_LAST_LOGIN_COOKIE = "last_login_cookie"
        const val ACTION_GRID_SPAN_COUNT = 2

        fun newInstance() = TechFragment()
    }

    private fun rememberLoginInfoIfNeeded(state: ConsoleUiState) {
        if (!state.loggedIn) {
            return
        }
        if (!state.userId.isUsableLoginValue() || !state.cookieStatus.isUsableLoginValue()) {
            return
        }

        SPUtils.getInstance(PREFS_NAME).apply {
            Timber.d("Remember login info: $state")
            put(KEY_LAST_LOGIN_USER_ID, state.userId)
            put(KEY_LAST_LOGIN_COOKIE, state.cookieStatus)
        }
    }

    private fun String.isUsableLoginValue(): Boolean =
        isNotBlank() && this != "-" && this != getString(R.string.status_none)

    private fun bindBackPressed() {
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                /**
                 * 收集 ViewModel 的 StateFlow，并把新状态交给 DataBinding。
                 */
                override fun handleOnBackPressed() {
                    if (!latestState.loggedIn) {
                        requireActivity().finish()
                        return
                    }

                    if (latestState.accessBlocked) {
                        AlertDialog.Builder(requireContext())
                            .setTitle(R.string.dialog_whitelist_blocked_title)
                            .setMessage(R.string.dialog_whitelist_blocked_message)
                            .setPositiveButton(R.string.dialog_logout_and_exit) { _, _ ->
                                viewModel.logout()
                                requireActivity().finish()
                            }
                            .setNegativeButton(R.string.dialog_stay, null)
                            .show()
                    } else if (latestState.upsertUserAllCompleted || latestState.logoutAllowedByTimeout) {
                        AlertDialog.Builder(requireContext())
                            .setTitle(R.string.dialog_logged_in_title)
                            .setMessage(R.string.dialog_logged_in_message)
                            .setPositiveButton(R.string.dialog_logout_and_exit) { _, _ ->
                                viewModel.logout()
                                requireActivity().finish()
                            }
                            .setNegativeButton(R.string.dialog_stay, null)
                            .show()
                    } else {
                        AlertDialog.Builder(requireContext())
                            .setTitle(R.string.dialog_cannot_logout_title)
                            .setMessage(R.string.dialog_cannot_logout_message)
                            .setPositiveButton(R.string.dialog_stay, null)
                            .show()
                    }
                }
            })
    }

    /**
     * state 的文字、按钮状态、ProgressBar 都已经写进 XML 绑定表达式里。
     * 所以这里拿到新 state 后，只需要更新 binding.state。
     */
    private fun collectState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.state.collect { state ->
                        latestState = state
                        binding.state = state
                        rememberLoginInfoIfNeeded(state)
                        updateActions(state)
                        showWhitelistBlockedDialogIfNeeded(state)
                    }
                }
            }
        }
    }
}

