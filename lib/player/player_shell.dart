import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../core/theme/motion.dart';
import '../core/theme/size.dart';
import '../core/theme/spacing.dart';
import 'mini_player_bar.dart';
import 'now_playing_page.dart';
import 'player_queue_notifier.dart';

/// 主框架（对齐原版 MainScreen）：
/// - Tab 框架（首页/我的）+ NavigationBar / 宽屏 NavigationRail
/// - 迷你条悬浮底部，点歌自动唤回；点开进播放页
/// - ≥600dp 底栏换侧边 Rail（对齐原版 useRail）
class MainShell extends ConsumerStatefulWidget {
  const MainShell({super.key, required this.child});
  final Widget child;

  @override
  ConsumerState<MainShell> createState() => _MainShellState();
}

class _MainShellState extends ConsumerState<MainShell> {
  int _lastVisitedTabIndex = 0;

  @override
  Widget build(BuildContext context) {
    final queue = ref.watch(playerQueueProvider);
    final useRail = MediaQuery.sizeOf(context).width >= 600;
    final location = GoRouterState.of(context).uri.path;
    final currentIndex = location.startsWith('/mine') ? 1 : 0;
    _lastVisitedTabIndex = currentIndex;

    final body = Column(
      children: [
        Expanded(child: widget.child),
        // 迷你条（有队列才显示；播放页打开时隐藏由 NowPlaying 覆盖层处理）
        if (queue.hasQueue)
          Align(
            alignment: Alignment.center,
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 420),
              child: Padding(
                padding: const EdgeInsets.fromLTRB(
                    Spacing.md, 0, Spacing.md, Spacing.sm),
                child: MiniPlayerBar(
                  onOpen: () => context.push('/now-playing'),
                ),
              ),
            ),
          ),
        if (!useRail)
          NavigationBar(
            selectedIndex: currentIndex,
            onDestinationSelected: (i) => context.go(i == 0 ? '/' : '/mine'),
            destinations: const [
              NavigationDestination(
                icon: Icon(Icons.home_outlined),
                selectedIcon: Icon(Icons.home),
                label: '首页',
              ),
              NavigationDestination(
                icon: Icon(Icons.person_outline),
                selectedIcon: Icon(Icons.person),
                label: '我的',
              ),
            ],
          ),
      ],
    );

    if (!useRail) return Scaffold(body: body);

    // 宽屏：侧边 Rail
    return Scaffold(
      body: Row(
        children: [
          NavigationRail(
            selectedIndex: currentIndex,
            onDestinationSelected: (i) => context.go(i == 0 ? '/' : '/mine'),
            labelType: NavigationRailLabelType.selected,
            destinations: const [
              NavigationRailDestination(
                icon: Icon(Icons.home_outlined),
                selectedIcon: Icon(Icons.home),
                label: Text('首页'),
              ),
              NavigationRailDestination(
                icon: Icon(Icons.person_outline),
                selectedIcon: Icon(Icons.person),
                label: Text('我的'),
              ),
            ],
          ),
          VerticalDivider(
            thickness: 1,
            width: 1,
            color: Theme.of(context).colorScheme.outlineVariant,
          ),
          Expanded(child: body),
        ],
      ),
    );
  }
}

/// 播放页薄壳（NowPlayingPage 实现在 player/now_playing_page.dart）
class NowPlayingPage extends StatelessWidget {
  const NowPlayingPage({super.key});

  @override
  Widget build(BuildContext context) {
    return const NowPlayingScreen();
  }
}
