package com.wliky.melody.songlist

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.wliky.melody.data.model.Song
import com.wliky.melody.player.AddToPlaylistSheet
import com.wliky.melody.ui.components.ErrorState
import com.wliky.melody.ui.components.SkeletonBox
import com.wliky.melody.ui.components.SongRow
import com.wliky.melody.ui.theme.MelodySize
import com.wliky.melody.ui.theme.Spacing
import com.wliky.melody.ui.util.formatDuration

/** 通用歌曲列表页的 UI 状态（每日推荐 / 云盘共用）。 */
data class SongListUiState(
    val loading: Boolean = true,
    val songs: List<Song> = emptyList(),
    val error: String? = null,
)

/**
 * 通用歌曲列表页：顶栏返回 + 「播放全部」头 + 序号列表。
 * 供每日推荐 / 云盘音乐复用；数据与加载行为由调用方的 ViewModel 提供。
 * 长按歌曲弹出「收藏到歌单」。
 */
@Composable
fun SongListScreen(
    title: String,
    state: SongListUiState,
    currentSongId: Long?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onPlaySongAt: (Int) -> Unit,
) {
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        when {
            state.loading -> SongListLoading()

            state.error != null -> ErrorState(
                text = state.error,
                onRetry = onRetry,
                modifier = Modifier.fillMaxSize(),
            )

            state.songs.isEmpty() -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "暂无歌曲",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> SongListBody(
                songs = state.songs,
                currentSongId = currentSongId,
                onPlaySongAt = onPlaySongAt,
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
private fun SongListBody(
    songs: List<Song>,
    currentSongId: Long?,
    onPlaySongAt: (Int) -> Unit,
    onSongLongPress: (Song) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        item(key = "play-all") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .clickable { onPlaySongAt(0) }
                    .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(MelodySize.iconPlay),
                )
                Spacer(modifier = Modifier.width(Spacing.md))
                Text(
                    text = "播放全部",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.width(Spacing.sm))
                Text(
                    text = "(${songs.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        itemsIndexed(songs, key = { _, s -> "song-${s.id}" }) { index, song ->
            val playing = song.id == currentSongId
            SongRow(
                title = song.name,
                subtitle = song.subtitle.ifBlank { "未知歌手" },
                artworkUrl = song.coverUrl,
                highlight = playing,
                onClick = { onPlaySongAt(index) },
                onLongClick = { onSongLongPress(song) },
                leading = {
                    Text(
                        text = if (playing) "♪" else "${index + 1}",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (playing || index < 3) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.width(28.dp),
                        maxLines = 1,
                    )
                },
                trailing = if (song.durationMs > 0) {
                    {
                        Text(
                            text = formatDuration(song.durationMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    null
                },
            )
        }

        item(key = "footer") { Spacer(modifier = Modifier.height(Spacing.xl)) }
    }
}

@Composable
private fun SongListLoading() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.screen, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        SkeletonBox(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(MaterialTheme.shapes.medium),
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
