package com.example.clockinreminder.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.clockinreminder.data.Task
import com.example.clockinreminder.databinding.ItemTaskBinding
import com.example.clockinreminder.databinding.ItemHistoryBinding
import com.example.clockinreminder.util.DateUtils

/**
 * 主页任务列表适配器。
 * 通过回调把“打卡 / 开关 / 删除”动作交回 Activity 处理。
 */
class TaskAdapter(
    private val onDone: (Task) -> Unit,
    private val onToggle: (Task) -> Unit,
    private val onDelete: (Task) -> Unit
) : RecyclerView.Adapter<TaskAdapter.TaskViewHolder>() {

    private var items: List<Task> = emptyList()

    fun submitList(list: List<Task>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val binding = ItemTaskBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TaskViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class TaskViewHolder(private val binding: ItemTaskBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(task: Task) {
            binding.tvTaskName.text = task.name
            binding.tvTaskTime.text = DateUtils.formatTime(task.hour, task.minute)
            binding.tvRepeat.text = if (task.repeatDaily) "每天" else "仅一次"

            // 注意：setChecked 不会触发监听器，避免重绑时误触发
            binding.switchEnabled.isChecked = task.enabled
            binding.switchEnabled.setOnCheckedChangeListener { _, isChecked ->
                onToggle(task.copy(enabled = isChecked))
            }
            binding.btnDone.setOnClickListener { onDone(task) }
            binding.btnDelete.setOnClickListener { onDelete(task) }
        }
    }
}

/**
 * 历史页适配器：展示每个任务的“连续天数”和“累计次数”。
 */
class HistoryAdapter : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

    data class HistoryItem(val name: String, val streak: Int, val total: Int)

    private var items: List<HistoryItem> = emptyList()

    fun submitList(list: List<HistoryItem>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class HistoryViewHolder(private val binding: ItemHistoryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: HistoryItem) {
            binding.tvHistoryName.text = item.name
            binding.tvHistoryStreak.text = "连续打卡 ${item.streak} 天 🔥"
            binding.tvHistoryTotal.text = "累计打卡 ${item.total} 次"
        }
    }
}
