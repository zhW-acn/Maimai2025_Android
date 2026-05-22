package com.okaca.maimai.android.ui.console.dialog

import android.view.Gravity
import android.widget.CheckBox
import android.widget.GridLayout
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.okaca.maimai.android.R
import kt.payload.KaleidxScopeGate

/**
 * KaleidxScope 选择弹窗。
 */
class KaleidxScopeDialog(
    private val activity: AppCompatActivity,
    private val onSubmit: (KaleidxScopeGate) -> Unit,
) {
    fun show() {
        val grid = GridLayout(activity).apply {
            columnCount = 2
            setPadding(20.dp, 12.dp, 20.dp, 0)
        }

        val dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.dialog_kaleidx_scope_title)
            .setView(grid)
            .setNegativeButton(R.string.dialog_cancel, null)
            .create()

        for (course in COURSES) {
            val button = MaterialButton(activity).apply {
                text = course.gateName
                setOnClickListener {
                    showGateOptions(course)
                    dialog.dismiss()
                }
            }
            grid.addView(
                button,
                GridLayout.LayoutParams().apply {
                    width = 0
                    height = LinearLayout.LayoutParams.WRAP_CONTENT
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(6.dp, 6.dp, 6.dp, 6.dp)
                }
            )
        }
        dialog.show()
    }

    private fun showGateOptions(course: KaleidxScopeCourse) {
        val isGateFoundInput = CheckBox(activity).apply {
            text = activity.getString(R.string.label_is_gate_found)
        }
        val isKeyFoundInput = CheckBox(activity).apply {
            text = activity.getString(R.string.label_is_key_found)
        }
        val isClearInput = CheckBox(activity).apply {
            text = activity.getString(R.string.label_is_clear)
        }
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(20.dp, 8.dp, 20.dp, 0)
            addView(isGateFoundInput)
            addView(isKeyFoundInput)
            addView(isClearInput)
        }

        AlertDialog.Builder(activity)
            .setTitle(
                activity.getString(
                    R.string.dialog_kaleidx_scope_options_title,
                    course.gateId
                )
            )
            .setView(root)
            .setPositiveButton(R.string.dialog_upload_score_confirm) { _, _ ->
                onSubmit(
                    KaleidxScopeGate(
                        gateId = course.gateId,
                        musicId = course.musicId,
                        isGateFound = isGateFoundInput.isChecked,
                        isKeyFound = isKeyFoundInput.isChecked,
                        isClear = isClearInput.isChecked,
                    )
                )
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private val Int.dp: Int
        get() = (this * activity.resources.displayMetrics.density).toInt()

    private companion object {
        val COURSES = listOf(
            KaleidxScopeCourse(1, "青の扉", 11740),
            KaleidxScopeCourse(2, "白の扉", 11745),
            KaleidxScopeCourse(3, "紫の扉", 11749),
            KaleidxScopeCourse(4, "黒の扉", 11753),
            KaleidxScopeCourse(5, "黄の扉", 11809),
            KaleidxScopeCourse(6, "赤の扉", 11814),
            KaleidxScopeCourse(7, "プリズムタワー", 11818),
            KaleidxScopeCourse(8, "KALEIDXSCOPE", 8),
            KaleidxScopeCourse(9, "希望の扉", 1819),
            KaleidxScopeCourse(10, "KALEIDXSCOPE（Xaleid◆scopiX）", 11820),
        )
    }
}

private data class KaleidxScopeCourse(
    val gateId: Int,
    val gateName: String,
    val musicId: Int,
)
