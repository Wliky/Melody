import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/network/app_error.dart';
import '../../core/theme/spacing.dart';
import '../../data/cache/repo_cache.dart';
import '../../data/models/models.dart';
import '../../data/repos/home_repository.dart';
import '../../shared/widgets/rows.dart';
import '../../shared/widgets/skeleton.dart';
import '../../shared/widgets/state_views.dart';
import '../login/session_controller.dart';

/// 「我的」数据流（对齐原版 MineViewModel）：缓存（按 uid 校验）→ 用户歌单
class MineState {
  const MineState({this.playlists = const [], this.loading = false, this.error});
  final List<Playlist> playlists;
  final bool loading;
  final AppError? error;
}

class MineNotifier extends Notifier<MineState> {
  static const _cacheKey = 'mine:snapshot';

  @override
  MineState build() {
    _load();
    return const MineState(loading: true);
  }

  Future<void> _load() async {
    final uid = ref.read(sessionProvider).user?.id;
    if (uid == null) {
      state = const MineState();
      return;
    }
    final cached = await ref.read(repoCacheProvider).readStale<MineSnapshot>(
          key: _cacheKey,
          ttlMs: const Duration(minutes: 10).inMilliseconds,
          fromJson: MineSnapshot.fromJson,
        );
    if (cached != null && cached.uid == uid) {
      state = MineState(playlists: cached.playlists);
      return;
    }
    final res = await ref.read(homeRepositoryProvider).userPlaylists(uid);
    if (res case Failure(:final error)) {
      state = MineState(error: error);
      return;
    }
    final playlists = res.valueOrNull ?? const [];
    ref.read(repoCacheProvider).write(
        _cacheKey, MineSnapshot(uid: uid, playlists: playlists).toJson());
    state = MineState(playlists: playlists);
  }

  Future<void> refresh() => _load();
}

final mineProvider = NotifierProvider<MineNotifier, MineState>(MineNotifier.new);

/// 「我的」页（对齐原版 MineScreen）：游客引导 / 账号卡 + 我的歌单
class MineScreen extends ConsumerWidget {
  const MineScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final session = ref.watch(sessionProvider);
    if (!session.loggedIn) return const _GuestView();

    final user = session.user!;
    return Scaffold(
      appBar: AppBar(
        title: const Text('我的'),
        actions: [
          IconButton(
            icon: const Icon(Icons.logout_outlined),
            tooltip: '退出登录',
            onPressed: () async {
              await ref.read(sessionProvider.notifier).logout();
              if (context.mounted) context.go('/');
            },
          ),
        ],
      ),
      body: _UserBody(user: user),
    );
  }
}

/// 登录用户主体：账号卡 + 我的歌单
class _UserBody extends ConsumerWidget {
  const _UserBody({required this.user});
  final User user;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final mine = ref.watch(mineProvider);
    return RefreshIndicator(
      onRefresh: () => ref.read(mineProvider.notifier).refresh(),
      child: ListView(
        padding: const EdgeInsets.all(Spacing.screen),
        children: [
          Row(children: [
            CircleAvatar(
              radius: 28,
              backgroundImage: user.avatarUrl != null
                  ? NetworkImage(user.avatarUrl!)
                  : null,
              child:
                  user.avatarUrl == null ? const Icon(Icons.person) : null,
            ),
            const SizedBox(width: Spacing.md),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(user.nickname,
                      style: Theme.of(context).textTheme.titleMedium),
                  if (user.signature?.isNotEmpty ?? false)
                    Text(user.signature!,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: Theme.of(context).textTheme.bodySmall),
                ],
              ),
            ),
          ]),
          const SizedBox(height: Spacing.lg),
          const SectionHeader(title: '我的歌单'),
          const SizedBox(height: Spacing.md),
          if (mine.loading)
            ...List.generate(
              6,
              (_) => const Padding(
                padding: EdgeInsets.only(bottom: Spacing.md),
                child: SongRowSkeleton(),
              ),
            )
          else if (mine.error != null)
            ErrorStateView(
              error: mine.error!,
              onRetry: () => ref.read(mineProvider.notifier).refresh(),
            )
          else if (mine.playlists.isEmpty)
            const EmptyStateView(message: '还没有歌单')
          else
            for (final p in mine.playlists)
              Padding(
                padding: const EdgeInsets.only(bottom: Spacing.sm),
                child: PlaylistRow(
                  playlist: p,
                  onTap: () => context.go('/playlist/${p.id}'),
                ),
              ),
        ],
      ),
    );
  }
}

/// 游客视图：登录引导
class _GuestView extends StatelessWidget {
  const _GuestView();

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('我的')),
      body: Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.music_note_outlined,
                size: 64,
                color: Theme.of(context).colorScheme.onSurfaceVariant),
            const SizedBox(height: Spacing.md),
            const Text('登录后同步你的歌单与红心'),
            const SizedBox(height: Spacing.lg),
            FilledButton(
              onPressed: () => context.go('/login'),
              child: const Text('扫码登录'),
            ),
          ],
        ),
      ),
    );
  }
}
