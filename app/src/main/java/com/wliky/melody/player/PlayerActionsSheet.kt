package com.wliky.melody.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wliky.melody.data.error.AppResult
import com.wliky.melody.data.model.Playlist
import com.wliky.melody.data.repo.AuthRepository
import com.wliky.melody.data.repo.HomeRepository
import com.wliky.melody.data.repo.SongRepository
import com.wliky.melody.ui.components.CoverImage
import com.wliky.melody.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 当前播放队列：迷你条 / 播放页的「播放列表」按钮共用。
 * 点击某首直接起播，不关闭弹层以外的页面。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerQueueSheet(
    queue: PlayerQueue.QueueState,
    onPlayAt: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val accent = MaterialTheme.colorScheme.primary
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(bottom = Spacing.lg)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = Spacing.screen, end = Spacing.screen, bottom = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.QueueMusic,
                    contentDescription = null,
                    tint = accent,
                )
                Spacer(modifier = Modifier.width(Spacing.xs))
                Text(
                    text = "播放列表（${queue.queue.size}）",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            HorizontalDivider()
            LazyColumn(modifier = Modifier.fillMaxWidth().height(360.dp)) {
                itemsIndexed(queue.queue, key = { index, song -> "${song.id}-$index" }) { index, song ->
                    val active = index == queue.index
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPlayAt(index) }
                            .padding(horizontal = Spacing.screen, vertical = Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CoverImage(
                            url = song.coverUrl,
                            contentDescription = song.name,
                            modifier = Modifier.size(40.dp),
                            shape = RoundedCornerShape(8.dp),
                        )
                        Spacer(modifier = Modifier.width(Spacing.md))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = song.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (active) {
                                    accent
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = song.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (active) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = "正在播放",
                                tint = accent,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 收藏到歌单弹层：列出我的歌单，点击即添加当前歌曲。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistSheet(
    songId: Long,
    onDismiss: () -> Unit,
    viewModel: AddToPlaylistViewModel = hiltViewModel(),
) {
    val sheetState = rememberModalBottomSheetState()
    val accent = MaterialTheme.colorScheme.primary
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(bottom = Spacing.lg)) {
            Text(
                text = "收藏到歌单",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = Spacing.screen, end = Spacing.screen, bottom = Spacing.sm),
            )
            HorizontalDivider()
            when {
                viewModel.loading -> {
                    Text(
                        text = "加载中…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(Spacing.screen),
                    )
                }

                viewModel.playlists.isEmpty() -> {
                    Text(
                        text = "暂无可用歌单（需登录后使用）",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(Spacing.screen),
                    )
                }

                else -> {
                    LazyColumn(modifier = Modifier.fillMaxWidth().height(320.dp)) {
                        itemsIndexed(
                            viewModel.playlists,
                            key = { _, playlist -> "pl-${playlist.id}" },
                        ) { _, playlist ->
                            AddToPlaylistRow(
                                playlist = playlist,
                                done = viewModel.doneIds.contains(playlist.id),
                                accent = accent,
                                onClick = { viewModel.add(playlist.id, songId) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddToPlaylistRow(
    playlist: Playlist,
    done: Boolean,
    accent: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.screen, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverImage(
            url = playlist.coverUrl,
            contentDescription = playlist.name,
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)),
            shape = RoundedCornerShape(8.dp),
        )
        Spacer(modifier = Modifier.width(Spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${playlist.trackCount} 首",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (done) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = "已添加",
                tint = accent,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * 收藏到歌单：拉取「我的歌单」并执行添加。
 * 未登录 / 接口失败时列表为空，界面提示需登录，不抛错。
 */
@HiltViewModel
class AddToPlaylistViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val homeRepository: HomeRepository,
    private val songRepository: SongRepository,
) : ViewModel() {

    /** 我的歌单（未登录为空） */
    var playlists by mutableStateOf<List<Playlist>>(emptyList())
        private set

    var loading by mutableStateOf(true)
        private set

    /** 本次已成功添加的歌单 id，打勾反馈 */
    var doneIds by mutableStateOf<Set<Long>>(emptySet())
        private set

    init {
        viewModelScope.launch {
            val user = (authRepository.currentUser() as? AppResult.Success)?.data
            playlists = if (user == null) {
                emptyList()
            } else {
                when (val res = homeRepository.getUserPlaylists(user.id)) {
                    is AppResult.Failure -> emptyList()
                    is AppResult.Success -> res.data
                }
            }
            loading = false
        }
    }

    /** 添加歌曲到歌单：成功打勾，失败静默（可后续接 Toast）。 */
    fun add(playlistId: Long, songId: Long) {
        viewModelScope.launch {
            if (songRepository.addToPlaylist(playlistId, songId) is AppResult.Success) {
                doneIds = doneIds + playlistId
            }
        }
    }
}
