import 'package:cached_network_image/cached_network_image.dart';
import 'package:flutter/material.dart';

import '../../core/theme/radii.dart';
import '../../core/utils/cover_url.dart';

/// 封面图（对齐原版 CoverImage：默认 token small=12 圆角 + URL 加参 + 占位）
class MelodyCoverImage extends StatelessWidget {
  const MelodyCoverImage({
    super.key,
    required this.url,
    this.width,
    this.height,
    this.borderRadius,
    this.fit = BoxFit.cover,
    this.placeholderColor,
  });

  final String? url;
  final double? width;
  final double? height;
  final BorderRadius? borderRadius;
  final BoxFit fit;
  final Color? placeholderColor;

  @override
  Widget build(BuildContext context) {
    final resized = resizeCoverUrl(url);
    final radius = borderRadius ?? MelodyRadii.smBorder;
    Widget image;
    if (resized.isEmpty) {
      image = Container(
        width: width,
        height: height,
        color: placeholderColor ??
            Theme.of(context).colorScheme.surfaceContainerHighest,
        child: Icon(
          Icons.music_note_outlined,
          color: Theme.of(context).colorScheme.onSurfaceVariant,
        ),
      );
    } else {
      image = CachedNetworkImage(
        imageUrl: resized,
        width: width,
        height: height,
        fit: fit,
        placeholder: (_, __) => Container(
          color: placeholderColor ??
              Theme.of(context).colorScheme.surfaceContainerHighest,
        ),
        errorWidget: (_, __, ___) => Container(
          color: placeholderColor ??
              Theme.of(context).colorScheme.surfaceContainerHighest,
          child: Icon(
            Icons.music_note_outlined,
            color: Theme.of(context).colorScheme.onSurfaceVariant,
          ),
        ),
      );
    }
    return ClipRRect(borderRadius: radius, child: image);
  }
}
