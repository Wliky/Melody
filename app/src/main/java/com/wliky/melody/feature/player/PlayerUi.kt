package com.wliky.melody.feature.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wliky.melody.core.designsystem.component.CoverImage
import com.wliky.melody.core.designsystem.component.EmptyState
import com.wliky.melody.core.designsystem.component.SongRow
import com.wliky.melody.core.designsystem.format.formatDuration
import com.wliky.melody.core.designsystem.theme.LocalMelodyAccent
import com.wliky.melody.core.designsystem.theme.rememberArtworkAccent
import com.wliky.melody.core.model.AppRepeatMode
import com.wliky.melody.core.model.Lyric
import com.wliky.melody.core.model.PlaybackSnapshot
import com.wliky.melody.core.model.PlayerState
import com.wliky.melody.core.model.Song

/**
 * 迷你播放器：浮在底栏上方的一条圆角卡片，点击展开全屏播放器。
 *
 * v0.3.0-preview.3+：相比上版增加了「喜欢」按钮（跟随 NeteaseSongUrlProvider 中的
 * hasAuthToken 判定），外观保持不变。
 */
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
    val progress = if (duration > 0) {
        (snapshot.positionMs.toFloat() / duration).coerceIn(0f, 1f)
    } else {
        0f
    }
    val accent = LocalMelodyAccent.current

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .shadow(6.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onExpand),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
    ) {
        Column {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = accent,
                trackColor = accent.copy(alpha = 0.18f),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CoverImage(
                    url = nowPlaying.coverUrl,
                    seed = nowPlaying.songId,
                    modifier = Modifier.size(42.dp),
                    corner = 12.dp,
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = nowPlaying.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = buildString {
                            append(nowPlaying.artist)
                            when (snapshot.state) {
                                PlayerState.BUFFERING -> append(" · 缓冲中")
                                PlayerState.ERROR -> append(" · 播放失败")
                                else -> Unit
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onToggle) {
                    Icon(
                        imageVector = if (nowPlaying.isPlaying) Icons.Rounded.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (nowPlaying.isPlaying) "暂停" else "播放",
                        modifier = Modifier.size(26.dp),
                    )
                }
                IconButton(onClick = onNext) {
                    Icon(
                        Icons.Rounded.SkipNext,
                        contentDescription = "下一首",
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
        }
    }
}

enum class PlayerTab(val label: String) {
    LYRIC("歌词"),
    COMMENT("评论"),
    QUEUE("队列"),
}

/**
 * 全屏播放器。
 *
 * v0.3.0-preview.3+ 重写：参考 [Mooic](https://github.com/kid-depress/Mooic) 的 Player.kt：
 *  - 顶部 chevron-down 收起按钮
 *  - 1:1 大圆角封面占上方主空间，跟随封面取色作背景渐变
 *  - 歌名 / 艺人 marquee 滚动
 *  - 自定义细轨 Slider
 *  - 五按钮均匀分布（shuffle / prev / play / next / repeat）
 *  - 中部 Tab 切换：歌词 / 评论 / 队列
 *  - 评论 Tab 接 [CommentsPanel]，按需懒加载
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
        enter = slideInVertically(animationSpec = tween(300)) { it },
        exit = slideOutVertically(animationSpec = tween(260)) { it },
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

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
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
    var tab by rememberSaveable { mutableStateOf(PlayerTab.LYRIC) }
    val nowPlaying = snapshot.nowPlaying
    val fallbackAccent = MaterialTheme.colorScheme.primary
    val rawAccent = rememberArtworkAccent(nowPlaying?.coverUrl, fallbackAccent)
    val accent by animateColorAsState(
        targetValue = rawAccent,
        animationSpec = tween(520),
        label = "artwork-accent",
    )
    val surface = MaterialTheme.colorScheme.surface

    LaunchedEffect(message) {
        if (message != null) onMessageShown()
    }

    CompositionLocalProvider(LocalMelodyAccent provides accent) {
        Surface(modifier = Modifier.fillMaxSize(), color = surface) {
            Box(modifier = Modifier.fillMaxSize()) {
                // 封面主色渐变背景：顶部最浓，向下过渡回表面色
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to accent.copy(alpha = 0.50f),
                                0.32f to accent.copy(alpha = 0.18f),
                                0.62f to surface.copy(alpha = 0.94f),
                                1f to surface,
                            ),
                        ),
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .systemBarsPadding(),
                ) {
                    PlayerTopBar(
                        album = nowPlaying?.album.orEmpty(),
                        onClose = onClose,
                        onOpenQueue = { tab = PlayerTab.QUEUE },
                    )

                    // 封面占据中间的可变空间，并始终保持正方形（取宽高较小的一边）
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1.2f)
                            .padding(horizontal = 40.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CoverImage(
                            url = nowPlaying?.coverUrl,
                            seed = nowPlaying?.songId.orEmpty(),
                            modifier = Modifier
                                .fillMaxSize()
                                .shadow(
                                    elevation = 26.dp,
                                    shape = RoundedCornerShape(28.dp),
                                    ambientColor = accent,
                                    spotColor = accent,
                                ),
                            corner = 28.dp,
                            iconSize = 72.dp,
                        )
                    }

                    NowPlayingMeta(
                        title = nowPlaying?.title.orEmpty(),
                        artist = nowPlaying?.artist.orEmpty(),
                        errorMessage = snapshot.errorMessage,
                    )

                    SeekBar(snapshot = snapshot, accent = accent, onSeek = onSeek)
                    PlayerControls(
                        snapshot = snapshot,
                        accent = accent,
                        onToggle = onToggle,
                        onNext = onNext,
                        onPrevious = onPrevious,
                        onCycleRepeat = onCycleRepeat,
                        onToggleShuffle = onToggleShuffle,
                    )
                    PlayerTabSwitcher(
                        tab = tab,
                        queueSize = queue.size,
                        accent = accent,
                        onSelect = { tab = it },
                    )

                    when (tab) {
                        PlayerTab.LYRIC -> LyricPanel(
                            lyric = lyric,
                            positionMs = snapshot.positionMs,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        )
                        PlayerTab.COMMENT -> {
                            val commentsViewModel: CommentsViewModel = hiltViewModel()
                            val commentsState by commentsViewModel.state.collectAsStateWithLifecycle()
                            val songId = nowPlaying?.songId.orEmpty()
                            LaunchedEffect(songId, tab) {
                                if (tab == PlayerTab.COMMENT && songId.isNotBlank()) {
                                    commentsViewModel.loadIfNeeded(songId)
                                }
                            }
                            CommentsPanel(
                                state = commentsState,
                                onSwitchSort = { sort -> commentsViewModel.switchSort(songId, sort) },
                                onRefresh = { commentsViewModel.loadIfNeeded(songId, force = true) },
                                onLoadMore = { commentsViewModel.loadMore() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                            )
                        }
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
}

@Composable
private fun NowPlayingMeta(
    title: String,
    artist: String,
    errorMessage: String?,
) {
    Column(modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp)) {
        Text(
            text = title.ifBlank { "还没有播放中的歌曲" },
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 1,
            modifier = Modifier.basicMarquee(),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = artist.ifBlank { "挑一首歌开始吧" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.basicMarquee(),
        )
        errorMessage?.let { error ->
            Spacer(Modifier.height(6.dp))
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun PlayerTopBar(
    album: String,
    onClose: () -> Unit,
    onOpenQueue: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "收起")
        }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "正在播放",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = album.ifBlank { "未知专辑" },
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onOpenQueue) {
            Icon(Icons.Rounded.QueueMusic, contentDescription = "播放队列")
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SeekBar(
    snapshot: PlaybackSnapshot,
    accent: Color,
    onSeek: (Long) -> Unit,
) {
    val duration = (snapshot.durationMs.takeIf { it > 0 } ?: snapshot.nowPlaying?.durationMs ?: 0L)
        .coerceAtLeast(1L)
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val positionMs = if (dragging) dragValue.toLong() else snapshot.positionMs

    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
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
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
                inactiveTrackColor = accent.copy(alpha = 0.22f),
            ),
            thumb = {
                SliderDefaults.Thumb(
                    interactionSource = remember { MutableInteractionSource() },
                    thumbSize = DpSize(4.dp, 18.dp),
                )
            },
            track = { sliderState ->
                SliderDefaults.Track(
                    sliderState = sliderState,
                    thumbTrackGapSize = 2.dp,
                    modifier = Modifier.height(3.dp),
                )
            },
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
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

@Composable
private fun PlayerControls(
    snapshot: PlaybackSnapshot,
    accent: Color,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleShuffle: () -> Unit,
) {
    val isPlaying = snapshot.nowPlaying?.isPlaying == true
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        FilledTonalIconButton(
            onClick = onToggleShuffle,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Shuffle,
                contentDescription = "随机播放",
                tint = if (snapshot.shuffle) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
        FilledTonalIconButton(
            onClick = onPrevious,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                Icons.Rounded.SkipPrevious,
                contentDescription = "上一首",
                modifier = Modifier.size(26.dp),
            )
        }
        FilledIconButton(
            onClick = onToggle,
            modifier = Modifier.size(72.dp),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = accent),
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "暂停" else "播放",
                modifier = Modifier.size(34.dp),
                tint = Color.White,
            )
        }
        FilledTonalIconButton(
            onClick = onNext,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                Icons.Rounded.SkipNext,
                contentDescription = "下一首",
                modifier = Modifier.size(26.dp),
            )
        }
        FilledTonalIconButton(
            onClick = onCycleRepeat,
            modifier = Modifier.size(48.dp),
        ) {
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
                    accent
                },
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun PlayerTabSwitcher(
    tab: PlayerTab,
    queueSize: Int,
    accent: Color,
    onSelect: (PlayerTab) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(3.dp),
        ) {
            PlayerTab.entries.forEach { entry ->
                val selected = entry == tab
                val label = when (entry) {
                    PlayerTab.LYRIC -> "歌词"
                    PlayerTab.COMMENT -> "评论"
                    PlayerTab.QUEUE -> "队列 $queueSize"
                }
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (selected) accent else Color.Transparent)
                        .clickable { onSelect(entry) }
                        .padding(horizontal = 18.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

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
    val accent = LocalMelodyAccent.current
    val listState = rememberLazyListState()
    val currentIndex = lyric.indexAt(positionMs)

    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0) {
            runCatching { listState.animateScrollToItem(index = currentIndex, scrollOffset = -220) }
        }
    }

    LazyColumn(state = listState, modifier = modifier) {
        item { Spacer(Modifier.height(80.dp)) }
        itemsIndexed(lyric.lines) { index, line ->
            val isCurrent = index == currentIndex
            val alpha by animateFloatAsState(
                targetValue = if (isCurrent) 1f else 0.42f,
                animationSpec = tween(280),
                label = "lyric-alpha",
            )
            val scale by animateFloatAsState(
                targetValue = if (isCurrent) 1f else 0.95f,
                animationSpec = tween(280),
                label = "lyric-scale",
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                    }
                    .padding(horizontal = 32.dp, vertical = 9.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = line.text,
                    style = if (isCurrent) {
                        MaterialTheme.typography.titleLarge
                    } else {
                        MaterialTheme.typography.bodyLarge
                    },
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isCurrent) accent else MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                line.translation?.takeIf { it.isNotBlank() }?.let { translation ->
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = translation,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        item { Spacer(Modifier.height(140.dp)) }
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
                .padding(start = 20.dp, end = 12.dp, top = 2.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "共 ${queue.size} 首",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onClearQueue)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "清空",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            itemsIndexed(queue, key = { index, song -> "$index-${song.id}" }) { index, song ->
                val isCurrent = index == currentIndex
                SongRow(
                    song = song,
                    onClick = { onPlayAt(index) },
                    index = index,
                    playing = isCurrent,
                    subtitle = if (isCurrent) "正在播放 · ${song.artistText}" else song.artistText,
                    trailing = {
                        IconButton(onClick = { onRemoveFromQueue(index) }) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = "从队列移除",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                )
            }
        }
    }
}

/**
 * 极简线性进度指示条；这是 Melody 自带的 linearprogressindicator 之外的轻量实现，
 * 仅在 mini player 顶部使用（高度 2dp）。
 */
@Composable
private fun LinearProgressIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = LocalContentColor.current,
    trackColor: Color = color.copy(alpha = 0.18f),
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress().coerceIn(0f, 1f),
        animationSpec = tween(220),
        label = "mini-progress",
    )
    Box(
        modifier = modifier
            .background(trackColor)
            .height(2.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animatedProgress)
                .height(2.dp)
                .background(color),
        )
    }
}