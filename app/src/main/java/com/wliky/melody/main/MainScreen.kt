package com.wliky.melody.main

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.wliky.melody.settings.SettingsScreen
import com.wliky.melody.settings.StorageScreen
import com.wliky.melody.songlist.DailySongsViewModel
import com.wliky.melody.songlist.NewSongsViewModel
import com.wliky.melody.songlist.SongListScreen
import com.wliky.melody.toplist.ToplistScreen
import com.wliky.melody.ui.components.LocalNavAnimatedVisibilityScope
import com.wliky.melody.ui.gallery.ComponentGalleryScreen
import com.wliky.melody.ui.theme.Spacing
import com.wliky.melody.ui.theme.ThemeMode

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
    var showAddToPlaylist by remember { mutableStateOf(false) }

    val playerQueue = homeViewModel.playerQueue

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
    playerQueue.state.current?.id?.let { songId ->
        if (showAddToPlaylist) {
            AddToPlaylistSheet(
                songId = songId,
                onDismiss = { showAddToPlaylist = false },
            )
        }
    }

    val openPlaylist: (Playlist) -> Unit = { playlist ->
        detailPlaylist = playlist
    }

    // 全屏播放页覆盖层（盖住主框架，播放不中断）
    // 歌手页（从播放页歌手名进入，盖住播放页；返回后回到播放页，播放不中断）
    artistTarget?.let { (artistId, artistName) ->
        ArtistScreen(
            artistId = artistId,
            artistName = artistName,
            onBack = { artistTarget = null },
        )
        return
    }

    // 全屏播放页不再硬切 return：与主框架放进 AnimatedContent 两分支带转场切换
    // （迷你条封面 ↔ 播放页大封面做共享元素飞越，见 ui/components/SharedTransition.kt）。
    // 播放页打开期间（歌手页除外），下方覆盖层暂时让位、状态保留 —— 与原 return 链的
    // 「播放页置顶、关闭后回到原页面」语义一致。

    // 组件画廊（设置页隐藏入口进入，开发期复查组件视觉）
    if (showGallery && !showNowPlaying) {
        ComponentGalleryScreen(
            themeMode = themeMode,
            onSelectThemeMode = onSelectThemeMode,
        )
        return
    }

    // 存储管理 / 歌词管理：从设置页进入，覆盖在设置页之上；
    // 渲染级联须先于设置页判定，否则设置页仍置顶时子页面永远出不来
    if (showStorage && !showNowPlaying) {
        StorageScreen(onBack = { showStorage = false })
        return
    }

    if (showLyricsSettings && !showNowPlaying) {
        LyricsSettingsScreen(onBack = { showLyricsSettings = false })
        return
    }

    if (showSettings && !showNowPlaying) {
        SettingsScreen(
            themeMode = themeMode,
            onSelectThemeMode = onSelectThemeMode,
            onOpenGallery = { showGallery = true },
            onOpenStorage = { showStorage = true },
            onOpenLyricsSettings = { showLyricsSettings = true },
            onBack = { showSettings = false },
        )
        return
    }

    detailPlaylist?.takeIf { !showNowPlaying }?.let { playlist ->
        PlaylistDetailScreen(
            playlist = playlist,
            onBack = { detailPlaylist = null },
        )
        return
    }

    // 搜索页覆盖层（歌单详情会盖住搜索）
    if (showSearch && !showNowPlaying) {
        SearchScreen(
            onBack = { showSearch = false },
            onOpenPlaylist = openPlaylist,
        )
        return
    }

    // 个人主页（听歌排行）
    if (showProfile && !showNowPlaying) {
        ProfileScreen(onBack = { showProfile = false })
        return
    }

    // 每日推荐（通用歌曲列表页）
    if (showDaily && !showNowPlaying) {
        val viewModel: DailySongsViewModel = hiltViewModel()
        SongListScreen(
            title = "每日推荐",
            state = viewModel.uiState,
            currentSongId = playerQueue.state.current?.id,
            onBack = { showDaily = false },
            onRetry = viewModel::load,
            onPlaySongAt = viewModel::playSongAt,
        )
        return
    }

    // 云盘音乐
    if (showCloud && !showNowPlaying) {
        val viewModel: CloudViewModel = hiltViewModel()
        SongListScreen(
            title = "云盘音乐",
            state = viewModel.uiState,
            currentSongId = playerQueue.state.current?.id,
            onBack = { showCloud = false },
            onRetry = viewModel::load,
            onPlaySongAt = viewModel::playSongAt,
        )
        return
    }

    // 排行榜（点榜单 → 复用歌单详情覆盖层）
    if (showToplist && !showNowPlaying) {
        ToplistScreen(
            onBack = { showToplist = false },
            onOpenPlaylist = openPlaylist,
        )
        return
    }

    // 新歌首发（通用歌曲列表页）
    if (showNewSongs && !showNowPlaying) {
        val viewModel: NewSongsViewModel = hiltViewModel()
        SongListScreen(
            title = "新歌首发",
            state = viewModel.uiState,
            currentSongId = playerQueue.state.current?.id,
            onBack = { showNewSongs = false },
            onRetry = viewModel::load,
            onPlaySongAt = viewModel::playSongAt,
        )
        return
    }

    // 电台节目（复用通用歌曲列表页；比播客列表更上层，须先判定）
    podcastProgram?.takeIf { !showNowPlaying }?.let { program ->
        val viewModel: PodcastProgramViewModel = hiltViewModel()
        viewModel.start(program.id)
        SongListScreen(
            title = program.name,
            state = viewModel.uiState,
            currentSongId = playerQueue.state.current?.id,
            onBack = { podcastProgram = null },
            onRetry = viewModel::load,
            onPlaySongAt = viewModel::playSongAt,
        )
        return
    }

    // 播客（热门电台列表）
    if (showPodcast && !showNowPlaying) {
        PodcastScreen(
            onBack = { showPodcast = false },
            onOpenRadio = { podcastProgram = it },
        )
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
                    (slideInVertically(tween(360)) { it / 5 } + fadeIn(tween(220))) togetherWith
                        fadeOut(tween(180))
                } else {
                    fadeIn(tween(220)) togetherWith
                        (slideOutVertically(tween(320)) { it / 5 } + fadeOut(tween(260)))
                }
            },
        ) { nowPlaying ->
            CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                if (nowPlaying) {
                    NowPlayingScreen(
                        onClose = { showNowPlaying = false },
                        onOpenArtist = { id, name -> artistTarget = id to name },
                        onOpenQueue = { showQueue = true },
                        onOpenCollect = { showAddToPlaylist = true },
                    )
                } else {
                    Scaffold(
                        bottomBar = {
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
                        },
                    ) { padding ->
                        Box(modifier = Modifier.padding(padding)) {
                            when (tab) {
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

                            // 全局悬浮迷你条：盖在 Tab 内容上、底部导航栏上方；有曲目才出现
                            if (playerQueue.state.current != null) {
                                MiniPlayerBar(
                                    state = playerQueue.state,
                                    onTogglePlayPause = playerQueue::togglePlayPause,
                                    onNext = playerQueue::playNext,
                                    onOpenQueue = { showQueue = true },
                                    onOpen = { showNowPlaying = true },
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(horizontal = Spacing.screen, vertical = Spacing.md),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
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
