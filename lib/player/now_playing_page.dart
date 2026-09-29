import 'package:flutter/material.dart' hide RepeatMode;
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../core/theme/radii.dart';
import '../../core/theme/size.dart';
import '../../core/theme/spacing.dart';
import '../../data/models/models.dart';
import '../../shared/widgets/cover_image.dart';
import '../core/theme/motion.dart';
import 'mini_player_bar.dart';
import 'player_queue_notifier.dart';
import 'playback_preferences.dart';

/// 播放页（对齐原版 NowPlayingScreen 核心）：
/// 大封面 Hero + 进度条 + 播放控制 + 队列按钮
class NowPlayingScreen extends ConsumerWidget {
  const NowPlayingScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(playerQueueProvider);
    final song = state.current;
    final scheme = Theme.of(context).colorScheme;

    return Scaffold(
      appBar: AppBar(
        leading: IconButton(
          icon: const Icon(Icons.keyboard_arrow_down_rounded),
          onPressed: () => context.pop(),
        ),
        title: Text(song?.name ?? '未在播放',
            maxLines: 1, overflow: TextOverflow.ellipsis),
        centerTitle: true,
      ),
      body: song == null
          ? const Center(child: Text('队列为空'))
          : _Body(state: state, song: song),
      backgroundColor: scheme.surface,
    );
  }
}

class _Body extends ConsumerWidget {
  const _Body({required this.state, required this.song});
  final QueueState state;
  final Song song;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final notifier = ref.read(playerQueueProvider.notifier);
    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.all(Spacing.xl),
        child: Column(
          children: [
            const Spacer(),
            // 封面（Hero：与迷你条共享元素）
            Hero(
              tag: 'now_playing_cover',
              child: AspectRatio(
                aspectRatio: 1,
                child: MelodyCoverImage(
                  url: song.displayCover,
                  borderRadius: MelodyRadii.lgBorder,
                ),
              ),
            ),
            const SizedBox(height: Spacing.xl),
            Text(song.name,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: Theme.of(context)
                    .textTheme
                    .headlineSmall
                    ?.copyWith(fontWeight: FontWeight.w600)),
            const SizedBox(height: Spacing.xs),
            Text(song.subtitle,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                    color: Theme.of(context).colorScheme.onSurfaceVariant)),
            const SizedBox(height: Spacing.lg),
            // 进度条（拖动松手才 seek——对齐原版）
            _SeekBar(state: state),
            const SizedBox(height: Spacing.lg),
            // 控制区
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceEvenly,
              children: [
                IconButton(
                  icon: Icon(Icons.shuffle_rounded,
                      size: MelodySize.iconL,
                      color: state.shuffle
                          ? Theme.of(context).colorScheme.primary
                          : Theme.of(context).colorScheme.onSurfaceVariant),
                  onPressed: notifier.toggleShuffle,
                ),
                IconButton(
                  icon: Icon(Icons.skip_previous_rounded, size: MelodySize.iconL),
                  onPressed: notifier.previous,
                ),
                _PlayButton(state: state),
                IconButton(
                  icon: Icon(Icons.skip_next_rounded, size: MelodySize.iconL),
                  onPressed: notifier.next,
                ),
                IconButton(
                  icon: Icon(
                    switch (state.repeatMode) {
                      RepeatMode.all => Icons.repeat_rounded,
                      RepeatMode.one => Icons.repeat_one_rounded,
                      RepeatMode.off => Icons.repeat_outlined,
                    },
                    size: MelodySize.iconL,
                    color: state.repeatMode == RepeatMode.off
                        ? Theme.of(context).colorScheme.onSurfaceVariant
                        : Theme.of(context).colorScheme.primary,
                  ),
                  onPressed: notifier.cycleRepeatMode,
                ),
              ],
            ),
            const Spacer(),
            // 队列入口
            Align(
              alignment: Alignment.center,
              child: TextButton.icon(
                onPressed: () => showModalBottomSheet<void>(
                  context: context,
                  showDragHandle: true,
                  isScrollControlled: true,
                  builder: (_) => const QueueSheet(),
                ),
                icon: const Icon(Icons.queue_music_outlined),
                label: Text('播放队列（${state.queue.length}）'),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// 进度条：本地拖动值，松手才 seek（对齐原版 500ms 采样不干扰拖动）
class _SeekBar extends ConsumerStatefulWidget {
  const _SeekBar({required this.state});
  final QueueState state;

  @override
  ConsumerState<_SeekBar> createState() => _SeekBarState();
}

class _SeekBarState extends ConsumerState<_SeekBar> {
  double? _dragValue;

  @override
  Widget build(BuildContext context) {
    final state = widget.state;
    final duration = state.durationMs > 0 ? state.durationMs : 0;
    final value = _dragValue ??
        (duration > 0 ? (state.positionMs / duration).clamp(0.0, 1.0) : 0.0);
    return Row(
      children: [
        Text(
            _fmt(_dragValue != null
                ? (_dragValue! * duration).round()
                : state.positionMs),
            style: Theme.of(context).textTheme.bodySmall),
        Expanded(
          child: Slider(
            value: value,
            onChanged: duration <= 0 ? null : (v) => setState(() => _dragValue = v),
            onChangeEnd: (v) {
              if (duration > 0) {
                ref
                    .read(playerQueueProvider.notifier)
                    .seek((v * duration).round());
              }
              setState(() => _dragValue = null);
            },
          ),
        ),
        Text(_fmt(duration), style: Theme.of(context).textTheme.bodySmall),
      ],
    );
  }

  static String _fmt(int ms) {
    if (ms < 0) ms = 0;
    final s = ms ~/ 1000;
    return '${(s ~/ 60).toString().padLeft(2, '0')}:${(s % 60).toString().padLeft(2, '0')}';
  }
}

/// 播放大按钮：resolving 转圈（对齐原版播放键转圈语义）
class _PlayButton extends ConsumerWidget {
  const _PlayButton({required this.state});
  final QueueState state;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final scheme = Theme.of(context).colorScheme;
    if (state.resolving) {
      return SizedBox(
        width: MelodySize.iconPlay + 20,
        height: MelodySize.iconPlay + 20,
        child: const CircularProgressIndicator(strokeWidth: 3),
      );
    }
    return IconButton.filled(
      iconSize: MelodySize.iconPlay,
      style: IconButton.styleFrom(
        backgroundColor: scheme.primary,
        foregroundColor: scheme.onPrimary,
        minimumSize: Size(MelodySize.iconPlay + 20, MelodySize.iconPlay + 20),
      ),
      icon:
          Icon(state.isPlaying ? Icons.pause_rounded : Icons.play_arrow_rounded),
      onPressed: () => ref.read(playerQueueProvider.notifier).togglePlayPause(),
    );
  }
}
