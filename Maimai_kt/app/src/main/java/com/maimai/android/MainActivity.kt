package com.maimai.android

import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.maimai.android.databinding.ActivityMainBinding
import com.maimai.android.logging.AppMaimaiLogger
import com.maimai.android.session.ConsoleUiState
import com.maimai.android.session.MaimaiConsoleViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * App 的主页面，也就是 MVVM 里的 View。
 *
 * 现在页面使用 DataBinding：
 * - XML 负责用 @{...} 显示 state.status、state.userId 等字段。
 * - XML 负责用 @{...} 控制按钮 enabled、ProgressBar visibility。
 * - Activity 只负责把 ViewModel 的 StateFlow 收集出来，然后赋值给 binding.state。
 *
 * 也就是说，Activity 不再手动写 renderState() 更新一堆控件了。
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private val viewModel: MaimaiConsoleViewModel by viewModels()

    @Inject lateinit var logger: AppMaimaiLogger

    private lateinit var binding: ActivityMainBinding
    private var latestState = ConsoleUiState()

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

        bindBackPressed()
        collectState()
    }

    private fun bindBackPressed() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (latestState.loggedIn) {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("当前仍处于登录状态")
                        .setMessage("离开前建议先 logout，否则服务端可能仍认为你在线。")
                        .setPositiveButton("Logout 并退出") { _, _ ->
                            viewModel.logout()
                            finish()
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
                    }
                }
                launch {
                    logger.logs.collect { logs ->
                        binding.logsText.text = if (logs.isEmpty()) "暂无日志" else logs.joinToString("\n")
                        binding.logsScroll.post { binding.logsScroll.fullScroll(View.FOCUS_DOWN) }
                    }
                }
            }
        }
    }
}
