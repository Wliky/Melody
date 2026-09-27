package com.wliky.melody.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wliky.melody.data.cache.RepoCache
import com.wliky.melody.data.error.AppResult
import com.wliky.melody.data.model.Banner
import com.wliky.melody.data.model.Playlist
import com.wliky.melody.data.model.Podcast
import com.wliky.melody.data.model.Song
import com.wliky.melody.data.model.Toplist
import com.wliky.melody.data.repo.AuthRepository
import com.wliky.melody.data.repo.DiscoverRepository
import com.wliky.melody.data.repo.HomeRepository
import com.wliky.melody.data.repo.PodcastRepository
import com.wliky.melody.data.repo.SessionEvent
import com.wliky.melody.player.PlayerQueue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import javax.inject.Inject

/**
 * 首页状态：每日推荐 / 排行榜 / 新歌 / 播客 / Banner / 推荐歌单（除每日推荐需登录外均免登录）。
 * 数据整体作为快照缓存（内存 + 磁盘）：进页秒显缓存，TTL 内不请求，
 * 过期静默拉新，下拉刷新强制走网络（对齐官方网易云）。
 * 登录 / 退出登录时强制刷新（每日推荐等区块依赖登录态）。
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val homeRepository: HomeRepository,
    private val discoverRepository: DiscoverRepository,
    private val podcastRepository: PodcastRepository,
    private val repoCache: RepoCache,
    authRepository: AuthRepository,
    val playerQueue: PlayerQueue,
) : ViewModel() {

    /** 页面快照：与 UI 数据一一对应，整体缓存 / 恢复。 */
    @Serializable
    data class HomeSnapshot(
        val banners: List<Banner> = emptyList(),
        val recommended: List<Playlist> = emptyList(),
        val dailySongs: List<Song> = emptyList(),
        val newSongs: List<Song> = emptyList(),
        val podcasts: List<Podcast> = emptyList(),
        val toplists: List<Toplist> = emptyList(),
    )

    data class HomeUiState(
        /** 首次无缓存时的骨架屏 */
        val loading: Boolean = true,
        /** 下拉刷新指示器（静默刷新不置位，对齐官方） */
        val refreshing: Boolean = false,
        val banners: List<Banner> = emptyList(),
        val recommended: List<Playlist> = emptyList(),
        val dailySongs: List<Song> = emptyList(),
        val newSongs: List<Song> = emptyList(),
        val podcasts: List<Podcast> = emptyList(),
        val toplists: List<Toplist> = emptyList(),
        val error: String? = null,
    )

    private companion object {
        const val CACHE_KEY = "home:snapshot"

        /** 缓存新鲜期：期间进页直接用缓存、不发请求（对齐官方首页策略）。 */
        const val CACHE_TTL_MS = 10 * 60_000L
    }

    var uiState by mutableStateOf(HomeUiState())
        private set

    private var loadJob: Job? = null

    init {
        load()
        // 登录 / 退出登录：强制刷新（每日推荐依赖登录态，TTL 缓存须绕过）
        viewModelScope.launch {
            authRepository.sessionEvents.collect { load(force = true) }
        }
    }

    /**
     * 进页加载 / 下拉刷新（官方策略）：
     * 有缓存先秒显 → TTL 内且非强刷直接返回 → 否则走网络，成功写缓存。
     * [force] = true 表示下拉刷新，强制走网络并显示刷新指示器。
     */
    fun load(force: Boolean = false) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (force) {
                // 错误态下拉刷新：无任何数据时回到骨架屏，而不是空白
                val empty = uiState.banners.isEmpty() && uiState.recommended.isEmpty() &&
                    uiState.toplists.isEmpty()
                uiState = uiState.copy(refreshing = true, error = null, loading = empty)
            }
            val cached = repoCache.read(CACHE_KEY, HomeSnapshot.serializer())
            if (cached != null) {
                applySnapshot(cached.value)
                if (!force && cached.isFresh(CACHE_TTL_MS)) {
                    uiState = uiState.copy(refreshing = false)
                    return@launch
                }
            } else if (!force) {
                uiState = uiState.copy(loading = true, error = null)
            }
            fetchRemote(fallback = cached?.value)
        }
    }

    /** 六路并行拉网：任一路失败只隐藏对应区块，不拖垮整页；逐字段回退旧缓存。 */
    private suspend fun fetchRemote(fallback: HomeSnapshot?) {
        coroutineScope {
            val bannersD = async { homeRepository.getBanners() }
            val recommendedD = async { homeRepository.getPersonalizedPlaylists() }
            val dailyD = async { discoverRepository.dailySongs() }
            val newD = async { discoverRepository.newSongs() }
            val podcastsD = async { podcastRepository.hotRadios() }
            val toplistsD = async { discoverRepository.toplists() }

            val snapshot = HomeSnapshot(
                banners = bannersD.await().orEmptyList(),
                recommended = recommendedD.await().orEmptyList(),
                dailySongs = dailyD.await().orEmptyList(),
                newSongs = newD.await().orEmptyList(),
                podcasts = podcastsD.await().orEmptyList(),
                toplists = toplistsD.await().orEmptyList(),
            )
            val gotAny = snapshot.banners.isNotEmpty() ||
                snapshot.recommended.isNotEmpty() ||
                snapshot.toplists.isNotEmpty()
            if (gotAny) repoCache.write(CACHE_KEY, snapshot, HomeSnapshot.serializer())

            val hasFallback = fallback != null && (
                fallback.banners.isNotEmpty() || fallback.recommended.isNotEmpty() ||
                    fallback.toplists.isNotEmpty()
                )
            uiState = uiState.copy(
                loading = false,
                refreshing = false,
                banners = pick(snapshot.banners, fallback?.banners),
                recommended = pick(snapshot.recommended, fallback?.recommended),
                dailySongs = pick(snapshot.dailySongs, fallback?.dailySongs),
                newSongs = pick(snapshot.newSongs, fallback?.newSongs),
                podcasts = pick(snapshot.podcasts, fallback?.podcasts),
                toplists = pick(snapshot.toplists, fallback?.toplists),
                // 网络全失败但有旧缓存：静默保留缓存不报错
                error = if (gotAny || hasFallback) null else "未收到任何数据，网络异常或接口返回结构有变化",
            )
        }
    }

    private fun applySnapshot(s: HomeSnapshot) {
        uiState = uiState.copy(
            loading = false,
            banners = s.banners,
            recommended = s.recommended,
            dailySongs = s.dailySongs,
            newSongs = s.newSongs,
            podcasts = s.podcasts,
            toplists = s.toplists,
            error = null,
        )
    }

    /** 网络结果为空时回退旧缓存（静默刷新失败不打断展示）。 */
    private fun <T> pick(new: List<T>, fallback: List<T>?): List<T> =
        if (new.isNotEmpty()) new else fallback.orEmpty()

    private fun <T> AppResult<List<T>>.orEmptyList(): List<T> = when (this) {
        is AppResult.Failure -> emptyList()
        is AppResult.Success -> data
    }

    /** 直接播放整个歌单（不进详情页）：拉取曲目后设置队列并从第一首起播。 */
    fun playPlaylist(playlist: Playlist) {
        viewModelScope.launch {
            when (val r = homeRepository.getPlaylistTracks(playlist.id, limit = 100)) {
                is AppResult.Success -> playerQueue.setQueue(r.data, 0)
                is AppResult.Failure -> Unit
            }
        }
    }

    /** 首页「每日推荐」入口点歌：以完整每日推荐列表为队列播放。 */
    fun playDailySongAt(index: Int) {
        if (index in uiState.dailySongs.indices) {
            playerQueue.setQueue(uiState.dailySongs, index)
        }
    }

    /** 首页「新歌首发」入口点歌：以新歌列表为队列播放。 */
    fun playNewSongAt(index: Int) {
        if (index in uiState.newSongs.indices) {
            playerQueue.setQueue(uiState.newSongs, index)
        }
    }

    /** 首页「播客」入口右下角播放：取第一个热门电台的节目列表起播。 */
    fun playFirstPodcast() {
        val radio = uiState.podcasts.firstOrNull() ?: return
        viewModelScope.launch {
            when (val r = podcastRepository.radioPrograms(radio.id)) {
                is AppResult.Success -> if (r.data.isNotEmpty()) playerQueue.setQueue(r.data, 0)
                is AppResult.Failure -> Unit
            }
        }
    }
}
