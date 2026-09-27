package com.wliky.melody.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wliky.melody.data.error.AppError
import com.wliky.melody.data.error.AppResult
import com.wliky.melody.data.model.Playlist
import com.wliky.melody.data.repo.AuthRepository
import com.wliky.melody.data.repo.HomeRepository
import com.wliky.melody.data.repo.SongRepository
import com.wliky.melody.ui.components.CoverImage
import com.wliky.melody.ui.components.PlaylistRow
import com.wliky.melody.ui.components.SongRow
import com.wliky.melody.ui.theme.MelodySize
import com.wliky.melody.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 队列弹层列表高度：最多占屏幕 60%，且不超过 420dp。
 * 原先固定 360dp —— 小屏（尤其横屏）会顶满、遮挡内容，大屏又只占中间一小条。
 */
@Composable
private fun queueSheetHeight(): Dp {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    return minOf(screenHeight * 0.6f, 420.dp)
}

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
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    // 队列高度自适应屏幕（最多 60% 屏高、不超过 420dp），
                    // 小屏不再被固定 360dp 挤爆，大屏也不会只占一小条
                    .height(queueSheetHeight()),
            ) {
                itemsIndexed(queue.queue, key = { index, song -> "${song.id}-$index" }) { index, song ->
                    val active = index == queue.index
                    SongRow(
                        title = song.name,
                        subtitle = song.subtitle,
                        artworkUrl = song.coverUrl,
                        modifier = Modifier.padding(horizontal = Spacing.screen),
                        onClick = { onPlayAt(index) },
                        highlight = active,
                        trailing = if (active) {
                            {
                                Icon(
                                    imageVector = Icons.Outlined.Check,
                                    contentDescription = "正在播放",
                                    tint = accent,
                                    modifier = Modifier.size(MelodySize.iconS),
                                )
                            }
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }
}

/** 收藏到歌单弹层：列出我的歌单，点击即添加；再次点击已勾选歌单 = 移除。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistSheet(
    songIds: List<Long>,
    onDismiss: () -> Unit,
    viewModel: AddToPlaylistViewModel = hiltViewModel(),
) {
    val sheetState = rememberModalBottomSheetState()
    val accent = MaterialTheme.colorScheme.primary
    // 每次打开弹层清掉上次的失败提示，避免误导
    LaunchedEffect(Unit) { viewModel.clearError() }
    // 目标歌曲集变化时重置打勾（ViewModel 未按歌曲集分键，避免残留勾选）
    LaunchedEffect(songIds) { viewModel.resetFor(songIds) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(bottom = Spacing.lg)) {
            Text(
                text = if (songIds.size > 1) "收藏 ${songIds.size} 首到歌单" else "收藏到歌单",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = Spacing.screen, end = Spacing.screen, bottom = Spacing.sm),
            )
            HorizontalDivider()
            // 失败原因直接回显，便于定位「收藏没反应」类问题
            viewModel.error?.let { msg ->
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = Spacing.screen, end = Spacing.screen, top = Spacing.xs),
                )
            }
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
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp),
                    ) {
                        itemsIndexed(
                            viewModel.playlists,
                            key = { _, playlist -> "pl-${playlist.id}" },
                        ) { _, playlist ->
                            val done = viewModel.doneIds.contains(playlist.id)
                            val pending = viewModel.pendingIds.contains(playlist.id)
                            PlaylistRow(
                                title = playlist.name,
                                subtitle = "${playlist.trackCount} 首",
                                coverUrl = playlist.coverUrl,
                                onClick = { viewModel.toggle(playlist.id, songIds) },
                                trailing = {
                                    when {
                                        pending -> CircularProgressIndicator(
                                            modifier = Modifier.size(MelodySize.iconS),
                                            strokeWidth = 2.dp,
                                        )

                                        done -> Icon(
                                            imageVector = Icons.Outlined.Check,
                                            contentDescription = "已收藏",
                                            tint = accent,
                                            modifier = Modifier.size(MelodySize.iconS),
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 收藏到歌单：拉取「我的歌单」并执行添加 / 移除（再次点击已勾选歌单 = 从该歌单移除）。
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

    /** 本次已成功收藏的歌单 id，打勾反馈；再次点击 = 取消（从歌单移除） */
    var doneIds by mutableStateOf<Set<Long>>(emptySet())
        private set

    /** 正在请求的歌单 id，避免连点重复提交 */
    var pendingIds by mutableStateOf<Set<Long>>(emptySet())
        private set

    /** 操作失败提示（UI 消费后调 [clearError]）。 */
    var error by mutableStateOf<String?>(null)
        private set

    /**
     * 切换目标歌曲集时重置打勾状态。
     * ViewModel 未按歌曲集分键复用，若不重置，给 A 歌收藏过的歌单
     * 在给 B 歌打开弹层时仍显示已勾选（实际未收藏），造成误判。
     */
    fun resetFor(songIds: List<Long>) {
        doneIds = emptySet()
        pendingIds = emptySet()
        error = null
    }

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

    /**
     * 切换收藏状态：未勾选 → 添加歌曲到歌单；
     * 已勾选 → 从歌单移除（服务端 op=del），成功后取消勾选。
     *
     * 服务端对「歌曲已在歌单里」返回业务码 502「歌单内歌曲重复」——
     * 这属于已收藏状态而非失败，按已勾选处理（否则用户点多少次都无法取消）。
     */
    fun toggle(playlistId: Long, songIds: List<Long>) {
        if (songIds.isEmpty()) return
        if (playlistId in pendingIds) return
        val wasDone = playlistId in doneIds
        pendingIds = pendingIds + playlistId
        viewModelScope.launch {
            val result = if (wasDone) {
                songRepository.removeFromPlaylist(playlistId, songIds)
            } else {
                songRepository.addToPlaylist(playlistId, songIds)
            }
            pendingIds = pendingIds - playlistId
            when (result) {
                is AppResult.Success -> {
                    error = null
                    doneIds = if (wasDone) doneIds - playlistId else doneIds + playlistId
                }

                is AppResult.Failure -> {
                    val api = result.error as? AppError.Api
                    if (!wasDone && api?.code == DUPLICATE_CODE) {
                        // 歌曲本就在该歌单里：视为已勾选，允许再次点击移除
                        error = null
                        doneIds = doneIds + playlistId
                    } else {
                        error = result.error.message
                    }
                }
            }
        }
    }

    fun clearError() {
        error = null
    }

    /** 服务端「歌单内歌曲重复」业务码：添加时命中代表已收藏。 */
    private companion object {
        const val DUPLICATE_CODE = 502
    }
}
