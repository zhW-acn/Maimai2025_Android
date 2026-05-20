package com.maimai.android.ui.console.dialog

import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.maimai.android.R
import com.maimai.android.databinding.DialogUploadPointBinding
import kt.constants.Point

/**
 * 修改舞里程弹窗。
 *
 * Dialog 必须使用 Activity Context，因为弹窗需要挂到当前 Activity 的窗口上。
 */
class UploadPointDialog(
    private val activity: AppCompatActivity,
    private val onSubmit: (Int?) -> Unit,
) {
    fun show() {
        val binding = DialogUploadPointBinding.inflate(LayoutInflater.from(activity))
        bindDefaults(binding)
        val dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.action_upload_point)
            .setView(binding.root)
            .setPositiveButton(R.string.dialog_upload_score_confirm, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                onSubmit(binding.inputNum.text.toString().toIntOrNull())
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun bindDefaults(binding: DialogUploadPointBinding) {
        // EditText.setText(Int) 会被当成字符串资源 ID，这里必须转成文本。
        val defaultPoint = Point.MAX.toString()
        binding.inputNum.setText(defaultPoint)
        binding.inputNum.setSelection(defaultPoint.length)
    }
}
