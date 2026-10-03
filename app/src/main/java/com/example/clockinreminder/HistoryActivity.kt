package com.example.clockinreminder

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.clockinreminder.databinding.ActivityHistoryBinding
import com.example.clockinreminder.ui.HistoryAdapter
import com.example.clockinreminder.util.DateUtils
import kotlinx.coroutines.launch

/**
 * 打卡历史页：列出每个任务的连续天数与累计次数。
 */
class HistoryActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHistoryBinding
    private lateinit var viewModel: TaskViewModel
    private lateinit var adapter: HistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[TaskViewModel::class.java]
        adapter = HistoryAdapter()
        binding.recyclerHistory.layoutManager = LinearLayoutManager(this)
        binding.recyclerHistory.adapter = adapter

        loadHistory()
    }

    private fun loadHistory() {
        lifecycleScope.launch {
            val tasks = viewModel.getAllTasks()
            val items = tasks.map { task ->
                val dates = viewModel.getDatesForTask(task.id)
                HistoryAdapter.HistoryItem(
                    name = task.name,
                    streak = DateUtils.currentStreak(dates),
                    total = viewModel.getCountForTask(task.id)
                )
            }
            adapter.submitList(items)
        }
    }
}
