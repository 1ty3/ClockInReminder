# 打卡提醒 · ClockInReminder

一个轻量的安卓打卡提醒应用：自定义多个打卡任务（上班、运动、吃药……），到点用**系统通知 + 震动**提醒；一键打卡，并记录**连续打卡天数**。

退出应用、息屏、锁屏后依然能准时提醒；未打卡前通知常驻，点「全部清除」也清不掉。

> **下载**：[最新版 APK](https://github.com/1ty3/ClockInReminder/releases/latest) —— 当前 `v1.1`

---

## 功能

| 功能 | 说明 |
|---|---|
| 多任务自定义 | 每个任务可设名称、提醒时间、是否震动 |
| 精确提醒 | 基于 `AlarmManager.setAlarmClock`，Doze 省电模式下也能到点唤醒 |
| 后台可靠 | 退出应用 / 锁屏 / 息屏后依然提醒；开机或应用更新后自动重排全部闹钟 |
| 通知 + 震动 | 高优先级通知渠道，带默认铃声与震动波形；另用 `Vibrator` 补震一次 |
| 锁屏强提醒 | `fullScreenIntent`，类似来电，息屏也能强弹，不被折叠 |
| 常驻通知 | 未打卡前通知**不可被侧滑或「全部清除」移除**，进 App 打卡后才消失（对标 GKD 的常驻通知） |
| 一键打卡 | 列表内点「打卡」，或直接在通知栏点「标记完成」 |
| 打卡历史 | 按任务查看每日打卡记录 |
| 连续天数 | 自动统计连续打卡天数（今天没打卡则从昨天往前数，不会误判断签） |
| 保活引导 | 内置电池优化 / 自启动 / 后台耗电设置引导，适配 vivo、小米、华为等国产 ROM |

---

## 安装

1. 从 [Releases](https://github.com/1ty3/ClockInReminder/releases/latest) 下载 `app-release.apk`。
2. 手机「设置 → 安全」里打开「未知来源应用 / 安装未知应用」（允许你用来打开 APK 的 App）。
3. 点开 APK → 安装 → 完成。

要求 **Android 6.0（API 23）及以上**。

### 首次运行必做

| 步骤 | 怎么做 | 不做会怎样 |
|---|---|---|
| 允许通知（Android 13+） | 首次进入 App 弹窗点「允许」 | 到点不弹通知 |
| 允许精确闹钟（Android 12+） | 在手机「闹钟与提醒」页给本应用开启 | 提醒可能延迟 |
| 关闭电池优化 | 打开 App 弹窗点「去设置①」 | 后台闹钟被系统清掉 |
| 开自启动 | 弹窗点「去设置②」 | 划掉 App 后不提醒 |
| 允许后台高耗电 | 弹窗点「去设置③」 | 划掉 App 后不提醒 |

> **vivo / iQOO 用户重点**：上面后三项是三道**独立**关卡，只做第一项不够。设置完建议在最近任务列表里**锁定**本应用卡片（下拉卡片点锁图标），防止被一键清理。
>
> ⚠️ 不要用「应用信息 → 强制停止」，它会永久清除所有已排闹钟，直到你重新打开 App。

### 验证是否生效

1. 打开 App，点右上角「**测试提醒**」→ 应立即收到通知 + 震动。
   - 有反应 = App 本身正常，之前不响是权限/省电问题。
   - 没反应 = 权限没给，回去检查上表。
2. 真机定时验证：退出 App、锁屏，添加一个**几分钟后**的任务，到点看是否响。

---

## 技术栈

| 用途 | 技术 |
|---|---|
| 语言 / 构建 | Kotlin 1.9.22 · Gradle 8.9（含 Wrapper） · AGP 8.2 · JDK 17 |
| 系统版本 | compileSdk 34 · targetSdk 34 · minSdk 23 |
| 提醒调度 | `AlarmManager.setAlarmClock` + `BroadcastReceiver`（静态注册） |
| 通知 | `NotificationCompat` + `NotificationChannel` + `fullScreenIntent` + `setOngoing` |
| 本地数据库 | Room 2.6.1（`Task` / `CheckInRecord`） |
| UI | ViewBinding · Material Components · RecyclerView · ConstraintLayout |
| 异步 | Kotlin Coroutines · `lifecycleScope` · `Flow` |

---

## 项目结构

```
ClockInReminder/
├── build.gradle / settings.gradle / gradle.properties
├── gradlew / gradlew.bat / gradle/wrapper/       # Gradle Wrapper，无需预装 Gradle
└── app/
    ├── build.gradle                              # 依赖 + release 签名配置
    └── src/main/
        ├── AndroidManifest.xml                   # 权限与 3 个 Receiver 注册
        ├── java/com/example/clockinreminder/
        │   ├── ClockInApplication.kt             # 全局持有 Room 数据库
        │   ├── MainActivity.kt                   # 任务列表 / 打卡 / 开关 / 删除 / 保活引导
        │   ├── AddEditTaskActivity.kt            # 新增任务 + 时间选择
        │   ├── HistoryActivity.kt                # 连续天数 + 累计次数
        │   ├── TaskViewModel.kt                  # 数据桥接
        │   ├── data/                             # Task / CheckInRecord 实体 + DAO + AppDatabase
        │   ├── scheduler/AlarmScheduler.kt       # 提醒调度
        │   ├── notification/NotificationHelper.kt# 通知 + 震动
        │   ├── receiver/ReminderReceivers.kt     # 闹钟 / 标记完成 / 开机 三个广播
        │   ├── repository/TaskRepository.kt
        │   ├── ui/Adapters.kt
        │   └── util/DateUtils.kt                 # 下次触发时间 + 连续天数算法
        └── res/  (layout / values / drawable)
```

---

## 构建

### 方式一：Android Studio

`File ▸ Open` 选中本目录，等 Gradle Sync 完成后 `Run ▸ Run 'app'`。

### 方式二：命令行

```bash
# debug 版（用默认调试签名，可直接安装）
./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk

# release 版（需要先配置签名，见下）
./gradlew assembleRelease
# 产物：app/build/outputs/apk/release/app-release.apk
```

Windows 下把 `./gradlew` 换成 `gradlew.bat`。

### 关于 release 签名

仓库里**不包含**签名密钥（这是有意的）。`app/build.gradle` 会读取根目录的 `keystore.properties`，文件不存在时自动跳过签名配置，**不影响 debug 构建**。

自己构建 release 版需要两步：

1. 生成密钥库：
   ```bash
   keytool -genkeypair -v -keystore my-release.keystore -alias mykey \
     -keyalg RSA -keysize 2048 -validity 10000
   ```
2. 在工程根目录建 `keystore.properties`：
   ```properties
   storeFile=my-release.keystore
   storePassword=你的密码
   keyAlias=mykey
   keyPassword=你的密码
   ```

> 该文件已被 `.gitignore` 屏蔽（连同 `*.keystore` / `*.jks`），不会被提交。**千万别**把密钥和密码传到公开仓库。

### 本地配置说明

`local.properties`（记录本机 Android SDK 路径）同样不入库，Android Studio 打开工程时会自动生成。

---

## 权限说明

| 权限 | 用途 |
|---|---|
| `POST_NOTIFICATIONS` | 弹通知（Android 13+ 需运行时申请） |
| `VIBRATE` | 提醒时震动 |
| `SCHEDULE_EXACT_ALARM` | 精确闹钟（当前用 `setAlarmClock`，保留以备回退，无害） |
| `RECEIVE_BOOT_COMPLETED` | 开机 / 应用更新后重排闹钟（系统会清空已排闹钟，必须重排） |
| `USE_FULL_SCREEN_INTENT` | 锁屏全屏强提醒 |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | 跳转到「忽略电池优化」设置页 |

本应用**不联网、不收集任何数据**，所有任务与打卡记录仅保存在本机 Room 数据库中。

---

## 已知限制

- **国产 ROM 的后台管理**是最大变量。已用最抗杀的 `setAlarmClock` 并内置保活引导，但部分机型（尤其 vivo/iQOO 的激进省电策略）仍需用户手动放行。
- 提醒时间只到**分钟**粒度，且为**每日重复**；暂不支持「只在工作日提醒」「提前 N 分钟提醒」「多个时间点」。
- 「稍后提醒」一条提醒**只能延后一次**、固定延后 10 分钟（避免无限顺延反而漏打卡）；延后补发的那条通知不再带该按钮。
- 应用名为「打卡提醒」，包名 `com.example.clockinreminder`，图标为矢量占位图。

---

## 版本

| 版本 | 说明 |
|---|---|
| `v1.1` | 打卡状态回流主页、今日进度看板、已打卡免打扰、删除/禁用联动清理、撤销打卡、通知显示连续天数、稍后提醒（限一次）、批量一键打卡 |
| `v1.0` | 首个发布版：多任务提醒、通知+震动、常驻通知、通知栏一键标记完成、连续天数统计、保活引导 |

---

## 许可

个人学习项目，可自由参考使用。
