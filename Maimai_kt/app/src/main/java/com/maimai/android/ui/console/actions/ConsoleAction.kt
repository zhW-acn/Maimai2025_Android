package com.maimai.android.ui.console.actions

import androidx.annotation.StringRes
import com.maimai.android.R

enum class ConsoleActionId {
    UploadScore,
    ChargeTicket
}


/**
 * 根据当前页面状态生成动作按钮列表。
 */
fun buildConsoleActions(enabled: Boolean): List<ConsoleAction> =
    listOf(
        ConsoleAction(
            id = ConsoleActionId.UploadScore,
            titleRes = R.string.action_upload_demo_score,
            enabled = enabled,
        ),
        ConsoleAction(
            id = ConsoleActionId.ChargeTicket,
            titleRes = R.string.action_charge_ticket,
            enabled = enabled,
            longClickEnabled = true,
        ),
    )

/**
 * RecyclerView 中每一个功能按钮的 UI 数据。
 */
data class ConsoleAction(
    val id: ConsoleActionId,
    @StringRes val titleRes: Int,
    val enabled: Boolean,
    val longClickEnabled: Boolean = false,
)
