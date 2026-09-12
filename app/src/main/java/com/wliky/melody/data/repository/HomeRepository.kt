package com.wliky.melody.data.repository

import com.wliky.melody.core.common.AppResult
import com.wliky.melody.core.common.appRunCatching
import com.wliky.melody.core.model.HomeFeed
import com.wliky.melody.core.model.PlaylistDetail
import com.wliky.melody.data.netease.NeteaseProviderResolver
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 首页（文档 §6）。
 * 元数据缓存 5 分钟：下拉刷新会强制穿透，正常进入页面优先用缓存，
 * 减少无谓请求（音乐文件本身不缓存，遵守版权限制）。
 */
@Singleton
class HomeRepository @Inject constructor(
    private val providers: NeteaseProviderResolver,
) {

    private var cachedFeed: HomeFeed? = null
    private var cachedAt: Long = 0L

    suspend fun loadHome(forceRefresh: Boolean = false): AppResult<HomeFeed> {
        val now = System.currentTimeMillis()
        if (!forceRefresh) {
            cachedFeed?.takeIf { now - cachedAt < CACHE_TTL_MS }?.let { return AppResult.Success(it) }
        }
        return appRunCatching {
            val feed = providers.current().homeFeed()
            cachedFeed = feed
            cachedAt = System.currentTimeMillis()
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

    private companion object {
        const val CACHE_TTL_MS = 5 * 60 * 1000L
    }
}
