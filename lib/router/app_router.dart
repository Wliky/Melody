import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../features/home/home_screen.dart';
import '../features/login/login_screen.dart';
import '../features/mine/mine_screen.dart';
import '../features/songlist/playlist_detail_screen.dart';
import '../player/player_shell.dart';

/// GoRouter 配置（对齐原版 Overlay 栈导航语义）
final appRouter = GoRouter(
  initialLocation: '/',
  routes: [
    ShellRoute(
      builder: (context, state, child) => MainShell(child: child),
      routes: [
        GoRoute(
          path: '/',
          name: 'home',
          pageBuilder: (context, state) => _fadePage(state, const HomeScreen()),
          routes: const [],
        ),
        GoRoute(
          path: '/mine',
          name: 'mine',
          pageBuilder: (context, state) => _fadePage(state, const MineScreen()),
          routes: const [],
        ),
      ],
    ),
    GoRoute(
      path: '/login',
      name: 'login',
      pageBuilder: (context, state) =>
          _slidePage(state, const LoginScreen()),
    ),
    // 歌单详情：二级页右滑入
    GoRoute(
      path: '/playlist/:id',
      name: 'playlist',
      pageBuilder: (context, state) => _slidePage(
        state,
        PlaylistDetailScreen(
          playlistId: int.parse(state.pathParameters['id']!),
        ),
      ),
    ),
    // 播放页：覆盖一切（根 Navigator push + 封面 Hero）
    GoRoute(
      path: '/now-playing',
      name: 'nowPlaying',
      parentNavigatorKey: rootNavigatorKey,
      pageBuilder: (context, state) => _playerPage(state),
    ),
  ],
);

final rootNavigatorKey = GlobalKey<NavigatorState>();

/// 二级页：右侧滑入 + 淡入（OverlayEnter 语义）
CustomTransitionPage<void> _slidePage(GoRouterState state, Widget child) {
  return CustomTransitionPage(
    key: state.pageKey,
    child: child,
    transitionDuration: const Duration(milliseconds: 280),
    transitionsBuilder: (context, animation, secondary, child) {
      final curved = CurvedAnimation(
        parent: animation,
        curve: const Cubic(0.2, 0.0, 0.0, 1.0),
      );
      return SlideTransition(
        position: Tween(
          begin: const Offset(0.12, 0),
          end: Offset.zero,
        ).animate(curved),
        child: FadeTransition(opacity: curved, child: child),
      );
    },
  );
}

/// Tab 切换：淡入（原版 AnimatedContent 语义）
CustomTransitionPage<void> _fadePage(GoRouterState state, Widget child) {
  return CustomTransitionPage(
    key: state.pageKey,
    child: child,
    transitionDuration: const Duration(milliseconds: 220),
    transitionsBuilder: (context, animation, secondary, child) =>
        FadeTransition(opacity: animation, child: child),
  );
}

/// 播放页：主框架淡出 → 播放页 1/5 上滑 + 淡入（时长组 360/180ms）
CustomTransitionPage<void> _playerPage(GoRouterState state) {
  return CustomTransitionPage(
    key: state.pageKey,
    child: const NowPlayingPage(),
    transitionDuration: const Duration(milliseconds: 360),
    reverseTransitionDuration: const Duration(milliseconds: 320),
    transitionsBuilder: (context, animation, secondary, child) {
      final enter = CurvedAnimation(parent: animation, curve: Curves.easeOut);
      return SlideTransition(
        position: Tween(
          begin: const Offset(0, 0.2),
          end: Offset.zero,
        ).animate(enter),
        child: FadeTransition(
          opacity: Tween(begin: 0.0, end: 1.0).animate(enter),
          child: child,
        ),
      );
    },
  );
}
