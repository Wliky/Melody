package com.wliky.melody.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.wliky.melody.artist.ArtistScreen
import com.wliky.melody.cloud.CloudViewModel
import com.wliky.melody.data.model.Playlist
import com.wliky.melody.data.model.Podcast
import com.wliky.melody.home.HomeScreen
import com.wliky.melody.home.HomeViewModel
import com.wliky.melody.mine.MineScreen
import com.wliky.melody.player.AddToPlaylistSheet
import com.wliky.melody.player.MiniPlayerBar
import com.wliky.melody.player.NowPlayingScreen
import com.wliky.melody.player.PlayerQueueSheet
import com.wliky.melody.playlist.PlaylistDetailScreen
import com.wliky.melody.podcast.PodcastProgramViewModel
import com.wliky.melody.podcast.PodcastScreen
import com.wliky.melody.profile.ProfileScreen
import com.wliky.melody.search.SearchScreen
import com.wliky.melody.settings.LyricsSettingsScreen
import com.wliky.melody.settings.MiniBarMode
import com.wliky.melody.settings.SettingsScreen
import com.wliky.melody.settings.SettingsViewModel
import com.wliky.melody.settings.StorageScreen
import com.wliky.melody.songlist.DailySongsViewModel
import com.wliky.melody.songlist.NewSongsViewModel
import com.wliky.melody.songlist.SongListScreen
import com.wliky.melody.toplist.ToplistScreen
import com.wliky.melody.ui.components.LocalNavAnimatedVisibilityScope
import com.wliky.melody.ui.gallery.ComponentGalleryScreen
import com.wliky.melody.ui.theme.MelodyMotion
import com.wliky.melody.ui.theme.Spacing
import com.wliky.melody.ui.theme.ThemeMode

/** 迷你条「滑动隐藏」模式下，收起后底部预留的上滑唤出手势区高度。 */
private const val SWIPE_REVEAL_ZONE_DP = 28f

/** 判定为「上滑」的最小位移（px），低于此值视为抖动。 */
private const val SWIPE_REVEAL_THRESHOLD_PX = 24f

/** 底部导航 Tab（双 Tab：首页 / 我的，参考网易云官方形态） */
private enum class MainTab(
    val label: String,
    val icon: ImageVector,
    val iconSelected: ImageVector,
) {
    Home("首页", Icons.Outlined.Home, Icons.Rounded.Home),
    Mine("我的", Icons.Outlined.AccountCircle, Icons.Rounded.AccountCircle),
}

/**
 * 主框架：底部双 Tab（首页 / 我的）+ 歌单详情页覆盖层。
 * 点任意歌单 → 覆盖层打开详情（隐藏底部栏），返回回到原 Tab。
 */
/**
 * 主框架 ↔ 播放页转场时长组（毫秒）：
 * 入场滑动比出场略慢（封面飞越需要纵深）、淡入淡出错开形成层次。
 * 这组值是整体调优的节奏，不映射全局 Duration 档位，只收敛命名避免魔法数。
 */
private object PlayerTransitionDurations {
    /** 入场：主框架淡出（先让位） */
    const val ExitFadeMain = 180

    /** 入场：播放页上滑 + 淡入 */
    const val EnterSlide = 360
    const val EnterFade = 220

    /** 出场：主框架淡入 */
    const val EnterFadeMain = 220

    /** 出场：播放页下滑 + 淡出（比入场利落） */
    const val ExitSlide = 320
    const val ExitFadePlayer = 260
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun MainScreen(
    onLogout: () -> Unit,
    themeMode: ThemeMode,
    onSelectThemeMode: (ThemeMode) -> Unit,
    homeViewModel: HomeViewModel = hiltViewModel(),
) {
    var tab by rememberSaveable { mutableStateOf(MainTab.Home) }
    var detailPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var showNowPlaying by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var showProfile by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showStorage by remember { mutableStateOf(false) }
    var showLyricsSettings by remember { mutableStateOf(false) }
    var showGallery by remember { mutableStateOf(false) }
    var showDaily by remember { mutableStateOf(false) }
    var showCloud by remember { mutableStateOf(false) }
    var showToplist by remember { mutableStateOf(false) }
    var showNewSongs by remember { mutableStateOf(false) }
    var showPodcast by remember { mutableStateOf(false) }
    var podcastProgram by remember { mutableStateOf<Podcast?>(null) }
    var artistTarget by remember { mutableStateOf<Pair<Long, String>?>(null) }
    var showQueue by remember { mutableStateOf(false) }
    var addToPlaylistSongIds by remember { mutableStateOf<List<Long>>(emptyList()) }

    val playerQueue = homeViewModel.playerQueue

    // 迷你条显示模式（固定 / 隐藏 / 滑动隐藏）
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val miniBarMode by settingsViewModel.miniBarMode.collectAsState()
    var miniBarHiddenBySwipe by remember { mutableStateOf(false) }

    // 宽屏判定（P10）：≥600dp（平板 / 折叠展开 / 横屏大屏）改走侧边 NavigationRail，
    // 窄屏保持底部 NavigationBar。与 HomeScreen 网格升三列的阈值保持一致。
    val useRail = LocalConfiguration.current.screenWidthDp >= 600

    // 换歌后，被下滑收起的迷你条自动回来
    val currentSongId = playerQueue.state.current?.id
    LaunchedEffect(currentSongId) { miniBarHiddenBySwipe = false }

    // 用户点播（列表页任一点击歌曲）→ 自动打开全屏播放页
    LaunchedEffect(Unit) {
        playerQueue.userPlayEvents.collect { showNowPlaying = true }
    }

    // 覆盖层按「后进先出」判定当前置顶页，返回键只关最上面那一层
    val overlay = when {
        artistTarget != null -> Overlay.Artist
        showNowPlaying -> Overlay.NowPlaying
        detailPlaylist != null -> Overlay.Playlist
        showSearch -> Overlay.Search
        showProfile -> Overlay.Profile
        showDaily -> Overlay.Daily
        showCloud -> Overlay.Cloud
        showToplist -> Overlay.Toplist
        showNewSongs -> Overlay.NewSongs
        showPodcast -> Overlay.Podcast
        podcastProgram != null -> Overlay.PodcastProgram
        // 设置的子页面（画廊/存储/歌词）压在设置页之上，须先于设置判定，
        // 否则返回键会先关掉设置页而不是子页面
        showGallery -> Overlay.Gallery
        showStorage -> Overlay.Storage
        showLyricsSettings -> Overlay.LyricsSettings
        showSettings -> Overlay.Settings
        else -> null
    }
    BackHandler(enabled = overlay != null) {
        when (overlay) {
            Overlay.NowPlaying -> showNowPlaying = false
            Overlay.Playlist -> detailPlaylist = null
            Overlay.Search -> showSearch = false
            Overlay.Profile -> showProfile = false
            Overlay.Daily -> showDaily = false
            Overlay.Cloud -> showCloud = false
            Overlay.Toplist -> showToplist = false
            Overlay.NewSongs -> showNewSongs = false
            Overlay.Podcast -> showPodcast = false
            Overlay.PodcastProgram -> podcastProgram = null
            Overlay.Settings -> showSettings = false
            Overlay.Storage -> showStorage = false
            Overlay.LyricsSettings -> showLyricsSettings = false
            Overlay.Gallery -> showGallery = false
            Overlay.Artist -> artistTarget = null
            null -> Unit
        }
    }

    // 播放列表 / 收藏到歌单弹层：ModalBottomSheet 走 Popup，浮在任意覆盖层之上
    if (showQueue) {
        PlayerQueueSheet(
            queue = playerQueue.state,
            onPlayAt = { index ->
                playerQueue.playAt(index)
                showQueue = false
            },
            onDismiss = { showQueue = false },
        )
    }
    if (addToPlaylistSongIds.isNotEmpty()) {
        AddToPlaylistSheet(
            songIds = addToPlaylistSongIds,
            onDismiss = { addToPlaylistSongIds = emptyList() },
        )
    }

    val openPlaylist: (Playlist) -> Unit = { playlist ->
        detailPlaylist = playlist
    }

    // 全屏播放页覆盖层（盖住主框架，播放不中断）
    // 歌手页（从播放页歌手名进入，盖住播放页；返回后回到播放页，播放不中断）
    artistTarget?.let { (artistId, artistName) ->
        OverlayEnter {
            ArtistScreen(
                artistId = artistId,
                artistName = artistName,
                onBack = { artistTarget = null },
            )
        }
        return
    }

    // 全屏播放页不再硬切 return：与主框架放进 AnimatedContent 两分支带转场切换
    // （迷你条封面 ↔ 播放页大封面做共享元素飞越，见 ui/components/SharedTransition.kt）。
    // 播放页打开期间（歌手页除外），下方覆盖层暂时让位、状态保留 —— 与原 return 链的
    // 「播放页置顶、关闭后回到原页面」语义一致。

    // 组件画廊（设置页隐藏入口进入，开发期复查组件视觉）
    if (showGallery && !showNowPlaying) {
        OverlayEnter {
            ComponentGalleryScreen(
                themeMode = themeMode,
                onSelectThemeMode = onSelectThemeMode,
            )
        }
        return
    }

    // 存储管理 / 歌词管理：从设置页进入，覆盖在设置页之上；
    // 渲染级联须先于设置页判定，否则设置页仍置顶时子页面永远出不来
    if (showStorage && !showNowPlaying) {
        OverlayEnter {
            StorageScreen(onBack = { showStorage = false })
        }
        return
    }

    if (showLyricsSettings && !showNowPlaying) {
        OverlayEnter {
            LyricsSettingsScreen(onBack = { showLyricsSettings = false })
        }
        return
    }

    if (showSettings && !showNowPlaying) {
        OverlayEnter {
            SettingsScreen(
                themeMode = themeMode,
                onSelectThemeMode = onSelectThemeMode,
                onOpenGallery = { showGallery = true },
                onOpenStorage = { showStorage = true },
                onOpenLyricsSettings = { showLyricsSettings = true },
                onBack = { showSettings = false },
            )
        }
        return
    }

    detailPlaylist?.takeIf { !showNowPlaying }?.let { playlist ->
        OverlayEnter {
            PlaylistDetailScreen(
                playlist = playlist,
                onBack = { detailPlaylist = null },
                onCollectSongs = { songIds -> addToPlaylistSongIds = songIds },
            )
        }
        return
    }

    // 搜索页覆盖层（歌单详情会盖住搜索）
    if (showSearch && !showNowPlaying) {
        OverlayEnter {
            SearchScreen(
                onBack = { showSearch = false },
                onOpenPlaylist = openPlaylist,
            )
        }
        return
    }

    // 个人主页（听歌排行）
    if (showProfile && !showNowPlaying) {
        OverlayEnter {
            ProfileScreen(onBack = { showProfile = false })
        }
        return
    }

    // 每日推荐（通用歌曲列表页）
    if (showDaily && !showNowPlaying) {
        val viewModel: DailySongsViewModel = hiltViewModel()
        OverlayEnter {
            SongListScreen(
                title = "每日推荐",
                state = viewModel.uiState,
                currentSongId = playerQueue.state.current?.id,
                onBack = { showDaily = false },
                onRetry = viewModel::load,
                onPlaySongAt = viewModel::playSongAt,
            )
        }
        return
    }

    // 云盘音乐
    if (showCloud && !showNowPlaying) {
        val viewModel: CloudViewModel = hiltViewModel()
        OverlayEnter {
            SongListScreen(
                title = "云盘音乐",
                state = viewModel.uiState,
                currentSongId = playerQueue.state.current?.id,
                onBack = { showCloud = false },
                onRetry = viewModel::load,
                onPlaySongAt = viewModel::playSongAt,
            )
        }
        return
    }

    // 排行榜（点榜单 → 复用歌单详情覆盖层）
    if (showToplist && !showNowPlaying) {
        OverlayEnter {
            ToplistScreen(
                onBack = { showToplist = false },
                onOpenPlaylist = openPlaylist,
            )
        }
        return
    }

    // 新歌首发（通用歌曲列表页）
    if (showNewSongs && !showNowPlaying) {
        val viewModel: NewSongsViewModel = hiltViewModel()
        OverlayEnter {
            SongListScreen(
                title = "新歌首发",
                state = viewModel.uiState,
                currentSongId = playerQueue.state.current?.id,
                onBack = { showNewSongs = false },
                onRetry = viewModel::load,
                onPlaySongAt = viewModel::playSongAt,
            )
        }
        return
    }

    // 电台节目（复用通用歌曲列表页；比播客列表更上层，须先判定）
    podcastProgram?.takeIf { !showNowPlaying }?.let { program ->
        val viewModel: PodcastProgramViewModel = hiltViewModel()
        viewModel.start(program.id)
        OverlayEnter {
            SongListScreen(
                title = program.name,
                state = viewModel.uiState,
                currentSongId = playerQueue.state.current?.id,
                onBack = { podcastProgram = null },
                onRetry = viewModel::load,
                onPlaySongAt = viewModel::playSongAt,
            )
        }
        return
    }

    // 播客（热门电台列表）
    if (showPodcast && !showNowPlaying) {
        OverlayEnter {
            PodcastScreen(
                onBack = { showPodcast = false },
                onOpenRadio = { podcastProgram = it },
            )
        }
        return
    }

    // 主框架 ↔ 全屏播放页：共享元素转场容器。
    // 进入：播放页自底部轻滑 + 淡入，迷你条封面飞越放大为播放页大封面；
    // 退出：反向飞回。其余覆盖层（歌手/搜索/歌单详情…）仍在上方 if-return 压栈，
    // 覆盖层打开时本容器整体被 return 跳过，与原有层级语义一致。
    SharedTransitionLayout {
        AnimatedContent(
            targetState = showNowPlaying,
            label = "playerTransition",
            transitionSpec = {
                if (targetState) {
                    (
                        slideInVertically(tween(PlayerTransitionDurations.EnterSlide)) { it / 5 } +
                            fadeIn(tween(PlayerTransitionDurations.EnterFade))
                        ) togetherWith
                        fadeOut(tween(PlayerTransitionDurations.ExitFadeMain))
                } else {
                    fadeIn(tween(PlayerTransitionDurations.EnterFadeMain)) togetherWith
                        (
                            slideOutVertically(tween(PlayerTransitionDurations.ExitSlide)) { it / 5 } +
                                fadeOut(tween(PlayerTransitionDurations.ExitFadePlayer))
                            )
                }
            },
        ) { nowPlaying ->
            CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                if (nowPlaying) {
                    NowPlayingScreen(
                        onClose = { showNowPlaying = false },
                        onOpenArtist = { id, name -> artistTarget = id to name },
                        onOpenQueue = { showQueue = true },
                        onOpenCollect = {
                            playerQueue.state.current?.let { song ->
                                addToPlaylistSongIds = listOf(song.id)
                            }
                        },
                    )
                } else {
                    Scaffold(
                        bottomBar = {
                            // 宽屏改走侧边 NavigationRail（见下方 Row），底栏只在窄屏出现：
                            // 底栏在 >600dp 宽度会横跨整屏，拇指区跑到边角、且白占一整条垂直空间
                            if (!useRail) {
                                NavigationBar {
                                    MainTab.entries.forEach { item ->
                                        val selected = tab == item
                                        NavigationBarItem(
                                            selected = selected,
                                            onClick = { tab = item },
                                            icon = {
                                                Icon(
                                                    imageVector = if (selected) item.iconSelected else item.icon,
                                                    contentDescription = item.label,
                                                )
                                            },
                                            label = { Text(item.label) },
                                        )
                                    }
                                }
                            }
                        },
                    ) { padding ->
                        Box(modifier = Modifier.padding(padding)) {
                            Row(modifier = Modifier.fillMaxSize()) {
                                // 宽屏（平板 / 折叠展开 / 横屏大屏）：侧边导航栏，内容区更高的一屏
                                if (useRail) {
                                    NavigationRail {
                                        MainTab.entries.forEach { item ->
                                            val selected = tab == item
                                            NavigationRailItem(
                                                selected = selected,
                                                onClick = { tab = item },
                                                icon = {
                                                    Icon(
                                                        imageVector = if (selected) item.iconSelected else item.icon,
                                                        contentDescription = item.label,
                                                    )
                                                },
                                                label = { Text(item.label) },
                                            )
                                        }
                                    }
                                }
                                Box(modifier = Modifier.weight(1f)) {
                                    // Tab 切换动效：淡入 + 轻微横移（原先 when 硬替换、毫无过渡）
                                    AnimatedContent(
                                        targetState = tab,
                                        label = "mainTab",
                                        transitionSpec = {
                                            (
                                                fadeIn(tween(MelodyMotion.DurationShort)) +
                                                    slideInHorizontally(tween(MelodyMotion.DurationShort)) { it / 24 }
                                                ) togetherWith fadeOut(tween(MelodyMotion.DurationShort))
                                        },
                                    ) { currentTab ->
                                        when (currentTab) {
                                            MainTab.Home -> HomeScreen(
                                                onOpenPlaylist = openPlaylist,
                                                onOpenSearch = { showSearch = true },
                                                onOpenDaily = { showDaily = true },
                                                onOpenToplist = { showToplist = true },
                                                onOpenNewSongs = { showNewSongs = true },
                                                onOpenPodcast = { showPodcast = true },
                                                modifier = Modifier.fillMaxSize(),
                                                viewModel = homeViewModel,
                                            )

                                            MainTab.Mine -> MineScreen(
                                                onLogout = {
                                                    playerQueue.stop()
                                                    onLogout()
                                                },
                                                onOpenPlaylist = openPlaylist,
                                                onOpenProfile = { showProfile = true },
                                                onOpenCloud = { showCloud = true },
                                                onOpenSettings = { showSettings = true },
                                            )
                                        }
                                    }
                                }
                            }

                            // 全局悬浮迷你条：盖在 Tab 内容上、底部导航栏上方；
                            // 有曲目且模式允许（固定 / 滑动隐藏且未被下滑收起）才出现
                            val miniBarVisible = playerQueue.state.current != null && when (miniBarMode) {
                                MiniBarMode.FIXED -> true
                                MiniBarMode.HIDDEN -> false
                                MiniBarMode.SWIPE_HIDE -> !miniBarHiddenBySwipe
                            }
                            if (miniBarVisible) {
                                MiniPlayerBar(
                                    state = playerQueue.state,
                                    onTogglePlayPause = playerQueue::togglePlayPause,
                                    onNext = playerQueue::playNext,
                                    onOpenQueue = { showQueue = true },
                                    onOpen = { showNowPlaying = true },
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(horizontal = Spacing.screen, vertical = Spacing.md),
                                    // 下滑 = 收起；收起后靠底部手势区上滑唤出
                                    onSwipeHide = if (miniBarMode == MiniBarMode.SWIPE_HIDE) {
                                        { miniBarHiddenBySwipe = true }
                                    } else {
                                        null
                                    },
                                )
                            } else if (
                                miniBarMode == MiniBarMode.SWIPE_HIDE &&
                                playerQueue.state.current != null
                            ) {
                                // 已收起：底部留一条透明手势区，上滑唤出迷你条
                                Spacer(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .height(SWIPE_REVEAL_ZONE_DP.dp)
                                        .pointerInput(Unit) {
                                            var acc = 0f
                                            detectVerticalDragGestures(
                                                onDragStart = { acc = 0f },
                                                onDragEnd = { acc = 0f },
                                                onVerticalDrag = { change, dragAmount ->
                                                    acc += dragAmount
                                                    change.consume()
                                                    // 向上滑 = 位移为负，累计超过阈值即唤出
                                                    if (acc < -SWIPE_REVEAL_THRESHOLD_PX) {
                                                        acc = 0f
                                                        miniBarHiddenBySwipe = false
                                                    }
                                                },
                                            )
                                        },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 二级页统一进入动画（P8）：从右侧轻滑入 + 淡入，与「进栈」体感一致。
 *
 * 原先所有覆盖层都是瞬切，进来毫无过渡；统一包一层后，搜索 / 歌单详情 / 设置 / 每日推荐
 * 等二级页共享同一条进入动线。退出保持瞬间：BackHandler 立即响应、不留动画尾巴。
 *
 * 用 [MutableTransitionState] 先 false 再立刻置 true —— 若直接 visible = true，
 * AnimatedVisibility 首次组合不会播放 enter 动画。
 */
@Composable
private fun OverlayEnter(
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = visibleState,
        enter = slideInHorizontally(tween(MelodyMotion.DurationShort)) { it / 12 } +
            fadeIn(tween(MelodyMotion.DurationShort)),
        exit = ExitTransition.None,
        content = content,
    )
}

/** 覆盖层类型：决定返回键关闭哪一层。 */
private enum class Overlay {
    NowPlaying,
    Playlist,
    Search,
    Profile,
    Daily,
    Cloud,
    Toplist,
    NewSongs,
    Podcast,
    PodcastProgram,
    Settings,
    Storage,
    LyricsSettings,
    Gallery,
    Artist,
}
