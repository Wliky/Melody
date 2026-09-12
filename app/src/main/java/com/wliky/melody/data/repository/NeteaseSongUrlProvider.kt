package com.wliky.melody.data.repository

import com.wliky.melody.core.datastore.SettingsRepository
import com.wliky.melody.core.model.ApiMode
import com.wliky.melody.core.model.AudioQuality
import com.wliky.melody.core.model.PlaybackEvent
import com.wliky.melody.core.player.SongUrlProvider
import com.wliky.melody.data.netease.NeteaseProviderResolver
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 音频地址解析（core 层定义的端口 → data 层的适配实现）。
 *
 * ExoPlayer 在加载线程上同步调用，所以内部用 runBlocking 把挂起请求桥接过来，
 * 并带 10 分钟缓存（地址本身有时效性）。
 */
@Singleton
class NeteaseSongUrlProvider @Inject constructor(
    private val providers: NeteaseProviderResolver,
    private val settingsRepository: SettingsRepository,
) : SongUrlProvider {

    private data class CacheEntry(val url: String, val expiresAt: Long)

    private val cache = ConcurrentHashMap<String, CacheEntry>()

    override fun resolveBlocking(songId: String): String? {
        cache[songId]?.takeIf { it.expiresAt > System.currentTimeMillis() }?.let { return it.url }

        val resolved = runBlocking {
            withTimeoutOrNull(RESOLVE_TIMEOUT_MS) {
                val quality: AudioQuality = runCatching { settingsRepository.current().audioQuality }
                    .getOrDefault(AudioQuality.EXHIGH)
                runCatching { providers.current().songUrl(songId, quality) }.getOrNull()?.url
            }
        }

        if (!resolved.isNullOrBlank()) {
            cache[songId] = CacheEntry(resolved, System.currentTimeMillis() + CACHE_TTL_MS)
        }
        return resolved
    }

    fun clearCache() {
        cache.clear()
    }

    private companion object {
        const val CACHE_TTL_MS = 10 * 60 * 1000L
        const val RESOLVE_TIMEOUT_MS = 20_000L
    }
}

/**
 * 网易云播放记录上报 Provider。
 *
 * **默认开启、完全自动**：播放行为一产生就入队并在后台提交，客户端没有任何手动同步入口。
 * 但只有当前数据源确实提供可用上报通道时才会真正上传 —— 直连模式下官方未向第三方开放
 * 该接口，此时记录只留在本机，开关状态不会造成任何数据外发。
 * 用户可在设置里整体关掉上传（本地记录不受影响）。
 */
@Singleton
class NeteaseSyncProvider @Inject constructor(
    private val providers: NeteaseProviderResolver,
    private val settingsRepository: SettingsRepository,
) : SyncProvider {

    override val name: String = "NeteasePlaybackProvider"

    override fun isAvailable(): Boolean = runBlocking {
        val settings = settingsRepository.current()
        if (!settings.reportPlayback) return@runBlocking false
        if (settings.apiMode == ApiMode.MOCK) return@runBlocking false
        runCatching { providers.current().supportsPlaybackReport }.getOrDefault(false)
    }

    override suspend fun push(events: List<PlaybackEvent>): SyncOutcome {
        val dataSource = providers.current()
        if (!dataSource.supportsPlaybackReport) return SyncOutcome.UNSUPPORTED
        val succeeded = runCatching { dataSource.reportPlayback(events) }.getOrDefault(false)
        return if (succeeded) SyncOutcome.SUCCESS else SyncOutcome.FAILED
    }
}
