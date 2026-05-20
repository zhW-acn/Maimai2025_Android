package com.okaca.maimai.android.ui.console.actions

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

/**
 * 给两列 Action 按钮增加间距。
 *
 * RecyclerView 的 item 本身只负责显示按钮；列表间距统一放在 ItemDecoration 里，
 * 后续按钮数量变多时，不需要每个 item 自己维护 margin。
 */
class ActionGridSpacingDecoration(
    private val spanCount: Int,
    private val horizontalSpacing: Int,
    private val verticalSpacing: Int,
) : RecyclerView.ItemDecoration() {
    /**
     * RecyclerView 布局每个 item 前会调用这里，让我们给当前位置补上外侧空白。
     */
    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State,
    ) {
        val position = parent.getChildAdapterPosition(view)
        if (position == RecyclerView.NO_POSITION) return

        val column = position % spanCount
        val row = position / spanCount

        outRect.left = if (column == 0) 0 else horizontalSpacing / 2
        outRect.right = if (column == spanCount - 1) 0 else horizontalSpacing / 2
        outRect.top = if (row == 0) 0 else verticalSpacing
    }
}

