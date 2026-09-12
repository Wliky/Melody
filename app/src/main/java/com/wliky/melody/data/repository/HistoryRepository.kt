package com.wliky.melody.data.repository

import com.wliky.melody.core.common.AppResult
import com.wliky.melody.core.common.Clock
import com.wliky.melody.core.common.appRunCatching
import com.wliky.melody.core.database.PlaybackHistoryDao
import com.wliky.melody.core.database.PlaybackHistoryEntity
import com.wliky.melody.core.model.Album
import com.wliky.melody.core.model.Artist
import com.wliky.melody.core.model.Song
import com.wliky.melody.core.security.SecureSessionStore
import com.wliky.melody.data.netease.NeteaseProviderResolver
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 播放历史。
 * 本地历史用 Room 持久化（离线可看、可搜索），远端记录走可选 Provider。
 */
@Singleton
class HistoryRepository @Inject constructor(
    private val historyDao: PlaybackHistoryDao,
    private val providers: NeteaseProviderResolver,
    private val session: SecureSessionStore,
    private val clock: Clock,
) {

    val localHistory: Flow<List<Song>> = historyDao.observeRecent(LOCAL_LIMIT)
        .map { list -> list.map { it.toSong() } }

    suspend fun recordPlay(song: Song) {
        runCatching {
            val existing = historyDao.find(song.id)
            historyDao.upsert(
                PlaybackHistoryEntity(
                    songId = song.id,
                    songName = song.name,
                    artistName = song.artistText,
                    albumName = song.album?.name.orEmpty(),
                    coverUrl = song.coverUrl,
                    durationMs = song.durationMs,
                    lastPlayedAt = clock.now(),
                    playCount = (existing?.playCount ?: 0) + 1,
                ),
            )
        }
    }

    suspend fun clearLocal() {
        runCatching { historyDao.clear() }
    }

    suspend fun remoteRecords(): AppResult<List<Song>> = appRunCatching {
        val userId = session.userId()
        if (userId.isBlank()) return@appRunCatching emptyList()
        providers.current().remotePlayRecords(userId)
    }

    private fun PlaybackHistoryEntity.toSong(): Song = Song(
        id = songId,
        name = songName,
        artists = if (artistName.isBlank()) emptyList() else listOf(Artist(id = "", name = artistName)),
        album = Album(id = "", name = albumName, coverUrl = coverUrl),
        durationMs = durationMs,
        coverUrl = coverUrl,
    )

    private companion object {
        const val LOCAL_LIMIT = 200
    }
}
