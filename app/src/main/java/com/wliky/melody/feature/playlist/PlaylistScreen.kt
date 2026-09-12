package com.wliky.melody.feature.playlist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wliky.melody.core.designsystem.component.EmptyState
import com.wliky.melody.core.designsystem.component.ErrorState
import com.wliky.melody.core.designsystem.component.LoadingBox
import com.wliky.melody.core.designsystem.component.SongRow
import com.wliky.melody.core.designsystem.format.formatCount
import com.wliky.melody.core.model.Song
import com.wliky.melody.feature.common.PlaylistHeader

/** 歌单 / 榜单详情。 */
@Composable
fun PlaylistScreen(
    viewModel: PlaylistViewModel,
    onPlay: (List<Song>, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    when {
        state.loading -> Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            LoadingBox()
        }

        state.error != null -> ErrorState(
            error = state.error!!,
            modifier = modifier.fillMaxSize(),
            onRetry = viewModel::load,
        )

        else -> LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp),
        ) {
            item {
                PlaylistHeader(
                    name = state.playlist?.name.orEmpty(),
                    coverUrl = state.playlist?.coverUrl,
                    seed = state.playlist?.id.orEmpty(),
                    metaText = buildString {
                        state.playlist?.creator?.takeIf { it.isNotBlank() }?.let { append(it).append(" · ") }
                        append("${state.songs.size} 首歌")
                        state.playlist?.playCount?.takeIf { it > 0 }?.let { append(" · ").append(formatCount(it)).append(" 次播放") }
                    },
                    description = state.playlist?.description,
                    onClickPlayAll = { if (state.songs.isNotEmpty()) onPlay(state.songs, 0) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (state.songs.isEmpty()) {
                item { EmptyState(title = "这个歌单还没有可播放的歌曲") }
            } else {
                itemsIndexed(state.songs, key = { index, song -> "$index-${song.id}" }) { index, song ->
                    SongRow(song = song, onClick = { onPlay(state.songs, index) })
                }
            }
        }
    }
}
