package com.wliky.melody.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wliky.melody.core.common.AppError
import com.wliky.melody.core.common.fold
import com.wliky.melody.core.model.Comment
import com.wliky.melody.core.model.CommentSort
import com.wliky.melody.data.repository.CommentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 评论区 ViewModel（v0.3.0-preview.3+，只读）。
 *
 * 当前进入播放页第一次切到「评论」Tab 才加载（懒加载，避免每次打开播放器都先打一次接口）。
 * 切歌时清空数据，新歌曲重新从第一页开始加载。
 */
@HiltViewModel
class CommentsViewModel @Inject constructor(
    private val commentRepository: CommentRepository,
) : ViewModel() {

    data class UiState(
        val items: List<Comment> = emptyList(),
        val sort: CommentSort = CommentSort.HOT,
        val cursor: Long = 0L,
        val hasMore: Boolean = false,
        val total: Int = 0,
        val loading: Boolean = false,
        val loadingMore: Boolean = false,
        val error: String? = null,
        val songId: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun loadIfNeeded(songId: String, force: Boolean = false) {
        val current = _state.value
        if (!force && current.songId == songId && current.items.isNotEmpty()) return
        if (current.songId != songId) {
            _state.value = UiState(songId = songId)
        }
        loadFirstPage(songId)
    }

    fun switchSort(songId: String, sort: CommentSort) {
        if (_state.value.sort == sort) return
        _state.value = UiState(songId = songId, sort = sort)
        loadFirstPage(songId)
    }

    fun refresh() {
        val songId = _state.value.songId ?: return
        _state.value = _state.value.copy(songId = songId, items = emptyList(), cursor = 0L, hasMore = false, error = null)
        loadFirstPage(songId)
    }

    fun loadMore() {
        val current = _state.value
        val songId = current.songId ?: return
        if (current.loading || current.loadingMore || !current.hasMore) return
        _state.update { it.copy(loadingMore = true) }
        viewModelScope.launch {
            commentRepository.fetchComments(songId, current.sort, current.cursor).fold(
                onSuccess = { page ->
                    _state.update {
                        it.copy(
                            items = it.items + page.items,
                            cursor = page.cursor,
                            hasMore = page.hasMore,
                            total = if (page.total > 0) page.total else it.total,
                            loadingMore = false,
                            error = null,
                        )
                    }
                },
                onFailure = { error ->
                    _state.update { it.copy(loadingMore = false, error = error.toMessage()) }
                },
            )
        }
    }

    private fun loadFirstPage(songId: String) {
        val sort = _state.value.sort
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            commentRepository.fetchComments(songId, sort, cursor = 0L).fold(
                onSuccess = { page ->
                    _state.update {
                        it.copy(
                            items = page.items,
                            cursor = page.cursor,
                            hasMore = page.hasMore,
                            total = if (page.total > 0) page.total else it.items.size,
                            loading = false,
                            error = null,
                        )
                    }
                },
                onFailure = { error ->
                    _state.update { it.copy(loading = false, error = error.toMessage()) }
                },
            )
        }
    }

    private fun AppError.toMessage(): String = when (this) {
        is AppError.Unauthorized -> "请先登录后查看评论"
        is AppError.Network -> "网络异常，下拉重试"
        is AppError.NotFound -> "这首歌暂无评论"
        is AppError.Parse -> "评论数据解析失败"
        else -> message
    }
}