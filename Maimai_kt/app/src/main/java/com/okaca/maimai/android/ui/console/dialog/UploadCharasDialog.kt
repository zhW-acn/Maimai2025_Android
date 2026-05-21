package com.okaca.maimai.android.ui.console.dialog

import android.view.LayoutInflater
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.okaca.maimai.android.R
import com.okaca.maimai.android.databinding.DialogUploadCharasBinding
import kt.constants.CharaLevel
import kt.payload.CharaDetail

/**
 * 旅行伙伴等级弹窗。
 */
class UploadCharasDialog(
    private val activity: AppCompatActivity,
    private val onInvalidInput: () -> Unit,
    private val onSubmit: (List<CharaDetail>) -> Unit,
) {
    fun show() {
        val binding = DialogUploadCharasBinding.inflate(LayoutInflater.from(activity))
        val dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.action_character_level)
            .setView(binding.root)
            .setPositiveButton(R.string.dialog_upload_score_confirm, null)
            .setNegativeButton(R.string.dialog_cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val levels = readLevels(binding)
                if (levels == null) {
                    onInvalidInput()
                    return@setOnClickListener
                }

                onSubmit(levels)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun readLevels(binding: DialogUploadCharasBinding): List<CharaDetail>? {
        return listOf(
            CharaDetail(CharaDetail.CHARA_ID_NONE, binding.input1.readLevelOrNull() ?: return null),
            CharaDetail(CharaDetail.CHARA_ID_NONE, binding.input2.readLevelOrNull() ?: return null),
            CharaDetail(CharaDetail.CHARA_ID_NONE, binding.input3.readLevelOrNull() ?: return null),
            CharaDetail(CharaDetail.CHARA_ID_NONE, binding.input4.readLevelOrNull() ?: return null),
            CharaDetail(CharaDetail.CHARA_ID_NONE, binding.input5.readLevelOrNull() ?: return null),
        )
    }

    private fun EditText.readLevelOrNull(): Int? {
        val value = text?.toString()?.trim()?.toIntOrNull()
        if (value == null) {
            error = activity.getString(R.string.error_character_level_required)
            requestFocus()
            return null
        }
        return value.coerceIn(CharaLevel.MIN, CharaLevel.MAX)
    }
}
