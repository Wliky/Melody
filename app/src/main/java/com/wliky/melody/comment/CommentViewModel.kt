package com.wliky.melody.comment

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wliky.melody.data.error.AppResult
import com.wliky.melody.data.model.Comment
import com.wliky.melody.data.repo.CommentPage
import com.wliky.melody.data.repo.CommentRepository
import com.wliky.melody.data.repo.CommentSort
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CommentViewModel @Inject constructor(
    private val repository: CommentRepository,
) : ViewModel() {

    /** 评论目标（歌曲或歌单） */
    data class Target(val threadId: String, val title: String?)

    data class CommentUiState(
        val loading: Boolean = false,
        val loadingMore: Boolean = false,
        val comments: List<Comment> = emptyList(),
        val total: Int = 0,
        val hasMore: Boolean = false,
        val sort: CommentSort = CommentSort.HOT,
        val error: String? = null,
    )

    var uiState by mutableStateOf(CommentUiState())
        private set

    /** 当前目标；切换资源时重置 */
    private var target: Target? = null
    private var pageNo = 1
    private var cursor: String? = null

    /** 加载歌曲评论。 */
    fun loadSongComments(songId: Long, songName: String?) {
        start(Target(repository.songThreadId(songId), songName))
    }

    /** 加载歌单评论。 */
    fun loadPlaylistComments(playlistId: Long, playlistName: String?) {
        start(Target(repository.playlistThreadId(playlistId), playlistName))
    }

    private fun start(target: Target) {
        if (this.target == target) return
        this.target = target
        reload()
    }

    /** 切换排序（最热/最新）并回到第一页。 */
    fun switchSort(sort: CommentSort) {
        if (uiState.sort == sort) return
        uiState = uiState.copy(sort = sort)
        reload()
    }

    private fun reload() {
        val target = this.target ?: return
        pageNo = 1
        cursor = null
        viewModelScope.launch {
            uiState = uiState.copy(loading = true, error = null)
            when (val res = repository.comments(target.threadId, sort = uiState.sort)) {
                is AppResult.Failure -> uiState = uiState.copy(loading = false, error = res.error.message)
                is AppResult.Success -> uiState = uiState.resultState(res.data)
            }
        }
    }

    /** 加载更多（滚动到底部触发）。 */
    fun loadMore() {
        val target = this.target ?: return
        if (uiState.loadingMore || !uiState.hasMore) return
        viewModelScope.launch {
            uiState = uiState.copy(loadingMore = true)
            val next = pageNo + 1
            when (
                val res = repository.comments(
                    target.threadId,
                    pageNo = next,
                    sort = uiState.sort,
                    previousCursor = cursor,
                )
            ) {
                is AppResult.Failure -> uiState = uiState.copy(loadingMore = false)
                is AppResult.Success -> {
                    pageNo = next
                    cursor = res.data.cursor
                    uiState = uiState.copy(
                        loadingMore = false,
                        comments = uiState.comments + res.data.comments,
                        hasMore = res.data.hasMore,
                    )
                }
            }
        }
    }

    private fun CommentUiState.resultState(page: CommentPage): CommentUiState = copy(
        loading = false,
        comments = page.comments,
        total = page.totalCount,
        hasMore = page.hasMore,
        error = null,
    )
}
