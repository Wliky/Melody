package com.wliky.melody.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wliky.melody.core.designsystem.component.CoverImage
import com.wliky.melody.core.designsystem.component.EmptyState
import com.wliky.melody.core.designsystem.component.ErrorState
import com.wliky.melody.core.designsystem.component.PlayingIndicator
import com.wliky.melody.core.designsystem.component.SectionHeader
import com.wliky.melody.core.designsystem.component.SongRow
import com.wliky.melody.core.model.HomeFeed
import com.wliky.melody.core.model.Playlist
import com.wliky.melody.core.model.Song
import com.wliky.melody.feature.common.PlaylistCard
import com.wliky.melody.feature.common.RankingCard
import java.util.Calendar

/**
 * 首页。
 *
 * 布局思路参考成熟的开源音乐客户端：一个带问候语的大标题区 → 吸睛的搜索入口 →
 * 每日推荐（横向大卡）→ 歌单网格 → 榜单横滑 → 新歌列表。
 * 手机两列歌单，平板四列，用同一个实现按宽度分支。
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
    nowPlayingId: String? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loggedIn by viewModel.loggedIn.collectAsStateWithLifecycle()

    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = viewModel::refresh,
        modifier = modifier.fillMaxSize(),
    ) {
        when {
            state.loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(30.dp), strokeWidth = 3.dp)
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
                nowPlayingId = nowPlayingId,
                onOpenSearch = onOpenSearch,
                onOpenPlaylist = onOpenPlaylist,
                onOpenLogin = onOpenLogin,
                onPlay = onPlay,
            )
        }
    }
}

@Composable
private fun HomeContent(
    feed: HomeFeed?,
    isWide: Boolean,
    loggedIn: Boolean,
    nowPlayingId: String?,
    onOpenSearch: () -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onOpenLogin: () -> Unit,
    onPlay: (List<Song>, Int) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 140.dp),
    ) {
        item {
            HomeHeader(
                greeting = rememberGreeting(),
                loggedIn = loggedIn,
                onOpenSearch = onOpenSearch,
                onOpenLogin = onOpenLogin,
            )
        }

        val daily = feed?.personalizedSongs.orEmpty()
        if (daily.isNotEmpty()) {
            item { SectionHeader(title = "每日推荐", subtitle = "根据你的口味生成") }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    itemsIndexed(daily, key = { index, song -> "daily-$index-${song.id}" }) { index, song ->
                        DailySongCard(
                            song = song,
                            playing = song.id == nowPlayingId,
                            onClick = { onPlay(daily, index) },
                        )
                    }
                }
            }
        }

        val recommended = feed?.recommendedPlaylists.orEmpty()
        if (recommended.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "推荐歌单",
                    subtitle = "精选自云音乐曲库",
                )
            }
            item {
                PlaylistGrid(
                    playlists = recommended,
                    columns = if (isWide) COLUMNS_WIDE else COLUMNS_NARROW,
                    onOpenPlaylist = onOpenPlaylist,
                )
            }
        }

        val rankings = feed?.rankings.orEmpty()
        if (rankings.isNotEmpty()) {
            item { SectionHeader(title = "排行榜", subtitle = "看看大家最近在听什么") }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(rankings, key = { it.id }) { ranking ->
                        RankingCard(
                            ranking = ranking,
                            onClick = { onOpenPlaylist(ranking.id) },
                        )
                    }
                }
            }
        }

        val newSongs = feed?.newSongs.orEmpty()
        if (newSongs.isNotEmpty()) {
            item { SectionHeader(title = "新歌速递") }
            itemsIndexed(newSongs, key = { index, song -> "new-$index-${song.id}" }) { index, song ->
                SongRow(
                    song = song,
                    onClick = { onPlay(newSongs, index) },
                    index = index,
                    playing = song.id == nowPlayingId,
                )
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

/**
 * 页头：问候语 + 品牌名 + 搜索入口。
 * 未登录时右侧显示登录按钮，而不是在页面中间插一条横幅提示。
 */
@Composable
private fun HomeHeader(
    greeting: String,
    loggedIn: Boolean,
    onOpenSearch: () -> Unit,
    onOpenLogin: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = greeting,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Melody",
                    style = MaterialTheme.typography.displaySmall,
                )
            }
            if (!loggedIn) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable(onClick = onOpenLogin),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Login,
                        contentDescription = "登录",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(onClick = onOpenSearch)
                .padding(horizontal = 18.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(21.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "搜索歌曲、歌手、专辑、歌单",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 每日推荐里的大卡片：封面 + 歌名 + 歌手。 */
@Composable
private fun DailySongCard(
    song: Song,
    playing: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(132.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(2.dp),
    ) {
        Box {
            CoverImage(
                url = song.coverUrl,
                seed = song.id,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                corner = 16.dp,
            )
            if (playing) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    PlayingIndicator(
                        playing = true,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.height(12.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = song.name,
            style = MaterialTheme.typography.bodyMedium,
            color = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = song.artistText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 歌单网格。用 chunked 手工分行而不是 LazyVerticalGrid —— 外层已经是 LazyColumn，
 * 同方向嵌套滚动会直接崩。
 */
@Composable
private fun PlaylistGrid(
    playlists: List<Playlist>,
    columns: Int,
    onOpenPlaylist: (String) -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        playlists.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                row.forEach { playlist ->
                    PlaylistCard(
                        playlist = playlist,
                        onClick = { onOpenPlaylist(playlist.id) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(columns - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

private const val COLUMNS_NARROW = 2
private const val COLUMNS_WIDE = 4

/** 按当前时段给一句问候，比固定的"首页"更有温度。 */
@Composable
private fun rememberGreeting(): String = remember {
    when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 5..11 -> "早上好"
        in 12..17 -> "下午好"
        in 18..22 -> "晚上好"
        else -> "夜深了"
    }
}
