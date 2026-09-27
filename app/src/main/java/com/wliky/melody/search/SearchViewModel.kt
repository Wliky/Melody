package com.wliky.melody.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wliky.melody.data.error.AppResult
import com.wliky.melody.data.model.Playlist
import com.wliky.melody.data.model.Song
import com.wliky.melody.data.repo.SearchRepository
import com.wliky.melody.player.PlayerQueue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 搜索页状态：
 * - 进入即拉热搜榜；输入防抖 300ms 请求联想词
 * - 提交搜索后展示结果（单曲/歌单）；单曲点击从所在位置播整页结果
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchRepository: SearchRepository,
    val playerQueue: PlayerQueue,
) : ViewModel() {

    data class SearchUiState(
        val keyword: String = "",
        val hot: List<String> = emptyList(),
        val suggestions: List<String> = emptyList(),
        /** 已提交搜索的关键词（非空表示结果页态） */
        val searchedKeyword: String = "",
        val songs: List<Song> = emptyList(),
        val playlists: List<Playlist> = emptyList(),
        val searching: Boolean = false,
        val error: String? = null,
    )

    var uiState by mutableStateOf(SearchUiState())
        private set

    private var suggestJob: Job? = null
    private var searchJob: Job? = null

    init {
        refreshHot()
    }

    private fun refreshHot() {
        viewModelScope.launch {
            when (val r = searchRepository.hotSearch()) {
                is AppResult.Failure -> Unit // 热搜拉不到不阻塞搜索流程
                is AppResult.Success -> uiState = uiState.copy(hot = r.data)
            }
        }
    }

    fun onKeywordChange(value: String) {
        uiState = uiState.copy(keyword = value)
        suggestJob?.cancel()
        if (value.isBlank()) {
            uiState = uiState.copy(suggestions = emptyList())
            return
        }
        suggestJob = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            when (val r = searchRepository.suggest(value.trim())) {
                is AppResult.Failure -> Unit
                is AppResult.Success -> if (uiState.keyword == value) {
                    uiState = uiState.copy(suggestions = r.data)
                }
            }
        }
    }

    fun clearKeyword() {
        suggestJob?.cancel()
        uiState = uiState.copy(
            keyword = "",
            suggestions = emptyList(),
            searchedKeyword = "",
            songs = emptyList(),
            playlists = emptyList(),
            error = null,
        )
    }

    /** 提交搜索（点联想词/热搜词时传入对应词） */
    fun search(keyword: String = uiState.keyword.trim()) {
        if (keyword.isBlank()) return
        searchJob?.cancel()
        uiState = uiState.copy(
            keyword = keyword,
            searchedKeyword = keyword,
            suggestions = emptyList(),
            searching = true,
            error = null,
        )
        searchJob = viewModelScope.launch {
            when (val r = searchRepository.search(keyword)) {
                is AppResult.Failure ->
                    uiState = uiState.copy(searching = false, error = r.error.message)
                is AppResult.Success ->
                    uiState = uiState.copy(
                        searching = false,
                        songs = r.data.songs,
                        playlists = r.data.playlists,
                    )
            }
        }
    }

    /** 从搜索结果 [index] 首开始播整页单曲 */
    fun playSongAt(index: Int) {
        val songs = uiState.songs
        if (index in songs.indices) playerQueue.setQueue(songs, index)
    }

    private companion object {
        private const val DEBOUNCE_MS = 300L
    }
}
