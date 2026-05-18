package com.maimai.android.ui.console.actions

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.maimai.android.databinding.ItemConsoleActionBinding

class ConsoleActionAdapter(
    private val onClick: (ConsoleActionId) -> Unit,
    private val onLongClick: (ConsoleActionId) -> Boolean,
) : ListAdapter<ConsoleAction, ConsoleActionAdapter.ActionViewHolder>(DiffCallback) {

    /**
     * RecyclerView 需要新建一行按钮视图时调用。
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ActionViewHolder {
        val binding = ItemConsoleActionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return ActionViewHolder(binding)
    }

    /**
     * RecyclerView 需要把某个 ConsoleAction 显示到指定行时调用。
     */
    override fun onBindViewHolder(holder: ActionViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ActionViewHolder(
        private val binding: ItemConsoleActionBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        /**
         * 把按钮数据交给 XML，并绑定点击/长按回调。
         */
        fun bind(action: ConsoleAction) {
            // 把当前行的数据交给 XML，文字、可用状态、透明度都由 item_console_action.xml 显示。
            binding.action = action
            binding.executePendingBindings()

            // 点击事件保留在 Adapter 中，方便统一分发给 Activity/ViewModel。
            binding.actionButton.setOnClickListener {
                if (action.enabled) onClick(action.id)
            }
            binding.actionButton.setOnLongClickListener {
                action.longClickEnabled && onLongClick(action.id)
            }
        }
    }

    private companion object {
        val DiffCallback = object : DiffUtil.ItemCallback<ConsoleAction>() {
            /**
             * 判断两个按钮是不是同一个业务动作。
             */
            override fun areItemsTheSame(oldItem: ConsoleAction, newItem: ConsoleAction): Boolean =
                oldItem.id == newItem.id

            /**
             * 判断同一个按钮的显示内容是否发生变化。
             */
            override fun areContentsTheSame(
                oldItem: ConsoleAction,
                newItem: ConsoleAction
            ): Boolean =
                oldItem == newItem
        }
    }
}
