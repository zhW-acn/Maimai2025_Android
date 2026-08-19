package com.okaca.maimai.android.ui.console

import android.app.Activity.RESULT_OK
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.blankj.utilcode.util.SPUtils
import com.okaca.maimai.android.R
import com.okaca.maimai.android.databinding.FragmentDivingFishUploadBinding
import com.okaca.maimai.android.divingfish.DivingFishUploadCoordinator
import com.okaca.maimai.android.divingfish.DivingFishUploadListener
import com.okaca.maimai.android.network.server.HttpServerService
import com.okaca.maimai.android.network.vpn.core.LocalVpnService
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class DivingFishUploadFragment : Fragment(), DivingFishUploadListener,
    LocalVpnService.onStatusChangedListener {
    private lateinit var binding: FragmentDivingFishUploadBinding

    private val httpServiceIntent by lazy {
        Intent(requireContext(), HttpServerService::class.java)
    }

    private val vpnActivityResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                startProxyServices()
            } else {
                appendLog("VPN 授权已取消")
                setRunning(false)
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = FragmentDivingFishUploadBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.passwordInput.inputType =
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        loadLastAccount()
        binding.saveAccountButton.setOnClickListener { saveCurrentAccount(showLog = true) }
        binding.selectAccountButton.setOnClickListener { showAccountPicker() }
        binding.deleteAccountButton.setOnClickListener { deleteCurrentAccount() }
        binding.startButton.setOnClickListener { startUploadFlow() }
        binding.stopButton.setOnClickListener { stopUploadFlow("已停止代理") }
        binding.clearLogsButton.setOnClickListener { binding.logsText.text = "" }
    }

    private fun startUploadFlow() {
        val username = binding.usernameInput.text?.toString()?.trim().orEmpty()
        val password = binding.passwordInput.text?.toString()?.trim().orEmpty()
        val difficulties = selectedDifficulties()

        if (username.isBlank() || password.isBlank() || difficulties.isEmpty()) {
            appendLog(getString(R.string.error_diving_fish_form_required))
            return
        }

        saveCurrentAccount(showLog = false)
        setRunning(true)
        appendLog("准备启动本地代理")
        DivingFishUploadCoordinator.configure(
            username = username,
            password = password,
            difficulties = difficulties,
            listener = this,
        )
        LocalVpnService.addOnStatusChangedListener(this)

        val vpnPrepareIntent = VpnService.prepare(requireContext())
        if (vpnPrepareIntent == null) {
            startProxyServices()
        } else {
            vpnActivityResultLauncher.launch(vpnPrepareIntent)
        }
    }

    private fun startProxyServices() {
        requireContext().startService(Intent(requireContext(), LocalVpnService::class.java))
        requireContext().startService(httpServiceIntent)
        val link = copyWechatLink()
        binding.copiedLinkText.text = link
        appendLog("已复制链接，请在微信中粘贴并打开")
        openWechat()
    }

    private fun copyWechatLink(): String {
        val link = "http://127.0.0.2:8284/${UUID.randomUUID().toString().take(10)}"
        val clipboard =
            requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("divingFishUploadLink", link))
        return link
    }

    private fun openWechat() {
        try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                component = ComponentName("com.tencent.mm", "com.tencent.mm.ui.LauncherUI")
            }
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            appendLog(getString(R.string.log_diving_fish_open_wechat_failed))
        }
    }

    private fun selectedDifficulties(): Set<Int> =
        listOf(
            binding.diffBasic to 0,
            binding.diffAdvanced to 1,
            binding.diffExpert to 2,
            binding.diffMaster to 3,
            binding.diffRemaster to 4,
        ).mapNotNull { (view, value) -> value.takeIf { view.isChecked } }.toSet()

    private fun setRunning(running: Boolean) {
        binding.statusText.text = getString(
            if (running) R.string.status_diving_fish_running else R.string.status_diving_fish_waiting
        )
        binding.startButton.isEnabled = !running
        binding.stopButton.isEnabled = running
    }

    private fun loadLastAccount() {
        val account = loadAccounts().firstOrNull() ?: return
        fillAccount(account)
    }

    private fun saveCurrentAccount(showLog: Boolean): Boolean {
        val username = binding.usernameInput.text?.toString()?.trim().orEmpty()
        val password = binding.passwordInput.text?.toString()?.trim().orEmpty()
        if (username.isBlank() || password.isBlank()) {
            if (showLog) {
                appendLog(getString(R.string.error_diving_fish_account_required))
            }
            return false
        }

        val accounts = loadAccounts()
            .filterNot { it.username == username }
            .toMutableList()
        accounts.add(0, DivingFishAccount(username, password))
        saveAccounts(accounts)
        if (showLog) {
            appendLog(getString(R.string.log_diving_fish_account_saved, username))
        }
        return true
    }

    private fun showAccountPicker() {
        val accounts = loadAccounts()
        if (accounts.isEmpty()) {
            appendLog(getString(R.string.log_diving_fish_account_empty))
            return
        }

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.dialog_select_diving_fish_account_title)
            .setItems(accounts.map { it.username }.toTypedArray()) { _, which ->
                val account = accounts[which]
                fillAccount(account)
                appendLog(getString(R.string.log_diving_fish_account_selected, account.username))
            }
            .show()
    }

    private fun deleteCurrentAccount() {
        val username = binding.usernameInput.text?.toString()?.trim().orEmpty()
        if (username.isBlank()) {
            appendLog(getString(R.string.error_diving_fish_account_required))
            return
        }

        val accounts = loadAccounts()
        val nextAccounts = accounts.filterNot { it.username == username }
        if (nextAccounts.size == accounts.size) {
            appendLog(getString(R.string.log_diving_fish_account_empty))
            return
        }

        saveAccounts(nextAccounts)
        binding.usernameInput.text?.clear()
        binding.passwordInput.text?.clear()
        appendLog(getString(R.string.log_diving_fish_account_deleted, username))
    }

    private fun fillAccount(account: DivingFishAccount) {
        binding.usernameInput.setText(account.username)
        binding.passwordInput.setText(account.password)
    }

    private fun loadAccounts(): List<DivingFishAccount> {
        val raw = SPUtils.getInstance(PREFS_NAME).getString(KEY_DIVING_FISH_ACCOUNTS, "[]")
        val jsonArray = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
        return (0 until jsonArray.length()).mapNotNull { index ->
            val item = jsonArray.optJSONObject(index) ?: return@mapNotNull null
            val username = item.optString(KEY_USERNAME)
            val password = item.optString(KEY_PASSWORD)
            DivingFishAccount(username, password).takeIf {
                it.username.isNotBlank() && it.password.isNotBlank()
            }
        }
    }

    private fun saveAccounts(accounts: List<DivingFishAccount>) {
        val jsonArray = JSONArray()
        accounts.forEach { account ->
            jsonArray.put(
                JSONObject()
                    .put(KEY_USERNAME, account.username)
                    .put(KEY_PASSWORD, account.password)
            )
        }
        SPUtils.getInstance(PREFS_NAME).put(KEY_DIVING_FISH_ACCOUNTS, jsonArray.toString())
    }

    private fun stopUploadFlow(message: String? = null) {
        LocalVpnService.IsRunning = false
        requireContext().stopService(httpServiceIntent)
        LocalVpnService.removeOnStatusChangedListener(this)
        DivingFishUploadCoordinator.clearListener(this)
        setRunning(false)
        message?.let { appendLog(it) }
    }

    private fun appendLog(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        binding.logsText.append("[$timestamp] $message\n")
        binding.logsScroll.post {
            binding.logsScroll.fullScroll(android.view.View.FOCUS_DOWN)
        }
    }

    override fun onMessageReceived(message: String) {
        appendLog(message)
    }

    override fun onStartAuth() {
        appendLog("已捕获微信授权回调，开始处理舞萌官方登录")
    }

    override fun onFinishUpdate() {
        appendLog("水鱼上传流程完成")
        stopUploadFlow()
    }

    override fun onError(error: Throwable) {
        appendLog("水鱼上传流程失败：${error.message ?: error::class.java.simpleName}")
        stopUploadFlow()
    }

    override fun onStatusChanged(status: String, isRunning: Boolean) {
        appendLog("代理状态：$status")
    }

    override fun onLogReceived(logString: String) {
        appendLog(logString)
    }

    override fun onDestroyView() {
        stopUploadFlow()
        super.onDestroyView()
    }

    companion object {
        const val TAG = "DivingFishUploadFragment"
        private const val PREFS_NAME = "maimai_android_preferences"
        private const val KEY_DIVING_FISH_ACCOUNTS = "diving_fish_accounts"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"

        fun newInstance() = DivingFishUploadFragment()
    }

    private data class DivingFishAccount(
        val username: String,
        val password: String,
    )
}
