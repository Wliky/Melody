package com.wliky.melody.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wliky.melody.core.common.AppError
import com.wliky.melody.core.common.fold
import com.wliky.melody.core.common.onFailure
import com.wliky.melody.core.common.onSuccess
import com.wliky.melody.core.model.Album
import com.wliky.melody.core.model.Artist
import com.wliky.melody.core.model.Page
import com.wliky.melody.core.model.Playlist
import com.wliky.melody.core.model.SearchSuggestions
import com.wliky.melody.core.model.Song
import com.wliky.melody.data.repository.SearchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SearchTab(val label: String) {
    SONG("单曲"),
    ARTIST("歌手"),
    ALBUM("专辑"),
    PLAYLIST("歌单"),
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchRepository: SearchRepository,
) : ViewModel() {

    data class UiState(
        val keyword: String = "",
        val submittedKeyword: String = "",
        val history: List<String> = emptyList(),
        val suggestions: SearchSuggestions = SearchSuggestions(),
        val suggestionsLoading: Boolean = false,
        val showSuggestions: Boolean = false,
        val tab: SearchTab = SearchTab.SONG,
        val songs: Page<Song> = Page.empty(),
        val artists: Page<Artist> = Page.empty(),
        val albums: Page<Album> = Page.empty(),
        val playlists: Page<Playlist> = Page.empty(),
        val loading: Boolean = false,
        val loadingMore: Boolean = false,
        val error: AppError? = null,
        val selectedSong: Song? = null,
    ) {
        val hasSearched: Boolean get() = submittedKeyword.isNotBlank()
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var suggestionJob: Job? = null
    private var page = 0

    init {
        viewModelScope.launch {
            searchRepository.history.collect { history ->
                _state.update { it.copy(history = history) }
            }
        }
    }

    fun onKeywordChange(keyword: String) {
        _state.update {
            it.copy(
                keyword = keyword,
                showSuggestions = keyword.isNotBlank(),
                suggestions = if (keyword.isBlank()) SearchSuggestions() else it.suggestions,
            )
        }
        suggestionJob?.cancel()
        if (keyword.isBlank()) return
        // 输入防抖，避免每敲一个字就打一次接口（文档 §6「联想词」）
        suggestionJob = viewModelScope.launch {
            delay(SUGGEST_DEBOUNCE_MS)
            _state.update { it.copy(suggestionsLoading = true) }
            searchRepository.suggestions(keyword)
                .onSuccess { suggestions ->
                    _state.update { it.copy(suggestions = suggestions, suggestionsLoading = false) }
                }
                .onFailure {
                    _state.update { it.copy(suggestionsLoading = false) }
                }
        }
    }

    fun dismissSuggestions() {
        _state.update { it.copy(showSuggestions = false) }
    }

    fun submit(keyword: String = _state.value.keyword) {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) return
        suggestionJob?.cancel()
        page = 0
        _state.update {
            it.copy(
                keyword = trimmed,
                submittedKeyword = trimmed,
                showSuggestions = false,
                loading = true,
                error = null,
                songs = Page.empty(),
                artists = Page.empty(),
                albums = Page.empty(),
                playlists = Page.empty(),
            )
        }
        viewModelScope.launch {
            searchRepository.recordHistory(trimmed)
            loadPage(0)
        }
    }

    fun switchTab(tab: SearchTab) {
        _state.update { it.copy(tab = tab) }
        val state = _state.value
        if (state.submittedKeyword.isBlank()) return
        val emptyForTab = when (tab) {
            SearchTab.SONG -> state.songs.items.isEmpty()
            SearchTab.ARTIST -> state.artists.items.isEmpty()
            SearchTab.ALBUM -> state.albums.items.isEmpty()
            SearchTab.PLAYLIST -> state.playlists.items.isEmpty()
        }
        if (emptyForTab) {
            page = 0
            _state.update { it.copy(loading = true, error = null) }
            viewModelScope.launch { loadPage(0) }
        }
    }

    fun loadMore() {
        val state = _state.value
        if (state.loading || state.loadingMore) return
        val hasMore = when (state.tab) {
            SearchTab.SONG -> state.songs.hasMore
            SearchTab.ARTIST -> state.artists.hasMore
            SearchTab.ALBUM -> state.albums.hasMore
            SearchTab.PLAYLIST -> state.playlists.hasMore
        }
        if (!hasMore) return
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            loadPage(page + 1)
            _state.update { it.copy(loadingMore = false) }
        }
    }

    private suspend fun loadPage(target: Int) {
        val keyword = _state.value.submittedKeyword
        if (keyword.isBlank()) return
        val onFailure: (AppError) -> Unit = { error ->
            _state.update { it.copy(loading = false, error = error) }
        }
        when (_state.value.tab) {
            SearchTab.SONG -> searchRepository.searchSongs(keyword, target, PAGE_SIZE).fold(
                onSuccess = { loaded ->
                    page = target
                    _state.update { current ->
                        current.copy(
                            loading = false,
                            error = null,
                            songs = if (target == 0) loaded else current.songs.append(loaded),
                        )
                    }
                },
                onFailure = onFailure,
            )

            SearchTab.ARTIST -> searchRepository.searchArtists(keyword, target, PAGE_SIZE).fold(
                onSuccess = { loaded ->
                    page = target
                    _state.update { current ->
                        current.copy(
                            loading = false,
                            error = null,
                            artists = if (target == 0) loaded else current.artists.append(loaded),
                        )
                    }
                },
                onFailure = onFailure,
            )

            SearchTab.ALBUM -> searchRepository.searchAlbums(keyword, target, PAGE_SIZE).fold(
                onSuccess = { loaded ->
                    page = target
                    _state.update { current ->
                        current.copy(
                            loading = false,
                            error = null,
                            albums = if (target == 0) loaded else current.albums.append(loaded),
                        )
                    }
                },
                onFailure = onFailure,
            )

            SearchTab.PLAYLIST -> searchRepository.searchPlaylists(keyword, target, PAGE_SIZE).fold(
                onSuccess = { loaded ->
                    page = target
                    _state.update { current ->
                        current.copy(
                            loading = false,
                            error = null,
                            playlists = if (target == 0) loaded else current.playlists.append(loaded),
                        )
                    }
                },
                onFailure = onFailure,
            )
        }
    }

    fun selectSong(song: Song?) {
        _state.update { it.copy(selectedSong = song) }
    }

    fun clearHistory() {
        viewModelScope.launch { searchRepository.clearHistory() }
    }

    private companion object {
        const val PAGE_SIZE = 30
        const val SUGGEST_DEBOUNCE_MS = 320L
    }
}
