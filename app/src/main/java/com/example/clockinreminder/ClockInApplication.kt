package com.example.clockinreminder

import android.app.Application
import com.example.clockinreminder.data.AppDatabase

/**
 * Application 级单例：持有 Room 数据库实例，供全局复用。
 */
class ClockInApplication : Application() {
    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }
}
