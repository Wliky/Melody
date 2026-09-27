package com.wliky.melody.cloud

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wliky.melody.data.error.AppResult
import com.wliky.melody.data.repo.CloudRepository
import com.wliky.melody.player.PlayerQueue
import com.wliky.melody.songlist.SongListUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 云盘音乐页：进入即加载，点击某首从该首播整个云盘列表。 */
@HiltViewModel
class CloudViewModel @Inject constructor(
    private val repository: CloudRepository,
    val playerQueue: PlayerQueue,
) : ViewModel() {

    var uiState by mutableStateOf(SongListUiState())
        private set

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            uiState = SongListUiState(loading = true)
            when (val res = repository.cloudSongs()) {
                is AppResult.Failure -> uiState = SongListUiState(loading = false, error = res.error.message)
                is AppResult.Success -> uiState = SongListUiState(loading = false, songs = res.data)
            }
        }
    }

    fun playSongAt(index: Int) {
        playerQueue.setQueue(songs = uiState.songs, startIndex = index)
    }
}
