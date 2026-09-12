package com.wliky.melody

import android.app.Application
import com.wliky.melody.data.repository.SyncManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MelodyApplication : Application() {

    @Inject
    lateinit var syncManager: SyncManager

    override fun onCreate() {
        super.onCreate()
        // 播放事件同步调度：网络恢复 / 用户开启开关 / 定时兜底重试（文档 §9）
        syncManager.start()
    }
}
