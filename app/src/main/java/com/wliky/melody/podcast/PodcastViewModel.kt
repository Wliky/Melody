package com.wliky.melody.podcast

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wliky.melody.data.error.AppResult
import com.wliky.melody.data.model.Podcast
import com.wliky.melody.data.repo.PodcastRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 播客页（热门电台列表）。 */
@HiltViewModel
class PodcastViewModel @Inject constructor(
    private val repository: PodcastRepository,
) : ViewModel() {

    data class PodcastUiState(
        val loading: Boolean = true,
        val podcasts: List<Podcast> = emptyList(),
        val error: String? = null,
    )

    var uiState by mutableStateOf(PodcastUiState())
        private set

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            uiState = PodcastUiState(loading = true)
            when (val res = repository.hotRadios()) {
                is AppResult.Failure -> uiState = PodcastUiState(loading = false, error = res.error.message)
                is AppResult.Success -> uiState = PodcastUiState(loading = false, podcasts = res.data)
            }
        }
    }
}
