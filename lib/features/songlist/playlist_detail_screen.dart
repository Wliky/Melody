import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/theme/spacing.dart';
import '../../core/utils/cover_url.dart';
import '../../player/player_queue_notifier.dart';
import '../../shared/widgets/cover_image.dart';
import '../../shared/widgets/rows.dart';
import '../../shared/widgets/skeleton.dart';
import '../../shared/widgets/state_views.dart';
import 'playlist_detail_provider.dart';

/// 歌单详情（对齐原版 SonglistScreen 核心）：
/// 头图 + 描述 + 全部播放 + 曲目列表 + 当前曲高亮
class PlaylistDetailScreen extends ConsumerStatefulWidget {
  const PlaylistDetailScreen({super.key, required this.playlistId});
  final int playlistId;

  @override
  ConsumerState<PlaylistDetailScreen> createState() =>
      _PlaylistDetailScreenState();
}

class _PlaylistDetailScreenState extends ConsumerState<PlaylistDetailScreen> {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      ref.read(playlistDetailProvider(widget.playlistId).notifier).load();
    });
  }

  @override
  Widget build(BuildContext context) {
    final detail = ref.watch(playlistDetailProvider(widget.playlistId));
    final queue = ref.watch(playerQueueProvider);

    return Scaffold(
      body: CustomScrollView(
        slivers: [
          SliverAppBar(
            expandedHeight: 220,
            pinned: true,
            flexibleSpace: FlexibleSpaceBar(
              title: Text(
                detail.playlist.name,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
              background: MelodyCoverImage(
                url: detail.playlist.coverUrl,
                borderRadius: BorderRadius.zero,
              ),
            ),
          ),
          SliverToBoxAdapter(child: _Header(detail: detail)),
          _SliverBody(detail: detail, queue: queue),
        ],
      ),
    );
  }
}

/// 头部：描述 + 播放全部
class _Header extends ConsumerWidget {
  const _Header({required this.detail});
  final PlaylistDetailState detail;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final desc = detail.playlist.description;
    return Padding(
      padding: const EdgeInsets.fromLTRB(
          Spacing.screen, Spacing.md, Spacing.screen, 0),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (desc != null && desc.isNotEmpty) ...[
            Text(
              desc,
              maxLines: 3,
              overflow: TextOverflow.ellipsis,
              style: Theme.of(context).textTheme.bodySmall?.copyWith(
                    color: Theme.of(context).colorScheme.onSurfaceVariant,
                  ),
            ),
            const SizedBox(height: Spacing.md),
          ],
          FilledButton.icon(
            onPressed: detail.songs.isEmpty
                ? null
                : () => ref
                    .read(playerQueueProvider.notifier)
                    .playQueue(detail.songs, 0),
            icon: const Icon(Icons.play_arrow_rounded),
            label: Text('播放全部（${detail.songs.length}）'),
          ),
          const SizedBox(height: Spacing.sm),
        ],
      ),
    );
  }
}

/// 列表体：骨架 / 错误 / 空 / 歌曲
class _SliverBody extends ConsumerWidget {
  const _SliverBody({required this.detail, required this.queue});
  final PlaylistDetailState detail;
  final QueueState queue;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final pad = const EdgeInsets.symmetric(
        horizontal: Spacing.screen, vertical: Spacing.sm);
    if (detail.loading) {
      return SliverPadding(
        padding: pad,
        sliver: SliverList(
          delegate: SliverChildBuilderDelegate(
            (_, __) => const Padding(
              padding: EdgeInsets.only(bottom: Spacing.md),
              child: SongRowSkeleton(),
            ),
            childCount: 10,
          ),
        ),
      );
    }
    if (detail.error != null) {
      return SliverFillRemaining(
        hasScrollBody: false,
        child: ErrorStateView(
          error: detail.error!,
          onRetry: () {}, // 由外层 provider 重建触发
        ),
      );
    }
    if (detail.songs.isEmpty) {
      return const SliverFillRemaining(
        hasScrollBody: false,
        child: EmptyStateView(message: '歌单暂无曲目'),
      );
    }
    return SliverPadding(
      padding: pad,
      sliver: SliverList(
        delegate: SliverChildBuilderDelegate(
          (_, i) {
            final song = detail.songs[i];
            final isCurrent = queue.current?.id == song.id;
            return Padding(
              padding: const EdgeInsets.only(bottom: Spacing.sm),
              child: SongRow(
                song: song,
                highlight: isCurrent,
                onTap: () => ref
                    .read(playerQueueProvider.notifier)
                    .playQueue(detail.songs, i),
                trailing: Text(
                  formatDuration(song.durationMs),
                  style: Theme.of(context).textTheme.bodySmall?.copyWith(
                        color: Theme.of(context).colorScheme.onSurfaceVariant,
                      ),
                ),
              ),
            );
          },
          childCount: detail.songs.length,
        ),
      ),
    );
  }
}

