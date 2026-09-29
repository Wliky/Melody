import 'package:flutter/material.dart';

import '../../core/theme/motion.dart';
import '../../core/theme/radii.dart';

/// 骨架屏盒子（对齐原版 SkeletonBox：默认 extraSmall=8 圆角）
class SkeletonBox extends StatefulWidget {
  const SkeletonBox({
    super.key,
    this.width,
    this.height,
    this.borderRadius,
    this.circular = false,
  });

  final double? width;
  final double? height;
  final BorderRadius? borderRadius;
  final bool circular;

  @override
  State<SkeletonBox> createState() => _SkeletonBoxState();
}

class _SkeletonBoxState extends State<SkeletonBox>
    with SingleTickerProviderStateMixin {
  late final AnimationController _controller = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 1400),
  );

  @override
  void initState() {
    super.initState();
    _controller.repeat(reverse: true);
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return AnimatedBuilder(
      animation: _controller,
      builder: (context, child) {
        final t = Curves.easeInOut.transform(_controller.value);
        return Container(
          width: widget.width,
          height: widget.height,
          decoration: BoxDecoration(
            borderRadius: widget.circular ? null : (widget.borderRadius ?? MelodyRadii.xsBorder),
            shape: widget.circular ? BoxShape.circle : BoxShape.rectangle,
            color: Color.lerp(
              scheme.surfaceContainerHighest,
              scheme.surfaceContainerLow,
              t * 0.5,
            ),
          ),
        );
      },
    );
  }
}

/// 歌曲行骨架（封面 48 + 两行文字）
class SongRowSkeleton extends StatelessWidget {
  const SongRowSkeleton({super.key});

  @override
  Widget build(BuildContext context) {
    return const Row(
      children: [
        SkeletonBox(width: 48, height: 48),
        SizedBox(width: 12),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              SkeletonBox(height: 16),
              SizedBox(height: 8),
              SkeletonBox(width: 160, height: 12),
            ],
          ),
        ),
      ],
    );
  }
}

/// 网格卡骨架（方形封面 + 标题行）
class PlaylistCardSkeleton extends StatelessWidget {
  const PlaylistCardSkeleton({super.key});

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: const [
        AspectRatio(aspectRatio: 1, child: SkeletonBox()),
        SizedBox(height: 8),
        SkeletonBox(height: 14),
      ],
    );
  }
}
