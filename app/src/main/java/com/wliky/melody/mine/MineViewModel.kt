package com.wliky.melody.mine

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wliky.melody.data.cache.RepoCache
import com.wliky.melody.data.error.AppResult
import com.wliky.melody.data.model.Playlist
import com.wliky.melody.data.model.User
import com.wliky.melody.data.repo.AuthRepository
import com.wliky.melody.data.repo.HomeRepository
import com.wliky.melody.data.repo.SessionEvent
import com.wliky.melody.player.PlayerQueue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import javax.inject.Inject

/**
 * 「我的」页状态：账号信息 + 我的歌单。
 * 页面快照缓存（内存 + 磁盘）按 uid 校验，换号 / 退出登录自动失效；
 * 进页秒显缓存，TTL 内不请求，过期静默拉新，下拉刷新强制走网络（对齐官方）。
 */
@HiltViewModel
class MineViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val homeRepository: HomeRepository,
    private val repoCache: RepoCache,
    private val playerQueue: PlayerQueue,
) : ViewModel() {

    /** 页面快照：登录用户 + 歌单列表。 */
    @Serializable
    data class MineSnapshot(
        val user: User,
        val playlists: List<Playlist> = emptyList(),
    )

    data class MineUiState(
        /** 首次无缓存时的骨架屏 */
        val loading: Boolean = true,
        /** 下拉刷新指示器 */
        val refreshing: Boolean = false,
        val user: User? = null,
        val playlists: List<Playlist> = emptyList(),
        val error: String? = null,
    )

    private companion object {
        const val CACHE_KEY = "mine:snapshot"

        /** 缓存新鲜期：期间进页直接用缓存、不发请求。 */
        const val CACHE_TTL_MS = 10 * 60_000L
    }

    var uiState by mutableStateOf(MineUiState())
        private set

    private var loadJob: Job? = null

    init {
        load()
        // 登录 / 退出登录：强制刷新账号信息与歌单
        viewModelScope.launch {
            authRepository.sessionEvents.collect { load(force = true) }
        }
    }

    /**
     * 进页加载 / 下拉刷新：
     * 本地登录态检查（快）→ 未登录清缓存展示占位；
     * 已登录且缓存命中（同 uid）：秒显，TTL 内非强刷直接返回；
     * 否则拉网络，成功写缓存，失败静默回退旧缓存。
     */
    fun load(force: Boolean = false) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (force) uiState = uiState.copy(refreshing = true, error = null)
            val user = (authRepository.currentUser() as? AppResult.Success)?.data
            if (user == null) {
                // 未登录 / 登录态失效：清本页缓存，避免残留上一账号数据
                repoCache.remove(CACHE_KEY)
                uiState = uiState.copy(
                    loading = false,
                    refreshing = false,
                    user = null,
                    playlists = emptyList(),
                    error = null,
                )
                return@launch
            }

            val cached = repoCache.read(CACHE_KEY, MineSnapshot.serializer())
                ?.takeIf { it.value.user.id == user.id }
            if (cached != null) {
                uiState = uiState.copy(
                    loading = false,
                    user = cached.value.user,
                    playlists = cached.value.playlists,
                    error = null,
                )
                if (!force && cached.isFresh(CACHE_TTL_MS)) {
                    uiState = uiState.copy(refreshing = false)
                    return@launch
                }
            } else if (!force) {
                uiState = uiState.copy(loading = true, error = null)
            }

            val playlists = when (val r = homeRepository.getUserPlaylists(user.id)) {
                is AppResult.Failure -> emptyList()
                is AppResult.Success -> r.data
            }
            if (playlists.isNotEmpty()) {
                repoCache.write(CACHE_KEY, MineSnapshot(user, playlists), MineSnapshot.serializer())
            }
            val fallbackPlaylists = cached?.value?.playlists.orEmpty()
            uiState = uiState.copy(
                loading = false,
                refreshing = false,
                user = user,
                playlists = if (playlists.isNotEmpty()) playlists else fallbackPlaylists,
                // 网络失败但有缓存：静默保留；两者皆空才提示
                error = if (playlists.isEmpty() && fallbackPlaylists.isEmpty()) "歌单列表为空" else null,
            )
        }
    }

    /** 退出登录：停播放 + 清空队列 + 清 cookie + 清本页缓存（NonCancellable 保证清完）。 */
    fun logout() {
        playerQueue.stop()
        authRepository.logout()
        viewModelScope.launch {
            withContext(NonCancellable) {
                repoCache.remove(CACHE_KEY)
            }
        }
    }
}
