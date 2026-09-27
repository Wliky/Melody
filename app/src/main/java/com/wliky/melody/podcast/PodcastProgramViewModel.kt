package com.wliky.melody.podcast

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wliky.melody.data.error.AppResult
import com.wliky.melody.data.repo.PodcastRepository
import com.wliky.melody.player.PlayerQueue
import com.wliky.melody.songlist.SongListUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 电台节目页：把节目当作普通歌曲列表。
 * 每个节目的 mainSong 已被 [PodcastRepository] 解析成 [com.wliky.melody.data.model.Song]。
 */
@HiltViewModel
class PodcastProgramViewModel @Inject constructor(
    private val repository: PodcastRepository,
    val playerQueue: PlayerQueue,
) : ViewModel() {

    var uiState by mutableStateOf(SongListUiState())
        private set

    private var radioId: Long = 0L

    fun start(radioId: Long) {
        if (this.radioId == radioId && uiState.songs.isNotEmpty()) return
        this.radioId = radioId
        load()
    }

    fun load() {
        if (radioId == 0L) return
        viewModelScope.launch {
            uiState = SongListUiState(loading = true)
            when (val res = repository.radioPrograms(radioId)) {
                is AppResult.Failure -> uiState = SongListUiState(loading = false, error = res.error.message)
                is AppResult.Success -> uiState = SongListUiState(loading = false, songs = res.data)
            }
        }
    }

    fun playSongAt(index: Int) {
        playerQueue.setQueue(songs = uiState.songs, startIndex = index)
    }
}
