package com.wliky.melody.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wliky.melody.core.designsystem.component.CoverImage
import com.wliky.melody.core.designsystem.component.EmptyState
import com.wliky.melody.core.designsystem.component.ErrorState
import com.wliky.melody.core.designsystem.component.LoadingBox
import com.wliky.melody.core.designsystem.component.SectionHeader
import com.wliky.melody.core.designsystem.component.SongRow
import com.wliky.melody.core.designsystem.format.formatCount
import com.wliky.melody.core.model.Song
import com.wliky.melody.feature.common.PlaylistCard

/**
 * 搜索：联想词 → 分类结果 → 分页 → 直接播放。
 * 平板用双栏：左侧结果列表，右侧当前选中歌曲的详情卡。
 */
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    isWide: Boolean,
    onOpenPlaylist: (String) -> Unit,
    onPlay: (List<Song>, Int) -> Unit,
    modifier: Modifier = Modifier,
    nowPlayingId: String? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        SearchField(
            keyword = state.keyword,
            onKeywordChange = viewModel::onKeywordChange,
            onSubmit = { viewModel.submit() },
        )

        when {
            state.showSuggestions && state.keyword.isNotBlank() -> SuggestionPanel(
                state = state,
                onPickKeyword = { viewModel.submit(it) },
                onPickSong = { song -> viewModel.submit(song.name) },
            )

            !state.hasSearched -> SearchLanding(
                history = state.history,
                onPickKeyword = { viewModel.submit(it) },
                onClearHistory = viewModel::clearHistory,
            )

            else -> Column(modifier = Modifier.fillMaxSize()) {
                SearchTabs(
                    current = state.tab,
                    onSelect = viewModel::switchTab,
                )

                when {
                    state.loading -> LoadingBox(modifier = Modifier.fillMaxWidth())

                    state.error != null -> ErrorState(
                        error = state.error!!,
                        onRetry = { viewModel.submit(state.submittedKeyword) },
                    )

                    else -> if (isWide) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            Box(modifier = Modifier.weight(1f)) {
                                SearchResults(
                                    state = state,
                                    nowPlayingId = nowPlayingId,
                                    onPlay = onPlay,
                                    onOpenPlaylist = onOpenPlaylist,
                                    onSelectSong = viewModel::selectSong,
                                    onLoadMore = viewModel::loadMore,
                                )
                            }
                            VerticalDivider(modifier = Modifier.fillMaxHeight())
                            SelectedSongPane(
                                song = state.selectedSong,
                                onPlay = { song ->
                                    val index = state.songs.items.indexOfFirst { it.id == song.id }
                                    onPlay(state.songs.items, if (index >= 0) index else 0)
                                },
                                modifier = Modifier.width(340.dp),
                            )
                        }
                    } else {
                        SearchResults(
                            state = state,
                            nowPlayingId = nowPlayingId,
                            onPlay = onPlay,
                            onOpenPlaylist = onOpenPlaylist,
                            onSelectSong = viewModel::selectSong,
                            onLoadMore = viewModel::loadMore,
                        )
                    }
                }
            }
        }
    }
}

/** 圆角胶囊搜索框，和首页的搜索入口保持同一形状语言。 */
@Composable
private fun SearchField(
    keyword: String,
    onKeywordChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    OutlinedTextField(
        value = keyword,
        onValueChange = onKeywordChange,
        singleLine = true,
        placeholder = { Text("搜索歌曲、歌手、专辑、歌单") },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = {
            if (keyword.isNotEmpty()) {
                IconButton(onClick = { onKeywordChange("") }) {
                    Icon(Icons.Rounded.Close, contentDescription = "清空")
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        shape = CircleShape,
    )
}

/** 分类切换：胶囊分段控件，与播放器页的歌词/队列切换统一。 */
@Composable
private fun SearchTabs(current: SearchTab, onSelect: (SearchTab) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(SearchTab.entries.toList()) { tab ->
            val selected = tab == current
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                    )
                    .clickable { onSelect(tab) }
                    .padding(horizontal = 18.dp, vertical = 8.dp),
            ) {
                Text(
                    text = tab.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

@Composable
private fun SuggestionPanel(
    state: SearchViewModel.UiState,
    onPickKeyword: (String) -> Unit,
    onPickSong: (Song) -> Unit,
) {
    val suggestions = state.suggestions
    Column(modifier = Modifier.fillMaxSize()) {
        if (state.suggestionsLoading) {
            LoadingBox(modifier = Modifier.fillMaxWidth())
        }
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 120.dp)) {
            itemsIndexed(suggestions.songs, key = { index, song -> "s-$index-${song.id}" }) { _, song ->
                SongRow(song = song, onClick = { onPickSong(song) })
            }
            items(suggestions.artists, key = { "a-${it.id}" }) { artist ->
                SuggestionRow(
                    title = artist.name,
                    subtitle = "歌手",
                    seed = artist.id,
                    avatarUrl = artist.avatarUrl,
                    onClick = { onPickKeyword(artist.name) },
                )
            }
            items(suggestions.albums, key = { "al-${it.id}" }) { album ->
                SuggestionRow(
                    title = album.name,
                    subtitle = "专辑 · ${album.artistText}",
                    seed = album.id,
                    avatarUrl = album.coverUrl,
                    onClick = { onPickKeyword(album.name) },
                )
            }
            items(suggestions.playlists, key = { "p-${it.id}" }) { playlist ->
                SuggestionRow(
                    title = playlist.name,
                    subtitle = "歌单 · ${formatCount(playlist.playCount)} 次播放",
                    seed = playlist.id,
                    avatarUrl = playlist.coverUrl,
                    onClick = { onPickKeyword(playlist.name) },
                )
            }
        }
    }
}

@Composable
private fun SuggestionRow(
    title: String,
    subtitle: String,
    seed: String,
    avatarUrl: String?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverImage(
            url = avatarUrl,
            seed = seed,
            modifier = Modifier.size(44.dp),
            corner = 22.dp,
            iconSize = 20.dp,
        )
        Spacer(Modifier.width(14.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 搜索落地页：没有历史时给一句友好的引导，有历史就用标签流。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchLanding(
    history: List<String>,
    onPickKeyword: (String) -> Unit,
    onClearHistory: () -> Unit,
) {
    if (history.isEmpty()) {
        EmptyState(
            title = "搜点什么吧",
            description = "支持歌曲、歌手、专辑、歌单",
            icon = Icons.Rounded.Search,
        )
        return
    }
    Column(modifier = Modifier.fillMaxSize()) {
        SectionHeader(
            title = "搜索历史",
            action = {
                TextButton(onClick = onClearHistory) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("清空")
                }
            },
        )
        FlowRow(
            modifier = Modifier.padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            history.forEach { keyword ->
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable { onPickKeyword(keyword) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = keyword,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResults(
    state: SearchViewModel.UiState,
    nowPlayingId: String?,
    onPlay: (List<Song>, Int) -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onSelectSong: (Song) -> Unit,
    onLoadMore: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 140.dp),
    ) {
        when (state.tab) {
            SearchTab.SONG -> {
                itemsIndexed(
                    state.songs.items,
                    key = { index, song -> "song-$index-${song.id}" },
                ) { index, song ->
                    SongRow(
                        song = song,
                        index = index,
                        playing = song.id == nowPlayingId,
                        onClick = {
                            onSelectSong(song)
                            onPlay(state.songs.items, index)
                        },
                    )
                }
            }

            SearchTab.ARTIST -> items(state.artists.items, key = { it.id }) { artist ->
                SuggestionRow(
                    title = artist.name,
                    subtitle = "歌手",
                    seed = artist.id,
                    avatarUrl = artist.avatarUrl,
                    onClick = { },
                )
            }

            SearchTab.ALBUM -> items(state.albums.items, key = { it.id }) { album ->
                SuggestionRow(
                    title = album.name,
                    subtitle = "专辑 · ${album.artistText}",
                    seed = album.id,
                    avatarUrl = album.coverUrl,
                    onClick = { },
                )
            }

            SearchTab.PLAYLIST -> items(state.playlists.items, key = { it.id }) { playlist ->
                PlaylistCard(
                    playlist = playlist,
                    onClick = { onOpenPlaylist(playlist.id) },
                    width = 150.dp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        }

        if (hasMore(state)) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    if (state.loadingMore) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        TextButton(onClick = onLoadMore) { Text("加载更多") }
                    }
                }
            }
        }

        if (state.songs.items.isEmpty() && state.tab == SearchTab.SONG && !state.loading) {
            item {
                EmptyState(
                    title = "没有找到相关歌曲",
                    description = "换个关键词试试",
                    icon = Icons.Rounded.Search,
                )
            }
        }
    }
}

private fun hasMore(state: SearchViewModel.UiState): Boolean = when (state.tab) {
    SearchTab.SONG -> state.songs.hasMore
    SearchTab.ARTIST -> state.artists.hasMore
    SearchTab.ALBUM -> state.albums.hasMore
    SearchTab.PLAYLIST -> state.playlists.hasMore
}

/** 宽屏右栏：当前选中歌曲的详情，直接给一个播放按钮。 */
@Composable
private fun SelectedSongPane(
    song: Song?,
    onPlay: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (song == null) {
        EmptyState(
            title = "选中一首歌",
            description = "这里会显示歌曲信息",
            modifier = modifier.fillMaxHeight(),
        )
        return
    }
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CoverImage(
            url = song.coverUrl,
            seed = song.id,
            modifier = Modifier
                .size(190.dp)
                .shadow(18.dp, androidx.compose.foundation.shape.RoundedCornerShape(22.dp)),
            corner = 22.dp,
            iconSize = 52.dp,
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = song.name,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = song.artistText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        song.album?.name?.takeIf { it.isNotBlank() }?.let { album ->
            Spacer(Modifier.height(2.dp))
            Text(
                text = album,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(22.dp))
        Button(
            onClick = { onPlay(song) },
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
            ),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = Color.White,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "播放这首歌",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
            )
        }
    }
}
