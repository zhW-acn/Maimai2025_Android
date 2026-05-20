package com.okaca.maimai.android.ui.console.dialog

import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.okaca.maimai.android.R
import com.okaca.maimai.android.databinding.DialogUploadScoreBinding
import com.okaca.maimai.android.enums.ComboStatus
import com.okaca.maimai.android.enums.ScoreOption
import com.okaca.maimai.android.enums.ScoreLevel
import com.okaca.maimai.android.enums.ScoreRank
import com.okaca.maimai.android.enums.SyncStatus
import kt.payload.MusicDetail

/**
 * 上传成绩弹窗。
 *
 * 负责初始化表单、读取并校验输入，MainActivity 只需要调用 show。
 */
class UploadScoreDialog(
    private val activity: AppCompatActivity,
    private val onInvalidInput: () -> Unit,
    private val onSubmit: (MusicDetail) -> Unit,
) {
    /**
     * 鏄剧ず涓婁紶鎴愮哗琛ㄥ崟銆?
     */
    fun show() {
        val binding = DialogUploadScoreBinding.inflate(LayoutInflater.from(activity))
        bindSpinners(binding)
        bindDefaults(binding)

        val dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.dialog_upload_score_title)
            .setView(binding.root)
            .setPositiveButton(R.string.dialog_upload_score_confirm, null)
            .setNegativeButton(R.string.dialog_cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val music = buildMusicDetail(binding)
                if (music == null) {
                    onInvalidInput()
                    return@setOnClickListener
                }

                onSubmit(music)
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    /** 使用 Kotlin enum 填充下拉框，避免 XML 数组 label/value 下标错位。 */
    private fun bindSpinners(binding: DialogUploadScoreBinding) {
        bindSpinner(binding.levelSpinner, ScoreLevel.values())
        bindSpinner(binding.comboStatusSpinner, ComboStatus.values())
        bindSpinner(binding.syncStatusSpinner, SyncStatus.values())
    }

    /** 把枚举选项转换成 Spinner 可展示的文本列表。 */
    private fun <T> bindSpinner(
        spinner: Spinner,
        options: Array<T>
    ) where T : Enum<T>, T : ScoreOption {
        val adapter = ArrayAdapter(
            activity,
            android.R.layout.simple_spinner_item,
            options.map { activity.getString(it.labelRes) },
        ).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        spinner.adapter = adapter
    }

    /** 给表单设置默认值，减少测试时重复输入。 */
    private fun bindDefaults(binding: DialogUploadScoreBinding) {
        binding.musicIdInput.setSelection(DEFAULT_MUSIC_ID)
        binding.levelSpinner.setSelection(DEFAULT_LEVEL_INDEX)
        binding.achievementIntegerInput.setText(DEFAULT_ACHIEVEMENT_INTEGER)
        binding.achievementFractionInput.setText(DEFAULT_ACHIEVEMENT_FRACTION)
        binding.deluxscoreMaxInput.setText(DEFAULT_DELUXSCORE_MAX)
    }

    /** 从表单控件读取值，校验成功后构造 MusicDetail。 */
    private fun buildMusicDetail(binding: DialogUploadScoreBinding): MusicDetail? {
        val musicId = binding.musicIdInput.text.toString().toIntOrNull()
        val achievementInteger = binding.achievementIntegerInput.text.toString().toIntOrNull()
        val achievementFraction = binding.achievementFractionInput.text.toString().toIntOrNull()
        val deluxscoreMax = binding.deluxscoreMaxInput.text.toString().toIntOrNull()
        if (
            musicId == null ||
            achievementInteger == null ||
            achievementFraction == null ||
            deluxscoreMax == null
        ) {
            return null
        }

        val level = ScoreLevel.values()[binding.levelSpinner.selectedItemPosition]
        val comboStatus = ComboStatus.values()[binding.comboStatusSpinner.selectedItemPosition]
        val syncStatus = SyncStatus.values()[binding.syncStatusSpinner.selectedItemPosition]
        val achievement = achievementInteger * ACHIEVEMENT_INTEGER_MULTIPLIER + achievementFraction
        val scoreRank = ScoreRank.fromAchievement(achievement)

        return MusicDetail(
            musicId = musicId,
            level = level.apiValue,
            achievement = achievement,
            comboStatus = comboStatus.apiValue,
            syncStatus = syncStatus.apiValue,
            deluxscoreMax = deluxscoreMax,
            scoreRank = scoreRank.apiValue,
        )
    }

    private companion object {
        var DEFAULT_LEVEL_INDEX = ScoreLevel.Master.apiValue
        const val DEFAULT_ACHIEVEMENT_INTEGER = "100"
        const val DEFAULT_MUSIC_ID = 18
        const val DEFAULT_ACHIEVEMENT_FRACTION = "0000"
        const val DEFAULT_DELUXSCORE_MAX = "0"
        const val ACHIEVEMENT_INTEGER_MULTIPLIER = 10000
    }
}

