package com.wliky.melody.data.repository

import com.wliky.melody.core.common.AppError
import com.wliky.melody.core.common.AppResult
import com.wliky.melody.core.common.appRunCatching
import com.wliky.melody.core.model.Playlist
import com.wliky.melody.core.model.PlaylistDetail
import com.wliky.melody.core.model.Song
import com.wliky.melody.core.security.SecureSessionStore
import com.wliky.melody.data.netease.NeteaseProviderResolver
import javax.inject.Inject
import javax.inject.Singleton

/** 歌单 / 收藏 / 榜单（文档 §6）。需要登录的接口在没有 Session 时直接给出明确错误。 */
@Singleton
class PlaylistRepository @Inject constructor(
    private val providers: NeteaseProviderResolver,
    private val session: SecureSessionStore,
) {

    suspend fun detail(playlistId: String): AppResult<PlaylistDetail> = appRunCatching {
        providers.current().playlistDetail(playlistId)
    }

    suspend fun userPlaylists(): AppResult<List<Playlist>> = appRunCatching {
        val userId = requireUserId()
        providers.current().userPlaylists(userId)
    }

    suspend fun likedSongs(): AppResult<List<Song>> = appRunCatching {
        val userId = requireUserId()
        providers.current().likeSongs(userId)
    }

    private fun requireUserId(): String {
        val userId = session.userId()
        if (userId.isBlank()) throw AppError.Unauthorized()
        return userId
    }
}
