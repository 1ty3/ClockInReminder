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
 * 通过回调把“打卡 / 撤销 / 开关 / 删除”动作交回 Activity 处理。
 */
class TaskAdapter(
    private val onDone: (Task) -> Unit,
    private val onUndo: (Task) -> Unit,
    private val onToggle: (Task) -> Unit,
    private val onDelete: (Task) -> Unit
) : RecyclerView.Adapter<TaskAdapter.TaskViewHolder>() {

    private var items: List<Task> = emptyList()
    private var doneIds: Set<Long> = emptySet()

    fun submitList(list: List<Task>) {
        items = list
        notifyDataSetChanged()
    }

    /** 联动点①：把「今天已打卡」的任务 id 集合交给列表 */
    fun submitDoneIds(ids: Set<Long>) {
        if (doneIds == ids) return
        doneIds = ids
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
            val done = task.id in doneIds

            binding.tvTaskName.text = task.name
            binding.tvTaskTime.text = DateUtils.formatTime(task.hour, task.minute)
            binding.tvRepeat.text = if (task.repeatDaily) "每天" else "仅一次"

            // ===== 联动点①：打卡状态回流 =====
            // 卡片上直接显示今天打没打，按钮跟着变；打过卡时给「撤销」，免得点错没救。
            if (done) {
                binding.tvDoneStatus.text = "✅ 今天已打卡"
                binding.tvDoneStatus.setTextColor(0xFF2E7D32.toInt())
                binding.tvTaskName.alpha = 0.55f
                binding.btnDone.text = "撤销"
            } else {
                binding.tvDoneStatus.text = "○ 今天还没打卡"
                binding.tvDoneStatus.setTextColor(0xFF9E9E9E.toInt())
                binding.tvTaskName.alpha = 1.0f
                binding.btnDone.text = "打卡"
            }

            // 注意：setChecked 不会触发监听器，避免重绑时误触发
            binding.switchEnabled.isChecked = task.enabled
            binding.switchEnabled.setOnCheckedChangeListener { _, isChecked ->
                onToggle(task.copy(enabled = isChecked))
            }
            binding.btnDone.setOnClickListener { if (done) onUndo(task) else onDone(task) }
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
