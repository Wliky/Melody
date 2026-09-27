package com.wliky.melody.artist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.wliky.melody.player.AddToPlaylistSheet
import com.wliky.melody.ui.components.CoverImage
import com.wliky.melody.ui.components.SkeletonBox
import com.wliky.melody.ui.components.SongRow
import com.wliky.melody.ui.theme.MelodySize
import com.wliky.melody.ui.theme.Spacing
import com.wliky.melody.ui.util.formatDuration

/**
 * 歌手页：热门歌曲 Top 50。
 *
 * 封面来源：热门歌曲里第一张有图的专辑（`/api/artist/top/song` 不返回歌手头像，
 * 单独的 `/api/v1/artist/{id}` 详情没必要为一页多请求一次）。
 */
@Composable
fun ArtistScreen(
    artistId: Long,
    artistName: String,
    onBack: () -> Unit,
    viewModel: ArtistViewModel = hiltViewModel(),
) {
    val state = viewModel.uiState
    LaunchedEffect(artistId) { viewModel.load(artistId) }

    // 长按目标歌曲（收藏到歌单弹层）
    var songActionFor by remember { mutableStateOf<List<Long>?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            // 二级页避让挖孔屏 / 状态栏
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
            }
            Text(
                text = "歌手",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        when {
            state.loading -> ArtistLoading()
            state.error != null -> ArtistError(message = state.error)
            state.songs.isEmpty() -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "暂无热门歌曲",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> ArtistSongList(
                artistName = artistName,
                songs = state.songs,
                onPlaySongAt = viewModel::playSongAt,
                onSongLongPress = { song -> songActionFor = listOf(song.id) },
            )
        }
    }

    songActionFor?.let { ids ->
        AddToPlaylistSheet(
            songIds = ids,
            onDismiss = { songActionFor = null },
        )
    }
}

@Composable
private fun ArtistSongList(
    artistName: String,
    songs: List<com.wliky.melody.data.model.Song>,
    onPlaySongAt: (Int) -> Unit,
    onSongLongPress: (com.wliky.melody.data.model.Song) -> Unit,
) {
    val cover = songs.firstNotNullOfOrNull { it.coverUrl }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CoverImage(
                    url = cover,
                    contentDescription = artistName,
                    modifier = Modifier
                        .size(MelodySize.coverL)
                        .clip(CircleShape),
                )
                Spacer(modifier = Modifier.width(Spacing.md))
                Column {
                    Text(
                        text = artistName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(Spacing.xs))
                    Text(
                        text = "热门 ${songs.size} 首",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(modifier = Modifier.height(Spacing.xs))
        }
        itemsIndexed(songs, key = { _, s -> "artist-${s.id}" }) { index, song ->
            SongRow(
                title = song.name,
                subtitle = song.album?.name?.takeIf { it.isNotBlank() } ?: song.subtitle,
                artworkUrl = song.coverUrl,
                onClick = { onPlaySongAt(index) },
                onLongClick = { onSongLongPress(song) },
                leading = {
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (index < 3) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.width(28.dp),
                    )
                },
                trailing = {
                    Text(
                        text = formatDuration(song.durationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
        }
        item { Spacer(modifier = Modifier.height(Spacing.xl)) }
    }
}

@Composable
private fun ArtistLoading() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.screen, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        SkeletonBox(
            modifier = Modifier
                .size(MelodySize.coverL)
                .clip(CircleShape),
        )
        repeat(8) {
            SkeletonBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(MaterialTheme.shapes.small),
            )
        }
    }
}

@Composable
private fun ArtistError(message: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.CloudOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(36.dp),
        )
        Spacer(modifier = Modifier.height(Spacing.sm))
        Text(
            text = "加载失败：$message",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
