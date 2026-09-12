package com.wliky.melody.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.wliky.melody.feature.auth.LoginScreen
import com.wliky.melody.feature.auth.LoginViewModel
import com.wliky.melody.feature.history.HistoryScreen
import com.wliky.melody.feature.history.HistoryViewModel
import com.wliky.melody.feature.home.HomeScreen
import com.wliky.melody.feature.home.HomeViewModel
import com.wliky.melody.feature.player.AnimatedFullPlayer
import com.wliky.melody.feature.player.MiniPlayer
import com.wliky.melody.feature.player.PlayerViewModel
import com.wliky.melody.feature.playlist.PlaylistScreen
import com.wliky.melody.feature.playlist.PlaylistViewModel
import com.wliky.melody.feature.profile.ProfileScreen
import com.wliky.melody.feature.profile.ProfileViewModel
import com.wliky.melody.feature.search.SearchScreen
import com.wliky.melody.feature.search.SearchViewModel
import com.wliky.melody.feature.settings.SettingsScreen
import com.wliky.melody.feature.settings.SettingsViewModel

object Destinations {
    const val HOME = "home"
    const val SEARCH = "search"
    const val PROFILE = "profile"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val LOGIN = "login"
    const val PLAYLIST = "playlist/{playlistId}"

    fun playlist(playlistId: String): String = "playlist/$playlistId"
}

private data class NavEntry(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val navEntries = listOf(
    NavEntry(Destinations.HOME, "首页", Icons.Rounded.Home),
    NavEntry(Destinations.SEARCH, "搜索", Icons.Rounded.Search),
    NavEntry(Destinations.HISTORY, "历史", Icons.Rounded.History),
    NavEntry(Destinations.PROFILE, "我的", Icons.Rounded.Person),
)

/** 用 600dp 作为「平板 / 折叠屏展开」的断点，两套布局共用同一份页面实现。 */
@Composable
fun rememberIsWideLayout(): Boolean {
    val configuration = LocalConfiguration.current
    return configuration.screenWidthDp >= WIDE_BREAKPOINT_DP
}

private const val WIDE_BREAKPOINT_DP = 600

@Composable
fun MelodyApp() {
    val navController = rememberNavController()
    val isWide = rememberIsWideLayout()
    val snackbarHostState = remember { SnackbarHostState() }

    // 播放器 ViewModel 挂在 Activity 上，所有页面共享同一个播放实例
    val playerViewModel: PlayerViewModel = hiltViewModel()
    val snapshot by playerViewModel.snapshot.collectAsStateWithLifecycle()
    val queue by playerViewModel.queue.collectAsStateWithLifecycle()
    val lyric by playerViewModel.lyric.collectAsStateWithLifecycle()
    val showFullPlayer by playerViewModel.showFullPlayer.collectAsStateWithLifecycle()
    val message by playerViewModel.message.collectAsStateWithLifecycle()

    val nowPlayingId = snapshot.nowPlaying?.songId

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    LaunchedEffect(message) {
        val current = message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(current)
        playerViewModel.consumeMessage()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {
            if (isWide) {
                NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                    navEntries.forEach { entry ->
                        NavigationRailItem(
                            selected = currentRoute == entry.route,
                            onClick = { navController.switchTab(entry.route) },
                            icon = { Icon(entry.icon, contentDescription = entry.label) },
                            label = { Text(entry.label) },
                        )
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    bottomBar = {
                        if (!isWide) {
                            Column {
                                MiniPlayer(
                                    snapshot = snapshot,
                                    onToggle = playerViewModel::togglePlayPause,
                                    onNext = playerViewModel::next,
                                    onExpand = playerViewModel::openFullPlayer,
                                )
                                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                                    navEntries.forEach { entry ->
                                        NavigationBarItem(
                                            selected = currentRoute == entry.route,
                                            onClick = { navController.switchTab(entry.route) },
                                            icon = { Icon(entry.icon, contentDescription = entry.label) },
                                            label = { Text(entry.label) },
                                            colors = NavigationBarItemDefaults.colors(
                                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                    },
                ) { padding ->
                    NavHost(
                        navController = navController,
                        startDestination = Destinations.HOME,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                    ) {
                        composable(Destinations.HOME) {
                            val viewModel: HomeViewModel = hiltViewModel()
                            HomeScreen(
                                viewModel = viewModel,
                                isWide = isWide,
                                nowPlayingId = nowPlayingId,
                                onOpenSearch = { navController.navigate(Destinations.SEARCH) },
                                onOpenPlaylist = { navController.navigate(Destinations.playlist(it)) },
                                onOpenLogin = { navController.navigate(Destinations.LOGIN) },
                                onPlay = playerViewModel::play,
                            )
                        }

                        composable(Destinations.SEARCH) {
                            val viewModel: SearchViewModel = hiltViewModel()
                            SearchScreen(
                                viewModel = viewModel,
                                isWide = isWide,
                                nowPlayingId = nowPlayingId,
                                onOpenPlaylist = { navController.navigate(Destinations.playlist(it)) },
                                onPlay = playerViewModel::play,
                            )
                        }

                        composable(Destinations.PROFILE) {
                            val viewModel: ProfileViewModel = hiltViewModel()
                            ProfileScreen(
                                viewModel = viewModel,
                                isWide = isWide,
                                onOpenLogin = { navController.navigate(Destinations.LOGIN) },
                                onOpenHistory = { navController.navigate(Destinations.HISTORY) },
                                onOpenSettings = { navController.navigate(Destinations.SETTINGS) },
                                onOpenPlaylist = { navController.navigate(Destinations.playlist(it)) },
                            )
                        }

                        composable(Destinations.HISTORY) {
                            val viewModel: HistoryViewModel = hiltViewModel()
                            HistoryScreen(
                                viewModel = viewModel,
                                onPlay = playerViewModel::play,
                                onOpenSettings = { navController.navigate(Destinations.SETTINGS) },
                            )
                        }

                        composable(Destinations.SETTINGS) {
                            val viewModel: SettingsViewModel = hiltViewModel()
                            SettingsScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                            )
                        }

                        composable(Destinations.LOGIN) {
                            val viewModel: LoginViewModel = hiltViewModel()
                            LoginScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onLoggedIn = { navController.popBackStack() },
                            )
                        }

                        composable(Destinations.PLAYLIST) {
                            val viewModel: PlaylistViewModel = hiltViewModel()
                            PlaylistScreen(
                                viewModel = viewModel,
                                nowPlayingId = nowPlayingId,
                                onPlay = playerViewModel::play,
                            )
                        }
                    }
                }

                if (isWide) {
                    MiniPlayer(
                        snapshot = snapshot,
                        onToggle = playerViewModel::togglePlayPause,
                        onNext = playerViewModel::next,
                        onExpand = playerViewModel::openFullPlayer,
                    )
                }
            }
        }

        AnimatedFullPlayer(
            visible = showFullPlayer,
            snapshot = snapshot,
            lyric = lyric,
            queue = queue,
            message = message,
            onClose = playerViewModel::closeFullPlayer,
            onToggle = playerViewModel::togglePlayPause,
            onNext = playerViewModel::next,
            onPrevious = playerViewModel::previous,
            onSeek = playerViewModel::seekTo,
            onCycleRepeat = playerViewModel::cycleRepeat,
            onToggleShuffle = playerViewModel::toggleShuffle,
            onPlayAt = playerViewModel::playAt,
            onRemoveFromQueue = playerViewModel::removeFromQueue,
            onClearQueue = playerViewModel::clearQueue,
            onMessageShown = playerViewModel::consumeMessage,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
