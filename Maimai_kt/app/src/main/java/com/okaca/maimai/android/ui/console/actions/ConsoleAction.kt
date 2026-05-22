package com.okaca.maimai.android.ui.console.actions

import androidx.annotation.StringRes
import com.okaca.maimai.android.R

enum class ConsoleActionId {
    UploadScore,
    ChargeTicket,
    Point,
    CharacterLevels,
    KaleidxScope,
    MapSock,
}


/**
 * 根据当前页面状态生成动作按钮列表。
 */
fun buildConsoleActions(
    enabled: Boolean,
    loggedIn: Boolean,
    accessBlocked: Boolean,
): List<ConsoleAction> =
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
            longClickEnabled = !accessBlocked,
        ),
        ConsoleAction(
            id = ConsoleActionId.Point,
            titleRes = R.string.action_point,
            enabled = enabled,
            longClickEnabled = loggedIn && !accessBlocked,
        ),
        ConsoleAction(
            id = ConsoleActionId.CharacterLevels,
            titleRes = R.string.action_character_level,
            enabled = enabled,
            longClickEnabled = false,
        ),
        ConsoleAction(
            id = ConsoleActionId.KaleidxScope,
            titleRes = R.string.action_kaleidx_scope,
            enabled = enabled,
            longClickEnabled = false,
        ),
        ConsoleAction(
            id = ConsoleActionId.MapSock,
            titleRes = R.string.action_map_stock,
            enabled = enabled,
            longClickEnabled = false,
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

