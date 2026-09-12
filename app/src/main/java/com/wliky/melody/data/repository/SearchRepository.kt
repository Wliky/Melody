package com.wliky.melody.data.repository

import com.wliky.melody.core.common.AppResult
import com.wliky.melody.core.common.Clock
import com.wliky.melody.core.common.appRunCatching
import com.wliky.melody.core.database.SearchHistoryDao
import com.wliky.melody.core.database.SearchHistoryEntity
import com.wliky.melody.core.model.Album
import com.wliky.melody.core.model.Artist
import com.wliky.melody.core.model.Page
import com.wliky.melody.core.model.Playlist
import com.wliky.melody.core.model.SearchSuggestions
import com.wliky.melody.core.model.Song
import com.wliky.melody.data.netease.NeteaseProviderResolver
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** 搜索（文档 §6）。含搜索历史本地存储。 */
@Singleton
class SearchRepository @Inject constructor(
    private val providers: NeteaseProviderResolver,
    private val searchHistoryDao: SearchHistoryDao,
    private val clock: Clock,
) {

    val history: Flow<List<String>> = searchHistoryDao.observeRecent(HISTORY_LIMIT)
        .map { list -> list.map { it.keyword } }

    suspend fun searchSongs(keyword: String, page: Int, pageSize: Int): AppResult<Page<Song>> =
        appRunCatching { providers.current().searchSongs(keyword, page, pageSize) }

    suspend fun searchArtists(keyword: String, page: Int, pageSize: Int): AppResult<Page<Artist>> =
        appRunCatching { providers.current().searchArtists(keyword, page, pageSize) }

    suspend fun searchAlbums(keyword: String, page: Int, pageSize: Int): AppResult<Page<Album>> =
        appRunCatching { providers.current().searchAlbums(keyword, page, pageSize) }

    suspend fun searchPlaylists(keyword: String, page: Int, pageSize: Int): AppResult<Page<Playlist>> =
        appRunCatching { providers.current().searchPlaylists(keyword, page, pageSize) }

    suspend fun suggestions(keyword: String): AppResult<SearchSuggestions> =
        appRunCatching { providers.current().suggestions(keyword) }

    suspend fun recordHistory(keyword: String) {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) return
        runCatching {
            searchHistoryDao.insert(SearchHistoryEntity(trimmed, clock.now()))
        }
    }

    suspend fun clearHistory() {
        runCatching { searchHistoryDao.clear() }
    }

    private companion object {
        const val HISTORY_LIMIT = 12
    }
}
