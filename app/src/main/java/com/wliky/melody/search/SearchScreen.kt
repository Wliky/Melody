package com.wliky.melody.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.wliky.melody.data.model.Playlist
import com.wliky.melody.data.model.Song
import com.wliky.melody.player.AddToPlaylistSheet
import com.wliky.melody.ui.components.PlaylistRow
import com.wliky.melody.ui.components.SongRow
import com.wliky.melody.ui.theme.MelodySize
import com.wliky.melody.ui.theme.Spacing
import com.wliky.melody.ui.util.formatCount
import com.wliky.melody.ui.util.formatDuration

/**
 * 搜索页：搜索框（自动聚焦）→ 未输入时展示热搜榜；输入中展示联想词；
 * 提交后展示结果（单曲/歌单两个 tab）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenPlaylist: (Playlist) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state = viewModel.uiState
    val focusRequester = remember { FocusRequester() }

    // 长按目标歌曲（收藏到歌单弹层）
    var songActionFor: List<Long>? by remember { mutableStateOf(null) }

    BackHandler(onBack = onBack)

    // 背景全屏延伸，内容避让状态栏挖孔与底部导航条（edge-to-edge）
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // ── 搜索栏 ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.xs, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.weight(1f),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(MelodySize.iconS),
                    )
                    Spacer(modifier = Modifier.width(Spacing.sm))
                    BasicTextField(
                        value = state.keyword,
                        onValueChange = viewModel::onKeywordChange,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = { viewModel.search() },
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                    )
                    if (state.keyword.isNotEmpty()) {
                        IconButton(
                            onClick = viewModel::clearKeyword,
                            modifier = Modifier.size(MelodySize.iconS),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "清空",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.width(Spacing.sm))
            Text(
                text = "搜索",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable { viewModel.search() }
                    .padding(Spacing.sm),
            )
        }

        // ── 内容区 ──
        // 联想态条件：正在输入且关键词已偏离上次搜索词（提交后 suggestions 已清空）
        when {
            state.searching && state.songs.isEmpty() && state.playlists.isEmpty() ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

            state.error != null -> ErrorHint(message = state.error)

            state.keyword.isNotBlank() &&
                state.keyword.trim() != state.searchedKeyword &&
                state.suggestions.isNotEmpty() ->
                SuggestionList(
                    suggestions = state.suggestions,
                    onSelect = { viewModel.search(it) },
                )

            state.searchedKeyword.isNotEmpty() -> SearchResultContent(
                state = state,
                onPlaySongAt = viewModel::playSongAt,
                onOpenPlaylist = onOpenPlaylist,
                onSongLongPress = { song -> songActionFor = listOf(song.id) },
            )

            else -> HotContent(hot = state.hot, onSelect = { viewModel.search(it) })
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    songActionFor?.let { ids ->
        AddToPlaylistSheet(
            songIds = ids,
            onDismiss = { songActionFor = null },
        )
    }
}

// ── 热搜 ──

@Composable
private fun HotContent(hot: List<String>, onSelect: (String) -> Unit) {
    if (hot.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = Spacing.screen,
            vertical = Spacing.md,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Whatshot,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(MelodySize.iconS),
                )
                Spacer(modifier = Modifier.width(Spacing.xs))
                Text(
                    text = "热门搜索",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        item {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                hot.forEach { keyword ->
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.clickable { onSelect(keyword) },
                    ) {
                        Text(
                            text = keyword,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs + 2.dp),
                        )
                    }
                }
            }
        }
    }
}

// ── 联想词 ──

@Composable
private fun SuggestionList(suggestions: List<String>, onSelect: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = Spacing.sm),
    ) {
        itemsIndexed(suggestions, key = { index, keyword -> "sug-$index-$keyword" }) { _, keyword ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(keyword) }
                    .padding(horizontal = Spacing.screen, vertical = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(Spacing.md))
                Text(
                    text = keyword,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ── 结果页 ──

@Composable
private fun SearchResultContent(
    state: SearchViewModel.SearchUiState,
    onPlaySongAt: (Int) -> Unit,
    onOpenPlaylist: (Playlist) -> Unit,
    onSongLongPress: (Song) -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            Tab(
                selected = tab == 0,
                onClick = { tab = 0 },
                text = { Text("单曲 ${state.songs.size}") },
            )
            Tab(
                selected = tab == 1,
                onClick = { tab = 1 },
                text = { Text("歌单 ${state.playlists.size}") },
            )
        }
        when (tab) {
            0 -> if (state.songs.isEmpty()) {
                EmptyHint(keyword = state.searchedKeyword)
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(state.songs, key = { _, s -> s.id }) { index, song ->
                        SongRow(
                            title = song.name,
                            subtitle = song.subtitle,
                            artworkUrl = song.coverUrl,
                            modifier = Modifier.padding(horizontal = Spacing.screen),
                            onClick = { onPlaySongAt(index) },
                            onLongClick = { onSongLongPress(song) },
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
                }
            }

            1 -> if (state.playlists.isEmpty()) {
                EmptyHint(keyword = state.searchedKeyword)
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(state.playlists, key = { _, p -> p.id }) { _, playlist ->
                        val creator = playlist.creatorName?.let { " · $it" } ?: ""
                        PlaylistRow(
                            title = playlist.name,
                            subtitle = "${playlist.trackCount}首$creator · ${formatCount(playlist.playCount)}次播放",
                            coverUrl = playlist.coverUrl,
                            modifier = Modifier.padding(horizontal = Spacing.screen),
                            onClick = { onOpenPlaylist(playlist) },
                        )
                    }
                }
            }
        }
    }
}

// ── 占位 ──

@Composable
private fun EmptyHint(keyword: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "没有找到与「$keyword」相关的内容",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ErrorHint(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Rounded.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(36.dp),
            )
            Spacer(modifier = Modifier.height(Spacing.sm))
            Text(
                text = "搜索失败：$message",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
