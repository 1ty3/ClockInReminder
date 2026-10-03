package com.example.clockinreminder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.clockinreminder.data.Task
import com.example.clockinreminder.databinding.ActivityMainBinding
import com.example.clockinreminder.scheduler.AlarmScheduler
import com.example.clockinreminder.notification.NotificationHelper
import com.example.clockinreminder.ui.TaskAdapter
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: TaskViewModel
    private lateinit var adapter: TaskAdapter

    /** 当前任务列表（渲染今日进度用） */
    private var currentTasks: List<Task> = emptyList()

    /** 今天已打卡的任务 id（联动点①） */
    private var doneIds: Set<Long> = emptySet()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) Toast.makeText(this, "未授予通知权限，提醒可能不会弹出", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[TaskViewModel::class.java]
        adapter = TaskAdapter(
            onDone = { markDone(it) },
            onUndo = { undoDone(it) },
            onToggle = { toggleTask(it) },
            onDelete = { deleteTask(it) }
        )
        binding.recyclerTasks.layoutManager = LinearLayoutManager(this)
        binding.recyclerTasks.adapter = adapter

        // 任务列表变化 → 刷新 UI 并（重新）排好闹钟
        viewModel.tasks.observe(this) { tasks ->
            currentTasks = tasks
            adapter.submitList(tasks)
            tasks.forEach { AlarmScheduler.schedule(this, it) }
            renderTodayProgress()
        }

        // 联动点①：今日打卡状态变化 → 卡片状态与进度条自动刷新
        viewModel.todayDoneTaskIds.observe(this) { ids ->
            doneIds = ids.toSet()
            adapter.submitDoneIds(doneIds)
            renderTodayProgress()
        }

        binding.fabAdd.setOnClickListener {
            startActivity(Intent(this, AddEditTaskActivity::class.java))
        }
        binding.btnHistory.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        // 立即测试提醒：点一下马上弹通知+震动，用来验证 App 本身通不通
        binding.btnTest.setOnClickListener {
            NotificationHelper.showReminder(this, 0L, "测试提醒", true)
            Toast.makeText(this, "已发送测试提醒，留意通知栏和震动", Toast.LENGTH_SHORT).show()
        }

        askNotificationPermission()
        requestBatteryOptimizationExemption()
    }

    override fun onResume() {
        super.onResume()
        // 联动点⑤（跨天自愈）：App 一直开着过了零点，回来时把"今日"指向新的一天
        viewModel.rolloverIfDateChanged()
    }

    private fun markDone(task: Task) {
        lifecycleScope.launch {
            viewModel.markDone(task.id)
            NotificationHelper.cancelReminder(this@MainActivity, task.id)
            Toast.makeText(this@MainActivity, "「${task.name}」已打卡 ✅", Toast.LENGTH_SHORT).show()
        }
    }

    /** 撤销今日打卡（点错了有救） */
    private fun undoDone(task: Task) {
        lifecycleScope.launch {
            viewModel.undoDone(task.id)
            Toast.makeText(this@MainActivity, "已撤销「${task.name}」今日打卡", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleTask(task: Task) {
        lifecycleScope.launch {
            viewModel.updateTask(task)
            if (task.enabled) {
                AlarmScheduler.schedule(this@MainActivity, task)
            } else {
                // 联动点⑥：禁用任务的联动清理 ——
                // 闹钟必须撤掉；已经挂在通知栏的那条常驻提醒也要收掉，
                // 否则它 setOngoing(true) 清不掉，会一直赖在通知栏里。
                AlarmScheduler.cancel(this@MainActivity, task.id)
                NotificationHelper.cancelReminder(this@MainActivity, task.id)
            }
        }
    }

    private fun deleteTask(task: Task) {
        lifecycleScope.launch {
            // 联动点⑥：删除任务的联动清理 —— 闹钟 + 通知 + 打卡记录，一条不留
            AlarmScheduler.cancel(this@MainActivity, task.id)
            NotificationHelper.cancelReminder(this@MainActivity, task.id)
            viewModel.deleteTask(task)   // 内部连带删除该任务的打卡记录
            Toast.makeText(this@MainActivity, "已删除「${task.name}」及其打卡记录", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 联动点①：今日进度 = 「启用中」任务的打卡完成度。
     * 已禁用的任务不算进分母 —— 它今天本来就不该打卡。
     */
    private fun renderTodayProgress() {
        val enabledTasks = currentTasks.filter { it.enabled }
        val doneCount = enabledTasks.count { it.id in doneIds }

        binding.tvTodayProgress.text = if (enabledTasks.isEmpty()) {
            "今日进度：暂无可打卡任务"
        } else if (doneCount == enabledTasks.size) {
            "今日进度：$doneCount / ${enabledTasks.size} —— 今天全部完成 🎉"
        } else {
            "今日进度：$doneCount / ${enabledTasks.size}"
        }

        binding.progressToday.max = enabledTasks.size.coerceAtLeast(1)
        binding.progressToday.progress = doneCount
    }

    /** Android 13+ 运行时申请通知权限 */
    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    /**
     * 引导用户做后台保活设置。这是“退出应用后仍能准时提醒”的关键。
     * 国产 ROM（vivo/小米/华为等）有三道独立关卡会杀后台闹钟：
     *   1) 电池优化（不受限制）
     *   2) 自启动管理（允许）
     *   3) 后台高耗电 / 后台运行（允许）
     * 只做第 1 项不够，建议三项都开，否则一划掉 App 闹钟就被清掉。
     */
    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(PowerManager::class.java)
            val ignoring = pm.isIgnoringBatteryOptimizations(packageName)

            // 组装完整提示文本，Dialog 内可滚动，不会被截断
            val message = if (!ignoring) {
                "为保证退出应用后仍能准时提醒，请完成以下 3 步：\n\n" +
                "1. 电池优化白名单\n" +
                "   点“电池优化”，把「打卡提醒」设为“不受限制”。\n\n" +
                "2. 允许自启动\n" +
                "   点“自启动”，在列表里找到「打卡提醒」并打开。\n\n" +
                "3. 允许后台高耗电\n" +
                "   点“后台耗电”，在列表里找到「打卡提醒」并设为“允许”。\n\n" +
                "只做第 1 步不够，vivo/i管家 会杀后台闹钟，3 步都做完才稳。"
            } else {
                "电池优化已放行，但还有 2 步必须手动完成，否则划掉 App 后提醒仍可能不响：\n\n" +
                "1. 允许自启动\n" +
                "   点“自启动”，在列表里找到「打卡提醒」并打开。\n\n" +
                "2. 允许后台高耗电\n" +
                "   点“后台耗电”，在列表里找到「打卡提醒」并设为“允许”。\n\n" +
                "设置完成后，建议在最近任务列表里“锁定”本应用卡片，防止被一键清理。"
            }

            val dialog = AlertDialog.Builder(this)
                .setTitle("让提醒在后台也能响")
                .setMessage(message)

            if (!ignoring) {
                // 电池优化还没放行：3 个按钮分别去对应设置页
                dialog.setPositiveButton("电池优化") { _, _ -> openBatteryOptimizationSettings() }
                    .setNegativeButton("自启动") { _, _ -> openAutoStartSettings() }
                    .setNeutralButton("后台耗电") { _, _ -> openBackgroundPowerSettings() }
            } else {
                // 电池优化已放行：只需自启动 + 后台耗电 + 我知道了
                dialog.setPositiveButton("自启动") { _, _ -> openAutoStartSettings() }
                    .setNegativeButton("后台耗电") { _, _ -> openBackgroundPowerSettings() }
                    .setNeutralButton("我知道了", null)
            }
            dialog.show()
        }
    }

    /** 直达“电池优化不受限制”开关页 */
    private fun openBatteryOptimizationSettings() {
        try {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
            )
        } catch (_: Exception) {
            // 部分国产 ROM 没有直达页，回退到总列表
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                Toast.makeText(this, "请在列表中找到「打卡提醒」并设为“不受限制”", Toast.LENGTH_LONG).show()
            } catch (_: Exception) {
                Toast.makeText(this, "请手动到：设置 → 电池 → 后台耗电管理，把「打卡提醒」设为允许", Toast.LENGTH_LONG).show()
            }
        }
    }

    /** 跳转到 vivo/iQOO 自启动管理页；失败则回退到应用详情页 */
    private fun openAutoStartSettings() {
        tryIntents(
            candidates = listOf(
                Intent().setClassName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
                Intent().setClassName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
                Intent().setClassName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.StartWhiteListActivity"),
                Intent().setClassName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.PurviewTabActivity")
            ),
            fallback = {
                startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                )
            },
            manualHint = "请手动到：设置 → 应用与权限 → 权限管理 → 自启动，把「打卡提醒」打开"
        )
    }

    /** 跳转到 vivo/iQOO 后台耗电管理页；失败则回退到电池设置页 */
    private fun openBackgroundPowerSettings() {
        tryIntents(
            candidates = listOf(
                Intent().setClassName("com.vivo.abe", "com.vivo.abe.activity.HighPowerAppActivity"),
                Intent().setClassName("com.vivo.abe", "com.vivo.abe.activity.HighPowerAppListActivity"),
                Intent().setClassName("com.iqoo.daemonservice", "com.iqoo.daemonservice.activity.BgPowerManagerActivity")
            ),
            fallback = {
                try {
                    startActivity(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))
                } catch (_: Exception) {
                    startActivity(Intent(Settings.ACTION_SETTINGS))
                }
            },
            manualHint = "请手动到：设置 → 电池 → 后台耗电管理，把「打卡提醒」设为“允许后台高耗电”"
        )
    }

    /**
     * 依次尝试多个系统页面，第一个能成功启动的就直接跳转；
     * 全部失败则执行 fallback；fallback 也失败则弹出 manualHint。
     */
    private fun tryIntents(
        candidates: List<Intent>,
        fallback: (() -> Unit)? = null,
        manualHint: String
    ) {
        for (intent in candidates) {
            try {
                startActivity(intent)
                return
            } catch (_: Exception) {
                // 尝试下一个
            }
        }
        try {
            fallback?.invoke()
        } catch (_: Exception) {
            Toast.makeText(this, manualHint, Toast.LENGTH_LONG).show()
        }
    }
}
