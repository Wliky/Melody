package com.wliky.melody.toplist

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wliky.melody.data.error.AppResult
import com.wliky.melody.data.model.Toplist
import com.wliky.melody.data.repo.DiscoverRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 排行榜页：榜单列表，点榜单走现有歌单详情。 */
@HiltViewModel
class ToplistViewModel @Inject constructor(
    private val repository: DiscoverRepository,
) : ViewModel() {

    data class ToplistUiState(
        val loading: Boolean = true,
        val toplists: List<Toplist> = emptyList(),
        val error: String? = null,
    )

    var uiState by mutableStateOf(ToplistUiState())
        private set

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            uiState = ToplistUiState(loading = true)
            when (val res = repository.toplists()) {
                is AppResult.Failure -> uiState = ToplistUiState(loading = false, error = res.error.message)
                is AppResult.Success -> uiState = ToplistUiState(loading = false, toplists = res.data)
            }
        }
    }
}
