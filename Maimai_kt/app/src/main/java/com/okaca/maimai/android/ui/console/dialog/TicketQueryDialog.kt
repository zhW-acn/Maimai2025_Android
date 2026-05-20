package com.okaca.maimai.android.ui.console.dialog

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.okaca.maimai.android.ui.console.session.TicketChargeItem
import com.okaca.maimai.android.ui.console.session.TicketQueryResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 查票弹窗。
 *
 * 已登录时直接使用当前会话查询；未登录时先让用户输入 userId，
 * 再走同一个查询逻辑。这样 MainActivity 不需要关心弹窗内部状态。
 */
class TicketQueryDialog(
    private val activity: AppCompatActivity,
    private val initialUserId: Long?,
    private val load: suspend (Long?) -> TicketQueryResult,
) {
    private lateinit var statusText: TextView
    private lateinit var form: LinearLayout
    private lateinit var userIdInput: EditText
    private lateinit var progress: ProgressBar
    private lateinit var content: LinearLayout
    private var loadJob: Job? = null

    fun show() {
        val root = buildRootView()
        val dialog = AlertDialog.Builder(activity)
            .setTitle("查票结果")
            .setView(root)
            .setNegativeButton("关闭", null)
            .create()

        dialog.setOnDismissListener {
            loadJob?.cancel()
            loadJob = null
        }
        dialog.setOnShowListener {
            if (initialUserId == null) {
                showInputState()
            } else {
                startLoading(initialUserId)
            }
        }
        dialog.show()
    }

    private fun buildRootView(): View {
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dp, 8.dp, 20.dp, 0)
        }

        statusText = TextView(activity).apply {
            text = ""
            textSize = 14f
            setTextColor(Color.parseColor("#586662"))
        }
        root.addView(statusText)

        form = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        userIdInput = EditText(activity).apply {
            hint = "用户 ID"
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine(true)
        }
        form.addView(userIdInput)
        form.addView(
            Button(activity).apply {
                text = "查询"
                setOnClickListener {
                    val userId = userIdInput.text?.toString()?.trim()?.toLongOrNull()
                    if (userId == null || userId <= 0L) {
                        userIdInput.error = "请输入有效的用户 ID"
                    } else {
                        startLoading(userId)
                    }
                }
            },
        )
        root.addView(form)

        progress = ProgressBar(activity).apply {
            isIndeterminate = true
            visibility = View.GONE
        }
        root.addView(
            progress,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = 16.dp
                bottomMargin = 16.dp
            },
        )

        val scroll = ScrollView(activity)
        content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }
        scroll.addView(content)
        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                420.dp,
            ),
        )

        return root
    }

    private fun showInputState() {
        statusText.text = "当前未登录，请输入用户 ID 后查询票券。"
        form.visibility = View.VISIBLE
        progress.visibility = View.GONE
        content.removeAllViews()
        userIdInput.requestFocus()
    }

    private fun startLoading(userId: Long?) {
        statusText.text = "正在查询票券..."
        form.visibility = View.GONE
        progress.visibility = View.VISIBLE
        content.removeAllViews()

        loadJob = activity.lifecycleScope.launch {
            runCatching { load(userId) }
                .onSuccess(::renderResult)
                .onFailure(::renderError)
        }
    }

    private fun renderResult(result: TicketQueryResult) {
        progress.visibility = View.GONE
        content.removeAllViews()
        statusText.text = "用户 ID：${result.userId}    票券数量：${result.length}"

        if (result.tickets.isEmpty()) {
            content.addView(
                TextView(activity).apply {
                    text = "没有查到票券数据。"
                    textSize = 14f
                    setTextColor(Color.parseColor("#586662"))
                    setPadding(0, 16.dp, 0, 16.dp)
                },
            )
            return
        }

        result.tickets.forEach { ticket ->
            content.addView(buildTicketCard(ticket))
        }
    }

    private fun renderError(error: Throwable) {
        progress.visibility = View.GONE
        content.removeAllViews()
        statusText.text = "查票失败"
        content.addView(
            TextView(activity).apply {
                text = error.message ?: error::class.java.simpleName
                textSize = 14f
                setTextColor(Color.parseColor("#B3261E"))
                setPadding(0, 16.dp, 0, 16.dp)
            },
        )
        form.visibility = if (initialUserId == null) View.VISIBLE else View.GONE
    }

    private fun buildTicketCard(ticket: TicketChargeItem): View {
        val stockColor = if (ticket.stock > 0) "#176B5B" else "#68736F"
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14.dp, 12.dp, 14.dp, 12.dp)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F3F6F4"))
                cornerRadius = 8.dp.toFloat()
                setStroke(1.dp, Color.parseColor(if (ticket.stock > 0) "#176B5B" else "#D8E0DC"))
            }
        }

        card.addView(
            TextView(activity).apply {
                text = "票券 ID ${ticket.chargeId}    库存 ${ticket.stock}"
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.parseColor(stockColor))
            },
        )
        card.addView(buildInfoRow("购买时间", ticket.purchaseDate))
        card.addView(buildInfoRow("有效期至", ticket.validDate))
        card.addView(buildInfoRow("扩展值", ticket.extNum1.toString()))

        return card.apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = 12.dp
            }
        }
    }

    private fun buildInfoRow(label: String, value: String): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 6.dp, 0, 0)
            addView(
                TextView(activity).apply {
                    text = label
                    textSize = 13f
                    setTextColor(Color.parseColor("#68736F"))
                },
                LinearLayout.LayoutParams(86.dp, LinearLayout.LayoutParams.WRAP_CONTENT),
            )
            addView(
                TextView(activity).apply {
                    text = value.ifBlank { "-" }
                    textSize = 13f
                    setTextColor(Color.parseColor("#17211F"))
                },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
        }

    private val Int.dp: Int
        get() = (this * activity.resources.displayMetrics.density).toInt()
}

