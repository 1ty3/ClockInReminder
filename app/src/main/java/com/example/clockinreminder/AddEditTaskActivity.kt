package com.example.clockinreminder

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.clockinreminder.data.Task
import com.example.clockinreminder.databinding.ActivityAddTaskBinding
import com.example.clockinreminder.scheduler.AlarmScheduler
import kotlinx.coroutines.launch

/**
 * 新增打卡任务页面（演示版仅做“新增”，编辑可在此基础上扩展）。
 */
class AddEditTaskActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAddTaskBinding
    private lateinit var viewModel: TaskViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddTaskBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[TaskViewModel::class.java]
        binding.timePicker.setIs24HourView(true)

        binding.btnSave.setOnClickListener { saveTask() }
    }

    private fun saveTask() {
        val name = binding.etTaskName.text.toString().trim()
        if (name.isEmpty()) {
            binding.etTaskName.error = "请输入任务名称"
            return
        }
        val task = Task(
            name = name,
            hour = binding.timePicker.hour,
            minute = binding.timePicker.minute,
            repeatDaily = binding.switchRepeat.isChecked,
            vibrate = binding.switchVibrate.isChecked,
            enabled = true
        )
        lifecycleScope.launch {
            val id = viewModel.insertTask(task.copy(id = 0))
            AlarmScheduler.schedule(this@AddEditTaskActivity, task.copy(id = id))
            finish()
        }
    }
}
