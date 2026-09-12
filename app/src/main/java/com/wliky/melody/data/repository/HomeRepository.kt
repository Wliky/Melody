package com.wliky.melody.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.wliky.melody.core.common.AppResult
import com.wliky.melody.core.common.appRunCatching
import com.wliky.melody.core.model.HomeFeed
import com.wliky.melody.core.model.PlaylistDetail
import com.wliky.melody.core.network.MelodyJson
import com.wliky.melody.data.netease.NeteaseProviderResolver
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/**
 * 首页（文档 §6）。
 *
 * v0.4.0 起加了两级缓存：
 *  1. 内存缓存 5 分钟（下拉刷新强制穿透）；
 *  2. **落盘缓存**：把最近一次成功拉取的 HomeFeed 序列化成 JSON 存 DataStore，
 *     进程重启后进 App 先读缓存秒开，再后台静默刷新 —— 解决「每次进 App 都拉取且慢」。
 *
 * 音乐文件本身不缓存（遵守版权限制），这里只缓存元数据。
 */
@Singleton
class HomeRepository @Inject constructor(
    private val providers: NeteaseProviderResolver,
    @ApplicationContext private val context: Context,
) {

    private val Context.homeCacheStore: DataStore<Preferences> by preferencesDataStore(name = "home_cache")

    private var cachedFeed: HomeFeed? = null
    private var cachedAt: Long = 0L

    private object Keys {
        val feedJson = stringPreferencesKey("home_feed_json")
        val cachedAt = longPreferencesKey("home_feed_cached_at")
    }

    suspend fun loadHome(forceRefresh: Boolean = false): AppResult<HomeFeed> {
        val now = System.currentTimeMillis()

        // 1) 内存缓存（进程内 5 分钟）
        if (!forceRefresh) {
            cachedFeed?.takeIf { now - cachedAt < CACHE_TTL_MS }?.let { return AppResult.Success(it) }
            // 2) 落盘缓存（进程重启后的首屏兜底）
            val diskFeed = readDiskCache()
            if (diskFeed != null) {
                cachedFeed = diskFeed
                cachedAt = readDiskCachedAt()
                return AppResult.Success(diskFeed)
            }
        }

        // 3) 网络拉取
        return appRunCatching {
            val feed = providers.current().homeFeed()
            cachedFeed = feed
            cachedAt = now
            writeDiskCache(feed, now)
            feed
        }
    }

    suspend fun playlistDetail(playlistId: String): AppResult<PlaylistDetail> = appRunCatching {
        providers.current().playlistDetail(playlistId)
    }

    fun invalidate() {
        cachedFeed = null
        cachedAt = 0L
    }

    private suspend fun readDiskCache(): HomeFeed? = runCatching {
        val prefs = context.homeCacheStore.data.first()
        val json = prefs[Keys.feedJson] ?: return@runCatching null
        MelodyJson.decodeFromString<HomeFeed>(json)
    }.getOrNull()

    private suspend fun readDiskCachedAt(): Long = runCatching {
        context.homeCacheStore.data.first()[Keys.cachedAt] ?: 0L
    }.getOrDefault(0L)

    private suspend fun writeDiskCache(feed: HomeFeed, at: Long) {
        runCatching {
            context.homeCacheStore.edit { prefs ->
                prefs[Keys.feedJson] = MelodyJson.encodeToString(feed)
                prefs[Keys.cachedAt] = at
            }
        }
    }

    private companion object {
        const val CACHE_TTL_MS = 5 * 60 * 1000L
    }
}
