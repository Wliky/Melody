package com.wliky.melody.feature.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wliky.melody.core.designsystem.component.CoverImage
import com.wliky.melody.core.designsystem.component.EmptyState
import com.wliky.melody.core.designsystem.component.SongRow
import com.wliky.melody.core.designsystem.format.formatDuration
import com.wliky.melody.core.designsystem.format.padStart2
import com.wliky.melody.core.model.AppRepeatMode
import com.wliky.melody.core.model.Lyric
import com.wliky.melody.core.model.PlaybackSnapshot
import com.wliky.melody.core.model.PlayerState
import com.wliky.melody.core.model.Song

/** 迷你播放器：贴在底栏上方，点击展开全屏播放器。 */
@Composable
fun MiniPlayer(
    snapshot: PlaybackSnapshot,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val nowPlaying = snapshot.nowPlaying ?: return
    val duration = snapshot.durationMs.takeIf { it > 0 } ?: nowPlaying.durationMs
    val progress = if (duration > 0) (snapshot.positionMs.toFloat() / duration).coerceIn(0f, 1f) else 0f

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onExpand),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp,
    ) {
        Column {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CoverImage(
                    url = nowPlaying.coverUrl,
                    seed = nowPlaying.songId,
                    modifier = Modifier.size(44.dp),
                    corner = 10.dp,
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = nowPlaying.title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = buildString {
                            append(nowPlaying.artist)
                            if (snapshot.state == PlayerState.BUFFERING) append(" · 缓冲中…")
                            if (snapshot.state == PlayerState.ERROR) append(" · 播放失败")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onToggle) {
                    Icon(
                        imageVector = if (nowPlaying.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (nowPlaying.isPlaying) "暂停" else "播放",
                    )
                }
                IconButton(onClick = onNext) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = "下一首")
                }
            }
        }
    }
}

enum class PlayerTab { LYRIC, QUEUE }

/**
 * 全屏播放器（文档 §6）。
 * 从迷你播放器展开，200–400ms 的轻量位移动画。
 */
@Composable
fun AnimatedFullPlayer(
    visible: Boolean,
    snapshot: PlaybackSnapshot,
    lyric: Lyric?,
    queue: List<Song>,
    message: String?,
    onClose: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleShuffle: () -> Unit,
    onPlayAt: (Int) -> Unit,
    onRemoveFromQueue: (Int) -> Unit,
    onClearQueue: () -> Unit,
    onMessageShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(animationSpec = tween(280)) { it },
        exit = slideOutVertically(animationSpec = tween(240)) { it },
        modifier = modifier,
    ) {
        FullPlayerScreen(
            snapshot = snapshot,
            lyric = lyric,
            queue = queue,
            message = message,
            onClose = onClose,
            onToggle = onToggle,
            onNext = onNext,
            onPrevious = onPrevious,
            onSeek = onSeek,
            onCycleRepeat = onCycleRepeat,
            onToggleShuffle = onToggleShuffle,
            onPlayAt = onPlayAt,
            onRemoveFromQueue = onRemoveFromQueue,
            onClearQueue = onClearQueue,
            onMessageShown = onMessageShown,
        )
    }
}

@Composable
private fun FullPlayerScreen(
    snapshot: PlaybackSnapshot,
    lyric: Lyric?,
    queue: List<Song>,
    message: String?,
    onClose: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleShuffle: () -> Unit,
    onPlayAt: (Int) -> Unit,
    onRemoveFromQueue: (Int) -> Unit,
    onClearQueue: () -> Unit,
    onMessageShown: () -> Unit,
) {
    var tab by remember { mutableStateOf(PlayerTab.LYRIC) }
    val nowPlaying = snapshot.nowPlaying

    LaunchedEffect(message) {
        if (message != null) onMessageShown()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 封面衍生渐变背景（动态强调色）
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
                                MaterialTheme.colorScheme.background,
                            ),
                        ),
                    ),
            )

            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "收起")
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "正在播放",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = nowPlaying?.album.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = { tab = PlayerTab.QUEUE }) {
                        Icon(Icons.Rounded.QueueMusic, contentDescription = "播放队列")
                    }
                }

                CoverImage(
                    url = nowPlaying?.coverUrl,
                    seed = nowPlaying?.songId.orEmpty(),
                    modifier = Modifier
                        .padding(horizontal = 40.dp, vertical = 8.dp)
                        .fillMaxWidth()
                        .height(300.dp),
                    corner = 24.dp,
                    iconSize = 64.dp,
                )

                Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                    Text(
                        text = nowPlaying?.title.orEmpty().ifBlank { "还没有播放中的歌曲" },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = nowPlaying?.artist.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    snapshot.errorMessage?.let { error ->
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                SeekBar(snapshot = snapshot, onSeek = onSeek)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    IconButton(onClick = onToggleShuffle) {
                        Icon(
                            imageVector = Icons.Rounded.Shuffle,
                            contentDescription = "随机播放",
                            tint = if (snapshot.shuffle) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    IconButton(onClick = onPrevious) {
                        Icon(
                            Icons.Rounded.SkipPrevious,
                            contentDescription = "上一首",
                            modifier = Modifier.size(36.dp),
                        )
                    }
                    FilledIconButton(
                        onClick = onToggle,
                        modifier = Modifier.size(64.dp),
                    ) {
                        Icon(
                            imageVector = if (nowPlaying?.isPlaying == true) {
                                Icons.Rounded.Pause
                            } else {
                                Icons.Rounded.PlayArrow
                            },
                            contentDescription = if (nowPlaying?.isPlaying == true) "暂停" else "播放",
                            modifier = Modifier.size(32.dp),
                        )
                    }
                    IconButton(onClick = onNext) {
                        Icon(
                            Icons.Rounded.SkipNext,
                            contentDescription = "下一首",
                            modifier = Modifier.size(36.dp),
                        )
                    }
                    IconButton(onClick = onCycleRepeat) {
                        Icon(
                            imageVector = if (snapshot.repeatMode == AppRepeatMode.ONE) {
                                Icons.Rounded.RepeatOne
                            } else {
                                Icons.Rounded.Repeat
                            },
                            contentDescription = "循环模式",
                            tint = if (snapshot.repeatMode == AppRepeatMode.OFF) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                        )
                    }
                }

                TabRow(selectedTabIndex = tab.ordinal) {
                    Tab(
                        selected = tab == PlayerTab.LYRIC,
                        onClick = { tab = PlayerTab.LYRIC },
                        text = { Text("歌词") },
                    )
                    Tab(
                        selected = tab == PlayerTab.QUEUE,
                        onClick = { tab = PlayerTab.QUEUE },
                        text = { Text("队列 ${queue.size}") },
                    )
                }

                when (tab) {
                    PlayerTab.LYRIC -> LyricPanel(
                        lyric = lyric,
                        positionMs = snapshot.positionMs,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )

                    PlayerTab.QUEUE -> QueuePanel(
                        queue = queue,
                        currentIndex = snapshot.queueIndex,
                        onPlayAt = onPlayAt,
                        onRemoveFromQueue = onRemoveFromQueue,
                        onClearQueue = onClearQueue,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SeekBar(
    snapshot: PlaybackSnapshot,
    onSeek: (Long) -> Unit,
) {
    val duration = (snapshot.durationMs.takeIf { it > 0 } ?: snapshot.nowPlaying?.durationMs ?: 0L)
        .coerceAtLeast(1L)
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val positionMs = if (dragging) dragValue.toLong() else snapshot.positionMs

    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
        Slider(
            value = (positionMs.toFloat() / duration).coerceIn(0f, 1f),
            onValueChange = { value ->
                dragging = true
                dragValue = value * duration
            },
            onValueChangeFinished = {
                onSeek(dragValue.toLong())
                dragging = false
            },
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = formatDuration(positionMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = formatDuration(duration),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 歌词面板：滚动与高亮同步；没有歌词时给出明确空态。 */
@Composable
fun LyricPanel(
    lyric: Lyric?,
    positionMs: Long,
    modifier: Modifier = Modifier,
) {
    if (lyric == null || lyric.isEmpty) {
        EmptyState(
            title = "暂无歌词",
            description = if (lyric == null) "正在获取歌词…" else "这首歌没有提供歌词",
            modifier = modifier,
        )
        return
    }
    val listState = rememberLazyListState()
    val currentIndex = lyric.indexAt(positionMs)

    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0) {
            runCatching { listState.animateScrollToItem(index = currentIndex, scrollOffset = -200) }
        }
    }

    LazyColumn(state = listState, modifier = modifier) {
        item { Spacer(Modifier.height(120.dp)) }
        itemsIndexed(lyric.lines) { index, line ->
            val isCurrent = index == currentIndex
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 8.dp),
            ) {
                Text(
                    text = line.text,
                    style = if (isCurrent) {
                        MaterialTheme.typography.titleMedium
                    } else {
                        MaterialTheme.typography.bodyLarge
                    },
                    color = if (isCurrent) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                line.translation?.takeIf { it.isNotBlank() }?.let { translation ->
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = translation,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        item { Spacer(Modifier.height(160.dp)) }
    }
}

@Composable
private fun QueuePanel(
    queue: List<Song>,
    currentIndex: Int,
    onPlayAt: (Int) -> Unit,
    onRemoveFromQueue: (Int) -> Unit,
    onClearQueue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (queue.isEmpty()) {
        EmptyState(
            title = "播放队列是空的",
            description = "去首页挑一首歌吧",
            icon = Icons.Rounded.QueueMusic,
            modifier = modifier,
        )
        return
    }
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "共 ${queue.size} 首",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "清空队列",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable(onClick = onClearQueue)
                    .padding(8.dp),
            )
        }
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            itemsIndexed(queue, key = { index, song -> "$index-${song.id}" }) { index, song ->
                SongRow(
                    song = song,
                    onClick = { onPlayAt(index) },
                    subtitle = if (index == currentIndex) "正在播放 · ${song.artistText}" else song.artistText,
                    leading = {
                        Text(
                            text = padStart2(index + 1),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (index == currentIndex) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    },
                    trailing = {
                        IconButton(onClick = { onRemoveFromQueue(index) }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "从队列移除")
                        }
                    },
                )
            }
        }
    }
}
