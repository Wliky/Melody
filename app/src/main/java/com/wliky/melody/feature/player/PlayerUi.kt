package com.wliky.melody.feature.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.wliky.melody.core.designsystem.component.CoverImage
import com.wliky.melody.core.designsystem.component.EmptyState
import com.wliky.melody.core.designsystem.component.SongRow
import com.wliky.melody.core.designsystem.component.coverBrush
import com.wliky.melody.core.designsystem.format.formatDuration
import com.wliky.melody.core.designsystem.theme.LocalMelodyAccent
import com.wliky.melody.core.designsystem.theme.rememberArtworkAccent
import com.wliky.melody.core.model.AppRepeatMode
import com.wliky.melody.core.model.Lyric
import com.wliky.melody.core.model.NowPlaying
import com.wliky.melody.core.model.PlaybackSnapshot
import com.wliky.melody.core.model.PlayerState
import com.wliky.melody.core.model.Song

/**
 * 迷你播放器：浮在底栏上方的一条圆角卡片，点击展开全屏播放器。
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
 * v0.4.0 重写：
 *  - 竖屏：居中**圆形旋转封面**（黑胶质感，播放时缓慢旋转、暂停停止），点击封面弹出歌词二级页
 *  - 横屏：左侧封面 + 歌曲信息，右侧歌词（双栏）
 *  - 用 BackHandler 拦截系统返回手势，先收起播放器而不是退出 App
 *  - 底部 Tab：歌词 / 评论 / 队列
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
    // 关键修复：全屏播放器打开时拦截系统返回手势，先收起播放器，而不是退出 App。
    BackHandler(enabled = visible) { onClose() }

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
    // 封面点击弹出的歌词二级页
    var showLyricOverlay by rememberSaveable { mutableStateOf(false) }
    val nowPlaying = snapshot.nowPlaying
    val fallbackAccent = MaterialTheme.colorScheme.primary
    val rawAccent = rememberArtworkAccent(nowPlaying?.coverUrl, fallbackAccent)
    val accent by animateColorAsState(
        targetValue = rawAccent,
        animationSpec = tween(520),
        label = "artwork-accent",
    )
    val surface = MaterialTheme.colorScheme.surface
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    LaunchedEffect(message) {
        if (message != null) onMessageShown()
    }

    CompositionLocalProvider(LocalMelodyAccent provides accent) {
        Surface(modifier = Modifier.fillMaxSize(), color = surface) {
            Box(modifier = Modifier.fillMaxSize()) {
                // 封面主色渐变背景
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

                if (isLandscape) {
                    LandscapePlayer(
                        snapshot = snapshot,
                        lyric = lyric,
                        queue = queue,
                        tab = tab,
                        accent = accent,
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
                        onSelectTab = { tab = it },
                        onOpenLyric = { showLyricOverlay = true },
                    )
                } else {
                    PortraitPlayer(
                        snapshot = snapshot,
                        lyric = lyric,
                        queue = queue,
                        tab = tab,
                        accent = accent,
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
                        onSelectTab = { tab = it },
                        onOpenLyric = { showLyricOverlay = true },
                    )
                }

                // 歌词二级页（点击封面弹出）
                if (showLyricOverlay) {
                    LyricOverlay(
                        snapshot = snapshot,
                        lyric = lyric,
                        accent = accent,
                        onClose = { showLyricOverlay = false },
                    )
                }
            }
        }
    }
}

/**
 * 竖屏播放器：居中圆形旋转封面 + 歌名 + 进度 + 控制 + Tab。
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun PortraitPlayer(
    snapshot: PlaybackSnapshot,
    lyric: Lyric?,
    queue: List<Song>,
    tab: PlayerTab,
    accent: Color,
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
    onSelectTab: (PlayerTab) -> Unit,
    onOpenLyric: () -> Unit,
) {
    val nowPlaying = snapshot.nowPlaying
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding(),
    ) {
        PlayerTopBar(
            album = nowPlaying?.album.orEmpty(),
            onClose = onClose,
            onOpenQueue = { onSelectTab(PlayerTab.QUEUE) },
        )

        // 居中圆形旋转封面
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.25f)
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            RotatingArtwork(
                url = nowPlaying?.coverUrl,
                seed = nowPlaying?.songId.orEmpty(),
                isPlaying = nowPlaying?.isPlaying == true,
                accent = accent,
                onClick = onOpenLyric,
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
            onSelect = onSelectTab,
        )

        when (tab) {
            PlayerTab.LYRIC -> LyricPanel(
                lyric = lyric,
                positionMs = snapshot.positionMs,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
            PlayerTab.COMMENT -> CommentTab(
                nowPlaying = nowPlaying,
                tab = tab,
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

/**
 * 横屏播放器：左侧封面 + 歌曲信息，右侧歌词。
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun LandscapePlayer(
    snapshot: PlaybackSnapshot,
    lyric: Lyric?,
    queue: List<Song>,
    tab: PlayerTab,
    accent: Color,
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
    onSelectTab: (PlayerTab) -> Unit,
    onOpenLyric: () -> Unit,
) {
    val nowPlaying = snapshot.nowPlaying
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding(),
    ) {
        PlayerTopBar(
            album = nowPlaying?.album.orEmpty(),
            onClose = onClose,
            onOpenQueue = { onSelectTab(PlayerTab.QUEUE) },
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            // 左侧：封面 + 歌曲信息 + 控制
            Column(
                modifier = Modifier
                    .weight(0.9f)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                RotatingArtwork(
                    url = nowPlaying?.coverUrl,
                    seed = nowPlaying?.songId.orEmpty(),
                    isPlaying = nowPlaying?.isPlaying == true,
                    accent = accent,
                    onClick = onOpenLyric,
                    maxSize = 300.dp,
                )
                Spacer(Modifier.height(18.dp))
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
            }

            Spacer(Modifier.width(20.dp))

            // 右侧：歌词 / 评论 / 队列
            Column(
                modifier = Modifier
                    .weight(1.1f)
                    .fillMaxHeight(),
            ) {
                PlayerTabSwitcher(
                    tab = tab,
                    queueSize = queue.size,
                    accent = accent,
                    onSelect = onSelectTab,
                )
                when (tab) {
                    PlayerTab.LYRIC -> LyricPanel(
                        lyric = lyric,
                        positionMs = snapshot.positionMs,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                    PlayerTab.COMMENT -> CommentTab(
                        nowPlaying = nowPlaying,
                        tab = tab,
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

/**
 * 圆形旋转封面：黑胶唱片质感，播放时缓慢旋转、暂停停止。
 */
@Composable
private fun RotatingArtwork(
    url: String?,
    seed: String,
    isPlaying: Boolean,
    accent: Color,
    onClick: () -> Unit,
    maxSize: androidx.compose.ui.unit.Dp = 300.dp,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "artwork-rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "rotation",
    )

    // 唱片底色圆盘（比封面稍大，模拟黑胶外圈）
    Box(
        modifier = Modifier
            .size(maxSize)
            .graphicsLayer {
                rotationZ = if (isPlaying) rotation else 0f
            }
            .clip(CircleShape)
            .background(Color(0xFF1A1A1A))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // 黑胶纹路：中心小圆 + 环形
        Box(
            modifier = Modifier
                .fillMaxSize(0.94f)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.15f)),
        )
        // 封面本体（圆形）
        Box(
            modifier = Modifier
                .fillMaxSize(0.66f)
                .shadow(8.dp, CircleShape)
                .clip(CircleShape)
                .background(coverBrush(seed)),
        ) {
            if (!url.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                        .data(url)
                        .crossfade(220)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.QueueMusic,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(40.dp),
                )
            }
        }
        // 中心唱针圆点
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                .background(Color(0xFFE0E0E0).copy(alpha = 0.9f)),
        )
    }
}

/**
 * 歌词二级页（点击封面弹出），全屏覆盖。
 */
@Composable
private fun LyricOverlay(
    snapshot: PlaybackSnapshot,
    lyric: Lyric?,
    accent: Color,
    onClose: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)) {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "收起歌词")
                }
                Text(
                    text = "歌词",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.width(48.dp))
            }
            LyricPanel(
                lyric = lyric,
                positionMs = snapshot.positionMs,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }
    }
}

@Composable
private fun CommentTab(
    nowPlaying: NowPlaying?,
    tab: PlayerTab,
    modifier: Modifier = Modifier,
) {
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
        modifier = modifier,
    )
}

@Composable
private fun NowPlayingMeta(
    title: String,
    artist: String,
    errorMessage: String?,
) {
    Column(
        modifier = Modifier.padding(horizontal = 28.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title.ifBlank { "还没有播放中的歌曲" },
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().basicMarquee(),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = artist.ifBlank { "挑一首歌开始吧" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().basicMarquee(),
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
 * 极简线性进度指示条。
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
