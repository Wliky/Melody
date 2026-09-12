package com.wliky.melody.data.repository

import com.wliky.melody.core.common.AppResult
import com.wliky.melody.core.common.appRunCatching
import com.wliky.melody.core.model.Lyric
import com.wliky.melody.core.model.Song
import com.wliky.melody.data.netease.NeteaseProviderResolver
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 歌曲详情与歌词。
 *
 * 歌词按 songId 做内存缓存：同一首歌来回切不会反复请求，
 * 且进程退出即释放（不落盘，避免缓存过多第三方内容）。
 */
@Singleton
class MusicRepository @Inject constructor(
    private val providers: NeteaseProviderResolver,
) {

    private val lyricCache = ConcurrentHashMap<String, Lyric>()

    suspend fun songDetail(ids: List<String>): AppResult<List<Song>> = appRunCatching {
        providers.current().songDetail(ids)
    }

    suspend fun lyric(songId: String): AppResult<Lyric> {
        lyricCache[songId]?.let { return AppResult.Success(it) }
        return appRunCatching {
            val lyric = providers.current().lyric(songId)
            if (!lyric.isEmpty) {
                if (lyricCache.size > LYRIC_CACHE_LIMIT) lyricCache.clear()
                lyricCache[songId] = lyric
            }
            lyric
        }
    }

    fun clearCache() {
        lyricCache.clear()
    }

    private companion object {
        const val LYRIC_CACHE_LIMIT = 60
    }
}
