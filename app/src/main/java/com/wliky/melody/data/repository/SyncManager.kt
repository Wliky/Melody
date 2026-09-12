package com.wliky.melody.data.repository

import com.wliky.melody.core.common.DispatchersProvider
import com.wliky.melody.core.common.NetworkMonitor
import com.wliky.melody.core.datastore.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 同步调度器（文档 §9）。
 *
 * **全自动，没有任何手动同步入口。** 播放行为一产生就入队，队列一有内容就自动提交，
 * 用户不需要（也无法）点「同步」按钮：
 *
 *  1. 队列一旦出现待同步事件 → 防抖合并后立即提交（一首歌听完只发一次请求）；
 *  2. 网络从离线恢复在线 → 补交积压；
 *  3. 每 [RETRY_INTERVAL_MS] 兜底重试一次，只重试未达上限的事件。
 *
 * 并发由 [mutex] 串行化：定时器、网络恢复、新事件可能同时想同步，串起来跑避免重复提交。
 */
@Singleton
class SyncManager @Inject constructor(
    private val syncRepository: SyncRepository,
    private val networkMonitor: NetworkMonitor,
    private val settingsRepository: SettingsRepository,
    dispatchers: DispatchersProvider,
) {

    private val scope = CoroutineScope(SupervisorJob() + dispatchers.io)
    private val mutex = Mutex()

    private var started = false

    fun start() {
        if (started) return
        started = true

        // 1) 自动同步：队列一非空就提交，不需要任何人触发
        scope.launch {
            syncRepository.pendingCount
                .map { it > 0 }
                .distinctUntilChanged()
                .filter { it }
                .collect {
                    // 防抖：把短时间内连续入队的事件合并成一次提交
                    delay(ENQUEUE_DEBOUNCE_MS)
                    runSync()
                    prune()
                }
        }

        // 2) 网络恢复 / 上报开关被打开时补交积压
        scope.launch {
            combine(
                networkMonitor.isOnline,
                settingsRepository.settings,
            ) { online, settings -> online && settings.reportPlayback }
                .distinctUntilChanged()
                .collect { shouldSync ->
                    if (shouldSync) {
                        runSync()
                        prune()
                    }
                }
        }

        // 3) 兜底重试（只处理未超过重试上限的失败事件，不会无限打接口）
        scope.launch {
            while (isActive) {
                delay(RETRY_INTERVAL_MS)
                runSync()
            }
        }
    }

    /** 所有同步请求的唯一入口，串行执行。返回值仅供内部日志/测试使用。 */
    private suspend fun runSync(): SyncReport = mutex.withLock {
        runCatching { syncRepository.syncNow() }.getOrDefault(SyncReport())
    }

    private suspend fun prune() {
        runCatching { syncRepository.pruneSynced() }
    }

    private companion object {
        /** 合并连续入队的窗口：听完一首歌期间不会有多次请求。 */
        const val ENQUEUE_DEBOUNCE_MS = 3_000L

        const val RETRY_INTERVAL_MS = 5 * 60 * 1000L
    }
}
