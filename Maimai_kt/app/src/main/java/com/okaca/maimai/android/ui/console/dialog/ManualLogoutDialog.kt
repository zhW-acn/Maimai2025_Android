package com.okaca.maimai.android.ui.console.dialog

import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/**
 * 长按登出按钮时显示的表单。
 *
 * 如果当前已经登录，MainActivity 会把已有的 userId 和 cookie 传进来作为默认值。
 */
class ManualLogoutDialog(
    private val activity: AppCompatActivity,
    private val initialUserId: String,
    private val initialCookie: String,
    private val onSubmit: (Long, String) -> Unit,
) {
    private lateinit var userIdInput: EditText
    private lateinit var cookieInput: EditText

    fun show() {
        val root = buildRootView()
        val dialog = AlertDialog.Builder(activity)
            .setTitle("手动登出")
            .setView(root)
            .setPositiveButton("登出", null)
            .setNegativeButton("取消", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val userId = userIdInput.text?.toString()?.trim()?.toLongOrNull()
                val cookie = cookieInput.text?.toString()?.trim().orEmpty()

                when {
                    userId == null || userId <= 0L -> userIdInput.error = "请输入有效的用户 ID"
                    cookie.isBlank() -> cookieInput.error = "请输入 Cookie"
                    else -> {
                        onSubmit(userId, cookie)
                        dialog.dismiss()
                    }
                }
            }
        }
        dialog.show()
    }

    private fun buildRootView(): View =
        LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dp, 8.dp, 20.dp, 0)

            userIdInput = EditText(activity).apply {
                hint = "用户 ID"
                inputType = InputType.TYPE_CLASS_NUMBER
                setSingleLine(true)
                setText(initialUserId)
            }
            addView(userIdInput)

            cookieInput = EditText(activity).apply {
                hint = "Cookie"
                inputType =
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                minLines = 2
                maxLines = 4
                setText(initialCookie)
            }
            addView(cookieInput)
        }

    private val Int.dp: Int
        get() = (this * activity.resources.displayMetrics.density).toInt()
}

