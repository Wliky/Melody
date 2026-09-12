package com.wliky.melody.data.repository

import com.wliky.melody.core.common.DispatchersProvider
import com.wliky.melody.core.common.NetworkMonitor
import com.wliky.melody.core.datastore.SettingsRepository
import com.wliky.melody.core.model.SyncState
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 同步调度器（文档 §9「网络恢复后进入同步队列」）。
 *
 * 触发时机：
 *  1. 网络从离线恢复在线；
 *  2. 用户打开「播放记录上报」开关；
 *  3. 每 [RETRY_INTERVAL_MS] 兜底重试一次（只重试未达上限的事件）。
 */
@Singleton
class SyncManager @Inject constructor(
    private val syncRepository: SyncRepository,
    private val networkMonitor: NetworkMonitor,
    private val settingsRepository: SettingsRepository,
    dispatchers: DispatchersProvider,
) {

    private val scope = CoroutineScope(SupervisorJob() + dispatchers.io)

    private var started = false

    fun start() {
        if (started) return
        started = true

        scope.launch {
            combine(
                networkMonitor.isOnline,
                settingsRepository.settings,
            ) { online, settings -> online && settings.reportPlayback }
                .distinctUntilChanged()
                .collect { shouldSync ->
                    if (shouldSync) {
                        runCatching { syncRepository.syncNow() }
                        runCatching { syncRepository.pruneSynced() }
                    }
                }
        }

        scope.launch {
            while (isActive) {
                delay(RETRY_INTERVAL_MS)
                runCatching { syncRepository.syncNow() }
            }
        }
    }

    /** 供 UI「立即同步」按钮调用。 */
    suspend fun syncNow(): SyncReport = syncRepository.syncNow()

    private companion object {
        const val RETRY_INTERVAL_MS = 5 * 60 * 1000L
    }
}
