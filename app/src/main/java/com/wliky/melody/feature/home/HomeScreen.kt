package com.wliky.melody.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wliky.melody.core.designsystem.component.EmptyState
import com.wliky.melody.core.designsystem.component.ErrorState
import com.wliky.melody.core.designsystem.component.SectionHeader
import com.wliky.melody.core.designsystem.component.SongRow
import com.wliky.melody.core.model.HomeFeed
import com.wliky.melody.core.model.Playlist
import com.wliky.melody.core.model.Song
import com.wliky.melody.feature.common.PlaylistCard
import com.wliky.melody.feature.common.RankingCard

/**
 * 首页（文档 §4 / §6）：顶部搜索入口 → 每日推荐 → 最近/推荐歌单 → 榜单 → 新歌。
 * 支持下拉刷新；首屏避免信息堆叠。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    isWide: Boolean,
    onOpenSearch: () -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onOpenLogin: () -> Unit,
    onPlay: (List<Song>, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loggedIn by viewModel.loggedIn.collectAsStateWithLifecycle()

    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = viewModel::refresh,
        modifier = modifier.fillMaxSize(),
    ) {
        when {
            state.loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
            }

            state.error != null && state.isEmpty -> ErrorState(
                error = state.error!!,
                modifier = Modifier.fillMaxSize(),
                onRetry = { viewModel.load(forceRefresh = true) },
            )

            else -> HomeContent(
                feed = state.feed,
                isWide = isWide,
                loggedIn = loggedIn,
                onOpenSearch = onOpenSearch,
                onOpenPlaylist = onOpenPlaylist,
                onOpenLogin = onOpenLogin,
                onPlay = onPlay,
                onRefresh = viewModel::refresh,
            )
        }
    }
}

@Composable
private fun HomeContent(
    feed: HomeFeed?,
    isWide: Boolean,
    loggedIn: Boolean,
    onOpenSearch: () -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onOpenLogin: () -> Unit,
    onPlay: (List<Song>, Int) -> Unit,
    onRefresh: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 120.dp),
    ) {
        item { SearchEntry(onOpenSearch = onOpenSearch, onRefresh = onRefresh) }

        if (!loggedIn) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "登录后可获取每日推荐与我的歌单",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onOpenLogin) {
                        Icon(Icons.Rounded.Login, contentDescription = "去登录")
                    }
                }
            }
        }

        val daily = feed?.personalizedSongs.orEmpty()
        if (daily.isNotEmpty()) {
            item { SectionHeader(title = "每日推荐", subtitle = "根据你的口味生成") }
            itemsIndexed(daily, key = { index, song -> "daily-$index-${song.id}" }) { index, song ->
                SongRow(song = song, onClick = { onPlay(daily, index) })
            }
        }

        val recommended = feed?.recommendedPlaylists.orEmpty()
        if (recommended.isNotEmpty()) {
            item { SectionHeader(title = "推荐歌单") }
            item { PlaylistSection(playlists = recommended, isWide = isWide, onOpenPlaylist = onOpenPlaylist) }
        }

        val rankings = feed?.rankings.orEmpty()
        if (rankings.isNotEmpty()) {
            item { SectionHeader(title = "排行榜", subtitle = "看看大家最近在听什么") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp)) {
                    items(rankings, key = { it.id }) { ranking ->
                        RankingCard(
                            ranking = ranking,
                            onClick = { onOpenPlaylist(ranking.id) },
                            modifier = Modifier.padding(end = 12.dp),
                        )
                    }
                }
            }
        }

        val newSongs = feed?.newSongs.orEmpty()
        if (newSongs.isNotEmpty()) {
            item { SectionHeader(title = "新歌速递") }
            itemsIndexed(newSongs, key = { index, song -> "new-$index-${song.id}" }) { index, song ->
                SongRow(song = song, onClick = { onPlay(newSongs, index) })
            }
        }

        if (feed == null) {
            item {
                EmptyState(
                    title = "还没有内容",
                    description = "下拉试试刷新",
                    icon = Icons.Rounded.MusicNote,
                )
            }
        }
    }
}

@Composable
private fun SearchEntry(onOpenSearch: () -> Unit, onRefresh: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onOpenSearch)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "搜索歌曲、歌手、专辑、歌单",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onRefresh) {
            Icon(Icons.Rounded.Refresh, contentDescription = "刷新")
        }
    }
}

/** 歌单区块：手机横滑，平板直接铺成多列，充分利用宽度。 */
@Composable
private fun PlaylistSection(
    playlists: List<Playlist>,
    isWide: Boolean,
    onOpenPlaylist: (String) -> Unit,
) {
    if (!isWide) {
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp)) {
            items(playlists, key = { it.id }) { playlist ->
                PlaylistCard(
                    playlist = playlist,
                    onClick = { onOpenPlaylist(playlist.id) },
                    width = 150.dp,
                    modifier = Modifier.padding(end = 12.dp),
                )
            }
        }
        return
    }
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        playlists.chunked(COLUMNS_WIDE).forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                row.forEach { playlist ->
                    PlaylistCard(
                        playlist = playlist,
                        onClick = { onOpenPlaylist(playlist.id) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(COLUMNS_WIDE - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

private const val COLUMNS_WIDE = 4
