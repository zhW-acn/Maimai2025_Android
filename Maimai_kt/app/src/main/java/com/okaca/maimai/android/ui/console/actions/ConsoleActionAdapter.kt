package com.okaca.maimai.android.ui.console.actions

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.okaca.maimai.android.databinding.ItemConsoleActionBinding

class ConsoleActionAdapter(
    private val onClick: (ConsoleActionId) -> Unit,
    private val onLongClick: (ConsoleActionId) -> Boolean,
) : ListAdapter<ConsoleAction, ConsoleActionAdapter.ActionViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ActionViewHolder {
        val binding = ItemConsoleActionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return ActionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ActionViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ActionViewHolder(
        private val binding: ItemConsoleActionBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(action: ConsoleAction) {
            binding.action = action
            binding.executePendingBindings()

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
            override fun areItemsTheSame(oldItem: ConsoleAction, newItem: ConsoleAction): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(
                oldItem: ConsoleAction,
                newItem: ConsoleAction
            ): Boolean =
                oldItem == newItem
        }
    }
}

