package com.wliky.melody.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.wliky.melody.data.model.Banner
import com.wliky.melody.data.model.Playlist
import com.wliky.melody.data.model.Toplist
import com.wliky.melody.ui.components.CoverImage
import com.wliky.melody.ui.components.EmptyState
import com.wliky.melody.ui.components.MelodyButton
import com.wliky.melody.ui.components.SkeletonBox
import com.wliky.melody.ui.theme.Spacing

/** 首页横滑区每区块预览的最大卡片数。 */
private const val STRIP_PREVIEW_COUNT = 10

/** 首页所有入口卡片的统一圆角（封面 / 横幅一致）。 */
private val CARD_CORNER = 16.dp

/**
 * 首页（主框架 Tab 1）：大标题问候 + 标题下搜索框 +
 * 第一行合并入口（每日推荐 / 新歌首发 / 播客，推荐歌单式封面，无分区标题）+
 * 排行榜（横向封面流） + 精选横幅 + 推荐歌单（双列网格）。
 * 入口卡片：点封面进各自页面，点右下角播放按钮直接起播。
 * 免登录可浏览；歌单点击 → 进入详情页（onOpenPlaylist）。
 */
@Composable
fun HomeScreen(
    onOpenPlaylist: (Playlist) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenDaily: () -> Unit,
    onOpenToplist: () -> Unit,
    onOpenPodcast: () -> Unit,
    onOpenNewSongs: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state = viewModel.uiState

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        // 大标题 + 时段问候（MusicStorm PageTitle 风格）
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Spacing.screen, end = Spacing.screen, top = Spacing.lg),
        ) {
            // 问候语按首次组合的时段计算并缓存，不再每次重组都读系统时间
            val greeting = remember { greetingLabel() }
            Text(
                text = "Melody",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "$greeting · 发现今日好音乐",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }

        // 标题下搜索框：点击进入搜索页
        SearchBarHint(onClick = onOpenSearch)

        // 下拉刷新（官方交互）：缓存秒显后手动拉新；TTL 内静默不请求
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = { viewModel.load(force = true) },
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                state.loading -> LoadingSection()
                state.error != null -> LoadErrorSection(state.error!!) { viewModel.load(force = true) }
                else -> HomeContent(
                    state,
                    onOpenPlaylist = onOpenPlaylist,
                    onPlayPlaylist = viewModel::playPlaylist,
                    onOpenDaily = onOpenDaily,
                    onOpenToplist = onOpenToplist,
                    onOpenNewSongs = onOpenNewSongs,
                    onOpenPodcast = onOpenPodcast,
                    onPlayPodcast = viewModel::playFirstPodcast,
                    onPlayDailySong = viewModel::playDailySongAt,
                    onPlayNewSong = viewModel::playNewSongAt,
                )
            }
        }
    }
}

@Composable
private fun LoadingSection() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.screen, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        SkeletonBox(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .clip(RoundedCornerShape(16.dp)),
        )
        SkeletonBox(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(16.dp)),
        )
        repeat(3) {
            SkeletonBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(RoundedCornerShape(12.dp)),
            )
        }
    }
}

@Composable
private fun LoadErrorSection(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.screen, vertical = Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        EmptyState(text = "数据加载失败", hint = message)
        MelodyButton(text = "重试", onClick = onRetry)
    }
}

@Composable
private fun HomeContent(
    state: HomeViewModel.HomeUiState,
    onOpenPlaylist: (Playlist) -> Unit,
    onPlayPlaylist: (Playlist) -> Unit,
    onOpenDaily: () -> Unit,
    onOpenToplist: () -> Unit,
    onOpenNewSongs: () -> Unit,
    onOpenPodcast: () -> Unit,
    onPlayPodcast: () -> Unit,
    onPlayDailySong: (Int) -> Unit,
    onPlayNewSong: (Int) -> Unit,
) {
    // 派生数据 remember：chunked / take 只在数据变化时重算，不再每次重组重新分配新列表
    val recommendedRows = remember(state.recommended) { state.recommended.chunked(2) }
    val toplistPreview = remember(state.toplists) { state.toplists.take(STRIP_PREVIEW_COUNT) }

    // 卡片宽度全局只算一次：原先两个 item 内各嵌 BoxWithConstraints（subcomposition），
    // 滚动到该 item 反复走子组合测量，拖慢滚动帧；现整体只测一次
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // 列表内容宽 = 总宽 - 左右 contentPadding；两列网格去掉列间距后均分
        val cardWidth = (maxWidth - Spacing.screen * 2 - Spacing.md) / 2
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            // 第一行合并入口（无分区标题）：每日推荐 / 新歌首发 / 播客。
            // 卡片宽度与推荐歌单网格卡片完全一致（同 CoverCard 同尺寸），横滑展示。
            item {
                MediaRow {
                    item(key = "entry-daily") {
                        EntryCard(
                            title = "每日推荐",
                            coverUrl = state.dailySongs.firstOrNull()?.coverUrl,
                            badge = state.dailySongs.size.takeIf { it > 0 }?.let { "$it 首" },
                            onClick = onOpenDaily,
                            onPlay = { onPlayDailySong(0) },
                            modifier = Modifier.width(cardWidth),
                        )
                    }
                    item(key = "entry-new") {
                        EntryCard(
                            title = "新歌首发",
                            coverUrl = state.newSongs.firstOrNull()?.coverUrl,
                            badge = state.newSongs.size.takeIf { it > 0 }?.let { "$it 首" },
                            onClick = onOpenNewSongs,
                            onPlay = { onPlayNewSong(0) },
                            modifier = Modifier.width(cardWidth),
                        )
                    }
                    item(key = "entry-podcast") {
                        EntryCard(
                            title = "播客",
                            coverUrl = state.podcasts.firstOrNull()?.coverUrl,
                            badge = state.podcasts.size.takeIf { it > 0 }?.let { "$it 期" },
                            onClick = onOpenPodcast,
                            onPlay = onPlayPodcast,
                            modifier = Modifier.width(cardWidth),
                        )
                    }
                }
            }

            // 排行榜（榜单即歌单，点卡片进榜单详情）
            if (state.toplists.isNotEmpty()) {
                item { SectionHeader(title = "排行榜", onMore = onOpenToplist) }
                item {
                    MediaRow {
                        items(
                            toplistPreview,
                            key = { "toplist-${it.id}" },
                        ) { toplist ->
                            ToplistMediaCard(
                                toplist = toplist,
                                onClick = {
                                    onOpenPlaylist(
                                        Playlist(
                                            id = toplist.id,
                                            name = toplist.name,
                                            coverUrl = toplist.coverUrl,
                                        ),
                                    )
                                },
                                onPlay = {
                                    onPlayPlaylist(
                                        Playlist(
                                            id = toplist.id,
                                            name = toplist.name,
                                            coverUrl = toplist.coverUrl,
                                        ),
                                    )
                                },
                                modifier = Modifier.width(cardWidth),
                            )
                        }
                    }
                }
            }

            if (state.banners.isNotEmpty()) {
                item { SectionTitle("精选横幅") }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        items(state.banners, key = { "banner-${it.id}-${it.imageUrl}" }) { banner ->
                            BannerCard(banner)
                        }
                    }
                }
            }
            if (state.recommended.isNotEmpty()) {
                item { SectionTitle("推荐歌单") }
                // 双列网格：一行两张大封面卡片
                items(
                    recommendedRows,
                    key = { row -> row.joinToString("-") { it.id.toString() } },
                ) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                        row.forEach { playlist ->
                            PlaylistCard(
                                playlist = playlist,
                                onClick = { onOpenPlaylist(playlist) },
                                onPlay = { onPlayPlaylist(playlist) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (row.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(Spacing.xl)) }
        }
    }
}

/** 可点击的分区标题：右侧「更多」箭头进完整页。 */
@Composable
private fun SectionHeader(title: String, onMore: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onMore)
            .padding(top = Spacing.sm, bottom = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = "更多",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = "查看全部$title",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = Spacing.sm),
    )
}

/** 横滑封面流通用容器。 */
@Composable
private fun MediaRow(content: LazyListScope.() -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        contentPadding = PaddingValues(end = Spacing.md),
        content = content,
    )
}

/** 入口卡片（第一行合并入口）：完全复用推荐歌单卡片样式（大小/比例/圆角/信息层级统一）。 */
@Composable
private fun EntryCard(
    title: String,
    coverUrl: String?,
    badge: String?,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CoverCard(
        coverUrl = coverUrl,
        title = title,
        badge = badge,
        onClick = onClick,
        onPlay = onPlay,
        badgeIcon = Icons.Outlined.MusicNote,
        modifier = modifier,
    )
}

/** 榜单封面卡片：完全复用推荐歌单卡片样式（封面+角标+播放按钮+渐变遮罩内榜名）。 */
@Composable
private fun ToplistMediaCard(
    toplist: Toplist,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CoverCard(
        coverUrl = toplist.coverUrl,
        title = toplist.name,
        badge = toplist.updateFrequency?.takeIf { it.isNotBlank() },
        onClick = onClick,
        onPlay = onPlay,
        badgeIcon = Icons.Outlined.Update,
        modifier = modifier,
    )
}

/** 标题下搜索框（点击跳转搜索页的占位输入框，MusicStorm 风格）。 */
@Composable
private fun SearchBarHint(onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screen, vertical = Spacing.md)
            .height(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(Spacing.sm))
            Text(
                text = "搜索歌曲、歌单、歌手",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun greetingLabel(): String = when (java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)) {
    in 0..5 -> "夜深了"
    in 6..11 -> "早上好"
    in 12..17 -> "下午好"
    else -> "晚上好"
}

@Composable
private fun BannerCard(banner: Banner) {
    CoverImage(
        url = banner.imageUrl,
        contentDescription = banner.title,
        modifier = Modifier
            .width(300.dp)
            .height(130.dp),
        shape = RoundedCornerShape(CARD_CORNER),
    )
}

/**
 * 推荐歌单卡片（双列网格）：大封面 + 右上角播放数角标 + 右下角直接播放按钮。
 * 点卡片进详情页；点播放按钮不进详情直接起播（涟漪反馈）。
 */
@Composable
private fun PlaylistCard(
    playlist: Playlist,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CoverCard(
        coverUrl = playlist.coverUrl,
        title = playlist.name,
        badge = formatCount(playlist.playCount),
        onClick = onClick,
        onPlay = onPlay,
        badgeIcon = Icons.Outlined.Headphones,
        modifier = modifier,
    )
}

/**
 * 全站统一封面卡片：1:1 大封面 + 16dp 统一圆角 + 右上角半透明「播放总数/更新」角标
 * + 右下角 34dp 播放按钮 + 封面下方名称（中等字重）。
 * 封面占整卡高度 70% 以上（双列卡片约 86%，三列入网约 80%），四类入口完全复用。
 *
 * @param badgeIcon 角标图标：推荐歌单为耳机（播放总数），入口为音符（首数），榜单为更新（频率）
 */
@Composable
private fun CoverCard(
    coverUrl: String?,
    title: String,
    badge: String?,
    onClick: () -> Unit,
    onPlay: (() -> Unit)?,
    modifier: Modifier = Modifier,
    badgeIcon: ImageVector? = null,
) {
    // 莫奈取色（Android 12+ 跟随系统壁纸；以下回退静态主色）
    val accent = MaterialTheme.colorScheme.primary
    Column(modifier = modifier.clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(CARD_CORNER)),
        ) {
            CoverImage(
                url = coverUrl,
                contentDescription = title,
                modifier = Modifier.matchParentSize(),
                shape = RoundedCornerShape(CARD_CORNER),
            )
            // 右上角角标：半透明底 + 线性图标 + 小字号
            badge?.let { text ->
                Surface(
                    color = Color.Black.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        badgeIcon?.let { icon ->
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(11.dp),
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                        }
                        Text(
                            text = text,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                        )
                    }
                }
            }
            // 右下角直接播放按钮：圆形半透明底 + 白色线性图标
            onPlay?.let { play ->
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f))
                        .clickable(onClick = play),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PlayArrow,
                        contentDescription = "播放$title",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(Spacing.xs + 2.dp))
        // 封面下方名称：中等字重单行省略
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun formatCount(count: Long): String {
    val wan = count / 10_000.0
    val yi = count / 100_000_000.0
    return when {
        count >= 100_000_000 ->
            if (yi % 1.0 == 0.0) "${yi.toInt()}亿" else "%.1f亿".format(yi)
        count >= 10_000 ->
            if (wan % 1.0 == 0.0) "${wan.toInt()}万" else "%.1f万".format(wan)
        else -> count.toString()
    }
}
