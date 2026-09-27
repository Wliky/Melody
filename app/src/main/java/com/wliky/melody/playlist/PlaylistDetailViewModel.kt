package com.wliky.melody.playlist

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wliky.melody.data.error.AppResult
import com.wliky.melody.data.model.Playlist
import com.wliky.melody.data.model.Song
import com.wliky.melody.data.repo.HomeRepository
import com.wliky.melody.data.repo.SongRepository
import com.wliky.melody.player.PlayerQueue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 歌单详情页状态：歌单元信息 + 曲目列表 + 收藏 + 点播。
 * 点击任意曲目 → 整个歌单进入 [PlayerQueue]，从该首开始播。
 */
@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    private val homeRepository: HomeRepository,
    private val songRepository: SongRepository,
    val playerQueue: PlayerQueue,
) : ViewModel() {

    data class DetailUiState(
        val loading: Boolean = true,
        val playlist: Playlist? = null,
        val songs: List<Song> = emptyList(),
        val error: String? = null,
    )

    var uiState by mutableStateOf(DetailUiState())
        private set

    /** 多选模式：选中的曲目下标集 */
    var selectionMode by mutableStateOf(false)
        private set
    var selectedIndexes by mutableStateOf<Set<Int>>(emptySet())
        private set

    private companion object {
        /** 红心变化后延迟重拉，避开服务端写入延迟（乐观更新先落地，再对齐服务端）。 */
        const val REFRESH_DEBOUNCE_MS = 800L
    }

    /** 首次进入或重试时加载；同一歌单重复调用只生效一次。 */
    fun start(playlist: Playlist) {
        if (uiState.playlist?.id == playlist.id && uiState.songs.isNotEmpty()) return
        uiState = DetailUiState(loading = true, playlist = playlist)
        load(playlist)
    }

    /** 曲目 + 详情元信息（标签/简介/创建者/评论数/收藏态）并行加载。 */
    private fun load(playlist: Playlist) {
        viewModelScope.launch {
            val tracksDeferred = async { homeRepository.getPlaylistTracks(playlist.id, limit = 1000) }
            val detailDeferred = async { homeRepository.getPlaylistDetail(playlist.id) }
            val detail = (detailDeferred.await() as? AppResult.Success)?.data

            when (val r = tracksDeferred.await()) {
                is AppResult.Failure ->
                    uiState = uiState.copy(
                        loading = false,
                        playlist = detail ?: playlist,
                        error = r.error.message,
                    )

                is AppResult.Success ->
                    uiState = uiState.copy(
                        loading = false,
                        // 详情元信息更全（标签/简介/创建者头像/评论数/收藏态），加载成功则替换
                        playlist = detail ?: playlist,
                        songs = r.data,
                        error = if (r.data.isEmpty()) "歌单里没有曲目" else null,
                    )
            }
        }
    }

    /** 收藏 / 取消收藏歌单（乐观更新，失败回滚）。 */
    fun toggleSubscribe() {
        val current = uiState.playlist ?: return
        val target = !current.subscribed
        uiState = uiState.copy(playlist = current.copy(subscribed = target))
        viewModelScope.launch {
            val result = homeRepository.subscribePlaylist(current.id, target)
            if (result is AppResult.Failure) {
                uiState = uiState.copy(playlist = current)
            }
        }
    }

    /** 是否为「我喜欢的音乐」红心歌单（specialType == 5）。 */
    val isLikedPlaylist: Boolean
        get() = uiState.playlist?.specialType == 5

    /**
     * 当前展示的曲目：普通歌单 = 原始列表；
     * 「我喜欢的音乐」= 按全局红心状态实时过滤，
     * 取消红心后列表立即少一首（无需手动刷新）。
     */
    val displaySongs: List<Song>
        get() {
            val all = uiState.songs
            return if (isLikedPlaylist) {
                val liked = playerQueue.likedSongIds
                all.filter { it.id in liked }
            } else {
                all
            }
        }

    init {
        // 红心歌单：红心状态变化后与服务端对齐。
        // 过滤能即时移除，但新红心的歌不在已缓存曲目里，需重拉一次才出现。
        viewModelScope.launch {
            snapshotFlow { playerQueue.likedSongIds }
                .drop(1)
                .collectLatest { liked ->
                    if (!isLikedPlaylist || uiState.songs.isEmpty()) return@collectLatest
                    val cached = uiState.songs.map { it.id }.toSet()
                    // 存在红心歌曲未出现在当前列表（新增 / 移除未同步）时才重拉
                    if (liked.any { it !in cached } || cached.any { it !in liked }) {
                        delay(REFRESH_DEBOUNCE_MS)
                        reloadTracks()
                    }
                }
        }
    }

    /** 只重拉曲目，保留已加载的歌单元信息（避免刷新时头部闪烁）。 */
    private suspend fun reloadTracks() {
        val playlist = uiState.playlist ?: return
        when (val r = homeRepository.getPlaylistTracks(playlist.id, limit = 1000)) {
            is AppResult.Success -> uiState = uiState.copy(
                songs = r.data,
                error = if (r.data.isEmpty()) "歌单里没有曲目" else null,
            )
            is AppResult.Failure -> Unit
        }
    }

    /** 点歌：整单入队，从 [index] 播。 */
    fun playSong(index: Int) {
        val songs = displaySongs
        if (index in songs.indices) {
            playerQueue.setQueue(songs, index)
        }
    }

    /** 播放所选（多选模式确认操作）。 */
    fun playSelected() {
        val songs = selectedSongs()
        if (songs.isNotEmpty()) {
            playerQueue.setQueue(songs, 0)
            exitSelectionMode()
        }
    }

    /** 选中曲目（按展示顺序）。 */
    fun selectedSongs(): List<Song> =
        selectedIndexes.sorted().mapNotNull { displaySongs.getOrNull(it) }

    fun enterSelectionMode(firstIndex: Int) {
        selectionMode = true
        selectedIndexes = setOf(firstIndex)
    }

    fun toggleSelected(index: Int) {
        selectedIndexes = if (index in selectedIndexes) {
            selectedIndexes - index
        } else {
            selectedIndexes + index
        }
    }

    fun selectAll() {
        selectedIndexes = displaySongs.indices.toSet()
    }

    fun exitSelectionMode() {
        selectionMode = false
        selectedIndexes = emptySet()
    }

    /**
     * 删除所选曲目（多选底部操作；仅自己创建的歌单可删，
     * 服务端拒绝时提示）。成功后从列表移除并退出多选。
     */
    fun removeSelected() {
        val playlist = uiState.playlist ?: return
        val songs = selectedSongs()
        if (songs.isEmpty()) return
        val removingIds = songs.map { it.id }.toSet()
        viewModelScope.launch {
            when (val r = songRepository.removeFromPlaylist(playlist.id, songs.map { it.id })) {
                is AppResult.Success -> {
                    uiState = uiState.copy(songs = uiState.songs.filter { it.id !in removingIds })
                    exitSelectionMode()
                }
                is AppResult.Failure ->
                    uiState = uiState.copy(error = "删除失败：${r.error.message}")
            }
        }
    }
}
