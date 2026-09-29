import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../core/theme/motion.dart';
import '../core/theme/radii.dart';
import '../core/theme/size.dart';
import '../core/theme/spacing.dart';
import '../core/utils/cover_url.dart';
import '../data/models/models.dart';
import '../shared/widgets/cover_image.dart';
import 'player_queue_notifier.dart';

/// 迷你播放条（对齐原版 MiniPlayerBar）：
/// 封面 + 标题/副标题 + 播放键 + 下一曲 + 队列键
class MiniPlayerBar extends ConsumerWidget {
  const MiniPlayerBar({super.key, this.onOpen});
  final VoidCallback? onOpen;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(playerQueueProvider);
    final song = state.current;
    if (song == null) return const SizedBox.shrink();
    final scheme = Theme.of(context).colorScheme;

    return AnimatedScale(
      scale: 1.0,
      duration: MelodyMotion.durationShort,
      child: Material(
        color: scheme.surfaceContainerHigh,
        elevation: 4,
        borderRadius: MelodyRadii.mdBorder,
        child: InkWell(
          borderRadius: MelodyRadii.mdBorder,
          onTap: onOpen,
          child: SizedBox(
            height: 64,
            child: Row(
              children: [
                const SizedBox(width: Spacing.sm),
                Hero(
                  tag: 'now_playing_cover',
                  child: ClipRRect(
                    borderRadius: MelodyRadii.smBorder,
                    child: MelodyCoverImage(
                      url: song.displayCover,
                      width: MelodySize.coverS,
                      height: MelodySize.coverS,
                    ),
                  ),
                ),
                const SizedBox(width: Spacing.sm + Spacing.xs),
                Expanded(
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        song.name,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: Theme.of(context)
                            .textTheme
                            .bodyMedium
                            ?.copyWith(fontWeight: FontWeight.w600),
                      ),
                      const SizedBox(height: 2),
                      Text(
                        song.subtitle,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: Theme.of(context).textTheme.bodySmall?.copyWith(
                              color: scheme.onSurfaceVariant,
                            ),
                      ),
                    ],
                  ),
                ),
                // 播放/暂停
                _ControlButton(
                  icon: state.resolving
                      ? null
                      : (state.isPlaying
                          ? Icons.pause_outlined
                          : Icons.play_arrow_outlined),
                  size: MelodySize.iconL,
                  onTap: () =>
                      ref.read(playerQueueProvider.notifier).togglePlayPause(),
                ),
                _ControlButton(
                  icon: Icons.skip_next_outlined,
                  onTap: () =>
                      ref.read(playerQueueProvider.notifier).next(),
                ),
                _ControlButton(
                  icon: Icons.queue_music_outlined,
                  onTap: () => _openQueue(context, ref),
                ),
                const SizedBox(width: Spacing.xs),
              ],
            ),
          ),
        ),
      ),
    );
  }

  void _openQueue(BuildContext context, WidgetRef ref) {
    showModalBottomSheet<void>(
      context: context,
      showDragHandle: true,
      isScrollControlled: true,
      builder: (_) => const QueueSheet(),
    );
  }
}

class _ControlButton extends StatelessWidget {
  const _ControlButton({
    required this.icon,
    this.onTap,
    this.size = MelodySize.iconM,
  });
  final IconData? icon;
  final VoidCallback? onTap;
  final double size;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return IconButton(
      onPressed: onTap,
      icon: icon == null
          ? SizedBox(
              width: size * 0.7,
              height: size * 0.7,
              child: const CircularProgressIndicator(strokeWidth: 2),
            )
          : Icon(icon, size: size),
      color: scheme.primary,
      constraints: const BoxConstraints(minWidth: MelodySize.touchMin),
    );
  }
}

/// 播放队列弹层（对齐原版 PlayerQueueSheet：当前曲高亮 + 点击切歌）
class QueueSheet extends ConsumerWidget {
  const QueueSheet({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(playerQueueProvider);
    final scheme = Theme.of(context).colorScheme;

    return DraggableScrollableSheet(
      expand: false,
      maxChildSize: 0.6,
      initialChildSize: 0.6,
      minChildSize: 0.3,
      builder: (context, controller) {
        if (!state.hasQueue) {
          return const Center(child: Text('队列为空'));
        }
        return Column(
          children: [
            Padding(
              padding: const EdgeInsets.all(Spacing.md),
              child: Row(
                children: [
                  Text(
                    '当前播放（${state.queue.length}）',
                    style: Theme.of(context)
                        .textTheme
                        .titleMedium
                        ?.copyWith(fontWeight: FontWeight.w600),
                  ),
                ],
              ),
            ),
            Expanded(
              child: ListView.builder(
                controller: controller,
                itemCount: state.queue.length,
                itemBuilder: (context, i) {
                  final song = state.queue[i];
                  final current = i == state.index;
                  return ListTile(
                    dense: true,
                    leading: MelodyCoverImage(
                      url: song.displayCover,
                      width: MelodySize.coverS,
                      height: MelodySize.coverS,
                    ),
                    title: Text(
                      song.name,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                        color: current ? scheme.primary : null,
                        fontWeight: current ? FontWeight.w600 : null,
                      ),
                    ),
                    subtitle: Text(
                      song.subtitle,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                    trailing: current && state.isPlaying
                        ? const _EqualizerBars()
                        : null,
                    onTap: () =>
                        ref.read(playerQueueProvider.notifier).playAt(i),
                  );
                },
              ),
            ),
          ],
        );
      },
    );
  }
}

/// 当前播放跳动指示（三根小竖条）
class _EqualizerBars extends StatefulWidget {
  const _EqualizerBars();

  @override
  State<_EqualizerBars> createState() => _EqualizerBarsState();
}

class _EqualizerBarsState extends State<_EqualizerBars>
    with SingleTickerProviderStateMixin {
  late final AnimationController _c = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 700),
  )..repeat(reverse: true);

  @override
  void dispose() {
    _c.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final color = Theme.of(context).colorScheme.primary;
    return AnimatedBuilder(
      animation: _c,
      builder: (context, _) {
        return Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            for (var i = 0; i < 3; i++)
              Container(
                margin: const EdgeInsets.symmetric(horizontal: 1),
                width: 2.5,
                height: 6 +
                    10 *
                        ((Curves.easeInOut.transform(
                                (_c.value + i * 0.3) % 1.0)) as double),
                color: color,
              ),
          ],
        );
      },
    );
  }
}
