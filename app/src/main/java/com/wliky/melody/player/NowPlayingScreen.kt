package com.wliky.melody.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowRightAlt
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.RepeatOne
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.wliky.melody.playlist.formatDuration
import com.wliky.melody.ui.components.CoverImage
import com.wliky.melody.ui.components.NOW_PLAYING_COVER_KEY
import com.wliky.melody.ui.components.coverUrlWithSize
import com.wliky.melody.ui.components.sharedCover
import com.wliky.melody.ui.theme.Spacing
import com.wliky.melody.ui.theme.lighten
import com.wliky.melody.ui.theme.rememberCoverColors
import kotlin.math.abs

/**
 * 全屏播放页（AMLL 风格）：中部三页横滑 —— 左页评论 / 中页封面 / 右页歌词，
 * 底部歌名歌手 + 胶囊进度条 + 控制 + 页点常驻。
 * 支持整页下拉关闭（拖动跟随 + 阈值判定），关闭（把手 / 系统返回）回到原页面，播放不中断。
 */
@Composable
fun NowPlayingScreen(
    onClose: () -> Unit,
    onOpenArtist: (artistId: Long, artistName: String) -> Unit,
    onOpenQueue: () -> Unit,
    onOpenCollect: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val queue = viewModel.playerQueue.state
    val song = queue.current
    val liked = viewModel.likedSongIds.contains(song?.id)
    val lyricFontSize by viewModel.lyricFontSize.collectAsStateWithLifecycle()

    BackHandler(onBack = onClose)

    if (song == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF141414)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "当前没有播放中的歌曲",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.7f),
            )
        }
        return
    }

    // 三页：0 评论 / 1 封面 / 2 歌词
    val pagerState = rememberPagerState(initialPage = 1) { 3 }

    // 全局主题色：进入下方取色覆盖的深色主题前先捕获
    val globalPrimary = MaterialTheme.colorScheme.primary

    // 封面取色（莫奈局部配色）：强调色 + 背景渐变遮罩；切歌缓慢过渡而非瞬跳。
    val coverColors by rememberCoverColors(url = song.coverUrl)
    val accentTarget = coverColors?.accent ?: globalPrimary
    val accent by animateColorAsState(
        targetValue = accentTarget,
        animationSpec = tween(durationMillis = 800),
        label = "npAccent",
    )
    val scrimTop by animateColorAsState(
        targetValue = coverColors?.scrimTop ?: Color.Black.copy(alpha = 0.45f),
        animationSpec = tween(durationMillis = 800),
        label = "npScrimTop",
    )
    val scrimBottom by animateColorAsState(
        targetValue = coverColors?.scrimBottom ?: Color.Black.copy(alpha = 0.80f),
        animationSpec = tween(durationMillis = 800),
        label = "npScrimBottom",
    )

    // ── 下拉关闭手势状态 ──
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val closeThresholdPx = with(density) { 120.dp.toPx() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationY = dragOffsetPx.coerceAtLeast(0f)
                alpha = 1f - (dragOffsetPx.coerceAtLeast(0f) / (closeThresholdPx * 3f)).coerceIn(0f, 0.55f)
            }
            .draggable(
                state = rememberDraggableState { delta ->
                    // 只响应向下拖动
                    dragOffsetPx = (dragOffsetPx + delta).coerceIn(0f, closeThresholdPx * 2.5f)
                },
                orientation = Orientation.Vertical,
                onDragStopped = { velocity ->
                    if (dragOffsetPx > closeThresholdPx || velocity > 2200f) {
                        onClose()
                    } else {
                        dragOffsetPx = 0f
                    }
                },
            ),
    ) {
        // 深色流体背景：模糊封面 + 暗色遮罩
        // 仅请求 200px 小图：背景反正要 56dp 模糊，原图是纯浪费（流量 + 解码 + 模糊开销全降）
        AsyncImage(
            model = remember(song.coverUrl) { song.coverUrl?.let { coverUrlWithSize(it, 200) } },
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .blur(56.dp)
                .scale(1f + (dragOffsetPx / (closeThresholdPx * 6f)).coerceIn(0f, 1f) * 0.25f),
        )
        // 取色遮罩：封面主色压暗渐变（顶部较透露出模糊封面，底部较深保证白字对比度）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(scrimTop, scrimBottom))),
        )
        // 莫奈氛围层：整页随强调色轻微浸染，切歌时与 accent 一起缓慢过渡
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(accent.copy(alpha = 0.10f)),
        )

        // 播放页固定深色主题：文本适配暗背景；强调色取自封面。
        // 背景（上方渐变 Box）保持全屏延伸，内容避让状态栏挖孔与底部导航条
        // ColorScheme 随 accent remember：取色动画期间不再每帧重建整套配色（原先每帧 ~50 个 Color 全量重算）
        val playerColorScheme = remember(accent) { darkColorScheme(primary = accent) }
        MaterialTheme(colorScheme = playerColorScheme) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                // 顶栏：居中把手（点击关闭）
                PlayerTopBar(
                    onClose = onClose,
                    progress = (dragOffsetPx / closeThresholdPx).coerceIn(0f, 1f),
                )

                // 中部横滑三页
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) { page ->
                    when (page) {
                        0 -> PlayerCommentsPage(
                            songId = song.id,
                            songName = song.name,
                        )

                        1 -> Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 56.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            // 挂共享元素：从迷你播放条封面飞越放大至此，退出时飞回
                            CoverImage(
                                url = song.coverUrl,
                                contentDescription = song.name,
                                requestSizePx = 1000,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .sharedCover(NOW_PLAYING_COVER_KEY)
                                    .clip(RoundedCornerShape(24.dp)),
                            )
                        }

                        2 -> AmllLyricsPage(
                            lines = viewModel.lyricLines,
                            loading = viewModel.lyricLoading,
                            positionMs = queue.positionMs,
                            fontSizeSp = lyricFontSize,
                            onSeek = { viewModel.playerQueue.seekTo(it) },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                // 信息行（进度条上方）：左为歌曲/歌手信息，右为红心与收藏歌单
                val pagerPosition = pagerState.currentPage + pagerState.currentPageOffsetFraction
                val titleAlpha = (1f - abs(pagerPosition - 1f)).coerceIn(0f, 1f)
                val artist = song.artists.firstOrNull()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.screen),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 左：歌曲 + 歌手（仅封面页展示，滑向评论/歌词页渐隐）
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .graphicsLayer { alpha = titleAlpha },
                    ) {
                        Text(
                            text = song.name,
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = song.subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFD8D0C8),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .then(
                                    if (artist != null && artist.id > 0) {
                                        Modifier.clickable { onOpenArtist(artist.id, artist.name) }
                                    } else {
                                        Modifier
                                    },
                                )
                                .padding(top = 2.dp),
                        )
                    }
                    // 右：红心 + 收藏歌单（与左侧歌名歌手同步显隐，仅封面页可见）
                    Row(modifier = Modifier.graphicsLayer { alpha = titleAlpha }) {
                        IconButton(onClick = { viewModel.toggleLike(song.id) }) {
                            Icon(
                                imageVector = if (liked) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = if (liked) "取消喜欢" else "喜欢",
                                tint = if (liked) accent else Color.White,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                        IconButton(onClick = onOpenCollect) {
                            Icon(
                                imageVector = Icons.Outlined.PlaylistAdd,
                                contentDescription = "收藏到歌单",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.sm))

                // 进度条（取色强调）
                ProgressSection(
                    positionMs = queue.positionMs,
                    durationMs = queue.durationMs,
                    onSeek = { viewModel.playerQueue.seekTo(it) },
                    contentColor = Color.White,
                    accent = accent,
                )

                // 控制区（随机/循环合并为一个模式键，循环位改为播放列表）
                ControlsSection(
                    isPlaying = queue.isPlaying,
                    resolving = queue.resolving,
                    shuffle = queue.shuffle,
                    repeatMode = queue.repeatMode,
                    onCycleMode = viewModel.playerQueue::cycleMode,
                    onPrevious = viewModel.playerQueue::playPrevious,
                    onToggle = viewModel.playerQueue::togglePlayPause,
                    onNext = viewModel.playerQueue::playNext,
                    onOpenQueue = onOpenQueue,
                    contentColor = Color.White,
                    accent = accent,
                )

                // 页点指示（选中态用取色强调）
                PageDots(page = pagerState.currentPage, accent = accent)

                Spacer(modifier = Modifier.height(Spacing.md))
            }
        }
    }
}

/** 顶栏：居中把手（点击关闭，下拉时变宽反馈）。 */
@Composable
private fun PlayerTopBar(
    onClose: () -> Unit,
    progress: Float,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.sm),
    ) {
        // 居中把手：点击关闭；随下拉进度变宽变透明
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 10.dp)
                .width(36.dp + (progress * 20.dp.value).dp)
                .height(4.dp)
                .alpha(0.65f - progress * 0.25f)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClose,
                ),
        )
    }
}

/** 三页页点指示（当前页用取色强调高亮），置于底部控制区上方。 */
@Composable
private fun PageDots(page: Int, accent: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { i ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(if (page == i) 8.dp else 6.dp)
                    .clip(CircleShape)
                    .background(
                        if (page == i) accent else Color.White.copy(alpha = 0.35f),
                    ),
            )
        }
    }
}

/**
 * 进度条：取色强调胶囊轨道，拖动时整条变粗（6dp → 10dp）+ thumb 浮出，形成聚焦感；
 * 拖动期间用本地值显示，松手才真正 seek，避免拖动中反复请求；起拖轻微震动反馈。
 */
@Composable
private fun ProgressSection(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    contentColor: Color,
    accent: Color,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    var trackWidthPx by remember { mutableFloatStateOf(1f) }
    val haptics = LocalHapticFeedback.current

    val sliderValue = when {
        dragging -> dragValue
        durationMs > 0 -> positionMs.toFloat() / durationMs
        else -> 0f
    }
    val fraction = sliderValue.coerceIn(0f, 1f)
    val thumbScale by animateFloatAsState(
        targetValue = if (dragging) 1f else 0f,
        animationSpec = tween(150),
        label = "thumbScale",
    )
    // 拖动时整条进度条变粗
    val barHeight by animateDpAsState(
        targetValue = if (dragging) 10.dp else 6.dp,
        animationSpec = tween(160),
        label = "barHeight",
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screen)
                .height(36.dp)
                .onSizeChanged { trackWidthPx = it.width.toFloat().coerceAtLeast(1f) }
                // 点按跳转
                .pointerInput(durationMs, trackWidthPx) {
                    if (durationMs <= 0) return@pointerInput
                    detectTapGestures { offset ->
                        val f = (offset.x / size.width).coerceIn(0f, 1f)
                        onSeek((f * durationMs).toLong())
                    }
                }
                // 横向拖动微调
                .pointerInput(durationMs) {
                    if (durationMs <= 0) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            dragging = true
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            dragValue = (offset.x / size.width).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            if (dragging) {
                                onSeek((dragValue * durationMs).toLong())
                                dragging = false
                            }
                        },
                        onDragCancel = { dragging = false },
                    ) { change, _ ->
                        change.consume()
                        dragValue = (change.position.x / size.width).coerceIn(0f, 1f)
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            // 轨道（未播放，低透明度）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(barHeight)
                    .clip(RoundedCornerShape(50))
                    .background(contentColor.copy(alpha = 0.28f)),
            )
            // 已播放（取色强调）
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(barHeight)
                    .clip(RoundedCornerShape(50))
                    .background(accent),
            )
            // thumb：拖动时浮出（沿进度定位）
            if (thumbScale > 0.01f) {
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            translationX = trackWidthPx * fraction - 7.dp.toPx()
                            scaleX = thumbScale
                            scaleY = thumbScale
                            alpha = thumbScale
                        }
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(accent),
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screen),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatDuration(if (dragging) (dragValue * durationMs).toLong() else positionMs),
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = 0.75f),
            )
            Text(
                text = formatDuration(durationMs),
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = 0.75f),
            )
        }
    }
}

/** 控制区：播放模式（随机/循环合并，点击切换）/ 上一首 / 播放暂停 / 下一首 / 播放列表。
 * 解析中禁用防连点；图标统一线性圆角（Outlined），颜色跟随取色强调。 */
@Composable
private fun ControlsSection(
    isPlaying: Boolean,
    resolving: Boolean,
    shuffle: Boolean,
    repeatMode: RepeatMode,
    onCycleMode: () -> Unit,
    onPrevious: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onOpenQueue: () -> Unit,
    contentColor: Color,
    accent: Color,
) {
    // 图标颜色按取色亮度自适应，保证渐变底上对比度
    val onAccent = if (accent.luminance() > 0.5f) Color(0xFF17171E) else Color.White
    val playGradient = Brush.linearGradient(listOf(accent, accent.lighten(0.28f)))
    var playPressed by remember { mutableStateOf(false) }
    val playScale by animateFloatAsState(
        targetValue = if (playPressed) 0.92f else 1f,
        animationSpec = tween(100),
        label = "playScale",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screen),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 播放模式键：随机 / 列表循环 / 单曲循环 / 顺序播放，点击循环切换
        IconButton(onClick = onCycleMode, enabled = !resolving) {
            val (icon, desc) = when {
                shuffle -> Icons.Outlined.Shuffle to "随机播放"
                repeatMode == RepeatMode.ONE -> Icons.Outlined.RepeatOne to "单曲循环"
                repeatMode == RepeatMode.OFF -> Icons.Outlined.ArrowRightAlt to "顺序播放"
                else -> Icons.Outlined.Repeat to "列表循环"
            }
            Icon(
                imageVector = icon,
                contentDescription = desc,
                tint = if (shuffle || repeatMode != RepeatMode.OFF) {
                    accent
                } else {
                    contentColor.copy(alpha = 0.55f)
                },
            )
        }
        IconButton(onClick = onPrevious, enabled = !resolving) {
            Icon(
                imageVector = Icons.Outlined.SkipPrevious,
                contentDescription = "上一首",
                tint = contentColor,
            )
        }
        // 主播放键：取色渐变圆形底 + 按压缩放动效
        Box(
            modifier = Modifier
                .size(64.dp)
                .graphicsLayer {
                    scaleX = playScale
                    scaleY = playScale
                }
                .clip(CircleShape)
                .background(playGradient)
                .pointerInput(resolving) {
                    detectTapGestures(
                        onPress = {
                            playPressed = true
                            tryAwaitRelease()
                            playPressed = false
                        },
                        onTap = { if (!resolving) onToggle() },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            if (resolving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = onAccent,
                    strokeWidth = 2.5.dp,
                )
            } else {
                Icon(
                    imageVector = if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                    contentDescription = if (isPlaying) "暂停" else "播放",
                    tint = onAccent,
                    modifier = Modifier.size(36.dp),
                )
            }
        }
        IconButton(onClick = onNext, enabled = !resolving) {
            Icon(
                imageVector = Icons.Outlined.SkipNext,
                contentDescription = "下一首",
                tint = contentColor,
            )
        }
        IconButton(onClick = onOpenQueue) {
            Icon(
                imageVector = Icons.Outlined.QueueMusic,
                contentDescription = "播放列表",
                tint = contentColor,
            )
        }
    }
}
