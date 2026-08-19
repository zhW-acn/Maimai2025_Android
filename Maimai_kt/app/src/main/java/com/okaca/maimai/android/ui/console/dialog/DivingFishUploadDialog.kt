package com.okaca.maimai.android.ui.console.dialog

import android.text.InputType
import android.view.LayoutInflater
import android.widget.CheckBox
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.okaca.maimai.android.R
import com.okaca.maimai.android.databinding.DialogDivingFishUploadBinding

class DivingFishUploadDialog(
    private val activity: AppCompatActivity,
    private val onInvalidInput: () -> Unit,
    private val onSubmit: (String, String, Set<Int>) -> Unit,
) {
    fun show() {
        val binding = DialogDivingFishUploadBinding.inflate(LayoutInflater.from(activity))
        binding.passwordInput.inputType =
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD

        val dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.dialog_diving_fish_upload_title)
            .setView(binding.root)
            .setNegativeButton(R.string.dialog_cancel, null)
            .setPositiveButton(R.string.dialog_diving_fish_upload_confirm, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val username = binding.usernameInput.text?.toString()?.trim().orEmpty()
                val password = binding.passwordInput.text?.toString()?.trim().orEmpty()
                val difficulties = selectedDifficulties(binding)
                if (username.isBlank() || password.isBlank() || difficulties.isEmpty()) {
                    onInvalidInput()
                    return@setOnClickListener
                }
                onSubmit(username, password, difficulties)
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun selectedDifficulties(binding: DialogDivingFishUploadBinding): Set<Int> =
        listOf(
            binding.diffBasic to 0,
            binding.diffAdvanced to 1,
            binding.diffExpert to 2,
            binding.diffMaster to 3,
            binding.diffRemaster to 4,
        ).mapNotNull { (view, value) ->
            value.takeIf { (view as CheckBox).isChecked }
        }.toSet()
}
