import 'package:flutter/material.dart';

import '../../core/theme/motion.dart';
import '../../core/theme/radii.dart';
import '../../core/theme/size.dart';
import '../../core/theme/spacing.dart';
import '../../core/utils/cover_url.dart';
import '../../data/models/models.dart';
import 'cover_image.dart';

/// 按压缩放容器（对齐原版 PressableScale）
class PressableScale extends StatefulWidget {
  const PressableScale({
    super.key,
    required this.child,
    this.onTap,
    this.onLongPress,
    this.scale = MelodyMotion.pressedScale,
  });

  final Widget child;
  final VoidCallback? onTap;
  final VoidCallback? onLongPress;
  final double scale;

  @override
  State<PressableScale> createState() => _PressableScaleState();
}

class _PressableScaleState extends State<PressableScale> {
  bool _pressed = false;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTapDown: (_) => setState(() => _pressed = true),
      onTapUp: (_) => setState(() => _pressed = false),
      onTapCancel: () => setState(() => _pressed = false),
      onTap: widget.onTap,
      onLongPress: widget.onLongPress,
      child: AnimatedScale(
        scale: _pressed ? widget.scale : 1.0,
        duration: MelodyMotion.durationShort,
        child: widget.child,
      ),
    );
  }
}

/// 歌曲行（对齐原版 SongRow：48 封面 + 标题/副标题 + 尾随控件）
class SongRow extends StatelessWidget {
  const SongRow({
    super.key,
    required this.song,
    this.onTap,
    this.trailing,
    this.highlight = false,
    this.showCover = true,
  });

  final Song song;
  final VoidCallback? onTap;
  final Widget? trailing;
  /// 当前播放曲高亮
  final bool highlight;
  final bool showCover;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return PressableScale(
      onTap: onTap,
      child: SizedBox(
        height: MelodySize.rowSong,
        child: Row(
          children: [
            if (showCover)
              Padding(
                padding: const EdgeInsets.only(right: Spacing.sm + Spacing.xs),
                child: MelodyCoverImage(
                  url: song.displayCover,
                  width: MelodySize.coverS,
                  height: MelodySize.coverS,
                ),
              ),
            Expanded(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    song.name,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: Theme.of(context).textTheme.bodyLarge?.copyWith(
                          color: highlight
                              ? scheme.primary
                              : scheme.onSurface,
                          fontWeight: highlight ? FontWeight.w600 : null,
                        ),
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
            if (trailing != null) ...[
              const SizedBox(width: Spacing.sm),
              trailing!,
            ],
          ],
        ),
      ),
    );
  }
}

/// 歌单行（对齐原版 PlaylistRow：56 封面 + 名称 + 数量）
class PlaylistRow extends StatelessWidget {
  const PlaylistRow({
    super.key,
    required this.playlist,
    this.onTap,
    this.trailing,
  });

  final Playlist playlist;
  final VoidCallback? onTap;
  final Widget? trailing;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return PressableScale(
      onTap: onTap,
      child: SizedBox(
        height: MelodySize.rowPlaylist,
        child: Row(
          children: [
            Padding(
              padding: const EdgeInsets.only(right: Spacing.sm + Spacing.xs),
              child: MelodyCoverImage(
                url: playlist.coverUrl,
                width: MelodySize.coverM,
                height: MelodySize.coverM,
              ),
            ),
            Expanded(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    playlist.name,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: Theme.of(context).textTheme.bodyLarge,
                  ),
                  const SizedBox(height: 2),
                  Text(
                    '${playlist.trackCount} 首',
                    style: Theme.of(context).textTheme.bodySmall?.copyWith(
                          color: scheme.onSurfaceVariant,
                        ),
                  ),
                ],
              ),
            ),
            if (trailing != null) ...[
              const SizedBox(width: Spacing.sm),
              trailing!,
            ],
          ],
        ),
      ),
    );
  }
}

/// 网格歌单卡（对齐原版 PlaylistCard）
class PlaylistCard extends StatelessWidget {
  const PlaylistCard({
    super.key,
    required this.playlist,
    this.onTap,
  });

  final Playlist playlist;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return PressableScale(
      onTap: onTap,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          AspectRatio(
            aspectRatio: 1,
            child: Stack(
              children: [
                Positioned.fill(
                  child: MelodyCoverImage(
                    url: playlist.coverUrl,
                    borderRadius: MelodyRadii.mdBorder,
                  ),
                ),
                if (playlist.playCount > 0)
                  Positioned(
                    right: Spacing.sm,
                    top: Spacing.sm,
                    child: Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: Spacing.sm,
                        vertical: 2,
                      ),
                      decoration: BoxDecoration(
                        color: Colors.black.withValues(alpha: 0.6),
                        borderRadius: MelodyRadii.xsBorder,
                      ),
                      child: Text(
                        formatPlayCount(playlist.playCount),
                        style: const TextStyle(
                          color: Colors.white,
                          fontSize: 11,
                        ),
                      ),
                    ),
                  ),
              ],
            ),
          ),
          const SizedBox(height: Spacing.sm),
          Text(
            playlist.name,
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
            style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                  color: scheme.onSurface,
                ),
          ),
        ],
      ),
    );
  }
}

/// 区块标题（对齐原版 SectionHeader）
class SectionHeader extends StatelessWidget {
  const SectionHeader({
    super.key,
    required this.title,
    this.trailing,
  });

  final String title;
  final Widget? trailing;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Expanded(
          child: Text(
            title,
            style: Theme.of(context)
                .textTheme
                .titleMedium
                ?.copyWith(fontWeight: FontWeight.w600),
          ),
        ),
        if (trailing != null) trailing!,
      ],
    );
  }
}
